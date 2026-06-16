package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorKeys;
import com.attendance.config.RecognitionProperties;
import com.attendance.entity.RecognitionQueueJob;
import com.attendance.mapper.RecognitionQueueMapper;
import com.attendance.util.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RecognitionQueueService {

    private static final Logger log = LoggerFactory.getLogger(RecognitionQueueService.class);

    @Autowired
    private RecognitionQueueMapper recognitionQueueMapper;

    @Autowired
    private RecognitionCoordinator recognitionCoordinator;

    @Autowired
    private RecognitionProperties recognitionProperties;

    @Autowired(required = false)
    private StringRedisTemplate redis;

    public String enqueue(String taskId, String userId, String configCountry, String client, String jobSource) {
        if (!recognitionProperties.getQueue().isEnabled()) {
            return null;
        }
        reclaimStaleRunningJobs();
        int pending = recognitionQueueMapper.countPending();
        if (pending >= recognitionProperties.getQueue().getMaxPending()) {
            throw new BusinessException(429, ErrorKeys.RECOGNITION_CONCURRENT_LIMIT);
        }
        if (recognitionQueueMapper.countActiveByTaskId(taskId) > 0) {
            log.info("任务已在识别队列中，跳过重复入队: taskId={}", taskId);
            return null;
        }

        RecognitionQueueJob job = new RecognitionQueueJob();
        job.setId(IdGenerator.generateId());
        job.setTaskId(taskId);
        job.setUserId(userId);
        job.setConfigCountry(configCountry);
        job.setClient(client);
        job.setJobSource(jobSource);
        job.setStatus("pending");
        recognitionQueueMapper.insertJob(job);

        if (redis != null && recognitionCoordinator.isDistributed()) {
            redis.opsForList().leftPush(recognitionCoordinator.getQueueRedisKey(), job.getId());
        }
        log.info("识别任务入队: jobId={}, taskId={}, source={}", job.getId(), taskId, jobSource);
        return job.getId();
    }

    public RecognitionQueueJob pollJob(String instanceId) {
        if (redis != null && recognitionCoordinator.isDistributed()) {
            String jobId = redis.opsForList().rightPop(recognitionCoordinator.getQueueRedisKey(), 1, TimeUnit.SECONDS);
            if (jobId != null && !jobId.trim().isEmpty()) {
                RecognitionQueueJob job = recognitionQueueMapper.selectById(jobId.trim());
                if (job != null && "pending".equals(job.getStatus())) {
                    if (recognitionQueueMapper.markRunning(job.getId(), instanceId) > 0) {
                        return recognitionQueueMapper.selectById(job.getId());
                    }
                }
            }
        }
        return claimFromDatabase(instanceId);
    }

    private RecognitionQueueJob claimFromDatabase(String instanceId) {
        RecognitionQueueJob job = recognitionQueueMapper.selectOldestPending();
        if (job == null) {
            return null;
        }
        if (recognitionQueueMapper.markRunning(job.getId(), instanceId) <= 0) {
            return null;
        }
        return recognitionQueueMapper.selectById(job.getId());
    }

    public void markCompleted(String jobId) {
        if (jobId == null) {
            return;
        }
        recognitionQueueMapper.markCompleted(jobId);
    }

    public void markFailed(String jobId) {
        if (jobId == null) {
            return;
        }
        recognitionQueueMapper.markFailed(jobId);
    }

    public void requeue(String jobId) {
        if (jobId == null) {
            return;
        }
        recognitionQueueMapper.requeue(jobId);
        if (redis != null && recognitionCoordinator.isDistributed()) {
            redis.opsForList().leftPush(recognitionCoordinator.getQueueRedisKey(), jobId);
        }
    }

    public boolean hasActiveJob(String taskId) {
        reclaimStaleRunningJobs();
        return recognitionQueueMapper.countActiveByTaskId(taskId) > 0;
    }

    public int reclaimStaleRunningJobs() {
        int staleSeconds = Math.max(180, recognitionProperties.getStaleHeartbeatSeconds() * 3);
        int reclaimed = recognitionQueueMapper.failStaleRunningJobs(staleSeconds);
        if (reclaimed > 0) {
            log.warn("回收僵死识别队列任务: count={}, staleSeconds={}", reclaimed, staleSeconds);
        }
        return reclaimed;
    }
}
