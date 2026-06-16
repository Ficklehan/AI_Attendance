package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.dto.ImageQualityAssessment;
import com.attendance.dto.RecognitionCheckpoint;
import com.attendance.util.RecordCountryDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;

@Component
public class RecognitionRunner {

    private static final Logger log = LoggerFactory.getLogger(RecognitionRunner.class);

    @Autowired
    private AIParserService aiParserService;

    @Autowired
    private SimulatedRecognitionService simulatedRecognitionService;

    @Autowired
    private RecognitionSupport recognitionSupport;

    @Autowired
    private TaskService taskService;

    @Autowired
    private UploadMediaSupport uploadMediaSupport;

    @Autowired
    private RecognitionQualityGuard recognitionQualityGuard;

    @Autowired
    private MimoKeyPool mimoKeyPool;

    @Autowired
    @Qualifier("recognitionExecutor")
    private Executor recognitionExecutor;

    private static final int PROGRESS_COUNT_EVERY = 5;
    private static final int FULL_RAW_FLUSH_EVERY = 15;
    public static final int RECOGNITION_TIMEOUT_SECONDS = 900;

    public static class RecognitionOutcome {
        private final List<JSONObject> records;
        private final String engine;
        private final String promptCountry;
        private final ImageQualityAssessment imageQuality;

        public RecognitionOutcome(List<JSONObject> records, String engine, String promptCountry) {
            this(records, engine, promptCountry, ImageQualityAssessment.ok());
        }

        public RecognitionOutcome(List<JSONObject> records, String engine, String promptCountry,
                                  ImageQualityAssessment imageQuality) {
            this.records = records;
            this.engine = engine;
            this.promptCountry = promptCountry;
            this.imageQuality = imageQuality != null ? imageQuality : ImageQualityAssessment.ok();
        }

        public List<JSONObject> getRecords() {
            return records;
        }

        public String getEngine() {
            return engine;
        }

        public String getPromptCountry() {
            return promptCountry;
        }

        public ImageQualityAssessment getImageQuality() {
            return imageQuality;
        }
    }

    public String plannedEngine() {
        if (recognitionSupport.shouldUseSimulatedRecognition()) {
            return "simulated";
        }
        recognitionSupport.requireRealAi();
        return "mimo";
    }

    public RecognitionOutcome run(byte[] fileBytes, String originalFilename, String configCountry) throws Exception {
        return run(fileBytes, originalFilename, configCountry, configCountry, null, null);
    }

    public RecognitionOutcome run(byte[] fileBytes, String originalFilename, String configCountry,
                                RecognitionTrace trace) throws Exception {
        return run(fileBytes, originalFilename, configCountry, configCountry, trace, null);
    }

    public RecognitionOutcome run(byte[] fileBytes, String originalFilename, String configCountry,
                                RecognitionTrace trace, String progressTaskId) throws Exception {
        return run(fileBytes, originalFilename, configCountry, configCountry, trace, progressTaskId);
    }

    public RecognitionOutcome run(byte[] fileBytes, String originalFilename, String promptCountry,
                                String workingCountry, RecognitionTrace trace, String progressTaskId) throws Exception {
        if (uploadMediaSupport.isPdf(fileBytes, originalFilename, null)) {
            List<UploadMediaSupport.ImagePage> pages = uploadMediaSupport.toRecognizablePages(
                    fileBytes, originalFilename, "application/pdf");
            return runMultiplePages(pages, promptCountry, workingCountry, trace, progressTaskId);
        }
        return runSingleImage(fileBytes, originalFilename, promptCountry, workingCountry, trace, progressTaskId, null);
    }

    public RecognitionOutcome runMultiplePages(List<UploadMediaSupport.ImagePage> pages, String promptCountry,
                                               String workingCountry, RecognitionTrace trace,
                                               String progressTaskId) throws Exception {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalStateException("无识别页面");
        }
        if (pages.size() == 1) {
            UploadMediaSupport.ImagePage only = pages.get(0);
            return runSingleImage(only.getBytes(), only.getLabel(), promptCountry, workingCountry, trace, progressTaskId, null);
        }
        List<JSONObject> merged = new ArrayList<>();
        String finalEngine = null;
        String finalCountry = promptCountry;
        int total = pages.size();
        for (int i = 0; i < total; i++) {
            UploadMediaSupport.ImagePage page = pages.get(i);
            if (trace != null) {
                JSONObject meta = new JSONObject();
                meta.put("index", i + 1);
                meta.put("total", total);
                meta.put("filename", page.getLabel());
                trace.step("pdf_page_start", meta);
            }
            RecognitionOutcome outcome = runSingleImage(
                    page.getBytes(), page.getLabel(), promptCountry, workingCountry, trace, progressTaskId, merged);
            merged.addAll(outcome.getRecords());
            finalEngine = outcome.getEngine();
            if (outcome.getPromptCountry() != null && !outcome.getPromptCountry().trim().isEmpty()) {
                finalCountry = outcome.getPromptCountry();
            }
            if (progressTaskId != null && !merged.isEmpty()) {
                flushProgress(progressTaskId, merged, finalEngine != null ? finalEngine : plannedEngine());
            }
        }
        if (merged.isEmpty() && !recognitionSupport.shouldUseSimulatedRecognition()) {
            throw new IllegalStateException("PDF 识别结果为空，请检查文件是否清晰");
        }
        String engine = finalEngine != null ? finalEngine : plannedEngine();
        ImageQualityAssessment imageQuality = recognitionQualityGuard.assessImageReadability(merged);
        return new RecognitionOutcome(merged, engine, finalCountry, imageQuality);
    }

    private RecognitionOutcome runSingleImage(byte[] fileBytes, String originalFilename, String promptCountry,
                                              String workingCountry, RecognitionTrace trace,
                                              String progressTaskId) throws Exception {
        return runSingleImage(fileBytes, originalFilename, promptCountry, workingCountry, trace, progressTaskId, null);
    }

    private RecognitionOutcome runSingleImage(byte[] fileBytes, String originalFilename, String promptCountry,
                                              String workingCountry, RecognitionTrace trace,
                                              String progressTaskId, List<JSONObject> mergedBaseline) throws Exception {
        final List<JSONObject> baseline = mergedBaseline != null ? mergedBaseline : new ArrayList<>();
        List<JSONObject> records = new ArrayList<>();
        final Exception[] error = {null};
        final CountDownLatch done = new CountDownLatch(1);
        final int[] recordIndex = {0};
        final String engineTag = "mimo:" + (promptCountry != null ? promptCountry : "default");

        if (trace != null) {
            JSONObject start = new JSONObject();
            start.put("filename", originalFilename);
            start.put("fileBytes", fileBytes != null ? fileBytes.length : 0);
            start.put("configCountry", promptCountry);
            start.put("workingCountry", workingCountry);
            start.put("mimoConfigured", recognitionSupport.isMimoConfigured());
            start.put("simulatedAllowed", recognitionSupport.allowSimulatedFallback());
            trace.step("backend_recognition_start", start);
        }

        if (recognitionSupport.shouldUseSimulatedRecognition()) {
            if (trace != null) {
                trace.step("engine_mode", "mode", "simulated");
            }
            log.warn("使用模拟识别（ALLOW_SIMULATED_RECOGNITION=true），不会调用真实 AI");
            SimulatedRecognitionService.SimCallback simCallback = new SimulatedRecognitionService.SimCallback() {
                @Override
                public void onRecord(JSONObject record) {
                    RecordCountryDefaults.applyMissingPays(record, workingCountry);
                    records.add(record);
                }

                @Override
                public void onComplete(int totalCount) {
                    done.countDown();
                }

                @Override
                public void onError(Exception e) {
                    error[0] = e;
                    done.countDown();
                }
            };
            simulatedRecognitionService.simulateRecognition(simCallback);
        } else {
            if (trace != null) {
                trace.step("engine_mode", "mode", "mimo");
            }
            recognitionSupport.requireRealAi();
            log.info("调用 MiMo 大模型识别: file={}, country={}, size={} bytes",
                    originalFilename, promptCountry, fileBytes.length);

            AIParserService.ParseCallback callback = new AIParserService.ParseCallback() {
                @Override
                public void onRecord(JSONObject record) {
                    RecordCountryDefaults.applyMissingPays(record, workingCountry);
                    records.add(record);
                    if (trace != null && (recordIndex[0] % 10 == 0)) {
                        JSONObject row = new JSONObject();
                        row.put("index", recordIndex[0]++);
                        row.put("NO", record.getString("NO"));
                        row.put("NOM_PRENOM", record.getString("NOM_PRENOM"));
                        row.put("Date", record.getString("Date"));
                        trace.step("backend_parsed_record", row);
                    } else if (trace != null) {
                        recordIndex[0]++;
                    }
                    if (progressTaskId != null && records.size() % PROGRESS_COUNT_EVERY == 0) {
                        flushMergedProgress(progressTaskId, baseline, records, engineTag);
                    }
                }

                @Override
                public void onComplete(int totalCount) {
                    log.info("MiMo 识别完成，共 {} 条", totalCount);
                    done.countDown();
                }

                @Override
                public void onError(Exception e) {
                    log.error("MiMo 识别失败", e);
                    error[0] = e;
                    done.countDown();
                }
            };

            aiParserService.parseImageStreamByLineFromBytes(
                    fileBytes, originalFilename, promptCountry, workingCountry, callback, trace);
        }

        if (!done.await(RECOGNITION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("识别超时（超过 15 分钟），请稍后重试");
        }
        if (error[0] != null) {
            throw error[0];
        }

        if (records.isEmpty() && !recognitionSupport.shouldUseSimulatedRecognition()) {
            String preview = aiParserService.getLastRecognitionRawText();
            String msg = "识别结果为空，请检查图片是否清晰。模型回复: "
                    + (preview.length() > 200 ? preview.substring(0, 200) + "..." : preview);
            throw new IllegalStateException(msg);
        }

        if (progressTaskId != null && !records.isEmpty()) {
            flushMergedProgress(progressTaskId, baseline, records, engineTag);
        }

        for (JSONObject record : records) {
            RecordCountryDefaults.applyMissingPays(record, workingCountry);
        }

        String country = recognitionSupport.shouldUseSimulatedRecognition()
                ? (promptCountry != null ? promptCountry : "default")
                : aiParserService.getLastPromptCountry();
        String engine = recognitionSupport.shouldUseSimulatedRecognition()
                ? "simulated"
                : ("mimo:" + (country != null ? country : "default"));

        if (trace != null) {
            JSONObject doneMeta = new JSONObject();
            doneMeta.put("engine", engine);
            doneMeta.put("promptCountry", country);
            doneMeta.put("recordCount", records.size());
            doneMeta.put("sampleRecords", RecognitionTrace.sampleRecords(records, 5));
            doneMeta.put("modelRawPreview", RecognitionTrace.preview(aiParserService.getLastRecognitionRawText(), 1500));
            doneMeta.put("modelRawLength", aiParserService.getLastRecognitionRawText().length());
            trace.step("backend_recognition_done", doneMeta);
        }

        ImageQualityAssessment imageQuality = recognitionQualityGuard.assessImageReadability(records);
        return new RecognitionOutcome(records, engine, country, imageQuality);
    }

    /**
     * 对任务内全部原图依次识别，合并为一份 rawData（多图上传场景）。
     */
    public RecognitionOutcome recognizeAllTaskImages(String taskId, String configCountry,
                                                     RecognitionTrace trace) throws Exception {
        return recognizeAllTaskImages(taskId, configCountry, trace, false);
    }

    public RecognitionOutcome recognizeAllTaskImages(String taskId, String configCountry,
                                                     RecognitionTrace trace, boolean systemRecovery) throws Exception {
        com.attendance.entity.Task task = systemRecovery
                ? taskService.getTaskByIdInternal(taskId)
                : taskService.getTaskForCurrentUser(taskId);
        if (task == null) {
            throw new IllegalStateException("任务不存在: " + taskId);
        }
        List<String> imageKeys = taskService.parseImageUrlList(task);
        if (imageKeys.isEmpty()) {
            throw new IllegalStateException("任务无原图，无法识别");
        }

        RecognitionCheckpoint checkpoint = taskService.loadRecognitionCheckpoint(taskId);
        boolean resuming = checkpoint.getImageIndex() > 0
                || checkpoint.getRecordCount() > 0
                || ("processing".equals(task.getStatus()) && hasPartialRawData(task));
        if (systemRecovery) {
            taskService.prepareTaskForRecognitionInternal(taskId, !resuming);
        } else {
            taskService.prepareTaskForRecognition(taskId, !resuming);
        }

        List<JSONObject> merged = resuming ? loadExistingRecords(task, checkpoint) : new ArrayList<>();
        String finalEngine = task.getAiRawOutput();
        String finalCountry = configCountry;
        String workingCountry = task.getPromptCountry() != null && !task.getPromptCountry().trim().isEmpty()
                ? task.getPromptCountry()
                : configCountry;
        int total = imageKeys.size();
        int startIndex = Math.min(Math.max(checkpoint.getImageIndex(), 0), total);

        if (resuming && trace != null) {
            JSONObject resumeMeta = new JSONObject();
            resumeMeta.put("imageIndex", startIndex);
            resumeMeta.put("recordCount", merged.size());
            trace.step("batch_recognition_resume", resumeMeta);
        }

        int pendingImages = total - startIndex;
        if (pendingImages >= 2 && mimoKeyPool.getPoolSize() > 1) {
            return recognizeAllTaskImagesParallel(taskId, configCountry, trace, systemRecovery, task, imageKeys,
                    merged, workingCountry, total, startIndex, checkpoint, resuming);
        }

        for (int i = startIndex; i < total; i++) {
            String fileKey = imageKeys.get(i);
            int baselineCount = merged.size();
            if (trace != null) {
                JSONObject meta = new JSONObject();
                meta.put("index", i + 1);
                meta.put("total", total);
                meta.put("fileKey", fileKey);
                trace.step("batch_image_start", meta);
            }
            taskService.touchRecognitionHeartbeat(taskId);
            byte[] fileBytes = systemRecovery
                    ? taskService.readUploadedImageBytesForTask(taskId, fileKey)
                    : taskService.readUploadedImageBytes(fileKey);
            List<UploadMediaSupport.ImagePage> pages;
            if (uploadMediaSupport.isPdf(fileBytes, fileKey, null)) {
                pages = uploadMediaSupport.toRecognizablePages(fileBytes, fileKey, "application/pdf");
            } else {
                pages = new ArrayList<>(1);
                pages.add(new UploadMediaSupport.ImagePage(fileBytes, fileKey));
            }
            for (UploadMediaSupport.ImagePage page : pages) {
                RecognitionOutcome outcome = runSingleImage(
                        page.getBytes(), page.getLabel(), configCountry, workingCountry, trace, taskId, merged);
                merged.addAll(outcome.getRecords());
                finalEngine = outcome.getEngine();
                if (outcome.getPromptCountry() != null && !outcome.getPromptCountry().trim().isEmpty()) {
                    finalCountry = outcome.getPromptCountry();
                }
            }
            checkpoint.setImageIndex(i + 1);
            checkpoint.setRecordCount(merged.size());
            checkpoint.setLastError(null);
            saveCheckpoint(taskId, checkpoint, merged, finalEngine != null ? finalEngine : plannedEngine());
            log.info("多图识别进度: taskId={}, image={}/{}, mergedRows={}, baseline={}",
                    taskId, i + 1, total, merged.size(), baselineCount);
        }

        if (merged.isEmpty() && !recognitionSupport.shouldUseSimulatedRecognition()) {
            throw new IllegalStateException("多图识别结果为空，请检查图片是否清晰");
        }

        if (trace != null) {
            JSONObject done = new JSONObject();
            done.put("imageCount", total);
            done.put("recordCount", merged.size());
            trace.step("batch_recognition_done", done);
        }

        String engine = finalEngine != null ? finalEngine : plannedEngine();
        String country = finalCountry != null ? finalCountry : configCountry;
        return finalizeBatchOutcome(merged, engine, country);
    }

    private RecognitionOutcome recognizeAllTaskImagesParallel(
            String taskId, String configCountry, RecognitionTrace trace, boolean systemRecovery,
            com.attendance.entity.Task task, List<String> imageKeys, List<JSONObject> merged,
            String workingCountry, int total, int startIndex, RecognitionCheckpoint checkpoint,
            boolean resuming) throws Exception {
        final List<JSONObject> mergedBaseline = new ArrayList<>(merged);
        final Map<Integer, List<JSONObject>> perImageRecords = new ConcurrentHashMap<>();
        final Map<Integer, String> perImageEngine = new ConcurrentHashMap<>();
        final Map<Integer, String> perImageCountry = new ConcurrentHashMap<>();
        final Object progressLock = new Object();
        final String engineTag = plannedEngine();

        if (trace != null) {
            JSONObject parallelMeta = new JSONObject();
            parallelMeta.put("pendingImages", total - startIndex);
            parallelMeta.put("keyPoolSize", mimoKeyPool.getPoolSize());
            trace.step("batch_recognition_parallel", parallelMeta);
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = startIndex; i < total; i++) {
            final int imageIndex = i;
            final String fileKey = imageKeys.get(i);
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    if (trace != null) {
                        JSONObject meta = new JSONObject();
                        meta.put("index", imageIndex + 1);
                        meta.put("total", total);
                        meta.put("fileKey", fileKey);
                        trace.step("batch_image_start", meta);
                    }
                    taskService.touchRecognitionHeartbeat(taskId);
                    ImageSliceResult slice = collectRecordsForImage(
                            taskId, fileKey, configCountry, workingCountry, trace, systemRecovery);
                    perImageRecords.put(imageIndex, slice.records);
                    if (slice.engine != null) {
                        perImageEngine.put(imageIndex, slice.engine);
                    }
                    if (slice.promptCountry != null && !slice.promptCountry.trim().isEmpty()) {
                        perImageCountry.put(imageIndex, slice.promptCountry);
                    }
                    synchronized (progressLock) {
                        List<JSONObject> contiguous = buildContiguousMergedRecords(
                                mergedBaseline, perImageRecords, startIndex, total);
                        flushProgress(taskId, contiguous, engineTag);
                        taskService.touchRecognitionHeartbeat(taskId);
                    }
                    log.info("多图并行识别完成单张: taskId={}, image={}/{}, rows={}",
                            taskId, imageIndex + 1, total, slice.records.size());
                } catch (Exception e) {
                    throw new java.util.concurrent.CompletionException(e);
                }
            }, recognitionExecutor));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(RECOGNITION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new IllegalStateException(cause != null ? cause.getMessage() : e.getMessage(), cause);
        }

        String finalEngine = null;
        String finalCountry = configCountry;
        for (int i = startIndex; i < total; i++) {
            List<JSONObject> slice = perImageRecords.get(i);
            if (slice == null) {
                throw new IllegalStateException("多图并行识别未完成: imageIndex=" + i);
            }
            merged.addAll(slice);
            String engine = perImageEngine.get(i);
            if (engine != null) {
                finalEngine = engine;
            }
            String country = perImageCountry.get(i);
            if (country != null && !country.trim().isEmpty()) {
                finalCountry = country;
            }
        }

        checkpoint.setImageIndex(total);
        checkpoint.setRecordCount(merged.size());
        checkpoint.setLastError(null);
        saveCheckpoint(taskId, checkpoint, merged, finalEngine != null ? finalEngine : plannedEngine());

        if (merged.isEmpty() && !recognitionSupport.shouldUseSimulatedRecognition()) {
            throw new IllegalStateException("多图识别结果为空，请检查图片是否清晰");
        }

        if (trace != null) {
            JSONObject done = new JSONObject();
            done.put("imageCount", total);
            done.put("recordCount", merged.size());
            done.put("parallel", true);
            trace.step("batch_recognition_done", done);
        }

        String engine = finalEngine != null ? finalEngine : plannedEngine();
        String country = finalCountry != null ? finalCountry : configCountry;
        return finalizeBatchOutcome(merged, engine, country);
    }

    private RecognitionOutcome finalizeBatchOutcome(List<JSONObject> merged, String engine, String country) {
        ImageQualityAssessment imageQuality = recognitionQualityGuard.assessImageReadability(merged);
        if (imageQuality.isBlock()) {
            throw new BusinessException(
                    ErrorCode.AI_PARSE_ERROR,
                    ErrorKeys.AI_IMAGE_TOO_BLURRY,
                    recognitionQualityGuard.blurryBlockMessageArgs(imageQuality));
        }
        return new RecognitionOutcome(merged, engine, country, imageQuality);
    }

    private static final class ImageSliceResult {
        final List<JSONObject> records;
        final String engine;
        final String promptCountry;

        ImageSliceResult(List<JSONObject> records, String engine, String promptCountry) {
            this.records = records;
            this.engine = engine;
            this.promptCountry = promptCountry;
        }
    }

    private ImageSliceResult collectRecordsForImage(String taskId, String fileKey, String configCountry,
                                                    String workingCountry, RecognitionTrace trace,
                                                    boolean systemRecovery) throws Exception {
        byte[] fileBytes = systemRecovery
                ? taskService.readUploadedImageBytesForTask(taskId, fileKey)
                : taskService.readUploadedImageBytes(fileKey);
        List<UploadMediaSupport.ImagePage> pages;
        if (uploadMediaSupport.isPdf(fileBytes, fileKey, null)) {
            pages = uploadMediaSupport.toRecognizablePages(fileBytes, fileKey, "application/pdf");
        } else {
            pages = new ArrayList<>(1);
            pages.add(new UploadMediaSupport.ImagePage(fileBytes, fileKey));
        }
        List<JSONObject> imageRecords = new ArrayList<>();
        String imageEngine = null;
        String imageCountry = configCountry;
        for (UploadMediaSupport.ImagePage page : pages) {
            RecognitionOutcome outcome = runSingleImage(
                    page.getBytes(), page.getLabel(), configCountry, workingCountry, trace, null);
            imageRecords.addAll(outcome.getRecords());
            imageEngine = outcome.getEngine();
            if (outcome.getPromptCountry() != null && !outcome.getPromptCountry().trim().isEmpty()) {
                imageCountry = outcome.getPromptCountry();
            }
        }
        return new ImageSliceResult(imageRecords, imageEngine, imageCountry);
    }

    private static List<JSONObject> buildContiguousMergedRecords(List<JSONObject> baseline,
                                                                 Map<Integer, List<JSONObject>> perImage,
                                                                 int startIndex, int total) {
        List<JSONObject> out = new ArrayList<>();
        if (baseline != null && !baseline.isEmpty()) {
            out.addAll(baseline);
        }
        for (int j = startIndex; j < total; j++) {
            List<JSONObject> slice = perImage.get(j);
            if (slice == null) {
                break;
            }
            out.addAll(slice);
        }
        return out;
    }

    private void flushMergedProgress(String taskId, List<JSONObject> baseline, List<JSONObject> current, String engineTag) {
        if (taskId == null || current == null) {
            return;
        }
        List<JSONObject> combined = new ArrayList<>();
        if (baseline != null && !baseline.isEmpty()) {
            combined.addAll(baseline);
        }
        combined.addAll(current);
        flushProgress(taskId, combined, engineTag);
    }

    private void flushProgress(String taskId, List<JSONObject> records, String engineTag) {
        if (taskId == null || records == null) {
            return;
        }
        try {
            int size = records.size();
            taskService.touchRecognitionHeartbeat(taskId);
            taskService.updateTaskRecognitionProgress(taskId, size, engineTag);
            if (size > 0 && (size % FULL_RAW_FLUSH_EVERY == 0 || size <= FULL_RAW_FLUSH_EVERY)) {
                JSONArray arr = new JSONArray();
                arr.addAll(records);
                taskService.updateTaskRawDataProgress(taskId, arr.toJSONString(), engineTag);
            }
            RecognitionCheckpoint cp = taskService.loadRecognitionCheckpoint(taskId);
            cp.setRecordCount(size);
            taskService.saveRecognitionCheckpoint(taskId, cp);
        } catch (Exception e) {
            log.warn("识别进度写入失败: taskId={}", taskId, e);
        }
    }

    private void saveCheckpoint(String taskId, RecognitionCheckpoint checkpoint,
                                List<JSONObject> records, String engineTag) {
        flushProgress(taskId, records, engineTag);
        taskService.saveRecognitionCheckpoint(taskId, checkpoint);
    }

    private static boolean hasPartialRawData(com.attendance.entity.Task task) {
        if (task == null || task.getRawData() == null || task.getRawData().trim().isEmpty()) {
            return false;
        }
        try {
            JSONArray arr = JSON.parseArray(task.getRawData());
            return arr != null && !arr.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static List<JSONObject> loadExistingRecords(com.attendance.entity.Task task,
                                                        RecognitionCheckpoint checkpoint) {
        List<JSONObject> merged = new ArrayList<>();
        if (task == null || task.getRawData() == null || task.getRawData().trim().isEmpty()) {
            return merged;
        }
        try {
            JSONArray arr = JSON.parseArray(task.getRawData());
            if (arr == null) {
                return merged;
            }
            int keep = Math.min(checkpoint.getRecordCount(), arr.size());
            for (int i = 0; i < keep; i++) {
                JSONObject row = arr.getJSONObject(i);
                if (row != null) {
                    merged.add(row);
                }
            }
        } catch (Exception e) {
            log.warn("恢复 partial raw_data 失败: taskId={}", task.getTaskId(), e);
        }
        return merged;
    }
}
