package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.RecognitionProperties;
import com.attendance.dto.RecognitionCheckpoint;
import com.attendance.entity.RecognitionQueueJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 识别任务编排：公平队列、分布式信号量、心跳、断点恢复、僵尸任务交由 {@link RecognitionZombieCleaner}。
 */
@Service
public class RecognitionJobService {

    private static final Logger log = LoggerFactory.getLogger(RecognitionJobService.class);
    private static final String JOB_SOURCE_USER = "user";
    private static final String JOB_SOURCE_PAGES = "pages";
    private static final String JOB_SOURCE_RECOVERY = "recovery";

    @Autowired
    private TaskService taskService;

    @Autowired
    private RecognitionRunner recognitionRunner;

    @Autowired
    private RecognitionCoordinator recognitionCoordinator;

    @Autowired
    private RecognitionQueueService recognitionQueueService;

    @Autowired
    private RecognitionProperties recognitionProperties;

    @Autowired
    @Qualifier("recognitionExecutor")
    private Executor recognitionExecutor;

    /**
     * 幂等提交多图/任务级识别。
     *
     * @return true 表示已接受（新提交或已在跑）；false 表示无需重复提交（已完成等）
     */
    public boolean submitTaskRecognition(String taskId, String configCountry, String client,
                                         SecurityContext securityContext, String recognitionUserId) {
        com.attendance.entity.Task task = taskService.getTaskForCurrentUser(taskId);
        if ("processed".equals(task.getStatus()) || "confirmed".equals(task.getStatus())) {
            return false;
        }
        if (shouldSkipDuplicateSubmission(taskId, task)) {
            return true;
        }
        if (recognitionProperties.getQueue().isEnabled()) {
            recognitionQueueService.enqueue(taskId, recognitionUserId, configCountry, client, JOB_SOURCE_USER);
            return true;
        }
        dispatchDirect(taskId, configCountry, client, securityContext, recognitionUserId, JOB_SOURCE_USER);
        return true;
    }

    public void submitPagesRecognition(String taskId, List<UploadMediaSupport.ImagePage> pages,
                                       String configCountry, String workingCountry, String client,
                                       SecurityContext securityContext, String recognitionUserId) {
        com.attendance.entity.Task task = taskService.getTaskByIdInternal(taskId);
        if (task != null && shouldSkipDuplicateSubmission(taskId, task)) {
            log.info("单图识别已在执行: taskId={}", taskId);
            return;
        }
        if (recognitionProperties.getQueue().isEnabled()) {
            recognitionQueueService.enqueue(taskId, recognitionUserId, configCountry, client, JOB_SOURCE_PAGES);
            return;
        }
        dispatchDirect(taskId, configCountry, client, securityContext, recognitionUserId, JOB_SOURCE_PAGES);
    }

    public void processQueuedJob(RecognitionQueueJob job) {
        if (job == null) {
            return;
        }
        String taskId = job.getTaskId();
        if (!recognitionCoordinator.tryLockTask(taskId)) {
            recognitionQueueService.requeue(job.getId());
            return;
        }
        try {
            if (!waitForSlots(job.getUserId())) {
                log.warn("识别队列等待并发槽超时，重新入队: jobId={}, taskId={}", job.getId(), taskId);
                recognitionCoordinator.unlockTask(taskId);
                recognitionQueueService.requeue(job.getId());
                return;
            }
            recognitionExecutor.execute(() -> {
                try {
                    runRecognitionJob(job, null);
                    recognitionQueueService.markCompleted(job.getId());
                } catch (Exception e) {
                    log.error("队列识别失败: jobId={}, taskId={}", job.getId(), taskId, e);
                    recognitionQueueService.markFailed(job.getId());
                    try {
                        handleRecognitionFailure(taskId, e, new RecognitionTrace(taskId, job.getClient()));
                    } catch (Exception failEx) {
                        log.error("标记任务失败状态时出错: taskId={}", taskId, failEx);
                    }
                } finally {
                    recognitionCoordinator.releaseSlots(job.getUserId());
                    recognitionCoordinator.unlockTask(taskId);
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            recognitionCoordinator.unlockTask(taskId);
            recognitionQueueService.requeue(job.getId());
        } catch (RuntimeException e) {
            recognitionCoordinator.releaseSlots(job.getUserId());
            recognitionCoordinator.unlockTask(taskId);
            recognitionQueueService.markFailed(job.getId());
            throw e;
        }
    }

    private void dispatchDirect(String taskId, String configCountry, String client,
                                SecurityContext securityContext, String recognitionUserId, String jobSource) {
        if (!recognitionCoordinator.tryLockTask(taskId)) {
            log.info("识别任务已在执行中，跳过: taskId={}", taskId);
            return;
        }
        try {
            recognitionCoordinator.acquireSlots(recognitionUserId);
        } catch (RuntimeException e) {
            recognitionCoordinator.unlockTask(taskId);
            throw e;
        }
        RecognitionQueueJob pseudo = new RecognitionQueueJob();
        pseudo.setTaskId(taskId);
        pseudo.setUserId(recognitionUserId);
        pseudo.setConfigCountry(configCountry);
        pseudo.setClient(client);
        pseudo.setJobSource(jobSource);
        recognitionExecutor.execute(() -> {
            try {
                runRecognitionJob(pseudo, securityContext);
            } finally {
                recognitionCoordinator.releaseSlots(recognitionUserId);
                recognitionCoordinator.unlockTask(taskId);
            }
        });
    }

    private void runRecognitionJob(RecognitionQueueJob job, SecurityContext securityContext) {
        if (securityContext != null) {
            SecurityContextHolder.setContext(securityContext);
        } else {
            SecurityContextHolder.clearContext();
        }
        String taskId = job.getTaskId();
        String configCountry = job.getConfigCountry() != null ? job.getConfigCountry() : "default";
        RecognitionTrace trace = new RecognitionTrace(taskId, job.getClient());
        try {
            taskService.touchRecognitionHeartbeat(taskId);
            // 队列/恢复线程无 HTTP 安全上下文，走内部任务访问路径
            boolean systemRecovery = securityContext == null
                    || JOB_SOURCE_RECOVERY.equals(job.getJobSource());
            RecognitionRunner.RecognitionOutcome outcome = recognitionRunner.recognizeAllTaskImages(
                    taskId, configCountry, trace, systemRecovery);
            String rawData = JSON.toJSONString(outcome.getRecords());
            taskService.updateTaskRawData(taskId, rawData, outcome.getEngine(), trace, outcome.getImageQuality());
            taskService.clearRecognitionCheckpoint(taskId);
            log.info("任务识别完成: taskId={}, rows={}, source={}", taskId, outcome.getRecords().size(), job.getJobSource());
        } catch (Exception e) {
            handleRecognitionFailure(taskId, e, trace);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private boolean shouldSkipDuplicateSubmission(String taskId, com.attendance.entity.Task task) {
        if (recognitionCoordinator.isTaskLocked(taskId)) {
            log.info("识别任务已在执行中，跳过重复提交: taskId={}", taskId);
            return true;
        }
        if (recognitionProperties.getQueue().isEnabled() && recognitionQueueService.hasActiveJob(taskId)) {
            log.info("识别任务已在队列中，跳过重复提交: taskId={}", taskId);
            return true;
        }
        int staleSeconds = recognitionProperties.getStaleHeartbeatSeconds();
        if ("processing".equals(task.getStatus())
                && taskService.isRecognitionHeartbeatFresh(taskId, staleSeconds * 1000L)
                && taskService.hasRecognitionWorkStarted(task)) {
            log.info("识别任务心跳正常且已有进度，跳过重复提交: taskId={}", taskId);
            return true;
        }
        return false;
    }

    private boolean waitForSlots(String userId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + recognitionProperties.getQueue().getSlotWaitTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            if (recognitionCoordinator.tryAcquireSlots(userId)) {
                return true;
            }
            Thread.sleep(500L);
        }
        return false;
    }

    private void handleRecognitionFailure(String taskId, Exception e, RecognitionTrace trace) {
        log.error("识别失败: taskId={}", taskId, e);
        String msg = com.attendance.util.RecognitionFailureMessages.toClientMessage(e);
        Map<String, Object> errorArgs = null;
        if (e instanceof com.attendance.common.BusinessException) {
            com.attendance.common.BusinessException be = (com.attendance.common.BusinessException) e;
            if (be.getMessageArgs() != null && !be.getMessageArgs().isEmpty()) {
                errorArgs = be.getMessageArgs();
            }
        }
        if (trace != null) {
            trace.step("upload_failed", "message", msg);
        }
        RecognitionCheckpoint cp = taskService.loadRecognitionCheckpoint(taskId);
        cp.setLastError(msg);
        taskService.saveRecognitionCheckpoint(taskId, cp);
        taskService.failTask(taskId, msg, errorArgs, trace);
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void recoverStaleRecognitionJobs() {
        if (!recognitionCoordinator.tryAcquireLeaderLock("recovery")) {
            return;
        }
        int staleSeconds = recognitionProperties.getStaleHeartbeatSeconds();
        int batchSize = recognitionProperties.getRecoveryBatchSize();
        List<String> staleIds = taskService.findStaleProcessingTaskIds(staleSeconds, batchSize);
        for (String taskId : staleIds) {
            if (recognitionCoordinator.isTaskLocked(taskId)) {
                continue;
            }
            log.warn("检测到停滞识别任务，尝试续跑: taskId={}", taskId);
            try {
                com.attendance.entity.Task task = taskService.getTaskByIdInternal(taskId);
                if (task == null || !"processing".equals(task.getStatus())) {
                    continue;
                }
                String configCountry = task.getPromptCountry() != null ? task.getPromptCountry() : "default";
                if (recognitionProperties.getQueue().isEnabled()) {
                    recognitionQueueService.enqueue(taskId, task.getUserId(), configCountry,
                            "recovery-scheduler", JOB_SOURCE_RECOVERY);
                } else if (recognitionCoordinator.tryAcquireSlots(task.getUserId())) {
                    RecognitionQueueJob job = new RecognitionQueueJob();
                    job.setTaskId(taskId);
                    job.setUserId(task.getUserId());
                    job.setConfigCountry(configCountry);
                    job.setClient("recovery-scheduler");
                    job.setJobSource(JOB_SOURCE_RECOVERY);
                    recognitionExecutor.execute(() -> {
                        try {
                            if (recognitionCoordinator.tryLockTask(taskId)) {
                                runRecognitionJob(job, null);
                            }
                        } finally {
                            recognitionCoordinator.releaseSlots(task.getUserId());
                            recognitionCoordinator.unlockTask(taskId);
                        }
                    });
                } else {
                    log.warn("恢复任务无空闲并发槽，延后: taskId={}", taskId);
                }
            } catch (Exception e) {
                log.error("恢复识别任务失败: taskId={}", taskId, e);
            }
        }
    }
}
