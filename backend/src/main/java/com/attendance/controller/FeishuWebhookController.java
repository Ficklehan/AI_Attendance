package com.attendance.controller;

import com.attendance.config.FeishuProperties;
import com.attendance.service.FeishuService;
import com.attendance.service.TaskService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@RestController
@RequestMapping("/webhook")
public class FeishuWebhookController {
    
    private static final Logger log = LoggerFactory.getLogger(FeishuWebhookController.class);

    @Autowired
    private FeishuProperties feishuProperties;

    @Autowired
    private FeishuService feishuService;

    @Autowired
    private TaskService taskService;

    @GetMapping("/feishu")
    public String verify(@RequestParam("challenge") String challenge,
                        @RequestParam("challenge_type") String challengeType) {
        log.info("飞书Webhook验证请求");
        JSONObject response = new JSONObject();
        response.put("challenge", challenge);
        return response.toJSONString();
    }

    @PostMapping("/feishu")
    public String handleWebhook(@RequestBody String requestBody,
                               @RequestHeader("X-Lark-Signature") String signature,
                               @RequestHeader("X-Lark-Request-Timestamp") String timestamp,
                               @RequestHeader("X-Lark-Encryption-Key") String encryptionKey) {
        try {
            if (!verifySignature(requestBody, signature, timestamp)) {
                log.warn("飞书Webhook签名验证失败");
                return "{\"error\":\"signature verification failed\"}";
            }

            JSONObject event = JSON.parseObject(requestBody);
            String eventType = event.getString("event");

            if ("im.message.receive_v1".equals(eventType)) {
                handleMessageReceive(event.getJSONObject("event"));
            }

            return "{\"code\":0}";
        } catch (Exception e) {
            log.error("处理飞书Webhook失败", e);
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private boolean verifySignature(String requestBody, String signature, String timestamp) {
        try {
            String signStr = timestamp + requestBody;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                feishuProperties.getVerificationToken().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(signStr.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = Base64.getEncoder().encodeToString(hash);
            
            return calculatedSignature.equals(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("签名验证异常", e);
            return false;
        }
    }

    private void handleMessageReceive(JSONObject event) {
        try {
            String messageType = event.getString("message_type");
            if (!"image".equals(messageType)) {
                log.info("忽略非图片消息");
                return;
            }

            String messageId = event.getString("message_id");
            JSONObject sender = event.getJSONObject("sender");
            String senderId = sender.getString("sender_id");
            String fileKey = event.getString("content");
            
            log.info("收到飞书图片消息: messageId={}, senderId={}", messageId, senderId);

            String imageUrl = feishuService.getImageUrl(fileKey);
            log.info("获取图片URL: {}", imageUrl);

            String userId = extractUserId(senderId);
            String taskId = taskService.createTask(fileKey).getTaskId();
            
            log.info("创建任务: taskId={}, userId={}", taskId, userId);

            feishuService.sendTextMessage(userId, "已收到您的考勤图片，任务ID: " + taskId);

        } catch (Exception e) {
            log.error("处理消息失败", e);
        }
    }

    private String extractUserId(String senderId) {
        try {
            JSONObject sender = JSON.parseObject(senderId);
            return sender.getString("user_id");
        } catch (Exception e) {
            return senderId;
        }
    }
}