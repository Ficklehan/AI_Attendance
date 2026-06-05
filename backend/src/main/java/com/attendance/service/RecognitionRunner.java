package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.dto.RecognitionCheckpoint;
import com.attendance.util.RecordCountryDefaults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    private static final int PROGRESS_COUNT_EVERY = 2;
    private static final int FULL_RAW_FLUSH_EVERY = 5;
    public static final int RECOGNITION_TIMEOUT_SECONDS = 900;

    public static class RecognitionOutcome {
        private final List<JSONObject> records;
        private final String engine;
        private final String promptCountry;

        public RecognitionOutcome(List<JSONObject> records, String engine, String promptCountry) {
            this.records = records;
            this.engine = engine;
            this.promptCountry = promptCountry;
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
        return runSingleImage(fileBytes, originalFilename, promptCountry, workingCountry, trace, progressTaskId);
    }

    public RecognitionOutcome runMultiplePages(List<UploadMediaSupport.ImagePage> pages, String promptCountry,
                                               String workingCountry, RecognitionTrace trace,
                                               String progressTaskId) throws Exception {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalStateException("无识别页面");
        }
        if (pages.size() == 1) {
            UploadMediaSupport.ImagePage only = pages.get(0);
            return runSingleImage(only.getBytes(), only.getLabel(), promptCountry, workingCountry, trace, progressTaskId);
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
                    page.getBytes(), page.getLabel(), promptCountry, workingCountry, trace, progressTaskId);
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
        return new RecognitionOutcome(merged, engine, finalCountry);
    }

    private RecognitionOutcome runSingleImage(byte[] fileBytes, String originalFilename, String promptCountry,
                                              String workingCountry, RecognitionTrace trace,
                                              String progressTaskId) throws Exception {
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
                    if (trace != null) {
                        JSONObject row = new JSONObject();
                        row.put("index", recordIndex[0]++);
                        row.put("NO", record.getString("NO"));
                        row.put("NOM_PRENOM", record.getString("NOM_PRENOM"));
                        row.put("Date", record.getString("Date"));
                        trace.step("backend_parsed_record", row);
                    }
                    if (progressTaskId != null && records.size() % PROGRESS_COUNT_EVERY == 0) {
                        flushProgress(progressTaskId, records, engineTag);
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

        for (JSONObject record : records) {
            RecordCountryDefaults.applyMissingPays(record, workingCountry);
        }

        String country = recognitionSupport.shouldUseSimulatedRecognition()
                ? (promptCountry != null ? promptCountry : "default")
                : aiParserService.getLastPromptCountry();
        String engine = recognitionSupport.shouldUseSimulatedRecognition()
                ? "simulated"
                : ("mimo:" + (country != null ? country : "default"));

        if (progressTaskId != null && !records.isEmpty()) {
            flushProgress(progressTaskId, records, engineTag);
        }

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

        return new RecognitionOutcome(records, engine, country);
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
                        page.getBytes(), page.getLabel(), configCountry, workingCountry, trace, taskId);
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
        return new RecognitionOutcome(merged, engine, country);
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
