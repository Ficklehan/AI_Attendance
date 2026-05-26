package com.attendance.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.common.Result;
import com.attendance.entity.User;
import com.attendance.mapper.UserMapper;
import com.attendance.service.BitableService;
import com.attendance.service.ConfigService;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/test-config")
public class TestConfigController {
    
    private static final Logger log = LoggerFactory.getLogger(TestConfigController.class);

    @Autowired
    private ConfigService configService;

    @Autowired
    private BitableService bitableService;

    @Autowired
    private UserMapper userMapper;

    @Value("${feishu.app-id}")
    private String feishuAppId;

    @Value("${feishu.app-secret}")
    private String feishuAppSecret;

    @GetMapping("/field-mapping")
    public Result<List<Map<String, Object>>> getFieldMapping() {
        return Result.success(configService.getFieldMapping("default"));
    }

    @GetMapping("/feishu-config")
    public Result<Map<String, Object>> getFeishuConfig() {
        return Result.success(configService.getFeishuConfig("default"));
    }

    @GetMapping("/users")
    public Result<List<User>> getUsers() {
        return Result.success(userMapper.selectUserList(0, 100));
    }

    @PostMapping("/test-push")
    public Result<Map<String, Object>> testPushToFeishu(@org.springframework.web.bind.annotation.RequestBody(required = false) Map<String, Object> requestBody) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String countryCode = "fr";
            if (requestBody != null && requestBody.containsKey("countryCode")) {
                countryCode = (String) requestBody.get("countryCode");
            }
            
            log.info("开始测试推送到飞书，countryCode={}", countryCode);
            
            // 查询所有用户
            List<User> users = userMapper.selectUserList(0, 100);
            log.info("查询到的用户: {}", users);
            result.put("users", users);
            
            // 找到目标用户
            User targetUser = null;
            if (requestBody != null && requestBody.containsKey("userId")) {
                String userId = (String) requestBody.get("userId");
                targetUser = userMapper.selectUserById(userId);
            }
            
            // 如果没找到指定用户，就找韩宝乐用户
            if (targetUser == null) {
                for (User user : users) {
                    if (user.getRealName() != null && user.getRealName().contains("韩宝乐")) {
                        targetUser = user;
                        break;
                    }
                }
            }
            
            // 如果还没找到，就用第一个用户
            if (targetUser == null && !users.isEmpty()) {
                targetUser = users.get(0);
            }
            
            log.info("使用的用户: {}", targetUser);
            
            // 获取用户信息
            String feishuUserId = targetUser != null ? targetUser.getFeishuUserId() : null;
            String userName = targetUser != null ? (targetUser.getRealName() != null ? targetUser.getRealName() : targetUser.getUsername()) : "未知用户";
            log.info("用户信息: feishuUserId={}, userName={}", feishuUserId, userName);
            
            // 测试所有字段，包括 AGENCE_INTERIMAIRE 和 UPLOADED_BY
            List<Map<String, Object>> testRecords = new ArrayList<>();
            Map<String, Object> testRecord = new HashMap<>();
            testRecord.put("NO", "TEST007");
            testRecord.put("NOM_PRENOM", "韩宝乐测试");
            testRecord.put("AGENCE_INTERIMAIRE", "测试中介");
            testRecord.put("HORAIRES_DU_TRAVAIL", "09:00-18:00");
            testRecord.put("Date", "2026-05-23");
            testRecord.put("ARRIVEE_DATETIME", "2026-05-23 08:55");
            testRecord.put("DEPAR_DATETIME", "2026-05-23 18:10");
            testRecord.put("PAUSE", 60);
            testRecord.put("CHECKER", "正常");
            testRecord.put("SmartMark", "正常;正常");
            testRecord.put("TASK_ID", "TEST_TASK_20260523_007");
            // 优先使用飞书用户ID，如果没有就使用用户名
            if (feishuUserId != null && !feishuUserId.isEmpty()) {
                testRecord.put("UPLOADED_BY", feishuUserId);
            } else {
                testRecord.put("UPLOADED_BY", userName);
            }
            
            testRecords.add(testRecord);
            
            result.put("testData", testRecords);
            result.put("targetUser", targetUser);
            
            // 真正调用 BitableService 推送
            log.info("开始调用 bitableService.batchWriteRecords...");
            try {
                bitableService.batchWriteRecords(testRecords, countryCode);
                result.put("success", true);
                result.put("message", "调用 bitableService.batchWriteRecords 成功！");
            } catch (Exception e) {
                log.error("调用 bitableService.batchWriteRecords 失败", e);
                result.put("success", false);
                result.put("message", "调用 bitableService.batchWriteRecords 失败: " + e.getMessage());
                result.put("error", e.getMessage());
            }
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("测试推送失败", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("stackTrace", Arrays.toString(e.getStackTrace()));
            return Result.success(result);
        }
    }

    private String getFeishuAccessToken() throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        JSONObject body = new JSONObject();
        body.put("app_id", feishuAppId);
        body.put("app_secret", feishuAppSecret);

        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")
                .post(okhttp3.RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();
        JSONObject result = JSON.parseObject(responseBody);
        
        log.info("获取飞书 access token 响应: {}", result);
        
        if (result.getIntValue("code") != 0) {
            throw new RuntimeException("获取token失败: " + result.getString("msg"));
        }
        
        return result.getString("tenant_access_token");
    }

    private Map<String, Object> sendToFeishu(String accessToken, String appToken, String tableId, List<Map<String, Object>> records) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build();

        // 获取字段映射
        List<Map<String, Object>> fieldMappings = configService.getFieldMapping("default");
        Map<String, String> aiToFeishuField = new HashMap<>();
        Map<String, String> aiToFieldType = new HashMap<>();
        for (Map<String, Object> mapping : fieldMappings) {
            String aiField = (String) mapping.get("aiField");
            String feishuField = (String) mapping.get("feishuField");
            String type = (String) mapping.get("type");
            aiToFeishuField.put(aiField, feishuField);
            aiToFieldType.put(aiField, type);
        }
        
        log.info("字段映射: {}", aiToFeishuField);
        log.info("字段类型: {}", aiToFieldType);

        JSONObject body = new JSONObject();
        
        JSONArray recordsArray = new JSONArray();
        for (Map<String, Object> record : records) {
            JSONObject fields = new JSONObject();
            
            for (Map.Entry<String, Object> entry : record.entrySet()) {
                String aiField = entry.getKey();
                Object value = entry.getValue();
                String feishuField = aiToFeishuField.get(aiField);
                
                if (feishuField != null) {
                    // 转换类型
                    if (value != null) {
                        String fieldType = aiToFieldType.get(aiField);
                        if ("date".equals(fieldType) && value instanceof String) {
                            fields.put(feishuField, convertDate((String) value));
                        } else if ("datetime".equals(fieldType) && value instanceof String) {
                            fields.put(feishuField, convertDateTime((String) value));
                        } else if ("number".equals(fieldType)) {
                            try {
                                if (value instanceof String) {
                                    fields.put(feishuField, Integer.parseInt((String) value));
                                } else if (value instanceof Integer || value instanceof Long) {
                                    fields.put(feishuField, value);
                                }
                            } catch (NumberFormatException e) {
                                fields.put(feishuField, value);
                            }
                        } else if ("user".equals(fieldType)) {
                            // 用户类型：首先尝试用用户类型推送，如果不行则在后续降级处理中改为字符串
                            String strValue = value.toString();
                            try {
                                JSONArray userArray = new JSONArray();
                                JSONObject userObj = new JSONObject();
                                userObj.put("id", strValue);
                                userArray.add(userObj);
                                fields.put(feishuField, userArray);
                                log.info("使用用户类型格式推送: {}", strValue);
                            } catch (Exception e) {
                                log.warn("用用户类型推送失败，将作为字符串推送: {}", strValue, e);
                                fields.put(feishuField, strValue);
                            }
                        } else {
                            fields.put(feishuField, value);
                        }
                    }
                }
            }
            
            // 打印转换后的字段
            log.info("转换后的记录: {}", fields);
            
            JSONObject recordObj = new JSONObject();
            recordObj.put("fields", fields);
            recordsArray.add(recordObj);
        }
        
        body.put("records", recordsArray);
        
        log.info("发送到飞书的完整请求体: {}", body.toJSONString());

        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/bitable/v1/apps/" + appToken + "/tables/" + tableId + "/records/batch_create")
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .post(okhttp3.RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();
        
        log.info("飞书API响应: {}", responseBody);
        
        JSONObject result = JSON.parseObject(responseBody);
        
        Map<String, Object> pushResult = new HashMap<>();
        pushResult.put("success", result.getIntValue("code") == 0);
        pushResult.put("code", result.getIntValue("code"));
        pushResult.put("message", result.getString("msg"));
        pushResult.put("response", result);
        pushResult.put("request", body);
        
        return pushResult;
    }

    private long convertDate(String dateStr) {
        try {
            String[] parts = dateStr.split("-");
            if (parts.length == 3) {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]);
                int day = Integer.parseInt(parts[2]);
                return java.time.LocalDate.of(year, month, day)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
            }
        } catch (Exception e) {
            log.warn("日期转换失败: {}", dateStr, e);
        }
        return System.currentTimeMillis();
    }

    private long convertDateTime(String dateTimeStr) {
        try {
            if (dateTimeStr.contains(" ")) {
                String[] parts = dateTimeStr.split(" ");
                String datePart = parts[0];
                String timePart = parts.length > 1 ? parts[1] : "00:00";
                
                String[] dateParts = datePart.split("-");
                String[] timeParts = timePart.split(":");
                
                int year = Integer.parseInt(dateParts[0]);
                int month = Integer.parseInt(dateParts[1]);
                int day = Integer.parseInt(dateParts[2]);
                int hour = timeParts.length > 0 ? Integer.parseInt(timeParts[0]) : 0;
                int minute = timeParts.length > 1 ? Integer.parseInt(timeParts[1]) : 0;
                
                return java.time.LocalDateTime.of(year, month, day, hour, minute)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
            }
        } catch (Exception e) {
            log.warn("日期时间转换失败: {}", dateTimeStr, e);
        }
        return System.currentTimeMillis();
    }
}
