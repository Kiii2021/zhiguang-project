package com.tongji.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Outbox 消息解析工具。
 *
 * <p>用于消费 Canal 推送的 binlog JSON 消息，从中提取 outbox 表的行数据（INSERT/UPDATE）。</p>
 */
public final class OutboxMessageUtil {  // final修饰，工具类禁止实例化

    private OutboxMessageUtil() {}
        /**
         * 从 Canal 消息中提取 outbox 表的变更行。
         * 注: Canal是用来缓存更新的,通过伪装成从节点,从主节点中获取数据库的binlog(二进制操作日志),Mysql专属
         * 然后从binlog中解析出操作类型和更新后的数据,再把数据放到环形缓冲区中(类似于消息队列?)供Redis更新使用
         * <p>仅处理：</p>
         * <ul>
         *   <li>table = outbox</li>
         *   <li>type ∈ {INSERT, UPDATE}</li>
         *   <li>data 为数组（每个元素是一行记录的列集合）</li>
         * </ul>
         *
         * @param mapper Jackson 解析器
         * @param message Canal JSON 消息
         * @return outbox 行数组；不匹配或解析失败返回空列表
         */
    public static List<JsonNode> extractRows(ObjectMapper mapper, String message) {
        try{
            JsonNode root = mapper.readTree(message);

            JsonNode table = root.get("table");
            if(table == null || !"outbox".equals(table.asText())){
                return Collections.emptyList();
            }

            JsonNode type = root.get("type");
            if (type == null || (!"INSERT".equals(type.asText()) && !"UPDATE".equals(type.asText()))) {
                return Collections.emptyList();
            }

            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) {
                return Collections.emptyList();
            }

            List<JsonNode> rows = new ArrayList<>();

            data.forEach(rows::add);

            return rows;

        }catch (Exception e){
            return Collections.emptyList();
        }
    }
}
