package org.qianye.cache;
/*
 * Ant Group
 * Copyright (c) 2004-2024 All Rights Reserved.
 */

import com.alibaba.fastjson.support.spring.FastJsonRedisSerializer;
import lombok.extern.slf4j.Slf4j;
import org.qianye.util.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author qiancheng
 * @version RedisLock.java, v 0.1 2024年02月20日 5:14 下午 qiancheng
 */
@Service
@Slf4j
public class RedisLock {
    /**
     * 大于10秒进行锁续期
     */
    private static final int lockDurationSecond = 15 * 60;
    private static final Integer renewThreshold = 10;
    private static Map<String, LockInfo> lockInfoMap = new ConcurrentHashMap<>();
    /**
     * 自定义线程池用于执行锁续期任务
     */
    private ScheduledExecutorService renewalExecutorService;
    private RedisTemplate redisTemplate;
    
    @Value("${app.lock.type:local}")
    private String lockType;
    
    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;
    
    // 本地锁支持
    private final Map<String, ReentrantLock> localLocks = new ConcurrentHashMap<>();
    private final Map<String, String> localLockOwners = new ConcurrentHashMap<>();
    private final Map<String, Long> localLockExpireTime = new ConcurrentHashMap<>();
    private volatile boolean useLocalLock = false;

    @PostConstruct
    public void init() {
        LoggerUtil.info(log, "RedisLock initializing, configured mode: {}", lockType);
        
        // 如果配置为本地模式或Redis不可用，则使用本地锁
        if ("local".equals(lockType) || redisConnectionFactory == null) {
            useLocalLock = true;
            LoggerUtil.info(log, "Using local lock mode");
            initRenewalExecutorService();
            return;
        }
        
        try {
            redisTemplate = new RedisTemplate<>();
            redisTemplate.setConnectionFactory(redisConnectionFactory);
            redisTemplate.setKeySerializer(new FastJsonRedisSerializer<>(Object.class));
            redisTemplate.setValueSerializer(new FastJsonRedisSerializer<>(Object.class));
            redisTemplate.afterPropertiesSet();
            LoggerUtil.info(log, "keySerializer:{} valueSerializer:{}", redisTemplate.getKeySerializer().getClass().getSimpleName(), redisTemplate.getValueSerializer().getClass().getSimpleName());
            // 初始化线程池
            initRenewalExecutorService();
            LoggerUtil.info(log, "Redis lock mode initialized successfully");
        } catch (Exception e) {
            LoggerUtil.warn(log, "Redis initialization failed, falling back to local lock mode: {}", e.getMessage());
            useLocalLock = true;
            initRenewalExecutorService();
        }
    }

    /**
     * 初始化锁续期线程池
     */
    private void initRenewalExecutorService() {
        renewalExecutorService = Executors.newScheduledThreadPool(2,
                r -> {
                    Thread thread = new Thread(r, "redis-lock-renewal-thread-");
                    thread.setDaemon(true);
                    return thread;
                });
        renewalExecutorService.scheduleAtFixedRate(this::renewal, 0, 2, TimeUnit.SECONDS);
        LoggerUtil.info(log, "Redis lock renewal executor service initialized with 4 threads, thread name pattern: redis-lock-renewal-thread-*");
    }

    /**
     * 关闭线程池
     */
    @PreDestroy
    public void destroy() {
        if (renewalExecutorService != null && !renewalExecutorService.isShutdown()) {
            renewalExecutorService.shutdown();
            try {
                if (!renewalExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    renewalExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                renewalExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            LoggerUtil.info(log, "Redis lock renewal executor service shutdown");
        }
    }

    /**
     * 获取锁
     *
     * @param lockKey            锁
     * @param value              身份标识（保证锁不会被其他人释放）
     * @param expireTimeSecond   锁的过期时间（单位：秒）
     * @param waitTimeMillSecond 等待时间内还未获取到锁则会返回false（单位：毫秒）
     * @Desc 注意事项，redisConfig配置里面必须使用 genericToStringSerializer序列化,否则获取不了返回值
     */
    public boolean lockWaitTime(String lockKey, String value, int expireTimeSecond, int waitTimeMillSecond) {
        boolean ret = tryLock(lockKey, value, expireTimeSecond);
        if (ret || waitTimeMillSecond <= 0) {
            return ret;
        }
        try {
            waitTimeMillSecond -= 100;
            Thread.sleep(100);
        } catch (Exception e) {
            LoggerUtil.error(log, "lockWaitTime sleep error", e);
        }
        return lockWaitTime(lockKey, value, expireTimeSecond, waitTimeMillSecond);
    }

    /**
     * 大于10秒自动续期
     *
     * @param lockKey          锁
     * @param value            身份标识（保证锁不会被其他人释放）
     * @param expireTimeSecond 锁的过期时间（单位：秒）
     * @Desc 注意事项，redisConfig配置里面必须使用 genericToStringSerializer序列化,否则获取不了返回值
     */
    public boolean tryLock(String lockKey, String value, int expireTimeSecond) {
        if (useLocalLock) {
            return localTryLock(lockKey, value, expireTimeSecond);
        }
        
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(lockKey, value, expireTimeSecond, TimeUnit.SECONDS);
            if (result && expireTimeSecond >= renewThreshold) {
                lockInfoMap.put(lockKey + value, LockInfo.getLockInfo(lockKey, value, expireTimeSecond));
                LoggerUtil.info(log, "renewal request success, lockKey:{}, expireTime:{}", lockKey, expireTimeSecond);
            }
            LoggerUtil.info(log, "redis tryLock, lockKey:{}, expireTime:{}", lockKey, expireTimeSecond);
            return result;
        } catch (Exception e) {
            LoggerUtil.warn(log, "Redis tryLock failed, falling back to local lock: {}", e.getMessage());
            useLocalLock = true;
            return localTryLock(lockKey, value, expireTimeSecond);
        }
    }
    
    /**
     * 本地锁实现
     */
    private boolean localTryLock(String lockKey, String value, int expireTimeSecond) {
        try {
            ReentrantLock lock = localLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
            boolean acquired = lock.tryLock(0, TimeUnit.MILLISECONDS);
            
            if (acquired) {
                localLockOwners.put(lockKey, value);
                localLockExpireTime.put(lockKey, System.currentTimeMillis() + expireTimeSecond * 1000L);
                
                if (expireTimeSecond >= renewThreshold) {
                    lockInfoMap.put(lockKey + value, LockInfo.getLockInfo(lockKey, value, expireTimeSecond));
                }
                LoggerUtil.info(log, "local tryLock success, lockKey:{}, expireTime:{}", lockKey, expireTimeSecond);
                return true;
            }
            
            // 检查是否已过期
            Long expireTime = localLockExpireTime.get(lockKey);
            if (expireTime != null && System.currentTimeMillis() > expireTime) {
                // 锁已过期，强制释放
                localLockOwners.remove(lockKey);
                localLockExpireTime.remove(lockKey);
                lock.unlock();
                
                // 重新尝试获取
                acquired = lock.tryLock(0, TimeUnit.MILLISECONDS);
                if (acquired) {
                    localLockOwners.put(lockKey, value);
                    localLockExpireTime.put(lockKey, System.currentTimeMillis() + expireTimeSecond * 1000L);
                    LoggerUtil.info(log, "local tryLock success (expired), lockKey:{}", lockKey);
                    return true;
                }
            }
            
            LoggerUtil.info(log, "local tryLock failed, lockKey:{}", lockKey);
            return false;
        } catch (Exception e) {
            LoggerUtil.error(log, e, "local tryLock error, lockKey:{}", lockKey);
            return false;
        }
    }

    /**
     * 使用lua脚本释放锁
     *
     * @param lockKey
     * @param value
     * @return 成功返回true, 失败返回false
     */
    public boolean unlock(String lockKey, String value) {
        lockInfoMap.remove(lockKey + value);
        
        if (useLocalLock) {
            return localUnlock(lockKey, value);
        }
        
        try {
            String luaScript = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
            DefaultRedisScript<Boolean> redisScript = new DefaultRedisScript<>();
            redisScript.setResultType(Boolean.class);
            redisScript.setScriptText(luaScript);
            List<String> keys = new ArrayList<>();
            keys.add(lockKey);
            Object result = redisTemplate.execute(redisScript, keys, value);
            LoggerUtil.info(log, "redis unlock key:{}, result:{}", lockKey, result);
            return (boolean) result;
        } catch (Exception e) {
            LoggerUtil.warn(log, "Redis unlock failed, trying local unlock: {}", e.getMessage());
            return localUnlock(lockKey, value);
        }
    }
    
    /**
     * 本地锁释放
     */
    private boolean localUnlock(String lockKey, String value) {
        try {
            String owner = localLockOwners.get(lockKey);
            if (owner == null) {
                LoggerUtil.info(log, "local unlock failed, lock not found: {}", lockKey);
                return false;
            }
            
            if (!owner.equals(value)) {
                LoggerUtil.warn(log, "local unlock failed, owner mismatch: {}, expected: {}, actual: {}", lockKey, value, owner);
                return false;
            }
            
            ReentrantLock lock = localLocks.get(lockKey);
            if (lock != null && lock.isHeldByCurrentThread()) {
                lock.unlock();
                localLockOwners.remove(lockKey);
                localLockExpireTime.remove(lockKey);
                LoggerUtil.info(log, "local unlock success: {}", lockKey);
                return true;
            }
            
            LoggerUtil.info(log, "local unlock failed, lock not held: {}", lockKey);
            return false;
        } catch (Exception e) {
            LoggerUtil.error(log, e, "local unlock error: {}", lockKey);
            return false;
        }
    }

    public void releaseAllLock() {
        for (Map.Entry<String, LockInfo> lockInfoEntry : lockInfoMap.entrySet()) {
            unlock(lockInfoEntry.getValue().getKey(), lockInfoEntry.getValue().getValue());
        }
    }

    /**
     * 使用redisTemplate expire命令更新redis锁的过期时间
     *
     * @param lockKey
     * @param value
     * @return 成功返回true, 失败返回false
     */
    private boolean renewal(String lockKey, String value, int expireTime) {
        if (useLocalLock) {
            return localRenewal(lockKey, value, expireTime);
        }
        
        try {
            // 先验证锁是否还存在且值匹配
            Object currentValue = redisTemplate.opsForValue().get(lockKey);
            if (currentValue == null || !value.equals(currentValue)) {
                LoggerUtil.info(log, "renewal lock failed, key not exist or value not match, lockKey:{}, expectedValue:{}, actualValue:{}",
                        lockKey, value, currentValue);
                return false;
            }
            // 使用expire命令更新过期时间
            Boolean result = redisTemplate.expire(lockKey, expireTime, TimeUnit.SECONDS);
            boolean success = Boolean.TRUE.equals(result);
            if (success) {
                LoggerUtil.info(log, "renewal lock success, lockKey:{}, expireTime:{}", lockKey, expireTime);
            } else {
                LoggerUtil.error(log, "renewal lock failed, expire command returned false, lockKey:{}", lockKey);
            }
            return success;
        } catch (Exception e) {
            LoggerUtil.error(log, e, "renewal lock exception: key: {} args: {} {}", lockKey, value, expireTime);
            return false;
        }
    }
    
    /**
     * 本地锁续期
     */
    private boolean localRenewal(String lockKey, String value, int expireTime) {
        try {
            String owner = localLockOwners.get(lockKey);
            if (owner == null || !owner.equals(value)) {
                LoggerUtil.info(log, "local renewal failed, owner mismatch: {}, expected: {}, actual: {}", lockKey, value, owner);
                return false;
            }
            
            localLockExpireTime.put(lockKey, System.currentTimeMillis() + expireTime * 1000L);
            LoggerUtil.info(log, "local renewal success, lockKey:{}, expireTime:{}", lockKey, expireTime);
            return true;
        } catch (Exception e) {
            LoggerUtil.error(log, e, "local renewal error: {}", lockKey);
            return false;
        }
    }

    public Boolean existKey(String lockKey) {
        if (useLocalLock) {
            return localLockOwners.containsKey(lockKey);
        }
        
        try {
            return redisTemplate.hasKey(lockKey);
        } catch (Exception e) {
            LoggerUtil.warn(log, "Redis existKey failed: {}", e.getMessage());
            return localLockOwners.containsKey(lockKey);
        }
    }

    /**
     * 定时去检查redis锁的过期时间
     * <p>
     * 使用自定义线程池异步执行锁续期任务
     */
    public void renewal() {
        LoggerUtil.info(log, "renewal lock start, size:{}", lockInfoMap.size());
        if (lockInfoMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, LockInfo> lockInfoEntry : lockInfoMap.entrySet()) {
            try {
                long now = System.currentTimeMillis();
                LockInfo lockInfo = lockInfoEntry.getValue();
                long duration = (now - lockInfo.getStartTime()) / 1000;
                if (duration > lockDurationSecond) {
                    LoggerUtil.info(log, "lock time is too long,over time:{}, key:{},lockTimeSecond:{}",
                            lockDurationSecond, lockInfo.getKey(), duration);
                }
                if (lockInfo.getRenewalTime() + lockInfo.getRenewalInterval() < now) {
                    boolean success = renewal(lockInfo.getKey(), lockInfo.getValue(), lockInfo.getExpireTime());
                    LoggerUtil.info(log, "renewalLock:{}，result:{} ", lockInfo.getKey(), success);
                    if (success) {
                        lockInfo.setRenewalTime(now);
                    }
                    if (!success) {
                        LoggerUtil.info(log, "renewalLockFail:{} ", lockInfo.getKey());
                    }
                    if (!success && !existKey(lockInfo.getKey())) {
                        // 如果失败且key 不存在则删除续期
                        lockInfoMap.remove(lockInfo.getKey() + lockInfo.getValue());
                        LoggerUtil.info(log, "renew key fail and not find in redis ,remove local key:{}", lockInfo.getKey());
                    }
                } else {
                    LoggerUtil.info(log, "no need renewalLock:{} ", lockInfo.getKey());
                }
            } catch (Exception e) {
                LoggerUtil.error(log, e, "renewalLock error");
            }
        }
    }

    private static class LockInfo {
        private String key;
        private String value;
        private int expireTime;
        private long startTime;
        //更新时间
        private long renewalTime;
        //更新间隔
        private long renewalInterval;

        public static LockInfo getLockInfo(String key, String value, int expireTime) {
            LockInfo lockInfo = new LockInfo();
            lockInfo.setKey(key);
            lockInfo.setValue(value);
            lockInfo.setExpireTime(expireTime);
            lockInfo.setRenewalTime(System.currentTimeMillis());
            lockInfo.setRenewalInterval(expireTime * 1000 / 3);
            lockInfo.setStartTime(System.currentTimeMillis());
            return lockInfo;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public int getExpireTime() {
            return expireTime;
        }

        public void setExpireTime(int expireTime) {
            this.expireTime = expireTime;
        }

        public long getRenewalTime() {
            return renewalTime;
        }

        public void setRenewalTime(long renewalTime) {
            this.renewalTime = renewalTime;
        }

        public long getRenewalInterval() {
            return renewalInterval;
        }

        public void setRenewalInterval(long renewalInterval) {
            this.renewalInterval = renewalInterval;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }
    }
}