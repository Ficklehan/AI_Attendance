package com.attendance.service;

import com.attendance.config.RecognitionProperties;
import com.attendance.entity.RecognitionQueueJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
public class RecognitionQueueWorker implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RecognitionQueueWorker.class);

    @Autowired
    private RecognitionProperties recognitionProperties;

    @Autowired
    private RecognitionQueueService recognitionQueueService;

    @Autowired
    private RecognitionJobService recognitionJobService;

    @Override
    public void run(ApplicationArguments args) {
        if (!recognitionProperties.getQueue().isEnabled()) {
            return;
        }
        int workers = Math.max(1, recognitionProperties.getQueue().getWorkerThreads());
        for (int i = 0; i < workers; i++) {
            Thread worker = new Thread(this::workerLoop, "recognition-queue-" + i);
            worker.setDaemon(true);
            worker.start();
        }
        log.info("识别队列 worker 已启动: threads={}", workers);
    }

    private void workerLoop() {
        long pollMs = Math.max(100L, recognitionProperties.getQueue().getPollIntervalMs());
        while (!Thread.currentThread().isInterrupted()) {
            try {
                RecognitionQueueJob job = recognitionQueueService.pollJob("worker");
                if (job == null) {
                    Thread.sleep(pollMs);
                    continue;
                }
                recognitionJobService.processQueuedJob(job);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("识别队列 worker 异常", e);
                try {
                    Thread.sleep(pollMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
