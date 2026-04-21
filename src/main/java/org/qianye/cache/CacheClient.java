package org.qianye.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存客户端
 * 支持本地缓存和Redis缓存两种模式，默认使用本地缓存
 * 通过配置 app.cache.type=local|redis 切换模式
 */
@Component
public class CacheClient {
    
    @Value("${app.cache.type:local}")
    private String cacheType;
    
    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;
    
    // 本地缓存实现
    private final ConcurrentHashMap<String, LocalCacheEntry> localCache = new ConcurrentHashMap<>();

    // 本地计数器缓存（用于incrBy）
    private final ConcurrentHashMap<String, AtomicLong> localCounters = new ConcurrentHashMap<>();
    // 计数器过期时间
    private final ConcurrentHashMap<String, Long> counterExpireTimes = new ConcurrentHashMap<>();

    /**
     * 自增操作
     * @param key 缓存key
     * @param delta 增量
     * @return 自增后的值
     */
    public long incrBy(String key, int delta) {
        if ("redis".equals(cacheType) && redisTemplate != null) {
            // 使用Redis实现
            return redisTemplate.opsForValue().increment(key, delta);
        } else {
            // 使用本地缓存实现
            cleanupExpiredCounters();
            AtomicLong counter = localCounters.computeIfAbsent(key, k -> new AtomicLong(0));
            long result = counter.addAndGet(delta);
            // 设置默认过期时间（如果未设置）
            counterExpireTimes.putIfAbsent(key, System.currentTimeMillis() + (60 * 1000L));
            return result;
        }
    }

    /**
     * 设置过期时间
     * @param key 缓存key
     * @param expireSeconds 过期时间（秒）
     */
    public void expire(String key, int expireSeconds) {
        if ("redis".equals(cacheType) && redisTemplate != null) {
            // 使用Redis实现
            redisTemplate.expire(key, expireSeconds, TimeUnit.SECONDS);
        } else {
            // 使用本地缓存实现 - 更新计数器过期时间
            long expireTime = System.currentTimeMillis() + (expireSeconds * 1000L);
            counterExpireTimes.put(key, expireTime);
            // 同时更新普通缓存条目的过期时间（如果存在）
            LocalCacheEntry entry = localCache.get(key);
            if (entry != null) {
                LocalCacheEntry newEntry = new LocalCacheEntry(entry.getValue(), expireTime);
                localCache.put(key, newEntry);
            }
        }
    }

    // 本地缓存条目
    private static class LocalCacheEntry {
        private final String value;
        private final long expireTime;

        public LocalCacheEntry(String value, long expireTime) {
            this.value = value;
            this.expireTime = expireTime;
        }

        public String getValue() {
            return value;
        }

        public long getExpireTime() {
            return expireTime;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }
    
    /**
     * 设置缓存（仅当key不存在时）
     * @param key 缓存key
     * @param value 缓存值
     * @param expireSeconds 过期时间（秒）
     * @return true-设置成功，false-key已存在
     */
    public boolean putNotExist(String key, String value, int expireSeconds) {
        if ("redis".equals(cacheType) && redisTemplate != null) {
            // 使用Redis实现
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, value, expireSeconds, TimeUnit.SECONDS);
            return result != null && result;
        } else {
            // 使用本地缓存实现
            cleanupExpiredEntries();
            long expireTime = System.currentTimeMillis() + (expireSeconds * 1000L);
            LocalCacheEntry entry = new LocalCacheEntry(value, expireTime);
            return localCache.putIfAbsent(key, entry) == null;
        }
    }
    
    /**
     * 删除缓存
     * @param key 缓存key
     */
    public void delete(String key) {
        if ("redis".equals(cacheType) && redisTemplate != null) {
            // 使用Redis实现
            redisTemplate.delete(key);
        } else {
            // 使用本地缓存实现
            localCache.remove(key);
        }
    }
    
    /**
     * 检查key是否存在
     * @param key 缓存key
     * @return true-存在，false-不存在
     */
    public boolean exists(String key) {
        if ("redis".equals(cacheType) && redisTemplate != null) {
            // 使用Redis实现
            Boolean result = redisTemplate.hasKey(key);
            return result != null && result;
        } else {
            // 使用本地缓存实现
            cleanupExpiredEntries();
            LocalCacheEntry entry = localCache.get(key);
            return entry != null && !entry.isExpired();
        }
    }
    
    /**
     * 获取缓存值
     * @param key 缓存key
     * @return 缓存值，不存在或过期返回null
     */
    public String get(String key) {
        if ("redis".equals(cacheType) && redisTemplate != null) {
            // 使用Redis实现
            return redisTemplate.opsForValue().get(key);
        } else {
            // 使用本地缓存实现
            cleanupExpiredEntries();
            LocalCacheEntry entry = localCache.get(key);
            if (entry != null && !entry.isExpired()) {
                return entry.getValue();
            } else {
                localCache.remove(key);
                return null;
            }
        }
    }
    
    /**
     * 设置缓存（覆盖已存在的值）
     * @param key 缓存key
     * @param value 缓存值
     * @param expireSeconds 过期时间（秒）
     */
    public void put(String key, String value, int expireSeconds) {
        if ("redis".equals(cacheType) && redisTemplate != null) {
            // 使用Redis实现
            redisTemplate.opsForValue().set(key, value, expireSeconds, TimeUnit.SECONDS);
        } else {
            // 使用本地缓存实现
            long expireTime = System.currentTimeMillis() + (expireSeconds * 1000L);
            LocalCacheEntry entry = new LocalCacheEntry(value, expireTime);
            localCache.put(key, entry);
        }
    }
    
    /**
     * 清理过期的本地缓存条目
     */
    private void cleanupExpiredEntries() {
        localCache.entrySet().removeIf(entry ->
                entry.getValue().isExpired()
        );
    }

    /**
     * 清理过期的本地计数器
     */
    private void cleanupExpiredCounters() {
        long now = System.currentTimeMillis();
        counterExpireTimes.entrySet().removeIf(entry -> {
            if (entry.getValue() < now) {
                localCounters.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
    
    /**
     * 获取当前缓存模式
     * @return local 或 redis
     */
    public String getCacheType() {
        return cacheType;
    }
    
    /**
     * 清空所有缓存（谨慎使用）
     */
    public void clearAll() {
        if ("redis".equals(cacheType) && redisTemplate != null) {
            // 清空Redis中所有匹配的keys（需要根据实际key前缀调整）
            redisTemplate.getConnectionFactory().getConnection().flushDb();
        } else {
            // 清空本地缓存
            localCache.clear();
        }
    }
}
