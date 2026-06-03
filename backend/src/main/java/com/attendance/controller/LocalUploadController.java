package com.attendance.controller;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.common.ErrorCode;
import com.attendance.service.AIParserService;
import com.attendance.service.ConfigService;
import com.attendance.service.RecognitionRunner;
import com.attendance.service.RecognitionSupport;
import com.attendance.service.RecognitionTrace;
import com.attendance.service.SimulatedRecognitionService;
import com.attendance.service.TaskService;
import com.attendance.service.UploadMediaSupport;
import com.attendance.security.TaskAccessService;
import com.attendance.util.CountryResolver;
import com.attendance.util.ExcelExportHelper;
import com.attendance.util.ExcelExportHelper.ExcelSheetWriter;
import com.attendance.util.ImageUploadValidator;
import com.attendance.util.RecordCountryDefaults;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

@RestController
@RequestMapping("/local")
public class LocalUploadController {
    
    private static final Logger log = LoggerFactory.getLogger(LocalUploadController.class);

    @Autowired
    private AIParserService aiParserService;

    @Autowired
    private SimulatedRecognitionService simulatedRecognitionService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private RecognitionSupport recognitionSupport;

    @Autowired
    private RecognitionRunner recognitionRunner;

    @Autowired
    private TaskAccessService taskAccessService;

    @Autowired
    private UploadMediaSupport uploadMediaSupport;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("recognitionExecutor")
    private Executor recognitionExecutor;

    private String resolveWorkingCountry(String countryHeader, String countryParam) {
        return CountryResolver.resolveForRecognition(countryHeader, countryParam, configService, log);
    }

    private String resolveConfigCountry(String countryHeader, String countryParam) {
        String workingCountry = resolveWorkingCountry(countryHeader, countryParam);
        String configCountry = configService.resolveEffectiveCountry(workingCountry);
        log.info("识别国家配置: working={}, effective={}", workingCountry, configCountry);
        return configCountry;
    }

    private String[] resolveCountryPair(String countryHeader, String countryParam) {
        String workingCountry = resolveWorkingCountry(countryHeader, countryParam);
        String configCountry = configService.resolveEffectiveCountry(workingCountry);
        log.info("识别国家配置: working={}, effective={}", workingCountry, configCountry);
        return new String[] { configCountry, workingCountry };
    }

    private List<String> saveRecognizablePages(byte[] fileBytes, String originalFilename, String contentType)
            throws IOException {
        List<UploadMediaSupport.ImagePage> pages = uploadMediaSupport.toRecognizablePages(
                fileBytes, originalFilename, contentType);
        List<String> keys = new ArrayList<>(pages.size());
        for (UploadMediaSupport.ImagePage page : pages) {
            String name = page.getLabel();
            if (!name.toLowerCase(Locale.ROOT).contains(".")) {
                name = name + ".jpg";
            }
            keys.add(aiParserService.saveUploadedFile(page.getBytes(), name));
        }
        return keys;
    }

    private void mergeTaskImageUrls(String taskId, List<String> savedKeys) {
        if (savedKeys == null || savedKeys.isEmpty()) {
            return;
        }
        com.attendance.entity.Task task = taskService.getTaskForCurrentUser(taskId);
        List<String> urls = new ArrayList<>(taskService.parseImageUrlList(task));
        for (String key : savedKeys) {
            if (key != null && !key.trim().isEmpty() && !urls.contains(key)) {
                urls.add(key.trim());
            }
        }
        taskService.updateTaskImageUrls(taskId, urls);
    }

    @PostMapping(value = "/upload-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter uploadStream(@RequestParam("image") MultipartFile image,
                                   @RequestParam(value = "taskId", required = false) String taskId,
                                   @RequestHeader(value = "X-Country", required = false) String countryHeader,
                                   @RequestParam(value = "country", required = false) String countryParam) {
        final String[] countryPair = resolveCountryPair(countryHeader, countryParam);
        final String configCountry = countryPair[0];
        final String workingCountry = countryPair[1];
        log.info("收到上传请求: image={}, taskId={}, configCountry={}, workingCountry={}",
                image.getOriginalFilename(), taskId, configCountry, workingCountry);
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitter.onCompletion(() -> log.info("SSE连接完成"));
        emitter.onTimeout(() -> log.warn("SSE连接超时"));
        emitter.onError(e -> log.error("SSE连接错误", e));

        SecurityContext securityContext = SecurityContextHolder.getContext();

        recognitionExecutor.execute(() -> {
        SecurityContextHolder.setContext(securityContext);
        try {
            byte[] fileBytes = image.getBytes();
            ImageUploadValidator.validate(fileBytes, image.getOriginalFilename(), image.getContentType(), log);
            List<String> savedKeys = saveRecognizablePages(
                    fileBytes, image.getOriginalFilename(), image.getContentType());
            String savedFilename = savedKeys.get(0);
            
            String newTaskId;
            if (taskId != null && !taskId.isEmpty()) {
                newTaskId = taskId;
                log.info("追加图片到现有任务: taskId={}, newFiles={}", newTaskId, savedKeys);
                mergeTaskImageUrls(newTaskId, savedKeys);
            } else {
                com.attendance.entity.Task newTask = taskService.createTask(savedFilename, workingCountry);
                newTaskId = newTask.getTaskId();
                log.info("创建新任务: taskId={}, files={}", newTaskId, savedKeys);
                taskService.updateTaskImageUrls(newTaskId, savedKeys);
            }

            JSONObject startEvent = new JSONObject();
            startEvent.put("taskId", newTaskId);
            startEvent.put("imagePreviewUrl", "/api/local/image/" + savedFilename);
            startEvent.put("imageCount", savedKeys.size());
            startEvent.put("promptCountry", configCountry);
            startEvent.put("promptSection", configService.describePromptSection(configCountry));
            emitter.send(SseEmitter.event()
                    .name("start")
                    .data(startEvent.toJSONString(), MediaType.APPLICATION_JSON));

            RecognitionTrace trace = new RecognitionTrace(newTaskId, "pc-upload-stream");
            JSONObject uploadMeta = new JSONObject();
            uploadMeta.put("filename", image.getOriginalFilename());
            uploadMeta.put("contentType", image.getContentType());
            uploadMeta.put("fileBytes", fileBytes.length);
            uploadMeta.put("configCountry", configCountry);
            uploadMeta.put("workingCountry", workingCountry);
            trace.step("upload_received", uploadMeta);
            trace.setStepListener(entry -> {
                String phase = entry.getString("phase");
                if ("model_request".equals(phase) || "model_response".equals(phase)) {
                    return;
                }
                try {
                    emitter.send(SseEmitter.event()
                            .name("trace")
                            .data(entry.toJSONString(), MediaType.APPLICATION_JSON));
                } catch (IOException ex) {
                    log.debug("SSE trace 推送失败: {}", ex.getMessage());
                }
            });

            List<JSONObject> records = new CopyOnWriteArrayList<>();

            if (recognitionSupport.shouldUseSimulatedRecognition()) {
                log.warn("使用模拟识别（ALLOW_SIMULATED_RECOGNITION=true），不会调用真实 AI");
                emitter.send(SseEmitter.event()
                        .name("info")
                        .data("{\"message\":\"当前使用模拟识别模式，将生成模拟数据\"}"));

                SimulatedRecognitionService.SimCallback simCallback = new SimulatedRecognitionService.SimCallback() {
                    @Override
                    public void onRecord(JSONObject record) {
                        RecordCountryDefaults.applyMissingPays(record, workingCountry);
                        records.add(record);
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("record")
                                    .data("{\"record\":" + record.toJSONString() + "}"));
                        } catch (IOException e) {
                            log.error("SSE发送失败", e);
                        }
                    }

                    @Override
                    public void onComplete(int totalCount) {
                        try {
                            appendRawData(newTaskId, records, "simulated");
                            
                            emitter.send(SseEmitter.event()
                                    .name("complete")
                                    .data("{\"taskId\":\"" + newTaskId + "\",\"rowCount\":" + totalCount + "}"));
                            emitter.complete();
                        } catch (Exception e) {
                            log.error("完成处理失败", e);
                            emitter.completeWithError(e);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        try {
                            JSONObject errorEvent = buildErrorEvent(e);
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data(errorEvent.toJSONString(), MediaType.APPLICATION_JSON));
                            emitter.complete();
                        } catch (IOException ex) {
                            log.error("错误发送失败", ex);
                        }
                    }
                };

                simulatedRecognitionService.simulateRecognition(simCallback);
            } else {
                recognitionSupport.requireRealAi();
                String originalFilename = image.getOriginalFilename();
                String contentType = image.getContentType();
                log.info("图片信息 - 文件名: {}, Content-Type: {}, 大小: {} bytes", originalFilename, contentType, fileBytes.length);
                
                List<UploadMediaSupport.ImagePage> pages = uploadMediaSupport.toRecognizablePages(
                        fileBytes, originalFilename, contentType);
                log.info("开始 AI 识别: pages={}, filename={}", pages.size(), originalFilename);
                final Exception[] streamError = {null};
                final boolean[] streamCompleted = {false};
                for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                    UploadMediaSupport.ImagePage page = pages.get(pageIndex);
                    final boolean lastPage = pageIndex == pages.size() - 1;
                    AIParserService.ParseCallback pageCallback = new AIParserService.ParseCallback() {
                        @Override
                        public void onRecord(JSONObject record) {
                            RecordCountryDefaults.applyMissingPays(record, workingCountry);
                            records.add(record);
                            try {
                                JSONObject recordEvent = new JSONObject();
                                recordEvent.put("record", record);
                                emitter.send(SseEmitter.event()
                                        .name("record")
                                        .data(recordEvent.toJSONString(), MediaType.APPLICATION_JSON));
                            } catch (IOException e) {
                                log.error("SSE发送失败", e);
                            }
                        }

                        @Override
                        public void onComplete(int totalCount) {
                            if (!lastPage) {
                                return;
                            }
                            try {
                                String promptCountry = aiParserService.getLastPromptCountry();
                                String engineTag = "mimo:" + (promptCountry != null ? promptCountry : configCountry);
                                appendRawData(newTaskId, records, engineTag, trace);
                                sendTraceDump(emitter, trace);

                                JSONObject completeEvent = new JSONObject();
                                completeEvent.put("taskId", newTaskId);
                                completeEvent.put("rowCount", records.size());
                                completeEvent.put("promptCountry", configCountry);
                                completeEvent.put("pageCount", pages.size());

                                emitter.send(SseEmitter.event()
                                        .name("complete")
                                        .data(completeEvent.toJSONString(), MediaType.APPLICATION_JSON));
                                emitter.complete();
                                streamCompleted[0] = true;
                            } catch (Exception e) {
                                log.error("完成处理失败", e);
                                emitter.completeWithError(e);
                                streamCompleted[0] = true;
                            }
                        }

                        @Override
                        public void onError(Exception e) {
                            streamError[0] = e;
                            try {
                                trace.step("recognition_failed", "message", e.getMessage());
                                taskService.failTask(newTaskId, e.getMessage(), trace);
                                sendTraceDump(emitter, trace);
                                JSONObject errorEvent = buildErrorEvent(e);
                                emitter.send(SseEmitter.event()
                                        .name("error")
                                        .data(errorEvent.toJSONString(), MediaType.APPLICATION_JSON));
                                emitter.complete();
                                streamCompleted[0] = true;
                            } catch (IOException ex) {
                                log.error("错误发送失败", ex);
                            }
                        }
                    };

                    log.info("识别 PDF/图片页 {}/{}: {}", pageIndex + 1, pages.size(), page.getLabel());
                    aiParserService.parseImageStreamByLineFromBytes(
                            page.getBytes(), page.getLabel(), configCountry, workingCountry, pageCallback, trace);
                    if (streamError[0] != null) {
                        break;
                    }
                }
                if (!streamCompleted[0] && streamError[0] == null) {
                    log.warn("识别结束但未收到 complete 回调，补发完成事件: taskId={}", newTaskId);
                    try {
                        String engineTag = "mimo:" + configCountry;
                        appendRawData(newTaskId, records, engineTag, trace);
                        JSONObject completeEvent = new JSONObject();
                        completeEvent.put("taskId", newTaskId);
                        completeEvent.put("rowCount", records.size());
                        completeEvent.put("promptCountry", configCountry);
                        emitter.send(SseEmitter.event()
                                .name("complete")
                                .data(completeEvent.toJSONString(), MediaType.APPLICATION_JSON));
                        emitter.complete();
                    } catch (Exception ex) {
                        emitter.completeWithError(ex);
                    }
                }
            }

        } catch (Exception e) {
            log.error("上传处理失败", e);
            try {
                JSONObject errorEvent = buildErrorEvent(e);
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(errorEvent.toJSONString(), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (IOException ex) {
                log.error("错误发送失败", ex);
            }
        }
        });

        return emitter;
    }

    /**
     * 移动端推荐：先返回 taskId，后台调用 MiMo，客户端轮询任务状态，避免 HTTP 超时。
     */
    @PostMapping("/upload-async")
    public com.attendance.common.Result<JSONObject> uploadAsync(@RequestParam("image") MultipartFile image,
                                                                @RequestParam(value = "taskId", required = false) String existingTaskId,
                                                                @RequestParam(value = "deferRecognition", required = false, defaultValue = "false") boolean deferRecognition,
                                                                @RequestHeader(value = "X-Country", required = false) String countryHeader,
                                                                @RequestParam(value = "country", required = false) String countryParam,
                                                                @RequestHeader(value = "X-Client", required = false) String clientHeader) {
        String[] countryPair = resolveCountryPair(countryHeader, countryParam);
        String configCountry = countryPair[0];
        String workingCountry = countryPair[1];
        String client = (clientHeader != null && !clientHeader.trim().isEmpty()) ? clientHeader.trim() : "unknown";
        log.info("收到异步上传请求: image={}, taskId={}, configCountry={}, workingCountry={}, client={}",
                image.getOriginalFilename(), existingTaskId, configCountry, workingCountry, client);
        try {
            String plannedEngine = recognitionRunner.plannedEngine();
            byte[] fileBytes = image.getBytes();
            ImageUploadValidator.validate(fileBytes, image.getOriginalFilename(), image.getContentType(), log);
            List<String> savedKeys = saveRecognizablePages(
                    fileBytes, image.getOriginalFilename(), image.getContentType());
            String filename = savedKeys.get(0);

            if (existingTaskId != null && !existingTaskId.trim().isEmpty()) {
                com.attendance.entity.Task existing = taskService.getTaskForCurrentUser(existingTaskId.trim());
                mergeTaskImageUrls(existing.getTaskId(), savedKeys);
                List<String> urls = taskService.parseImageUrlList(existing);
                log.info("追加图片到任务: taskId={}, totalImages={}", existing.getTaskId(), urls.size());

                JSONObject result = new JSONObject();
                result.put("taskId", existing.getTaskId());
                result.put("status", existing.getStatus());
                result.put("appendOnly", true);
                result.put("imageCount", urls.size());
                result.put("pageCount", savedKeys.size());
                result.put("recognitionEngine", existing.getAiRawOutput() != null ? existing.getAiRawOutput() : plannedEngine);
                result.put("promptCountry", configCountry);
                return com.attendance.common.Result.success(result);
            }

            String taskId = taskService.createTask(filename, workingCountry).getTaskId();
            taskService.updateTaskImageUrls(taskId, savedKeys);

            if (deferRecognition) {
                log.info("批量模式：仅创建任务并保存首图，延后识别: taskId={}, pages={}", taskId, savedKeys.size());
                JSONObject deferred = new JSONObject();
                deferred.put("taskId", taskId);
                deferred.put("status", "processing");
                deferred.put("deferred", true);
                deferred.put("imageCount", savedKeys.size());
                deferred.put("recognitionEngine", plannedEngine);
                deferred.put("promptCountry", configCountry);
                return com.attendance.common.Result.success(deferred);
            }

            RecognitionTrace trace = new RecognitionTrace(taskId, client);
            JSONObject uploadMeta = new JSONObject();
            uploadMeta.put("filename", image.getOriginalFilename());
            uploadMeta.put("contentType", image.getContentType());
            uploadMeta.put("fileBytes", fileBytes.length);
            uploadMeta.put("configCountry", configCountry);
            uploadMeta.put("workingCountry", workingCountry);
            uploadMeta.put("savedFiles", savedKeys);
            trace.step("upload_received", uploadMeta);

            SecurityContext securityContext = SecurityContextHolder.getContext();
            List<UploadMediaSupport.ImagePage> recognizePages = uploadMediaSupport.toRecognizablePages(
                    fileBytes, image.getOriginalFilename(), image.getContentType());
            recognitionExecutor.execute(() -> {
                SecurityContextHolder.setContext(securityContext);
                try {
                    RecognitionRunner.RecognitionOutcome outcome = recognitionRunner.runMultiplePages(
                            recognizePages, configCountry, workingCountry, trace, taskId);
                    String rawData = JSON.toJSONString(outcome.getRecords());
                    taskService.updateTaskRawData(taskId, rawData, outcome.getEngine(), trace);
                    log.info("异步识别完成: taskId={}, engine={}, rows={}",
                            taskId, outcome.getEngine(), outcome.getRecords().size());
                } catch (Exception e) {
                    log.error("异步识别失败: taskId={}", taskId, e);
                    String msg = e.getMessage() != null ? e.getMessage() : "识别失败";
                    trace.step("upload_failed", "message", msg);
                    taskService.failTask(taskId, msg, trace);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });

            JSONObject result = new JSONObject();
            result.put("taskId", taskId);
            result.put("status", "processing");
            result.put("recognitionEngine", plannedEngine);
            result.put("promptCountry", configCountry);
            result.put("promptSection", configService.describePromptSection(configCountry));

            return com.attendance.common.Result.success(result);
        } catch (com.attendance.common.BusinessException e) {
            log.warn("异步上传被拒绝: {}", e.getMessage());
            return com.attendance.common.Result.error(e);
        } catch (Exception e) {
            log.error("异步上传失败", e);
            return com.attendance.common.Result.error(com.attendance.common.ErrorKeys.SYSTEM_ERROR);
        }
    }

    /**
     * 多图全部上传完成后，对任务内所有原图依次识别并合并结果。
     */
    @PostMapping("/tasks/{taskId}/recognize")
    public com.attendance.common.Result<JSONObject> recognizeTask(@PathVariable String taskId,
                                                                  @RequestHeader(value = "X-Country", required = false) String countryHeader,
                                                                  @RequestParam(value = "country", required = false) String countryParam,
                                                                  @RequestHeader(value = "X-Client", required = false) String clientHeader) {
        String configCountry = resolveConfigCountry(countryHeader, countryParam);
        String client = (clientHeader != null && !clientHeader.trim().isEmpty()) ? clientHeader.trim() : "unknown";
        com.attendance.entity.Task task = taskService.getTaskForCurrentUser(taskId);
        if ("confirmed".equals(task.getStatus())) {
            return com.attendance.common.Result.error("任务已确认，无法重新识别");
        }

        List<String> images = taskService.parseImageUrlList(task);
        if (images.isEmpty()) {
            return com.attendance.common.Result.error("任务无原图");
        }

        log.info("开始多图合并识别: taskId={}, imageCount={}, client={}", taskId, images.size(), client);

        RecognitionTrace trace = new RecognitionTrace(taskId, client);
        SecurityContext securityContext = SecurityContextHolder.getContext();
        recognitionExecutor.execute(() -> {
            SecurityContextHolder.setContext(securityContext);
            try {
                RecognitionRunner.RecognitionOutcome outcome = recognitionRunner.recognizeAllTaskImages(
                        taskId, configCountry, trace);
                String rawData = JSON.toJSONString(outcome.getRecords());
                taskService.updateTaskRawData(taskId, rawData, outcome.getEngine(), trace);
                log.info("多图合并识别完成: taskId={}, engine={}, rows={}, images={}",
                        taskId, outcome.getEngine(), outcome.getRecords().size(), images.size());
            } catch (Exception e) {
                log.error("多图合并识别失败: taskId={}", taskId, e);
                String msg = e.getMessage() != null ? e.getMessage() : "识别失败";
                trace.step("upload_failed", "message", msg);
                taskService.failTask(taskId, msg, trace);
            } finally {
                SecurityContextHolder.clearContext();
            }
        });

        JSONObject result = new JSONObject();
        result.put("taskId", taskId);
        result.put("status", "processing");
        result.put("imageCount", images.size());
        result.put("recognitionEngine", recognitionRunner.plannedEngine());
        result.put("promptCountry", configCountry);
        return com.attendance.common.Result.success(result);
    }

    @GetMapping("/image/{fileKey}")
    public void getImage(@PathVariable String fileKey, HttpServletResponse response) throws IOException {
        taskAccessService.requireFileAccess(fileKey);
        try {
            Path uploadPath = Paths.get("./uploads");
            Path filePath = uploadPath.resolve(fileKey);
            
            if (!Files.exists(filePath)) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.FILE_NOT_FOUND);
            }

            File file = filePath.toFile();
            String lowerKey = fileKey.toLowerCase(Locale.ROOT);
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                if (lowerKey.endsWith(".png")) {
                    contentType = "image/png";
                } else if (lowerKey.endsWith(".pdf")) {
                    contentType = "application/pdf";
                } else if (lowerKey.endsWith(".webp")) {
                    contentType = "image/webp";
                } else {
                    contentType = "image/jpeg";
                }
            }
            response.setContentType(contentType);
            response.setContentLength((int) file.length());

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = fis.read(buffer)) != -1) {
                    response.getOutputStream().write(buffer, 0, bytesRead);
                }
            }
        } catch (BusinessException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/export/{taskId}/xlsx")
    public void exportXlsx(@PathVariable String taskId, HttpServletResponse response) {
        java.nio.file.Path tempFile = null;
        try {
            com.attendance.entity.Task task = taskService.getTaskForCurrentUser(taskId);
            String data = task.getConfirmedData() != null ? task.getConfirmedData() : task.getRawData();

            if (data == null) {
                throw new BusinessException(ErrorCode.TASK_NOT_FOUND, ErrorKeys.NO_EXPORT_DATA);
            }

            JSONArray records = JSON.parseArray(data);
            tempFile = Files.createTempFile("attendance-export-", ".xlsx");
            try (ExcelSheetWriter writer = ExcelExportHelper.open(tempFile)) {
                writer.writeHeader("工号", "国家", "仓库", "日期", "姓名", "中介机构", "班次",
                        "到达时间", "离开时间", "休息(分钟)", "员工签名", "备注", "标记");
                for (int i = 0; i < records.size(); i++) {
                    JSONObject record = records.getJSONObject(i);
                    writer.writeRow(
                            ExcelExportHelper.cell(record.getString("NO")),
                            ExcelExportHelper.cell(record.getString("Pays")),
                            ExcelExportHelper.cell(record.getString("Entrepot")),
                            ExcelExportHelper.cell(record.getString("Date")),
                            ExcelExportHelper.cell(record.getString("NOM_PRENOM")),
                            ExcelExportHelper.cell(record.getString("AGENCE_INTERIMAIRE")),
                            ExcelExportHelper.cell(record.getString("HORAIRES_DU_TRAVAIL")),
                            ExcelExportHelper.cell(record.getString("ARRIVEE")),
                            ExcelExportHelper.cell(record.getString("DEPAR")),
                            ExcelExportHelper.cell(record.getInteger("PAUSE")),
                            ExcelExportHelper.cell(record.getString("SIGNATURE")),
                            ExcelExportHelper.cell(record.getString("Observations")),
                            ExcelExportHelper.cell(record.getString("SmartMark")));
                }
            }

            response.setContentType(ExcelExportHelper.CONTENT_TYPE);
            response.setHeader("Content-Disposition",
                    "attachment;filename=\"attendance_" + taskId + ".xlsx\"");
            Files.copy(tempFile, response.getOutputStream());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("导出失败", e);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private void appendRawData(String taskId, List<JSONObject> newRecords, String recognitionEngine) {
        appendRawData(taskId, newRecords, recognitionEngine, null);
    }

    private void appendRawData(String taskId, List<JSONObject> newRecords, String recognitionEngine,
                               RecognitionTrace recognitionTrace) {
        String engine = recognitionEngine != null ? recognitionEngine : "";
        try {
            com.attendance.entity.Task task = taskService.getTaskForCurrentUser(taskId);
            JSONArray allRecords = new JSONArray();
            
            if (task.getRawData() != null && !task.getRawData().isEmpty()) {
                try {
                    JSONArray existingRecords = JSON.parseArray(task.getRawData());
                    for (int i = 0; i < existingRecords.size(); i++) {
                        allRecords.add(existingRecords.get(i));
                    }
                } catch (Exception e) {
                    log.warn("解析已有rawData失败，将覆盖", e);
                }
            }
            
            for (JSONObject record : newRecords) {
                allRecords.add(new JSONObject(record));
            }
            
            String mergedData = JSON.toJSONString(allRecords);
            if (recognitionTrace != null) {
                taskService.updateTaskRawData(taskId, mergedData, engine, recognitionTrace);
            } else {
                taskService.updateTaskRawData(taskId, mergedData, engine);
            }
        } catch (Exception e) {
            log.error("追加rawData失败", e);
            String rawData = JSON.toJSONString(newRecords);
            if (recognitionTrace != null) {
                taskService.updateTaskRawData(taskId, rawData, engine, recognitionTrace);
            } else {
                taskService.updateTaskRawData(taskId, rawData, engine);
            }
        }
    }

    private static JSONObject buildErrorEvent(Exception e) {
        JSONObject errorEvent = new JSONObject();
        errorEvent.put("message", e.getMessage());
        if (e instanceof BusinessException) {
            BusinessException businessException = (BusinessException) e;
            errorEvent.put("code", businessException.getCode());
            errorEvent.put("messageKey", businessException.getMessageKey());
            if (businessException.getMessageArgs() != null && !businessException.getMessageArgs().isEmpty()) {
                errorEvent.put("messageArgs", businessException.getMessageArgs());
            }
        }
        return errorEvent;
    }

    private static void sendTraceDump(SseEmitter emitter, RecognitionTrace trace) throws IOException {
        if (trace == null) {
            return;
        }
        emitter.send(SseEmitter.event()
                .name("trace_dump")
                .data(trace.toJson().toJSONString(), MediaType.APPLICATION_JSON));
    }
}
