package com.tongji.cache.hotkey;

import com.tongji.cache.config.CacheProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


@Component
public class HotKeyDetector {
    public enum Level {NONE, LOW, MEDIUM, HIGH}

    /** 缓存配置（包含窗口/分段参数、等级阈值、扩展秒数） */
    private final CacheProperties properties;
    /** 每个 key 的滑窗分段计数数组，长度为 segments */
    private final Map<String, int[]> counters = new ConcurrentHashMap<>();
    /** 当前活跃分段索引（原子维护） AtomicInteger是并发的原子类, 更新current参数时是读取->修改->写入同时进行的, 并发安全 */
    private final AtomicInteger current = new AtomicInteger(0);
    /** 滑窗分段数量：windowSeconds / segmentSeconds */
    private final int segments;

    /**
     * 初始化探测器：根据配置计算分段数量。
     * @param cacheProperties 缓存配置（hotkey）
     */
    public HotKeyDetector(CacheProperties cacheProperties) {
        this.properties = cacheProperties;
        int segSeconds = properties.getHotkey().getSegmentSeconds();
        int winSeconds = properties.getHotkey().getWindowSeconds();
        this.segments = Math.max(1, winSeconds / Math.max(1, segSeconds));
    }

    /**
     * 记录一次访问，将计数累加到当前分段。
     * @param key 缓存键
     */
    public void record(String key){
        // 查找key并返回value(int[])，如果不存在则创造k: new int [segments]键值对
        int[] arr = counters.computeIfAbsent(key, k -> new int[segments]);
        // .get()获取
        arr[current.get()]++;
    }

    /**
     * 计算近窗口总热度（各分段求和）。
     * @param key 缓存键
     * @return 热度值
     */
    public int heat(String key){
        int[] arr = counters.get(key);  // 获取该key的分段热度数组
        if(arr == null) return 0;
        int sum = 0;
        for(int v : arr)
            sum += v;
        return sum;
    }

    /**
     * 计算热度评级：根据总热度与阈值映射到等级。
     * 阈值来源：properties.hotkey.levelLow/Medium/High。
     * @param key 缓存键
     * @return 热度等级
     */
    public Level level(String key){
        int h = heat(key);
        if(h >= properties.getHotkey().getLevelHigh()) return Level.HIGH;
        if(h >= properties.getHotkey().getLevelMedium()) return Level.MEDIUM;
        if(h >= properties.getHotkey().getLevelLow()) return Level.LOW;
        return Level.NONE;
    }

    /**
     * 根据热度等级返回扩展秒数。
     * @param l 热度等级
     * @return 扩展秒数
     */
    private int extendSeconds(Level l) {
        return switch (l) {
            case HIGH -> properties.getHotkey().getExtendHighSeconds();
            case MEDIUM -> properties.getHotkey().getExtendMediumSeconds();
            case LOW -> properties.getHotkey().getExtendLowSeconds();
            default -> 0;
        };
    }

    /**
     * 计算公共页面的动态 TTL：基准 TTL + 等级扩展秒数。
     * @param baseTtlSeconds 基准 TTL 秒数
     * @param key 缓存键
     * @return 动态 TTL 秒数
     */
    public int ttlForPublic(int baseTtlSeconds, String key){
        Level l = level(key);
        return baseTtlSeconds + extendSeconds(l);
    }

    /**
     * 计算“我的发布”页面的动态 TTL：基准 TTL + 等级扩展秒数。
     * @param baseTtlSeconds 基准 TTL 秒数
     * @param key 缓存键
     * @return 动态 TTL 秒数
     */
    public int ttlForMine(int baseTtlSeconds, String key) {
        Level l = level(key);
        return baseTtlSeconds + extendSeconds(l);
    }

    /**
     * 定时轮转当前分段，清零新分段以实现滑动窗口统计。
     * 触发频率由配置 `cache.hotkey.segment-seconds` 指定（单位秒）。
     */
    @Scheduled(fixedRateString = "10000")
    public void rotate(){
        int next = (current.get() + 1) %  segments;
        current.set(next);
        for (int[] arr : counters.values()) {  // 直接遍历这个Map的所有Value
            arr[next] = 0;
        }
        // 问题：这个Map是所有线程共用的吗？
        // 是的, ConcurrentHashMap是线程安全的
    }

    /**
     * 重置指定 key 的滑窗计数（全部清零）。
     * 用于手动降级或在配置变更后清理历史热度。
     * @param key 缓存键
     */
    public void reset(String key){
        int[] arr = counters.get(key);
        if(arr != null) Arrays.fill(arr, 0);
    }
}
