package com.attendance.controller;

import com.attendance.service.AIParserService;
import com.attendance.service.MarkdownConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private AIParserService aiParserService;

    @Autowired
    private MarkdownConfigService markdownConfigService;

    @PostMapping("/completion")
    public Map<String, Object> chatCompletion(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String message = request.get("message");
            String country = request.getOrDefault("country", "CN");
            
            log.info("收到对话请求: message={}, country={}", message, country);
            
            // 获取对话提示词
            String systemPrompt = getSystemPrompt(country);
            
            // 构建对话内容
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

    @PostMapping(value = "/image", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeImage(@RequestParam("file") MultipartFile image,
                                   @RequestParam(value = "country", defaultValue = "CN") String country,
                                   @RequestParam(value = "question", required = false) String question) {
        log.info("收到图片分析请求: country={}, question={}", country, question);
        
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitter.onCompletion(() -> log.info("图片分析SSE完成"));
        emitter.onTimeout(() -> log.warn("图片分析SSE超时"));
        emitter.onError(e -> log.error("图片分析SSE错误", e));
        
        new Thread(() -> {
            try {
                // 保存上传的图片
                String savedFilename = aiParserService.saveUploadedFile(
                    image.getBytes(), 
                    image.getOriginalFilename()
                );
                
                // 构建图片路径
                String imagePath = Paths.get(
                    System.getProperty("user.dir"), 
                    "uploads", 
                    savedFilename
                ).toString();
                
                // 获取分析提示词
                String prompt = getImageAnalysisPrompt(country, question);
                
                // 发送图片数据给AI
                String base64Image = encodeImageToBase64(imagePath);
                String aiResponse = analyzeImageWithAI(base64Image, prompt);
                
                // 发送完成事件
                emitter.send(SseEmitter.event()
                    .name("message")
                    .data("{\"success\":true,\"data\":{\"content\":\"" + escapeJson(aiResponse) + "\"}}"));
                emitter.complete();
                
            } catch (Exception e) {
                log.error("图片分析失败", e);
                try {
                    emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"success\":false,\"message\":\"" + escapeJson(e.getMessage()) + "\"}"));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        }).start();
        
        return emitter;
    }

    private String getSystemPrompt(String country) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个专业的考勤助手，可以帮助用户处理考勤相关的问题。\n\n");
        prompt.append("你可以帮助用户：\n");
        prompt.append("1. 分析考勤数据，统计出勤情况\n");
        prompt.append("2. 回答关于考勤规则的问题\n");
        prompt.append("3. 解释识别结果中的标记含义\n");
        prompt.append("4. 提供考勤管理的建议\n\n");
        prompt.append("请用简洁友好的语言回答用户的问题。");
        return prompt.toString();
    }

    private String getImageAnalysisPrompt(String country, String question) {
        StringBuilder prompt = new StringBuilder();
        
        if (question != null && !question.isEmpty()) {
            prompt.append("用户的问题：").append(question).append("\n\n");
        } else {
            prompt.append("请分析这张考勤表图片，识别其中的考勤数据并回答用户的问题。\n\n");
        }
        
        prompt.append("请按照以下格式返回识别结果：\n");
        prompt.append("1. 如果是考勤表，请列出识别的考勤记录\n");
        prompt.append("2. 如果有统计需求，请给出统计结果\n");
        prompt.append("3. 回答用户的问题\n\n");
        
        return prompt.toString();
    }

    private String generateResponse(String message, String systemPrompt) {
        // 简单的对话响应逻辑
        String lowerMessage = message.toLowerCase();
        
        if (lowerMessage.contains("多少") && lowerMessage.contains("人")) {
            return "请先上传考勤表图片，我可以帮您统计人数。";
        } else if (lowerMessage.contains("出勤率")) {
            return "请先上传考勤表，我可以帮您计算出勤率。出勤率的计算公式是：实际出勤人数 / 应出勤人数 × 100%。";
        } else if (lowerMessage.contains("未出勤") || lowerMessage.contains("缺勤")) {
            return "要查找未出勤的人员，请先上传考勤表。我会帮您识别并标注未出勤的记录。";
        } else if (lowerMessage.contains("手写")) {
            return "在考勤表中，手写记录通常表示该考勤数据需要人工核实或补充。我会在识别时自动标注手写记录。";
        } else if (lowerMessage.contains("帮助") || lowerMessage.contains("help")) {
            return "我可以帮助您：\n📷 识别考勤表图片\n📊 统计出勤数据\n❓ 回答考勤相关问题\n请告诉我您需要什么帮助？";
        } else if (lowerMessage.contains("你好") || lowerMessage.contains("hi") || lowerMessage.contains("hello")) {
            return "您好！我是AI考勤助手 👋\n\n我可以帮您：\n📷 识别考勤表\n📊 统计数据\n❓ 回答问题\n\n请问有什么可以帮您的？";
        } else {
            return "我理解您的问题。\n\n作为考勤助手，我可以帮助您：\n• 识别上传的考勤表图片\n• 统计出勤、缺勤、手写记录\n• 回答考勤相关问题\n\n请告诉我您具体需要什么帮助，或者直接上传考勤表图片让我帮您分析。";
        }
    }

    private String analyzeImageWithAI(String base64Image, String prompt) {
        // 这里应该调用真实的AI服务进行图片分析
        // 暂时返回模拟结果
        return "我已经收到并分析了您上传的图片。\n\n根据图片内容，这看起来是一份考勤表。\n\n📊 初步分析结果：\n• 表格格式清晰\n• 包含日期、姓名、签到时间等字段\n• 请稍候，系统正在进行详细识别...\n\n如需获取完整的识别结果，请在首页上传图片进行识别。";
    }

    private String encodeImageToBase64(String imagePath) {
        try {
            Path path = Paths.get(imagePath);
            if (Files.exists(path)) {
                byte[] imageBytes = Files.readAllBytes(path);
                return Base64.getEncoder().encodeToString(imageBytes);
            }
        } catch (IOException e) {
            log.error("读取图片失败: {}", imagePath, e);
        }
        return null;
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
