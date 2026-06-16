package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorKeys;
import com.attendance.config.RecognitionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.InetAddress;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 识别分布式协调：Redis 信号量 / 任务锁 / 调度 leader / MiMo QPS；无 Redis 时降级为进程内实现。
 */
@Component
public class RecognitionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(RecognitionCoordinator.class);

    private static final String KEY_GLOBAL = "recognition:sem:global";
    private static final String KEY_USER_PREFIX = "recognition:sem:user:";
    private static final String KEY_TASK_LOCK_PREFIX = "recognition:lock:task:";
    private static final String KEY_LEADER = "recognition:scheduler:leader";
    private static final String KEY_MIMO_QPS_PREFIX = "recognition:mimo:qps:";
    private static final String KEY_QUEUE = "recognition:queue";

    private static final String LUA_ACQUIRE = ""
            + "local current = tonumber(redis.call('GET', KEYS[1]) or '0') "
            + "local limit = tonumber(ARGV[1]) "
            + "if current >= limit then return 0 end "
            + "redis.call('INCR', KEYS[1]) "
            + "redis.call('EXPIRE', KEYS[1], ARGV[2]) "
            + "return 1";

    private static final String LUA_RELEASE = ""
            + "local current = tonumber(redis.call('GET', KEYS[1]) or '0') "
            + "if current > 0 then redis.call('DECR', KEYS[1]) end "
            + "return 1";

    private final RecognitionProperties properties;
    private final Environment environment;

    @Autowired(required = false)
    private StringRedisTemplate redis;

    private final Map<String, AtomicInteger> localUserSem = new ConcurrentHashMap<>();
    private final AtomicInteger localGlobalSem = new AtomicInteger(0);
    private final ConcurrentHashMap<String, Boolean> localTaskLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> localMimoCounters = new ConcurrentHashMap<>();

    private boolean distributed;
    private String instanceId;

    public RecognitionCoordinator(RecognitionProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @PostConstruct
    void init() {
        instanceId = resolveInstanceId();
        Boolean explicit = properties.getDistributed().getEnabled();
        String redisHost = environment.getProperty("spring.redis.host", "").trim();
        distributed = redis != null && (explicit == null ? !redisHost.isEmpty() : Boolean.TRUE.equals(explicit));
        log.info("识别协调器: distributed={}, instanceId={}, globalMax={}, maxPerUser={}, mimoQps={}",
                distributed, instanceId, properties.getGlobalMaxConcurrent(),
                properties.getMaxPerUser(), properties.getMimoQps());
    }

    public boolean isDistributed() {
        return distributed;
    }

    public String getQueueRedisKey() {
        return KEY_QUEUE;
    }

    public boolean tryAcquireSlots(String userId) {
        if (!tryAcquireGlobal()) {
            return false;
        }
        if (!tryAcquireUser(userId)) {
            releaseGlobal();
            return false;
        }
        return true;
    }

    public void acquireSlots(String userId) {
        if (!tryAcquireSlots(userId)) {
            throw new BusinessException(429, ErrorKeys.RECOGNITION_CONCURRENT_LIMIT);
        }
    }

    public void releaseSlots(String userId) {
        releaseUser(userId);
        releaseGlobal();
    }

    public boolean tryAcquireUser(String userId) {
        return acquireCounter(userKey(userId), properties.getMaxPerUser());
    }

    public void acquireUser(String userId) {
        if (!tryAcquireUser(userId)) {
            throw new BusinessException(429, ErrorKeys.RECOGNITION_CONCURRENT_LIMIT);
        }
    }

    public void releaseUser(String userId) {
        releaseCounter(userKey(userId));
    }

    public boolean tryAcquireGlobal() {
        return acquireCounter(KEY_GLOBAL, properties.getGlobalMaxConcurrent());
    }

    public void releaseGlobal() {
        releaseCounter(KEY_GLOBAL);
    }

    public boolean tryLockTask(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return true;
        }
        if (distributed) {
            String key = KEY_TASK_LOCK_PREFIX + taskId;
            Boolean ok = redis.opsForValue().setIfAbsent(key, instanceId,
                    properties.getDistributed().getTaskLockSeconds(), TimeUnit.SECONDS);
            return Boolean.TRUE.equals(ok);
        }
        return localTaskLocks.putIfAbsent(taskId, Boolean.TRUE) == null;
    }

    public void unlockTask(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return;
        }
        if (distributed) {
            String key = KEY_TASK_LOCK_PREFIX + taskId;
            String holder = redis.opsForValue().get(key);
            if (instanceId.equals(holder)) {
                redis.delete(key);
            }
            return;
        }
        localTaskLocks.remove(taskId);
    }

    public boolean isTaskLocked(String taskId) {
        if (taskId == null || taskId.trim().isEmpty()) {
            return false;
        }
        if (distributed) {
            return Boolean.TRUE.equals(redis.hasKey(KEY_TASK_LOCK_PREFIX + taskId));
        }
        return localTaskLocks.containsKey(taskId);
    }

    public boolean tryAcquireLeaderLock(String schedulerName) {
        if (!distributed) {
            return true;
        }
        String key = KEY_LEADER + ":" + schedulerName;
        Boolean ok = redis.opsForValue().setIfAbsent(key, instanceId,
                properties.getDistributed().getLeaderLockSeconds(), TimeUnit.SECONDS);
        if (Boolean.TRUE.equals(ok)) {
            return true;
        }
        String holder = redis.opsForValue().get(key);
        if (instanceId.equals(holder)) {
            redis.expire(key, properties.getDistributed().getLeaderLockSeconds(), TimeUnit.SECONDS);
            return true;
        }
        return false;
    }

    /**
     * 阻塞等待 MiMo QPS 配额（全局限流，兼容旧调用）。
     */
    public void acquireMimoPermit() throws InterruptedException {
        acquireMimoPermitForKey(0);
    }

    /**
     * 阻塞等待指定 Key 槽位的 MiMo QPS 配额。
     */
    public void acquireMimoPermitForKey(int keyIndex) throws InterruptedException {
        double qps = properties.getMimoQps();
        if (qps <= 0) {
            return;
        }
        int limit = Math.max(1, (int) Math.ceil(qps));
        long windowMs = 1000L;
        long deadline = System.currentTimeMillis() + 120_000L;
        while (System.currentTimeMillis() < deadline) {
            if (tryAcquireMimoPermitForKey(keyIndex, limit, windowMs)) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new BusinessException(429, ErrorKeys.RECOGNITION_CONCURRENT_LIMIT);
    }

    /**
     * 非阻塞尝试获取指定 Key 槽位的 MiMo QPS 配额。
     */
    public boolean tryAcquireMimoPermitForKey(int keyIndex) {
        double qps = properties.getMimoQps();
        if (qps <= 0) {
            return true;
        }
        int limit = Math.max(1, (int) Math.ceil(qps));
        return tryAcquireMimoPermitForKey(keyIndex, limit, 1000L);
    }

    private boolean tryAcquireMimoPermitForKey(int keyIndex, int limit, long windowMs) {
        long bucket = System.currentTimeMillis() / windowMs;
        String counterKey = KEY_MIMO_QPS_PREFIX + keyIndex + ":" + bucket;
        if (distributed) {
            Long count = redis.opsForValue().increment(counterKey);
            if (count != null && count == 1L) {
                redis.expire(counterKey, 3, TimeUnit.SECONDS);
            }
            return count != null && count <= limit;
        }
        AtomicInteger counter = localMimoCounters.computeIfAbsent(counterKey, k -> new AtomicInteger(0));
        if (counter.incrementAndGet() > limit) {
            return false;
        }
        if (localMimoCounters.size() > 64) {
            long cutoff = bucket - 5;
            localMimoCounters.keySet().removeIf(k -> {
                int colon = k.lastIndexOf(':');
                if (colon < 0) {
                    return true;
                }
                try {
                    return Long.parseLong(k.substring(colon + 1)) < cutoff;
                } catch (NumberFormatException e) {
                    return true;
                }
            });
        }
        return true;
    }

    private boolean acquireCounter(String key, int limit) {
        if (distributed) {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_ACQUIRE, Long.class);
            Long ok = redis.execute(script, Collections.singletonList(key),
                    String.valueOf(limit),
                    String.valueOf(properties.getDistributed().getSemaphoreTtlSeconds()));
            return ok != null && ok == 1L;
        }
        AtomicInteger counter = counterForKey(key);
        int current = counter.incrementAndGet();
        if (current > limit) {
            counter.decrementAndGet();
            return false;
        }
        return true;
    }

    private void releaseCounter(String key) {
        if (distributed) {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_RELEASE, Long.class);
            redis.execute(script, Collections.singletonList(key));
            return;
        }
        AtomicInteger counter = counterForKey(key);
        int left = counter.decrementAndGet();
        if (left <= 0) {
            removeLocalCounter(key);
        }
    }

    private AtomicInteger counterForKey(String key) {
        if (KEY_GLOBAL.equals(key)) {
            return localGlobalSem;
        }
        return localUserSem.computeIfAbsent(key, k -> new AtomicInteger(0));
    }

    private void removeLocalCounter(String key) {
        if (KEY_GLOBAL.equals(key)) {
            localGlobalSem.set(0);
            return;
        }
        localUserSem.remove(key);
    }

    private String userKey(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return KEY_USER_PREFIX + "anonymous";
        }
        return KEY_USER_PREFIX + userId.trim();
    }

    private String resolveInstanceId() {
        try {
            String host = InetAddress.getLocalHost().getHostName();
            return host + ":" + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception e) {
            return "node:" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}
