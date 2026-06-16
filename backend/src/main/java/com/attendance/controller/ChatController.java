package com.attendance.controller;

import com.alibaba.fastjson.JSONObject;
import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.Result;
import com.attendance.service.ConfigService;
import com.attendance.service.ImageQualityConfigService;
import com.attendance.service.RecognitionConcurrencyGuard;
import com.attendance.service.RecognitionRunner;
import com.attendance.service.RecognitionSupport;
import com.attendance.security.TaskAccessService;
import com.attendance.util.CountryResolver;
import com.attendance.util.ImageUploadValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ConfigService configService;

    @Autowired
    private RecognitionRunner recognitionRunner;

    @Autowired
    private RecognitionSupport recognitionSupport;

    @Autowired
    private ImageQualityConfigService imageQualityConfigService;

    @Autowired
    private RecognitionConcurrencyGuard recognitionConcurrencyGuard;

    @Autowired
    private TaskAccessService taskAccessService;

    @PostMapping("/completion")
    public Map<String, Object> chatCompletion(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();

        try {
            String message = request.get("message");
            String country = request.getOrDefault("country", "CN");

            log.info("收到对话请求: message={}, country={}", message, country);

            String systemPrompt = getSystemPrompt(country);
            String aiResponse = generateResponse(message, systemPrompt);

            Map<String, Object> data = new HashMap<>();
            data.put("content", aiResponse);
            data.put("messageId", UUID.randomUUID().toString());

            response.put("success", true);
            response.put("data", data);

        } catch (Exception e) {
            log.error("对话处理失败", e);
            response.put("success", false);
            response.put("message", "处理失败: " + e.getMessage());
        }

        return response;
    }

    /**
     * 与首页识别同源：调用 MiMo + prompts.md，返回 JSON（非 SSE 假数据）。
     */
    @PostMapping("/image")
    public Result<Map<String, Object>> analyzeImage(
            @RequestParam("file") MultipartFile image,
            @RequestHeader(value = "X-Country", required = false) String countryHeader,
            @RequestParam(value = "country", required = false) String countryParam,
            @RequestParam(value = "question", required = false) String question) {
        log.info("收到聊天图片分析: countryHeader={}, question={}", countryHeader, question);

        try {
            byte[] fileBytes = image.getBytes();
            ImageUploadValidator.validate(fileBytes, image.getOriginalFilename(), image.getContentType(), log,
                    imageQualityConfigService.getConfig());

            String workingCountry = CountryResolver.resolveForRecognition(
                    countryHeader, countryParam, configService, log);
            String configCountry = configService.resolveEffectiveCountry(workingCountry);

            recognitionSupport.requireRealAi();

            String userId = taskAccessService.requireCurrentUserId();
            recognitionConcurrencyGuard.acquire(userId);
            RecognitionRunner.RecognitionOutcome outcome;
            try {
                outcome = recognitionRunner.run(
                        fileBytes, image.getOriginalFilename(), configCountry, workingCountry, null, null);
            } finally {
                recognitionConcurrencyGuard.release(userId);
            }

            List<JSONObject> records = outcome.getRecords();
            String content = formatRecognitionForChat(records, outcome.getEngine(), question);

            Map<String, Object> data = new HashMap<>();
            data.put("content", content);
            data.put("messageId", UUID.randomUUID().toString());
            data.put("rowCount", records.size());
            data.put("recognitionEngine", outcome.getEngine());

            return Result.success(data);
        } catch (BusinessException e) {
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("聊天图片识别失败", e);
            return Result.error("识别失败: " + e.getMessage());
        }
    }

    private String formatRecognitionForChat(List<JSONObject> records, String engine, String question) {
        StringBuilder sb = new StringBuilder();
        if (question != null && !question.trim().isEmpty()) {
            sb.append("您的问题：").append(question.trim()).append("\n\n");
        }
        sb.append("识别完成（引擎：").append(engine).append("），共 ").append(records.size()).append(" 条记录。\n\n");
        int show = Math.min(records.size(), 8);
        for (int i = 0; i < show; i++) {
            JSONObject r = records.get(i);
            sb.append(i + 1).append(". ")
                    .append(r.getString("NO")).append(" ")
                    .append(r.getString("NOM_PRENOM")).append(" | ")
                    .append(r.getString("Date")).append(" ")
                    .append(r.getString("ARRIVEE")).append("-")
                    .append(r.getString("DEPAR")).append("\n");
        }
        if (records.size() > show) {
            sb.append("… 另有 ").append(records.size() - show).append(" 条，请在「任务/结果」页查看完整列表。\n");
        }
        sb.append("\n如需编辑或提交飞书，请从首页拍照识别进入结果页。");
        return sb.toString();
    }

    private String getSystemPrompt(String country) {
        return "你是一个专业的考勤助手，可以帮助用户处理考勤相关的问题。\n\n"
                + "请用简洁友好的语言回答用户的问题。"
                + "若用户需要识别考勤表，请引导其在首页上传图片（与 PC 使用同一套 MiMo 识别）。";
    }

    private String generateResponse(String message, String systemPrompt) {
        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("多少") && lowerMessage.contains("人")) {
            return "请先上传考勤表图片，我可以帮您统计人数。";
        } else if (lowerMessage.contains("出勤率")) {
            return "请先上传考勤表，我可以帮您计算出勤率。";
        } else if (lowerMessage.contains("未出勤") || lowerMessage.contains("缺勤")) {
            return "要查找未出勤的人员，请先在首页上传考勤表进行识别。";
        } else if (lowerMessage.contains("帮助") || lowerMessage.contains("help")) {
            return "我可以帮助您：\n📷 在首页识别考勤表图片\n📊 统计出勤数据\n❓ 回答考勤相关问题";
        } else if (lowerMessage.contains("你好") || lowerMessage.contains("hi") || lowerMessage.contains("hello")) {
            return "您好！我是AI考勤助手 👋\n\n请在首页上传考勤表进行识别，或在此发送图片（将调用与 PC 相同的 MiMo 识别）。";
        } else {
            return "我理解您的问题。\n\n请从首页上传考勤表图片进行识别（与 PC 端相同引擎），或在此直接发送图片。";
        }
    }
}
