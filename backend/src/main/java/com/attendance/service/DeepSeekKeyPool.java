package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorKeys;
import com.attendance.config.DeepSeekProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DeepSeek API Key 池：多 Key 轮询借用；单 Key 鉴权/限流失败时自动切换其他 Key。
 */
@Component
public class DeepSeekKeyPool {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekKeyPool.class);
    private static final long ACQUIRE_TIMEOUT_MS = 120_000L;

    private final DeepSeekProperties deepSeekProperties;
    private final RecognitionCoordinator recognitionCoordinator;

    private List<String> keys = Collections.emptyList();
    private final AtomicInteger roundRobin = new AtomicInteger(0);

    public DeepSeekKeyPool(DeepSeekProperties deepSeekProperties, RecognitionCoordinator recognitionCoordinator) {
        this.deepSeekProperties = deepSeekProperties;
        this.recognitionCoordinator = recognitionCoordinator;
    }

    @PostConstruct
    void init() {
        keys = Collections.unmodifiableList(new ArrayList<>(deepSeekProperties.getResolvedApiKeys()));
        if (keys.isEmpty()) {
            log.warn("DeepSeek Key 池为空，请配置 DEEPSEEK_API_KEYS 或 DEEPSEEK_API_KEY");
        } else {
            log.info("DeepSeek Key 池已加载: {} 个 Key", keys.size());
        }
    }

    public boolean isConfigured() {
        return !keys.isEmpty();
    }

    public int getPoolSize() {
        return keys.size();
    }

    public MimoKeyLease acquire() throws InterruptedException {
        return acquireExcluding(Collections.emptySet());
    }

    public MimoKeyLease acquireExcluding(Set<Integer> excludeIndices) throws InterruptedException {
        if (keys.isEmpty()) {
            throw new BusinessException(500, ErrorKeys.DEEPSEEK_NOT_CONFIGURED);
        }
        Set<Integer> excluded = excludeIndices != null ? excludeIndices : Collections.emptySet();
        if (excluded.size() >= keys.size()) {
            throw new BusinessException(503, ErrorKeys.DEEPSEEK_UNAVAILABLE);
        }
        if (keys.size() == 1) {
            if (excluded.contains(0)) {
                throw new BusinessException(503, ErrorKeys.DEEPSEEK_UNAVAILABLE);
            }
            recognitionCoordinator.acquireMimoPermitForKey(0);
            return new MimoKeyLease(keys.get(0), 0);
        }
        long deadline = System.currentTimeMillis() + ACQUIRE_TIMEOUT_MS;
        int attempts = 0;
        int start = roundRobin.getAndIncrement();
        while (System.currentTimeMillis() < deadline) {
            int idx = Math.floorMod(start + attempts, keys.size());
            attempts++;
            if (excluded.contains(idx)) {
                continue;
            }
            if (recognitionCoordinator.tryAcquireMimoPermitForKey(idx)) {
                return new MimoKeyLease(keys.get(idx), idx);
            }
            Thread.sleep(attempts % 20 == 0 ? 100L : 50L);
        }
        throw new BusinessException(429, ErrorKeys.RECOGNITION_CONCURRENT_LIMIT);
    }
}
