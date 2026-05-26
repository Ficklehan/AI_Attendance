package com.attendance.controller;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.config.MimoProperties;
import com.attendance.service.AIParserService;
import com.attendance.service.SimulatedRecognitionService;
import com.attendance.service.TaskService;
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

import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private MimoProperties mimoProperties;

    private final ExecutorService asyncExecutor = Executors.newCachedThreadPool();

    private boolean useSimulatedRecognition() {
        String apiKey = mimoProperties.getApiKey();
        return apiKey == null || apiKey.isEmpty();
    }

    @PostMapping(value = "/upload-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter uploadStream(@RequestParam("image") MultipartFile image,
                                   @RequestParam(value = "taskId", required = false) String taskId) {
        log.info("收到上传请求: image={}, taskId={}", image.getOriginalFilename(), taskId);
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitter.onCompletion(() -> log.info("SSE连接完成"));
        emitter.onTimeout(() -> log.warn("SSE连接超时"));
        emitter.onError(e -> log.error("SSE连接错误", e));

        SecurityContext securityContext = SecurityContextHolder.getContext();

        asyncExecutor.submit(() -> {
        SecurityContextHolder.setContext(securityContext);
        try {
            String savedFilename = aiParserService.saveUploadedFile(image.getBytes(), image.getOriginalFilename());
            
            String newTaskId;
            if (taskId != null && !taskId.isEmpty()) {
                newTaskId = taskId;
                log.info("追加图片到现有任务: taskId={}, newFilename={}", newTaskId, savedFilename);
                com.attendance.entity.Task existingTask = taskService.getTaskById(newTaskId);
                List<String> existingUrls = new ArrayList<>();
                if (existingTask.getImageUrls() != null && !existingTask.getImageUrls().isEmpty()) {
                    try {
                        JSONArray urlArray = JSON.parseArray(existingTask.getImageUrls());
                        for (int i = 0; i < urlArray.size(); i++) {
                            existingUrls.add(urlArray.getString(i));
                        }
                        log.info("已有图片: count={}, urls={}", existingUrls.size(), existingUrls);
                    } catch (Exception e) {
                        log.warn("解析已有图片URL失败", e);
                    }
                }
                existingUrls.add(savedFilename);
                log.info("更新后图片: count={}, urls={}", existingUrls.size(), existingUrls);
                taskService.updateTaskImageUrls(newTaskId, existingUrls);
            } else {
                com.attendance.entity.Task newTask = taskService.createTask(savedFilename);
                newTaskId = newTask.getTaskId();
                log.info("创建新任务: taskId={}, filename={}", newTaskId, savedFilename);
                // 创建任务时初始化 imageUrls，包含第一张图片
                List<String> initialUrls = new ArrayList<>();
                initialUrls.add(savedFilename);
                taskService.updateTaskImageUrls(newTaskId, initialUrls);
                log.info("初始化任务 imageUrls: taskId={}, urls={}", newTaskId, initialUrls);
            }

            JSONObject startEvent = new JSONObject();
            startEvent.put("taskId", newTaskId);
            startEvent.put("imagePreviewUrl", "/api/local/image/" + savedFilename);
            emitter.send(SseEmitter.event()
                    .name("start")
                    .data(startEvent.toJSONString(), MediaType.APPLICATION_JSON));

            List<JSONObject> records = new CopyOnWriteArrayList<>();

            if (useSimulatedRecognition()) {
                log.info("使用模拟识别服务");
                emitter.send(SseEmitter.event()
                        .name("info")
                        .data("{\"message\":\"当前使用模拟识别模式，将生成模拟数据\"}"));

                SimulatedRecognitionService.SimCallback simCallback = new SimulatedRecognitionService.SimCallback() {
                    @Override
                    public void onRecord(JSONObject record) {
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
                            appendRawData(newTaskId, records);
                            
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
                            emitter.send(SseEmitter.event()
                                    .name("error")
                                    .data("{\"message\":\"" + e.getMessage() + "\"}"));
                            emitter.completeWithError(e);
                        } catch (IOException ex) {
                            log.error("错误发送失败", ex);
                        }
                    }
                };

                simulatedRecognitionService.simulateRecognition(simCallback);
            } else {
                byte[] fileBytes = image.getBytes();
                
                String originalFilename = image.getOriginalFilename();
                String contentType = image.getContentType();
                log.info("图片信息 - 文件名: {}, Content-Type: {}, 大小: {} bytes", originalFilename, contentType, fileBytes.length);
                
                if (contentType != null && !contentType.startsWith("image/")) {
                    throw new IllegalArgumentException("只支持图片文件");
                }
                
                AIParserService.ParseCallback callback = new AIParserService.ParseCallback() {
                @Override
                public void onRecord(JSONObject record) {
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
                    try {
                        appendRawData(newTaskId, records);

                        JSONObject completeEvent = new JSONObject();
                        completeEvent.put("taskId", newTaskId);
                        completeEvent.put("rowCount", totalCount);
                        
                        emitter.send(SseEmitter.event()
                                .name("complete")
                                .data(completeEvent.toJSONString(), MediaType.APPLICATION_JSON));
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("完成处理失败", e);
                        emitter.completeWithError(e);
                    }
                }

                @Override
                public void onError(Exception e) {
                    try {
                        JSONObject errorEvent = new JSONObject();
                        errorEvent.put("message", e.getMessage());
                        
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data(errorEvent.toJSONString(), MediaType.APPLICATION_JSON));
                        emitter.completeWithError(e);
                    } catch (IOException ex) {
                        log.error("错误发送失败", ex);
                    }
                }
            };

                aiParserService.parseImageStreamByLineFromBytes(fileBytes, originalFilename, callback);
            }

        } catch (Exception e) {
            log.error("上传处理失败", e);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"message\":\"" + e.getMessage() + "\"}"));
            } catch (IOException ex) {
                log.error("错误发送失败", ex);
            }
            emitter.completeWithError(e);
        }
        });

        return emitter;
    }

    @PostMapping("/upload")
    public com.attendance.common.Result<JSONObject> upload(@RequestParam("image") MultipartFile image) {
        try {
            String filename = aiParserService.saveUploadedFile(image.getBytes(), image.getOriginalFilename());
            String taskId = taskService.createTask(filename).getTaskId();

            List<JSONObject> records = new ArrayList<>();
            final boolean[] completed = {false};
            final Exception[] error = {null};

            if (useSimulatedRecognition()) {
                log.info("使用模拟识别服务");

                SimulatedRecognitionService.SimCallback simCallback = new SimulatedRecognitionService.SimCallback() {
                    @Override
                    public void onRecord(JSONObject record) {
                        records.add(record);
                    }

                    @Override
                    public void onComplete(int totalCount) {
                        completed[0] = true;
                    }

                    @Override
                    public void onError(Exception e) {
                        error[0] = e;
                        completed[0] = true;
                    }
                };

                simulatedRecognitionService.simulateRecognition(simCallback);
            } else {
                byte[] fileBytes = image.getBytes();
                String base64Image = Base64.getEncoder().encodeToString(fileBytes);

                AIParserService.ParseCallback callback = new AIParserService.ParseCallback() {
                    @Override
                    public void onRecord(JSONObject record) {
                        records.add(record);
                    }

                    @Override
                    public void onComplete(int totalCount) {
                        completed[0] = true;
                    }

                    @Override
                    public void onError(Exception e) {
                        error[0] = e;
                        completed[0] = true;
                    }
                };

                aiParserService.parseImageStreamByLine(base64Image, callback);
            }

            int timeout = 0;
            while (!completed[0] && timeout < 120) {
                try {
                    Thread.sleep(1000);
                    timeout++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (error[0] != null) {
                throw error[0];
            }

            String rawData = JSON.toJSONString(records);
            taskService.updateTaskRawData(taskId, rawData, "");

            JSONObject result = new JSONObject();
            result.put("taskId", taskId);
            result.put("rowCount", records.size());

            return com.attendance.common.Result.success(result);
        } catch (Exception e) {
            log.error("上传失败", e);
            return com.attendance.common.Result.error(e.getMessage());
        }
    }

    @GetMapping("/image/{fileKey}")
    public void getImage(@PathVariable String fileKey, HttpServletResponse response) throws IOException {
        try {
            Path uploadPath = Paths.get("./uploads");
            Path filePath = uploadPath.resolve(fileKey);
            
            if (!Files.exists(filePath)) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, "文件不存在");
            }

            File file = filePath.toFile();
            response.setContentType("image/jpeg");
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

    @GetMapping("/export/{taskId}/csv")
    public void exportCsv(@PathVariable String taskId, HttpServletResponse response) {
        try {
            com.attendance.entity.Task task = taskService.getTaskById(taskId);
            String data = task.getConfirmedData() != null ? task.getConfirmedData() : task.getRawData();
            
            if (data == null) {
                throw new BusinessException(ErrorCode.TASK_NOT_FOUND, "没有可导出的数据");
            }

            JSONArray records = JSON.parseArray(data);
            
            StringBuilder csv = new StringBuilder();
            csv.append("工号,姓名,中介机构,班次,日期,到达时间,离开时间,休息(分钟),检查器,标记\n");
            
            for (int i = 0; i < records.size(); i++) {
                JSONObject record = records.getJSONObject(i);
                csv.append(escapeCsv(record.getString("NO"))).append(",");
                csv.append(escapeCsv(record.getString("NOM_PRENOM"))).append(",");
                csv.append(escapeCsv(record.getString("AGENCE_INTERIMAIRE"))).append(",");
                csv.append(escapeCsv(record.getString("HORAIRES_DU_TRAVAIL"))).append(",");
                csv.append(escapeCsv(record.getString("Date"))).append(",");
                csv.append(escapeCsv(record.getString("ARRIVEE"))).append(",");
                csv.append(escapeCsv(record.getString("DEPAR"))).append(",");
                csv.append(record.getInteger("PAUSE")).append(",");
                csv.append(escapeCsv(record.getString("CHECKER"))).append(",");
                csv.append(escapeCsv(record.getString("SmartMark"))).append("\n");
            }

            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=\"attendance_" + taskId + ".csv\"");
            response.getWriter().write(csv.toString());
        } catch (Exception e) {
            log.error("导出失败", e);
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void appendRawData(String taskId, List<JSONObject> newRecords) {
        try {
            com.attendance.entity.Task task = taskService.getTaskById(taskId);
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
            taskService.updateTaskRawData(taskId, mergedData, "");
        } catch (Exception e) {
            log.error("追加rawData失败", e);
            String rawData = JSON.toJSONString(newRecords);
            taskService.updateTaskRawData(taskId, rawData, "");
        }
    }
}
