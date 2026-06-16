package com.attendance.service;

import com.attendance.config.RecognitionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 僵尸任务治理：长时间 processing 且无心跳的任务自动标失败，避免反复恢复占槽。
 */
@Component
public class RecognitionZombieCleaner {

    private static final Logger log = LoggerFactory.getLogger(RecognitionZombieCleaner.class);

    @Autowired
    private TaskService taskService;

    @Autowired
    private RecognitionCoordinator recognitionCoordinator;

    @Autowired
    private RecognitionProperties recognitionProperties;

    @Autowired
    private RecognitionQueueService recognitionQueueService;

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void expireZombieTasks() {
        if (!recognitionCoordinator.tryAcquireLeaderLock("zombie")) {
            return;
        }
        recognitionQueueService.reclaimStaleRunningJobs();
        int zombieMinutes = recognitionProperties.getZombieTimeoutMinutes();
        int batchSize = recognitionProperties.getRecoveryBatchSize();
        List<String> zombieIds = taskService.findZombieProcessingTaskIds(zombieMinutes, batchSize);
        for (String taskId : zombieIds) {
            try {
                log.warn("僵尸识别任务自动失败: taskId={}, thresholdMinutes={}", taskId, zombieMinutes);
                taskService.failTask(taskId,
                        "识别超时未完成（超过 " + zombieMinutes + " 分钟无心跳），请重新上传");
                recognitionCoordinator.unlockTask(taskId);
            } catch (Exception e) {
                log.error("僵尸任务清理失败: taskId={}", taskId, e);
            }
        }
    }
}
