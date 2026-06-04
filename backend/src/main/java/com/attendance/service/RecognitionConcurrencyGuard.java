package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorKeys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 限制单用户同时进行中的识别任务数量。
 */
@Component
public class RecognitionConcurrencyGuard {

    private final ConcurrentHashMap<String, AtomicInteger> activeByUser = new ConcurrentHashMap<>();

    @Value("${attendance.recognition.max-per-user:2}")
    private int maxPerUser;

    public void acquire(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }
        AtomicInteger counter = activeByUser.computeIfAbsent(userId, k -> new AtomicInteger(0));
        int current = counter.incrementAndGet();
        if (current > maxPerUser) {
            counter.decrementAndGet();
            throw new BusinessException(429, ErrorKeys.RECOGNITION_CONCURRENT_LIMIT);
        }
    }

    public void release(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }
        AtomicInteger counter = activeByUser.get(userId);
        if (counter == null) {
            return;
        }
        int left = counter.decrementAndGet();
        if (left <= 0) {
            activeByUser.remove(userId, counter);
        }
    }
}
