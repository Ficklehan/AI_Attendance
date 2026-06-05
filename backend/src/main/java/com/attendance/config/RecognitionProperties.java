package com.attendance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 识别并发、队列、僵尸任务与 MiMo 配额相关配置。
 */
@Component
@ConfigurationProperties(prefix = "attendance.recognition")
public class RecognitionProperties {

    /** 单用户同时进行中的识别上限 */
    private int maxPerUser = 2;
    /** 全集群同时进行中的识别上限（对齐 MiMo 并发配额） */
    private int globalMaxConcurrent = 8;
    /** 全集群 MiMo API 每秒请求上限 */
    private double mimoQps = 5.0;
    /** 无心跳 processing 任务自动失败阈值（分钟），默认 0.6h */
    private int zombieTimeoutMinutes = 36;
    /** 多图延后上传宽限期（分钟） */
    private int uploadGraceMinutes = 5;
    /** 心跳停滞判定（秒） */
    private int staleHeartbeatSeconds = 120;
    private int recoveryBatchSize = 20;
    private final Queue queue = new Queue();
    private final Executor executor = new Executor();
    private final Distributed distributed = new Distributed();

    public int getMaxPerUser() {
        return maxPerUser;
    }

    public void setMaxPerUser(int maxPerUser) {
        this.maxPerUser = maxPerUser;
    }

    public int getGlobalMaxConcurrent() {
        return globalMaxConcurrent;
    }

    public void setGlobalMaxConcurrent(int globalMaxConcurrent) {
        this.globalMaxConcurrent = globalMaxConcurrent;
    }

    public double getMimoQps() {
        return mimoQps;
    }

    public void setMimoQps(double mimoQps) {
        this.mimoQps = mimoQps;
    }

    public int getZombieTimeoutMinutes() {
        return zombieTimeoutMinutes;
    }

    public void setZombieTimeoutMinutes(int zombieTimeoutMinutes) {
        this.zombieTimeoutMinutes = zombieTimeoutMinutes;
    }

    public int getUploadGraceMinutes() {
        return uploadGraceMinutes;
    }

    public void setUploadGraceMinutes(int uploadGraceMinutes) {
        this.uploadGraceMinutes = uploadGraceMinutes;
    }

    public int getStaleHeartbeatSeconds() {
        return staleHeartbeatSeconds;
    }

    public void setStaleHeartbeatSeconds(int staleHeartbeatSeconds) {
        this.staleHeartbeatSeconds = staleHeartbeatSeconds;
    }

    public int getRecoveryBatchSize() {
        return recoveryBatchSize;
    }

    public void setRecoveryBatchSize(int recoveryBatchSize) {
        this.recoveryBatchSize = recoveryBatchSize;
    }

    public Queue getQueue() {
        return queue;
    }

    public Executor getExecutor() {
        return executor;
    }

    public Distributed getDistributed() {
        return distributed;
    }

    public static class Queue {
        private boolean enabled = true;
        private int maxPending = 2000;
        private int workerThreads = 2;
        private long pollIntervalMs = 500;
        private long slotWaitTimeoutMs = 300_000L;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxPending() {
            return maxPending;
        }

        public void setMaxPending(int maxPending) {
            this.maxPending = maxPending;
        }

        public int getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public long getSlotWaitTimeoutMs() {
            return slotWaitTimeoutMs;
        }

        public void setSlotWaitTimeoutMs(long slotWaitTimeoutMs) {
            this.slotWaitTimeoutMs = slotWaitTimeoutMs;
        }
    }

    public static class Executor {
        private int corePoolSize = 4;
        private int maxPoolSize = 8;
        private int queueCapacity = 100;

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }

    public static class Distributed {
        /** 显式开启 Redis 分布式协调；未配置时若 spring.redis.host 非空则自动开启 */
        private Boolean enabled;
        private int taskLockSeconds = 900;
        private int leaderLockSeconds = 55;
        private int semaphoreTtlSeconds = 3600;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public int getTaskLockSeconds() {
            return taskLockSeconds;
        }

        public void setTaskLockSeconds(int taskLockSeconds) {
            this.taskLockSeconds = taskLockSeconds;
        }

        public int getLeaderLockSeconds() {
            return leaderLockSeconds;
        }

        public void setLeaderLockSeconds(int leaderLockSeconds) {
            this.leaderLockSeconds = leaderLockSeconds;
        }

        public int getSemaphoreTtlSeconds() {
            return semaphoreTtlSeconds;
        }

        public void setSemaphoreTtlSeconds(int semaphoreTtlSeconds) {
            this.semaphoreTtlSeconds = semaphoreTtlSeconds;
        }
    }
}
