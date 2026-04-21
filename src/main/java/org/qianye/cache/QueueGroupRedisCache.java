package org.qianye.cache;

import lombok.extern.slf4j.Slf4j;
import org.qianye.common.OutCallScheduleDrm;
import org.qianye.util.LoggerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 双模式队列组缓存
 * 支持本地缓存和Redis缓存两种模式，默认使用本地缓存
 * 通过配置 app.queue.group.cache.type=local|redis 切换模式
 */
@Component
@Slf4j
public class QueueGroupRedisCache {
    private static final String MAIN_GROUP_PREFIX = "outboundTask:mainGroup:";
    private static final String PRIVATE_GROUP_PREFIX = "outboundTask:privateGroup:";
    private static final String LOCAL_IP_FALLBACK = "127.0.0.1";
    private static final long DEFAULT_EXPIRE_HOURS = 24L;
    private static final long DEFAULT_EXPIRE_SECONDS = TimeUnit.HOURS.toSeconds(DEFAULT_EXPIRE_HOURS);
    private final DefaultRedisScript<List> popScript = new DefaultRedisScript<>();
    private final DefaultRedisScript<Long> moveScript = new DefaultRedisScript<>();
    private final DefaultRedisScript<List> replenishScript = new DefaultRedisScript<>();
    private final DefaultRedisScript<Long> moveElementsScript = new DefaultRedisScript<>();
    
    @Value("${app.queue.group.cache.type:local}")
    private String cacheType;
    
    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;
    
    @Resource
    private OutCallScheduleDrm outCallScheduleDrm;
    
    // Redis相关
    private RedisTemplate<String, String> redisTemplate;
    
    // 本地缓存相关
    private final Map<String, LocalListGroup> localCache = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private volatile boolean useLocalCache = false;
    
    private String localIpAddress;

    @PostConstruct
    public void init() {
        LoggerUtil.info(log, "QueueGroupRedisCache initializing, configured mode: {}", cacheType);
        
        // 如果配置为本地模式或Redis不可用，则使用本地缓存
        if ("local".equals(cacheType) || redisConnectionFactory == null) {
            useLocalCache = true;
            LoggerUtil.info(log, "Using local cache mode");
            return;
        }
        
        try {
            redisTemplate = new RedisTemplate<>();
            redisTemplate.setConnectionFactory(redisConnectionFactory);
            redisTemplate.setKeySerializer(new StringRedisSerializer());
            redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
            redisTemplate.afterPropertiesSet();
            // 初始化Lua脚本
            initializeScripts();
            LoggerUtil.info(log, "Redis cache mode initialized successfully");
        } catch (Exception e) {
            LoggerUtil.warn(log, "Redis initialization failed, falling back to local cache mode: {}", e.getMessage());
            useLocalCache = true;
        }
    }

    /**
     * 添加队列组，队列尾部添加（左推）
     */
    public boolean addGroupFromLeft(String instanceId, String taskCode, String env, List<String> groups, boolean isFixedGroups) {
        if (CollectionUtils.isEmpty(groups)) {
            LoggerUtil.warn(log, "Attempt to add empty main group");
            return true;
        }
        
        if (useLocalCache) {
            return localAddGroupFromLeft(instanceId, taskCode, env, groups, isFixedGroups);
        }
        
        String cacheKey = buildGroupCacheKey(taskCode, env, instanceId, isFixedGroups);
        try {
            // 使用Lua脚本保证原子性
            List<String> keys = Collections.singletonList(cacheKey);
            // 将groups中的所有元素作为参数传递给Lua脚本
            List<Object> scriptArgs = new ArrayList<>();
            scriptArgs.add(DEFAULT_EXPIRE_SECONDS);
            scriptArgs.addAll(groups);
            Long pushedCount = redisTemplate.execute(moveElementsScript, keys, scriptArgs.toArray());
            long totalGroupSize = getGroupSize(taskCode, env, instanceId, isFixedGroups);
            boolean add = pushedCount > 0;
            LoggerUtil.info(log, "addGroupsToCache[REDIS],instanceId:{},taskCode:{},env:{},cacheKey:{}" +
                            ",currentGroupSize:{},totalGroupSize:{}",
                    instanceId, taskCode, env, cacheKey, groups.size(), totalGroupSize);
            return add;
        } catch (Exception e) {
            LoggerUtil.warn(log, "Redis operation failed, falling back to local cache: {}", e.getMessage());
            useLocalCache = true;
            return localAddGroupFromLeft(instanceId, taskCode, env, groups, isFixedGroups);
        }
    }
    
    /**
     * 本地缓存添加队列组
     */
    private boolean localAddGroupFromLeft(String instanceId, String taskCode, String env, List<String> groups, boolean isFixedGroups) {
        String cacheKey = buildGroupCacheKey(taskCode, env, instanceId, isFixedGroups);
        try {
            cacheLock.writeLock().lock();
            try {
                LocalListGroup groupList = localCache.computeIfAbsent(cacheKey, k -> new LocalListGroup());
                // 添加所有元素到列表头部（左推）
                for (int i = groups.size() - 1; i >= 0; i--) {
                    groupList.elements.addFirst(groups.get(i));
                }
                // 设置过期时间
                groupList.expireTime = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(DEFAULT_EXPIRE_HOURS);
                long totalGroupSize = getGroupSize(taskCode, env, instanceId, isFixedGroups);
                LoggerUtil.info(log, "addGroupsToCache[LOCAL],instanceId:{},taskCode:{},env:{},cacheKey:{}" +
                                ",currentGroupSize:{},totalGroupSize:{}",
                        instanceId, taskCode, env, cacheKey, groups.size(), totalGroupSize);
                return true;
            } finally {
                cacheLock.writeLock().unlock();
            }
        } catch (Exception e) {
            LoggerUtil.error(log, e, "Failed to add main group to local cache, key: {}", cacheKey);
            return false;
        }
    }

    public boolean isReachedCacheMaxSize(String instanceId, String taskCode, String env, boolean isFixedGroups) {
        long groupSize = getGroupSize(taskCode, env, instanceId, isFixedGroups);
        return groupSize >= outCallScheduleDrm.getCacheQueueGroupLimitNum();
    }

    /**
     * 原子性的获取并弹出主队列组右侧元素
     */
    public List<String> popRightGroup(String taskCode, String envId, String instanceId, int count) {
        if (useLocalCache) {
            return localPopRightGroup(taskCode, envId, instanceId, count);
        }
        
        try {
            String cacheKey = buildGroupCacheKey(taskCode, envId, instanceId, true);
            List<String> keys = Collections.singletonList(cacheKey);
            List<String> result = redisTemplate.execute(popScript, keys, count);
            if (!CollectionUtils.isEmpty(result)) {
                return result;
            }
            List<String> publicKeys = Collections.singletonList(buildGroupCacheKey(taskCode, envId, instanceId, false));
            return redisTemplate.execute(popScript, publicKeys, count);
        } catch (Exception e) {
            LoggerUtil.warn(log, "Redis popRightGroup failed, falling back to local cache: {}", e.getMessage());
            useLocalCache = true;
            return localPopRightGroup(taskCode, envId, instanceId, count);
        }
    }
    
    /**
     * 本地缓存弹出队列组
     */
    private List<String> localPopRightGroup(String taskCode, String envId, String instanceId, int count) {
        try {
            cacheLock.writeLock().lock();
            try {
                // 先尝试从私有组获取
                String cacheKey = buildGroupCacheKey(taskCode, envId, instanceId, true);
                LocalListGroup groupList = localCache.get(cacheKey);
                
                if (groupList == null || groupList.elements.isEmpty()) {
                    // 尝试从公共组获取
                    cacheKey = buildGroupCacheKey(taskCode, envId, instanceId, false);
                    groupList = localCache.get(cacheKey);
                }
                
                if (groupList == null || groupList.elements.isEmpty()) {
                    return Collections.emptyList();
                }
                
                List<String> result = new ArrayList<>();
                int actualCount = Math.min(count, groupList.elements.size());
                for (int i = 0; i < actualCount; i++) {
                    result.add(groupList.elements.removeLast());
                }
                
                return result;
            } finally {
                cacheLock.writeLock().unlock();
            }
        } catch (Exception e) {
            LoggerUtil.error(log, "Failed to pop main group elements atomically[LOCAL], params: {}/{}/{}",
                    taskCode, envId, instanceId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 获取队列大小
     */
    public long getGroupSize(String taskCode, String envId, String instanceId, boolean isFixedGroup) {
        if (useLocalCache) {
            return localGetGroupSize(taskCode, envId, instanceId, isFixedGroup);
        }
        
        try {
            String cacheKey = buildGroupCacheKey(taskCode, envId, instanceId, isFixedGroup);
            Long size = redisTemplate.opsForList().size(cacheKey);
            return size != null ? size : 0L;
        } catch (Exception e) {
            LoggerUtil.warn(log, "Redis getGroupSize failed, falling back to local cache: {}", e.getMessage());
            useLocalCache = true;
            return localGetGroupSize(taskCode, envId, instanceId, isFixedGroup);
        }
    }
    
    /**
     * 本地缓存获取队列大小
     */
    private long localGetGroupSize(String taskCode, String envId, String instanceId, boolean isFixedGroup) {
        try {
            cacheLock.readLock().lock();
            try {
                String cacheKey = buildGroupCacheKey(taskCode, envId, instanceId, isFixedGroup);
                LocalListGroup groupList = localCache.get(cacheKey);
                return groupList != null ? groupList.elements.size() : 0L;
            } finally {
                cacheLock.readLock().unlock();
            }
        } catch (Exception e) {
            LoggerUtil.error(log, "Failed to get group size from local cache, params: {}/{}/{}",
                    taskCode, envId, instanceId, e);
            return 0L;
        }
    }

    public List<String> listElements(String taskCode, String envId, String instanceId, boolean isPrivate) {
        if (useLocalCache) {
            return localListElements(taskCode, envId, instanceId, isPrivate);
        }
        
        try {
            String cacheKey = buildGroupCacheKey(taskCode, envId, instanceId, isPrivate);
            List<String> elements = redisTemplate.opsForList().range(cacheKey, 0, -1);
            return elements != null ? elements : Collections.emptyList();
        } catch (Exception e) {
            LoggerUtil.warn(log, "Redis listElements failed, falling back to local cache: {}", e.getMessage());
            useLocalCache = true;
            return localListElements(taskCode, envId, instanceId, isPrivate);
        }
    }
    
    /**
     * 本地缓存列出元素
     */
    private List<String> localListElements(String taskCode, String envId, String instanceId, boolean isPrivate) {
        try {
            cacheLock.readLock().lock();
            try {
                String cacheKey = buildGroupCacheKey(taskCode, envId, instanceId, isPrivate);
                LocalListGroup groupList = localCache.get(cacheKey);
                if (groupList != null) {
                    return new ArrayList<>(groupList.elements);
                }
                return Collections.emptyList();
            } finally {
                cacheLock.readLock().unlock();
            }
        } catch (Exception e) {
            LoggerUtil.error(log, "Failed to list elements from local cache, params: {}/{}/{}",
                    taskCode, envId, instanceId, e);
            return Collections.emptyList();
        }
    }

    public boolean clearGroupCache(String taskCode, String envId, String instanceId) {
        if (useLocalCache) {
            return localClearGroupCache(taskCode, envId, instanceId);
        }
        
        try {
            Boolean deleteNormal = redisTemplate.delete(buildGroupCacheKey(taskCode, envId, instanceId, false));
            Boolean deleteFixed = redisTemplate.delete(buildGroupCacheKey(taskCode, envId, instanceId, true));
            return Boolean.TRUE.equals(deleteNormal) || Boolean.TRUE.equals(deleteFixed);
        } catch (Exception e) {
            LoggerUtil.warn(log, "Redis clearGroupCache failed, falling back to local cache: {}", e.getMessage());
            useLocalCache = true;
            return localClearGroupCache(taskCode, envId, instanceId);
        }
    }
    
    /**
     * 本地缓存清除
     */
    private boolean localClearGroupCache(String taskCode, String envId, String instanceId) {
        try {
            cacheLock.writeLock().lock();
            try {
                String normalKey = buildGroupCacheKey(taskCode, envId, instanceId, false);
                String fixedKey = buildGroupCacheKey(taskCode, envId, instanceId, true);
                localCache.remove(normalKey);
                localCache.remove(fixedKey);
                return true;
            } finally {
                cacheLock.writeLock().unlock();
            }
        } catch (Exception e) {
            LoggerUtil.error(log, "Failed to clear group cache from local, params: {}/{}/{}",
                    taskCode, envId, instanceId, e);
            return false;
        }
    }

    // ===== 私有方法 =====
    private void initializeScripts() {
        // 初始化popRightGroup的Lua脚本
        popScript.setResultType(List.class);
        popScript.setScriptText(
                "local result = {}\n" +
                        "for i = 1, tonumber(ARGV[1]) do\n" +
                        " local element = redis.call('RPOP', KEYS[1])\n" +
                        " if element == false then\n" +
                        " break\n" +
                        " end\n" +
                        " table.insert(result, element)\n" +
                        "end\n" +
                        "return result"
        );
        // 初始化addGroupFromLeft的Lua脚本
        moveElementsScript.setResultType(Long.class);
        moveElementsScript.setScriptText(
                "local key = KEYS[1]\n" +
                        "local expireTime = tonumber(ARGV[1])\n" +
                        "\n" +
                        "-- 添加所有元素到列表\n" +
                        "for i = 2, #ARGV do\n" +
                        " redis.call('LPUSH', key, ARGV[i])\n" +
                        "end\n" +
                        "\n" +
                        "-- 设置过期时间\n" +
                        "redis.call('EXPIRE', key, expireTime)\n" +
                        "\n" +
                        "return #ARGV - 1" // 返回添加的元素数量
        );
    }

    public String buildGroupCacheKey(String taskCode, String envId, String instanceId, boolean isFixedGroup) {
        if (isFixedGroup) {
            return PRIVATE_GROUP_PREFIX + taskCode + ":" + envId + ":" + instanceId;
        }
        return MAIN_GROUP_PREFIX + taskCode + ":" + envId + ":" + instanceId;
    }
    
    /**
     * 本地列表组数据结构
     */
    private static class LocalListGroup {
        final LinkedList<String> elements = new LinkedList<>();
        volatile long expireTime = Long.MAX_VALUE;
    }
}