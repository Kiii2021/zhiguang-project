package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.schema.CounterKeys;
import com.tongji.counter.schema.CounterSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CounterAggregationConsumer 单元测试")
@ExtendWith(MockitoExtension.class)
class CounterAggregationConsumerTest {

    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private HashOperations<String, Object, Object> hashOps;

    @InjectMocks
    private CounterAggregationConsumer consumer;

    /**
     * 用例目的：
     * - 验证当收到合法计数事件时，能够将增量写入 Redis 聚合桶并提交 Kafka 位点。
     * 覆盖点：
     * - 聚合桶 HINCRBY 调用参数正确（aggKey、field、delta）
     * - ack.acknowledge 仅调用一次
     * 前置条件：
     * - ObjectMapper 能将消息反序列化为 CounterEvent
     */
    @Test
    @DisplayName("onMessage - 成功写入聚合桶并提交位点")
    void testOnMessage_Success_Ack() throws Exception {
        // Given：构造事件与 Mock 行为
        CounterEvent evt = CounterEvent.of("knowpost", "123", "like", 0, 456L, +1);
        String msg = "{\"entityType\":\"knowpost\"}";
        when(objectMapper.readValue(eq(msg), eq(CounterEvent.class))).thenReturn(evt);
        when(redis.opsForHash()).thenReturn(hashOps);
        Acknowledgment ack = mock(Acknowledgment.class);

        // When：调用被测方法
        consumer.onMessage(msg, ack);

        // Then：校验聚合桶写入与位点确认
        String aggKey = CounterKeys.aggKey("knowpost", "123");
        verify(hashOps, times(1)).increment(eq(aggKey), eq(String.valueOf(evt.getIdx())), eq((long) evt.getDelta()));
        verify(ack, times(1)).acknowledge();
    }

    @Test
    @DisplayName("onMessage - 写入聚合桶异常不提交位点")
    void testOnMessage_Failure_NoAck() throws Exception {
        CounterEvent evt = CounterEvent.of("knowpost", "123", "like", 0, 456L, +1);
        String msg = "{\"entityType\":\"knowpost\"}";
        when(objectMapper.readValue(eq(msg), eq(CounterEvent.class))).thenReturn(evt);
        when(redis.opsForHash()).thenReturn(hashOps);
        doThrow(new RuntimeException("redis err")).when(hashOps).increment(anyString(), any(), anyLong());
        Acknowledgment ack = mock(Acknowledgment.class);

        consumer.onMessage(msg, ack);

        verify(ack, never()).acknowledge();
    }

    @Test
    @DisplayName("flush - 无聚合桶直接返回")
    void testFlush_NoKeys_Returns() {
        when(redis.keys("agg:" + CounterSchema.SCHEMA_ID + ":*")).thenReturn(Collections.emptySet());
        assertDoesNotThrow(() -> consumer.flush());
        verify(redis, never()).opsForHash();
    }

    @Test
    @DisplayName("flush - 处理单桶单字段：执行脚本并清理字段与空桶")
    void testFlush_ProcessBucket_ExecuteScriptAndCleanup() {
        String aggKey = CounterKeys.aggKey("knowpost", "123");            // agg:schema:etype:eid
        String cntKey = CounterKeys.sdsKey("knowpost", "123");            // cnt:schema:etype:eid
        when(redis.keys("agg:" + CounterSchema.SCHEMA_ID + ":*")).thenReturn(Set.of(aggKey));

        Map<Object, Object> entries = new HashMap<>();
        entries.put("0", "3"); // idx=0, delta=3
        when(redis.opsForHash()).thenReturn(hashOps);
        when(hashOps.entries(aggKey)).thenReturn(entries);
        when(hashOps.size(aggKey)).thenReturn(0L);

        assertDoesNotThrow(() -> consumer.flush());

        verify(redis, times(1)).execute(any(), eq(List.of(cntKey)), any(), any(), any(), any());
        verify(hashOps, times(1)).delete(eq(aggKey), eq("0"));
        verify(redis, times(1)).delete(eq(aggKey));
    }
}
