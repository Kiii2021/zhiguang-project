package com.tongji.counter.event;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.schema.CounterKeys;
import com.tongji.counter.schema.CounterSchema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 灾难场景下的计数重建消费者：基于 earliest 回放历史事件，直接折叠到 SDS。
 * 默认关闭，仅当 counter.rebuild.enabled=true 时启用。
 */
@Service
@ConditionalOnProperty(name = "counter.rebuild.enabled", havingValue = "true") // 读application.yml
public class CounterRebuildConsumer {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> incrScript;

    public CounterRebuildConsumer(ObjectMapper objectMapper, StringRedisTemplate redis) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.incrScript = new DefaultRedisScript<>();
        this.incrScript.setScriptText(INCR_FIELD_LUA);
        this.incrScript.setResultType(Long.class);
    }

    @KafkaListener(topics = CounterTopics.EVENTS, groupId = "counter-rebuild",
            properties = {"auto.offset.reset=earliest"})
    public void onMessage(String message, Acknowledgment ack) throws Exception {
        // 灾难场景下, 不写redis聚合桶了, 直接把消息的事件计数写到redis SDS里面
        // 同一个topic的不同消费者组, 因为广播机制, 他们各自的位点是独立的
        // 所以这个重建其实就是多加一个消费者组呗
        CounterEvent evt = objectMapper.readValue(message, CounterEvent.class);
        String cntKey = CounterKeys.sdsKey(evt.getEntityType(), evt.getEntityId());
        try {
            redis.execute(incrScript, List.of(cntKey),
                    String.valueOf(CounterSchema.SCHEMA_LEN),
                    String.valueOf(CounterSchema.FIELD_SIZE),
                    String.valueOf(evt.getIdx()),
                    String.valueOf(evt.getDelta())
            );
            ack.acknowledge();
        }catch (Exception e){
            // 不提交位点, 如果出错的话
        }
    }


    // 与聚合消费者的原子计数折叠脚本一致
    private static final String INCR_FIELD_LUA = """
            
            local cntKey = KEYS[1]
            local schemaLen = tonumber(ARGV[1])
            local fieldSize = tonumber(ARGV[2]) -- 固定为4
            local idx = tonumber(ARGV[3])
            local delta = tonumber(ARGV[4])
            
            local function read32be(s, off)
              local b = {string.byte(s, off+1, off+4)}
              local n = 0
              for i=1,4 do n = n * 256 + b[i] end
              return n
            end
            
            local function write32be(n)
              local t = {}
              for i=4,1,-1 do t[i] = n % 256; n = math.floor(n/256) end
              return string.char(unpack(t))
            end
            
            local cnt = redis.call('GET', cntKey)
            if not cnt then cnt = string.rep(string.char(0), schemaLen * fieldSize) end
            local off = idx * fieldSize
            local v = read32be(cnt, off) + delta
            if v < 0 then v = 0 end
            local seg = write32be(v)
            cnt = string.sub(cnt, 1, off) .. seg .. string.sub(cnt, off+fieldSize+1)
            redis.call('SET', cntKey, cnt)
            return 1
            """;

}
