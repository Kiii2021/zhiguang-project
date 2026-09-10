package com.tongji.config;


import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties; // spring-boot-autoconfigure依赖包

@Configuration
public class RedissonConfig {  // Red
    @Value("30000")
    private long lockWatchdogMs;  // 看门狗超时时间：30秒

    @Bean
    public RedissonClient redissonClient(RedisProperties redisProperties){
        Config config = new Config();

        config.setLockWatchdogTimeout(lockWatchdogMs);  // 设置锁的看门狗超时，用于自动续约锁

        // 连接配置
        String address = "redis://" +  redisProperties.getHost() + ":" + redisProperties.getPort();
        SingleServerConfig single = config.useSingleServer().setAddress(address);

        // 认证配置
        if(redisProperties.getPassword()!=null && !redisProperties.getPassword().isEmpty()){
            single.setPassword(redisProperties.getPassword());
        }

        // Spring Boot RedisProperties返回的是int(默认0)，无需判空
        single.setDatabase(redisProperties.getDatabase());
        return Redisson.create(config);
    }
}
