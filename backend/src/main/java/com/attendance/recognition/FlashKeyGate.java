package com.attendance.recognition;

import com.attendance.service.MimoKeyLease;
import com.attendance.service.RecognitionModelRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 多 key 占用互斥：ground 条带、到离 VLM、姓名 VLM 共用。
 */
@Component
public class FlashKeyGate {

    private static final Logger log = LoggerFactory.getLogger(FlashKeyGate.class);

    private final RecognitionModelRuntime runtime;
    private final KeyOccupancy occupancy = new KeyOccupancy();

    public FlashKeyGate(RecognitionModelRuntime runtime) {
        this.runtime = runtime;
    }

    public HeldKey acquire() throws InterruptedException {
        int attempts = Math.max(4, runtime.getKeyPoolSize() * 2);
        for (int attempt = 0; attempt < attempts; attempt++) {
            Set<Integer> exclude = occupancy.snapshot();
            MimoKeyLease lease = runtime.acquireKeyExcluding(exclude);
            if (lease == null || lease.getApiKey() == null || lease.getApiKey().trim().isEmpty()) {
                continue;
            }
            if (occupancy.tryOccupy(lease.getKeyIndex())) {
                return new HeldKey(lease.getApiKey().trim(), lease.getKeyIndex(), true);
            }
        }
        throw new IllegalStateException("无法占用空闲 vision key");
    }

    public HeldKey acquireOrFallback(String fallbackKey) {
        try {
            return acquire();
        } catch (Exception e) {
            log.warn("Flash key 占用失败，回退: {}", e.getMessage());
            String key = fallbackKey == null ? "" : fallbackKey.trim();
            return new HeldKey(key, -1, false);
        }
    }

    KeyOccupancy occupancy() {
        return occupancy;
    }

    public final class HeldKey implements AutoCloseable {
        private final String apiKey;
        private final int keyIndex;
        private final boolean occupied;
        private final AtomicBoolean closed = new AtomicBoolean(false);

        HeldKey(String apiKey, int keyIndex, boolean occupied) {
            this.apiKey = apiKey;
            this.keyIndex = keyIndex;
            this.occupied = occupied;
        }

        public String apiKey() {
            return apiKey == null ? "" : apiKey;
        }

        public int keyIndex() {
            return keyIndex;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true) && occupied) {
                occupancy.release(keyIndex);
            }
        }
    }
}
