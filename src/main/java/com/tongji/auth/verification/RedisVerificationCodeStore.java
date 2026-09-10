package com.tongji.auth.verification;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;


@Component
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final String FIELD_CODE = "code";
    private static final String FIELD_MAX_ATTEMPTS = "maxAttempts";
    private static final String FIELD_ATTEMPTS = "attempts";

    private final StringRedisTemplate redisTemplate;

    public RedisVerificationCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 保存验证码到 Redis Hash，并设置 TTL。
     *
     * @param scene       场景名称。
     * @param identifier  标识（手机号或邮箱）。
     * @param code        验证码字符串。
     * @param ttl         有效期。
     * @param maxAttempts 最大尝试次数。
     * @throws RedisSystemException 保存失败时抛出。
     */
    @Override
    public void saveCode(String scene, String identifier, String code, Duration ttl, int maxAttempts){
        String key = buildKey(scene, identifier);
        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        try{
            ops.put(key, FIELD_CODE, code);
            ops.put(key, FIELD_MAX_ATTEMPTS, String.valueOf(maxAttempts));
            ops.put(key, FIELD_ATTEMPTS, "0");
            redisTemplate.expire(key, ttl);  // 设置TTL
        }catch(DataAccessException e){
            throw new RedisSystemException("Faild to save verification code", e);
        }
    }

    /**
     * 校验验证码是否匹配，更新尝试计数并在成功时删除记录。
     * 验证码在redis中用一个hash表存, "code"对应验证码, "attempt"对应申请发送次数
     *
     * @param scene      场景名称。
     * @param identifier 标识（手机号或邮箱）。
     * @param code       用户输入的验证码。
     * @return 校验结果（成功、未找到、错误、尝试过多）。
     */
    @Override
    public VerificationCheckResult verify(String scene, String identifier, String code){
        String key = buildKey(scene, identifier);
        HashOperations<String, String, String> ops = redisTemplate.opsForHash();
        Map<String, String> data = ops.entries(key);  // 获取hash结构的所有 field-value 对的 Map
        if (data.isEmpty()){
            return new VerificationCheckResult(VerificationCodeStatus.NOT_FOUND, 0, 0);
        }

        String storedCode = data.get(FIELD_CODE);
        int maxAttempts = parseInt(data.get(FIELD_MAX_ATTEMPTS), 5);  // 最大发送次数
        int attempts = parseInt(data.get(FIELD_ATTEMPTS), 0);         // 当前发送次数

        if (attempts >= maxAttempts) {  // 超过最大发送次数
            return new VerificationCheckResult(VerificationCodeStatus.TOO_MANY_ATTEMPTS, attempts, maxAttempts);
        }

        if (Objects.equals(storedCode, code)) { // 验证成功
            redisTemplate.delete(key);
            return new VerificationCheckResult(VerificationCodeStatus.SUCCESS, attempts, maxAttempts);
        }

        // 插入一条验证记录(value是验证次数)
        int updatedAttempts = attempts + 1;
        ops.put(key, FIELD_ATTEMPTS, String.valueOf(updatedAttempts));

        if (updatedAttempts >= maxAttempts) {  // 到达最大发送次数, 指定 key 在 30 分钟后自动从 Redis 中删除(留足时间给他找验证码)
            redisTemplate.expire(key, Duration.ofMinutes(30));
            return new VerificationCheckResult(VerificationCodeStatus.TOO_MANY_ATTEMPTS, updatedAttempts, maxAttempts);
        }
        // 上面都没return, 那就是验证失败
        return new VerificationCheckResult(VerificationCodeStatus.MISMATCH, updatedAttempts, maxAttempts);
    }

    /**
     * 使验证码失效（删除存储记录）。
     *
     * @param scene      场景名称。
     * @param identifier 标识（手机号或邮箱）。
     */
    @Override
    public void invalidate(String scene, String identifier) {
        redisTemplate.delete(buildKey(scene, identifier));
    }
    /**
     * 生成验证码的 Redis 键名。
     *
     * @param scene      场景名称。
     * @param identifier 标识（手机号或邮箱）。
     * @return 键名字符串。
     */
    private static String buildKey(String scene, String identifier) {
        return "auth:code:%s:%s".formatted(scene, identifier);
    }

    /**
     * 解析整数字符串，失败返回默认值。
     *
     * @param value        待解析字符串。
     * @param defaultValue 解析失败时的默认值。
     * @return 整数值。
     */
    private static int parseInt(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

}
