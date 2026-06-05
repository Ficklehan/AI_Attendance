package com.attendance.service;

import org.springframework.stereotype.Component;

/**
 * 兼容旧调用方：委托 {@link RecognitionCoordinator} 实现单用户 + 全局分布式信号量。
 */
@Component
public class RecognitionConcurrencyGuard {

    private final RecognitionCoordinator recognitionCoordinator;

    public RecognitionConcurrencyGuard(RecognitionCoordinator recognitionCoordinator) {
        this.recognitionCoordinator = recognitionCoordinator;
    }

    public void acquire(String userId) {
        recognitionCoordinator.acquireSlots(userId);
    }

    public boolean tryAcquire(String userId) {
        return recognitionCoordinator.tryAcquireSlots(userId);
    }

    public void release(String userId) {
        recognitionCoordinator.releaseSlots(userId);
    }
}
