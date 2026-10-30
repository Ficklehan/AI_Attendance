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
    /** legacy = 整页 VLM；ocr_primary = Harness 切格 + dsh-ocr-local */
    private String pipeline = "ocr_primary";
    private final Ocr ocr = new Ocr();
    private final Harness harness = new Harness();
    private final Rule rule = new Rule();

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

    public String getPipeline() {
        return pipeline;
    }

    public void setPipeline(String pipeline) {
        this.pipeline = pipeline;
    }

    public Ocr getOcr() {
        return ocr;
    }

    public Harness getHarness() {
        return harness;
    }

    public Rule getRule() {
        return rule;
    }

    public boolean isOcrPrimary() {
        return pipeline == null || "ocr_primary".equalsIgnoreCase(pipeline.trim())
                || "structured".equalsIgnoreCase(pipeline.trim());
    }

    public static class Ocr {
        private boolean enabled = true;
        private String engine = "dsh-ocr-local";
        private String pluginRoot = "";
        private String pythonPath = "";
        private String modelDir = "";
        private int timeoutMs = 8000;
        private int parallelism = 8;
        private boolean fallbackLegacyOnUnavailable = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getEngine() {
            return engine;
        }

        public void setEngine(String engine) {
            this.engine = engine;
        }

        public String getPluginRoot() {
            return pluginRoot;
        }

        public void setPluginRoot(String pluginRoot) {
            this.pluginRoot = pluginRoot;
        }

        public String getPythonPath() {
            return pythonPath;
        }

        public void setPythonPath(String pythonPath) {
            this.pythonPath = pythonPath;
        }

        public String getModelDir() {
            return modelDir;
        }

        public void setModelDir(String modelDir) {
            this.modelDir = modelDir;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public int getParallelism() {
            return parallelism;
        }

        public void setParallelism(int parallelism) {
            this.parallelism = parallelism;
        }

        public boolean isFallbackLegacyOnUnavailable() {
            return fallbackLegacyOnUnavailable;
        }

        public void setFallbackLegacyOnUnavailable(boolean fallbackLegacyOnUnavailable) {
            this.fallbackLegacyOnUnavailable = fallbackLegacyOnUnavailable;
        }
    }

    public static class Harness {
        private boolean enabled = true;
        private String profile = "attendance";
        private String cropPlugin = "dsh-crop-zoom";
        private String groundPlugin = "dsh-vision-router";
        private String cropZoomBin = "";
        private int zoomTarget = 800;
        private int zoomMaxScale = 16;
        private String zoomMode = "nearest";
        private int previewWidth = 1000;
        private int padPx = 2;
        private boolean preferCli = true;
        private int maxVlmCellsPerPage = 24;
        private int vlmParallelism = 8;
        private int groundTimeoutMs = 90000;
        private int vlmTimeoutMs = 45000;
        private String dshBin = "";
        private int extractTimeoutMs = 600000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProfile() {
            return profile;
        }

        public void setProfile(String profile) {
            this.profile = profile;
        }

        public String getCropPlugin() {
            return cropPlugin;
        }

        public void setCropPlugin(String cropPlugin) {
            this.cropPlugin = cropPlugin;
        }

        public String getGroundPlugin() {
            return groundPlugin;
        }

        public void setGroundPlugin(String groundPlugin) {
            this.groundPlugin = groundPlugin;
        }

        public String getCropZoomBin() {
            return cropZoomBin;
        }

        public void setCropZoomBin(String cropZoomBin) {
            this.cropZoomBin = cropZoomBin;
        }

        public int getZoomTarget() {
            return zoomTarget;
        }

        public void setZoomTarget(int zoomTarget) {
            this.zoomTarget = zoomTarget;
        }

        public int getZoomMaxScale() {
            return zoomMaxScale;
        }

        public void setZoomMaxScale(int zoomMaxScale) {
            this.zoomMaxScale = zoomMaxScale;
        }

        public String getZoomMode() {
            return zoomMode;
        }

        public void setZoomMode(String zoomMode) {
            this.zoomMode = zoomMode;
        }

        public int getPreviewWidth() {
            return previewWidth;
        }

        public void setPreviewWidth(int previewWidth) {
            this.previewWidth = previewWidth;
        }

        public int getPadPx() {
            return padPx;
        }

        public void setPadPx(int padPx) {
            this.padPx = padPx;
        }

        public boolean isPreferCli() {
            return preferCli;
        }

        public void setPreferCli(boolean preferCli) {
            this.preferCli = preferCli;
        }

        public int getMaxVlmCellsPerPage() {
            return maxVlmCellsPerPage;
        }

        public void setMaxVlmCellsPerPage(int maxVlmCellsPerPage) {
            this.maxVlmCellsPerPage = maxVlmCellsPerPage;
        }

        public int getVlmParallelism() {
            return vlmParallelism;
        }

        public void setVlmParallelism(int vlmParallelism) {
            this.vlmParallelism = vlmParallelism;
        }

        public int getGroundTimeoutMs() {
            return groundTimeoutMs;
        }

        public void setGroundTimeoutMs(int groundTimeoutMs) {
            this.groundTimeoutMs = groundTimeoutMs;
        }

        public int getVlmTimeoutMs() {
            return vlmTimeoutMs;
        }

        public void setVlmTimeoutMs(int vlmTimeoutMs) {
            this.vlmTimeoutMs = vlmTimeoutMs;
        }

        public String getDshBin() {
            return dshBin;
        }

        public void setDshBin(String dshBin) {
            this.dshBin = dshBin;
        }

        public int getExtractTimeoutMs() {
            return extractTimeoutMs;
        }

        public void setExtractTimeoutMs(int extractTimeoutMs) {
            this.extractTimeoutMs = extractTimeoutMs;
        }
    }

    public static class Rule {
        private double acceptThreshold = 0.85d;
        private double reviewThreshold = 0.50d;
        private boolean ignoreLongLine = true;
        private double inkDarkRatio = 0.028d;
        private double inkVariance = 280d;
        private int lineAngleMin = 20;
        private int lineAngleMax = 75;
        private boolean blurKeepText = true;
        private int darkLuminance = 165;

        public double getAcceptThreshold() {
            return acceptThreshold;
        }

        public void setAcceptThreshold(double acceptThreshold) {
            this.acceptThreshold = acceptThreshold;
        }

        public double getReviewThreshold() {
            return reviewThreshold;
        }

        public void setReviewThreshold(double reviewThreshold) {
            this.reviewThreshold = reviewThreshold;
        }

        public boolean isIgnoreLongLine() {
            return ignoreLongLine;
        }

        public void setIgnoreLongLine(boolean ignoreLongLine) {
            this.ignoreLongLine = ignoreLongLine;
        }

        public double getInkDarkRatio() {
            return inkDarkRatio;
        }

        public void setInkDarkRatio(double inkDarkRatio) {
            this.inkDarkRatio = inkDarkRatio;
        }

        public double getInkVariance() {
            return inkVariance;
        }

        public void setInkVariance(double inkVariance) {
            this.inkVariance = inkVariance;
        }

        public int getLineAngleMin() {
            return lineAngleMin;
        }

        public void setLineAngleMin(int lineAngleMin) {
            this.lineAngleMin = lineAngleMin;
        }

        public int getLineAngleMax() {
            return lineAngleMax;
        }

        public void setLineAngleMax(int lineAngleMax) {
            this.lineAngleMax = lineAngleMax;
        }

        public boolean isBlurKeepText() {
            return blurKeepText;
        }

        public void setBlurKeepText(boolean blurKeepText) {
            this.blurKeepText = blurKeepText;
        }

        public int getDarkLuminance() {
            return darkLuminance;
        }

        public void setDarkLuminance(int darkLuminance) {
            this.darkLuminance = darkLuminance;
        }
    }
}
