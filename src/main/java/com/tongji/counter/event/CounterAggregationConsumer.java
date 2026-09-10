package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.schema.CounterKeys;
import com.tongji.counter.schema.CounterSchema;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * 计数事件聚合与刷写消费者。
 *
 * <p>职责：</p>
 * - 消费点赞/收藏等增量事件，写入 Redis 聚合桶（Hash）；
 * - 以固定延迟定时任务将聚合增量折叠到 SDS 固定结构计数；
 * - 刷写成功后删除聚合字段，避免重复加算。
 */
@Service
public class CounterAggregationConsumer {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> incrScript;


    // 使用Redis的哈希表结构作为持久化聚合桶：agg:{schema}:{etype}:{eid} ，field=idx ，value=delta
    public CounterAggregationConsumer(ObjectMapper objectMapper, StringRedisTemplate redis) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.incrScript = new DefaultRedisScript<>();
        this.incrScript.setResultType(Long.class);
        this.incrScript.setScriptText(INCR_FIELD_LUA);
    }

    /**
     * 消费计数事件并写入聚合桶。
     * @param message 事件 JSON
     * @param ack 位点确认对象（手动提交）
     */
    @KafkaListener(topics = CounterTopics.EVENTS, groupId = "counter-agg")
    public void onMessage(String message, Acknowledgment ack) throws Exception {
        CounterEvent evt = objectMapper.readValue(message, CounterEvent.class);
        String aggKey = CounterKeys.aggKey(evt.getEntityType(), evt.getEntityId());
        String field = String.valueOf(evt.getIdx());
        try{
            redis.opsForHash().increment(aggKey, field, evt.getDelta());  // 该帖子的redis聚合桶中某一个field(点赞)的数字+1或-1
            ack.acknowledge();  // 手动提交kafka消息位点
        }catch(Exception e){
            // 不提交位点
        }

    }

    /**
     * 将聚合增量刷写到 SDS 固定结构计数。
     * 固定延迟 1s，保证秒级最终一致性。
     */
    @Scheduled(fixedRate = 1000L)
    public void flush(){
        // 简化实现：扫描所有聚合桶键（生产建议使用索引集合替代 KEYS）
        Set<String> keys = redis.keys("agg:" + CounterSchema.SCHEMA_ID + ":*");
        if(keys.isEmpty()){
            return;
        }

        for(String aggKey : keys){
            Map<Object, Object> entries = redis.opsForHash().entries(aggKey);
            if(entries.isEmpty()) continue;
            // 解析key中的etype/eid来定位 SDS key
            String[] parts = aggKey.split(":", 4); // agg:schema:etype:eid
            if(parts.length < 4) continue;

            String cntKey = CounterKeys.sdsKey(parts[2], parts[3]); // cnt:schema:etype:eid

            for(Map.Entry<Object, Object> e : entries.entrySet()){

                String field = String.valueOf(e.getKey());

                long delta; // 增量
                try{
                    delta = Long.parseLong(String.valueOf(e.getValue()));
                }catch(NumberFormatException nfe){continue;}
                if (delta == 0) continue;

                int idx;
                try{
                    idx = Integer.parseInt(field);
                }catch(NumberFormatException nfe){continue;}


                try{
                    redis.execute(incrScript, List.of(cntKey),        // 每个笔记的SDS的key
                            String.valueOf(CounterSchema.SCHEMA_LEN), // 每个笔记的SDS用5个字段表示(阅读/点赞/关注/转发/评论)
                            String.valueOf(CounterSchema.FIELD_SIZE), // 每个字段用4B大小存储
                            String.valueOf(idx),                      // 字段序号
                            String.valueOf(delta));                   // 增量

                    redis.opsForHash().delete(aggKey, field); // 桶中这个field的增量处理完后, 把这个field记录删了
                    // todo 这里直接删桶的field有问题, 与LUA不在同一个原子操作中, 如果此时LUA完了又有计量写入桶, 那数据就丢失了
                    // 所以这里是不能直接删桶的field, 要用个LUA判断当前field是否为0, 如果为0就删了.
                    // (这又要保证写那边是单线程, 这就不用担心了, "HINCRBY"是线程安全的)



                }catch (Exception ex) {
                    // 如果出错, 保留增量记录, 下次定时聚合写入时再次处理这个增量
                }
            }

            Long size = redis.opsForHash().size(aggKey);
            if (size == 0L)
                redis.delete(aggKey);  // 这桶处理完了, 把桶删了
        }
    }



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
