package com.attendance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 提醒历史欠账补发：异步单飞 + 合并排队，避免启动/保存规则时在请求线程同步扫全库。
 */
@Service
public class ReminderCatchUpCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ReminderCatchUpCoordinator.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean requeue = new AtomicBoolean(false);
    private final AtomicReference<String> pendingReason = new AtomicReference<>();

    @Autowired
    @Qualifier("reminderExecutor")
    private Executor reminderExecutor;

    @Autowired
    @Lazy
    private ReminderSchedulerService reminderSchedulerService;

    /** 请求一次补发；若已在跑则合并到下一轮，不阻塞调用方。 */
    public void requestCatchUp(String reason) {
        pendingReason.set(reason != null ? reason : "catch-up");
        if (running.compareAndSet(false, true)) {
            reminderExecutor.execute(this::drain);
            return;
        }
        requeue.set(true);
        log.debug("提醒补发已在执行，已合并排队 reason={}", reason);
    }

    private void drain() {
        try {
            do {
                requeue.set(false);
                String reason = pendingReason.getAndSet(null);
                long started = System.currentTimeMillis();
                try {
                    reminderSchedulerService.runCatchUpScan();
                    log.info("提醒补发完成 reason={} elapsedMs={}",
                            reason, System.currentTimeMillis() - started);
                } catch (Exception e) {
                    log.warn("提醒补发失败 reason={}: {}", reason, e.getMessage(), e);
                }
            } while (requeue.get());
        } finally {
            running.set(false);
            if (requeue.compareAndSet(true, false) || pendingReason.get() != null) {
                requestCatchUp(pendingReason.get() != null ? pendingReason.get() : "requeue");
            }
        }
    }
}
