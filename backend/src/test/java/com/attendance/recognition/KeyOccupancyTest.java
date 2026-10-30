package com.attendance.recognition;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyOccupancyTest {

    @Test
    void concurrentOccupyDoesNotShareIndex() throws Exception {
        final KeyOccupancy occupancy = new KeyOccupancy();
        final Set<Integer> taken = java.util.Collections.synchronizedSet(new HashSet<Integer>());
        final AtomicInteger duplicates = new AtomicInteger();
        int n = 8;
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(n);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        if (occupancy.tryOccupy(idx)) {
                            if (!taken.add(Integer.valueOf(idx))) {
                                duplicates.incrementAndGet();
                            }
                        } else {
                            duplicates.incrementAndGet();
                        }
                    } catch (Exception e) {
                        duplicates.incrementAndGet();
                    } finally {
                        occupancy.release(idx);
                        done.countDown();
                    }
                }
            }).start();
        }
        start.countDown();
        done.await();
        assertEquals(0, duplicates.get());
        assertEquals(0, occupancy.size());
    }

    @Test
    void snapshotExcludesOccupied() {
        KeyOccupancy occupancy = new KeyOccupancy();
        assertTrue(occupancy.tryOccupy(2));
        assertTrue(occupancy.snapshot().contains(Integer.valueOf(2)));
        occupancy.release(2);
        assertTrue(occupancy.snapshot().isEmpty());
    }
}
