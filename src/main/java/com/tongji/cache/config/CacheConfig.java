package com.tongji.cache.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.tongji.knowpost.api.dto.FeedPageResponse;
import com.tongji.knowpost.api.dto.KnowPostDetailResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;


/**
 * Caffeine 本地缓存配置。
 *
 * 用于在应用进程内缓存分页结果，降低数据库与下游服务压力。
 */

@Configuration
public class CacheConfig {
    /**
     * 公共信息流（广场/推荐）分页缓存。
     *
     * <p>键通常由分页游标、页大小、过滤条件等组合而成；值为一页的 {@link FeedPageResponse}。</p>
     */
    @Bean("feedPublicCache")
    public Cache<String, FeedPageResponse> feedPubilcCache(CacheProperties cacheProperties) {
        return Caffeine.newBuilder().maximumSize(cacheProperties.getL2().getPublicCfg().getMaxSize())
                .expireAfterWrite(Duration.ofSeconds(cacheProperties.getL2().getPublicCfg().getTtlSeconds()))
                .build();  // Caffeine对象，设置了最大size和过期时间
    }

    /**
     * 我的发布信息流分页缓存。
     *
     * <p>键由分页游标、页大小等组合而成；值为一页的 {@link FeedPageResponse}。</p>
     */
    @Bean("feedMineCache")
    public Cache<String, FeedPageResponse> feedMineCache(CacheProperties cacheProperties) {
        return Caffeine.newBuilder().maximumSize(cacheProperties.getL2().getMineCfg().getMaxSize())
                .expireAfterWrite(Duration.ofSeconds(cacheProperties.getL2().getMineCfg().getTtlSeconds()))
                .build();  // Caffeine对象，设置了最大size和过期时间
    }

    /**
     * 知文详情本地缓存。
     *
     * <p>键为 knowpost:detail:{id}:v{version}，值为 {@link KnowPostDetailResponse}。</p>
     */

    @Bean("knowPostDetailCache")
    public Cache<String, KnowPostDetailResponse> knowPostDetailCache(CacheProperties cacheProperties) {
        return Caffeine.newBuilder().maximumSize(cacheProperties.getL2().getPublicCfg().getMaxSize())
                .expireAfterWrite(Duration.ofSeconds(cacheProperties.getL2().getPublicCfg().getTtlSeconds()))
                .build();  // Caffeine对象，设置了最大size和过期时间
    }

}



