package com.attendance.service;

import com.attendance.config.FeishuProperties;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class BitableService {
    
    private static final Logger log = LoggerFactory.getLogger(BitableService.class);

    @Autowired
    private FeishuProperties feishuProperties;

    @Autowired
    private ConfigService configService;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build();

    private String getAccessToken() throws IOException {
        JSONObject body = new JSONObject();
        body.put("app_id", feishuProperties.getAppId());
        body.put("app_secret", feishuProperties.getAppSecret());

        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        Response response = httpClient.newCall(request).execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException("获取飞书Token失败");
        }

        JSONObject result = JSON.parseObject(response.body().string());
        return result.getString("tenant_access_token");
    }

    public void batchWriteRecords(List<Map<String, Object>> records, String countryCode) throws IOException {
        String token = getAccessToken();
        String appToken = getAppToken(countryCode);
        String tableId = getTableId(countryCode);

        int batchSize = 100;
        for (int i = 0; i < records.size(); i += batchSize) {
            int end = Math.min(i + batchSize, records.size());
            List<Map<String, Object>> batch = records.subList(i, end);
            
            try {
                writeBatch(token, appToken, tableId, batch, countryCode, false);
                log.info("写入飞书表格成功: 批次{}，记录数{}", (i / batchSize) + 1, batch.size());
            } catch (RuntimeException e) {
                log.error("写入飞书表格失败: {}", e.getMessage());
                throw e;
            }
        }
    }

    private void writeBatch(String token, String appToken, String tableId, List<Map<String, Object>> records, String countryCode, Object excludeParam) throws IOException {
        JSONObject body = new JSONObject();
        body.put("app_token", appToken);
        body.put("table_id", tableId);
        
        JSONArray recordsArray = new JSONArray();
        for (Map<String, Object> record : records) {
            recordsArray.add(convertRecord(record, countryCode, excludeParam, false));
        }
        body.put("records", recordsArray);

        log.info("发送到飞书的请求: appToken={}, tableId={}, recordsCount={}, excludeParam={}", appToken, tableId, records.size(), excludeParam);
        log.info("请求URL: https://open.feishu.cn/open-apis/bitable/v1/apps/{}/tables/{}/records/batch_create", appToken, tableId);
        log.info("完整请求体: {}", body.toJSONString());

        Request request = new Request.Builder()
                .url("https://open.feishu.cn/open-apis/bitable/v1/apps/" + appToken + "/tables/" + tableId + "/records/batch_create")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

        Response response = httpClient.newCall(request).execute();
        String responseBody = response.body().string();
        log.info("飞书API响应: {}", responseBody);
        
        JSONObject result = JSON.parseObject(responseBody);
        Integer code = result.getInteger("code");
        
        // 如果第一次失败且不是排除参数的情况，尝试排除用户类型字段重新推送
        boolean isFirstAttempt = (excludeParam == null || (excludeParam instanceof Boolean && !(Boolean)excludeParam));
        if ((!response.isSuccessful() || (code != null && code != 0)) && isFirstAttempt) {
            log.warn("第一次推送失败，尝试排除用户类型字段重新推送");
            
            // 重新构建请求，排除用户类型字段（UPLOADED_BY）
            JSONObject retryBody = new JSONObject();
            retryBody.put("app_token", appToken);
            retryBody.put("table_id", tableId);
            
            JSONArray retryRecordsArray = new JSONArray();
            for (Map<String, Object> record : records) {
                // 创建新记录，排除 UPLOADED_BY 字段
                Map<String, Object> filteredRecord = new java.util.HashMap<>(record);
                filteredRecord.remove("UPLOADED_BY");
                retryRecordsArray.add(convertRecord(filteredRecord, countryCode, null, false));
            }
            retryBody.put("records", retryRecordsArray);
            
            log.info("降级后的请求体: {}", retryBody.toJSONString());
            
            Request retryRequest = new Request.Builder()
                    .url("https://open.feishu.cn/open-apis/bitable/v1/apps/" + appToken + "/tables/" + tableId + "/records/batch_create")
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(retryBody.toJSONString(), MediaType.parse("application/json")))
                    .build();
            
            Response retryResponse = httpClient.newCall(retryRequest).execute();
            String retryResponseBody = retryResponse.body().string();
            log.info("降级推送飞书API响应: {}", retryResponseBody);
            
            JSONObject retryResult = JSON.parseObject(retryResponseBody);
            Integer retryCode = retryResult.getInteger("code");
            
            if (!retryResponse.isSuccessful() || (retryCode != null && retryCode != 0)) {
                log.error("降级推送也失败: HTTP状态码={}, 响应={}", retryResponse.code(), retryResponseBody);
                throw new RuntimeException("写入飞书多维表格失败: " + retryResponseBody);
            }
            
            log.info("降级推送飞书写入成功: {}", retryResult);
            return;
        }
        
        if (!response.isSuccessful()) {
            log.error("写入飞书失败: HTTP状态码={}, 响应={}", response.code(), responseBody);
            throw new RuntimeException("写入飞书多维表格失败: " + responseBody);
        }
        
        if (code != null && code != 0) {
            log.error("飞书API返回错误: code={}, msg={}", code, result.getString("msg"));
            throw new RuntimeException("飞书API错误: " + result.getString("msg"));
        }
        
        log.info("飞书写入成功: {}", result);
    }

    private JSONObject convertRecord(Map<String, Object> record, String countryCode, Object excludeParam) {
        return convertRecord(record, countryCode, excludeParam, false);
    }
    
    private JSONObject convertRecord(Map<String, Object> record, String countryCode, Object excludeParam, boolean forceUserTypeAsString) {
        JSONObject result = new JSONObject();
        JSONObject fields = new JSONObject();
        
        Map<String, FieldMapping> mappings = getFieldMappings(countryCode);
        log.info("字段映射数量: {}, 强制用户类型为字符串: {}", mappings.size(), forceUserTypeAsString);
        log.info("记录包含的字段: {}", record.keySet());
        log.info("排除参数: {}", excludeParam);
        
        // 特别检查 UPLOADED_BY 和 TASK_ID 字段
        Object uploadedByValue = record.get("UPLOADED_BY");
        Object taskIdValue = record.get("TASK_ID");
        log.info("UPLOADED_BY 原始值: {}, TASK_ID 原始值: {}", uploadedByValue, taskIdValue);
        
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            String aiField = entry.getKey();
            
            // 检查是否需要排除该字段
            boolean shouldExclude = false;
            if (excludeParam instanceof Boolean) {
                shouldExclude = (Boolean) excludeParam && ("TASK_ID".equals(aiField) || "UPLOADED_BY".equals(aiField));
            } else if (excludeParam instanceof String) {
                shouldExclude = excludeParam.equals(aiField);
            }
            
            if (shouldExclude) {
                log.info("排除字段: {}", aiField);
                continue;
            }
            
            FieldMapping mapping = mappings.get(aiField);
            if (mapping != null) {
                String fieldType = mapping.type;
                // 如果强制用户类型为字符串，则将 user 类型改为 string
                if (forceUserTypeAsString && "user".equals(fieldType)) {
                    fieldType = "string";
                }
                Object convertedValue = convertValue(entry.getValue(), fieldType);
                fields.put(mapping.feishuField, convertedValue);
                log.info("映射字段: {} -> {} = {} (类型: {})", aiField, mapping.feishuField, convertedValue, fieldType);
            } else {
                log.info("跳过未映射字段: {}", aiField);
            }
        }
        
        log.info("最终字段数量: {}", fields.size());
        log.info("最终字段内容: {}", fields);
        
        // 计算出勤工时 (WorkHours)
        calculateAndAddWorkHours(record, fields);
        
        result.put("fields", fields);
        return result;
    }
    
    private void calculateAndAddWorkHours(Map<String, Object> record, JSONObject fields) {
        try {
            // 检查是否已删除或未出勤
            Boolean isDeleted = record.containsKey("isDeleted") ? (Boolean) record.get("isDeleted") : false;
            Object smartMark = record.get("SmartMark");
            boolean isAbsent = smartMark != null && smartMark.toString().contains("未出勤");
            
            if (isDeleted || isAbsent) {
                log.info("已删除或未出勤记录，不设置WorkHours");
                return;
            }
            
            // 获取到达时间、离开时间和休息时间
            Object arriveTime = record.get("ARRIVEE");
            Object departTime = record.get("DEPAR");
            Object pauseMinutes = record.get("PAUSE");
            
            if (arriveTime == null || departTime == null) {
                log.info("缺少时间数据，不设置WorkHours");
                return;
            }
            
            String arriveStr = arriveTime.toString();
            String departStr = departTime.toString();
            
            // 解析时间
            Integer arriveMinutes = parseTimeToMinutes(arriveStr);
            Integer departMinutes = parseTimeToMinutes(departStr);
            
            if (arriveMinutes == null || departMinutes == null) {
                log.info("时间格式解析失败，不设置WorkHours");
                return;
            }
            
            // 计算总分钟数（处理跨天情况）
            int totalMinutes = departMinutes - arriveMinutes;
            if (totalMinutes < 0) {
                totalMinutes += 24 * 60;
            }
            
            // 减去休息时间
            int pause = 0;
            if (pauseMinutes != null && !pauseMinutes.toString().isEmpty()) {
                try {
                    pause = Integer.parseInt(pauseMinutes.toString());
                } catch (NumberFormatException e) {
                    log.warn("休息时间格式错误: {}", pauseMinutes);
                }
            }
            
            int workMinutes = totalMinutes - pause;
            if (workMinutes < 0) {
                log.info("出勤时间为负数，不设置WorkHours");
                return;
            }
            
            // 转换为小时，保留2位小数
            double workHours = Math.round(workMinutes * 100.0 / 60.0) / 100.0;
            fields.put("WorkHours", workHours);
            log.info("计算出勤工时: 到达={}, 离开={}, 休息={}, 出勤工时={}小时", arriveStr, departStr, pause, workHours);
            
        } catch (Exception e) {
            log.error("计算出勤工时失败: {}", e.getMessage(), e);
        }
    }
    
    private Integer parseTimeToMinutes(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty() || "???".equals(timeStr)) {
            return null;
        }
        
        try {
            // 清理时间字符串
            String cleanTime = timeStr.trim().replace(',', '.').replace('h', ':').replace('H', ':');
            String[] parts = cleanTime.split(":");
            
            if (parts.length == 2) {
                int hours = Integer.parseInt(parts[0].trim());
                int minutes = Integer.parseInt(parts[1].trim());
                return hours * 60 + minutes;
            } else if (parts.length == 1) {
                double num = Double.parseDouble(parts[0].trim());
                return (int) (Math.floor(num) * 60 + Math.round((num % 1) * 60));
            }
        } catch (Exception e) {
            log.warn("解析时间失败: {}", timeStr, e);
        }
        
        return null;
    }

    private Object convertValue(Object value, String type) {
        if (value == null) return null;
        
        String strValue = value.toString();
        
        switch (type) {
            case "date":
                return convertDate(strValue);
            case "datetime":
                return convertDateTime(strValue);
            case "number":
                try {
                    return Double.parseDouble(strValue);
                } catch (NumberFormatException e) {
                    return 0;
                }
            case "user":
                // 人员类型字段 - 飞书人员格式: {id: "user_id"}
                if (strValue != null && !strValue.isEmpty()) {
                    // 直接构建用户对象
                    JSONObject userObj = new JSONObject();
                    userObj.put("id", strValue);
                    JSONArray userArray = new JSONArray();
                    userArray.add(userObj);
                    return userArray;
                }
                return null;
            default:
                return strValue;
        }
    }

    private long convertDate(String dateStr) {
        try {
            String[] parts = dateStr.split("-");
            if (parts.length == 3) {
                int year = Integer.parseInt(parts[0]);
                int month = Integer.parseInt(parts[1]) - 1;
                int day = Integer.parseInt(parts[2]);
                return java.time.LocalDate.of(year, month + 1, day)
                        .atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
            }
        } catch (Exception e) {
            log.warn("日期转换失败: {}", dateStr);
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
                int month = Integer.parseInt(dateParts[1]) - 1;
                int day = Integer.parseInt(dateParts[2]);
                int hour = timeParts.length > 0 ? Integer.parseInt(timeParts[0]) : 0;
                int minute = timeParts.length > 1 ? Integer.parseInt(timeParts[1]) : 0;
                
                return java.time.LocalDateTime.of(year, month + 1, day, hour, minute)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();
            }
        } catch (Exception e) {
            log.warn("日期时间转换失败: {}", dateTimeStr);
        }
        return System.currentTimeMillis();
    }

    private String getAppToken(String countryCode) {
        Map<String, Object> config = configService.getFeishuConfig(countryCode);
        String token = config != null ? (String) config.get("bitable_app_token") : null;
        log.info("获取飞书App Token: country={}, token={}", countryCode, token != null ? token.substring(0, Math.min(10, token.length())) + "..." : "null");
        return token != null && !token.isEmpty() ? token : "MssZb4YXQaEeR5swLencoIcRnLc";
    }

    private String getTableId(String countryCode) {
        Map<String, Object> config = configService.getFeishuConfig(countryCode);
        String tableId = config != null ? (String) config.get("bitable_table_id") : null;
        log.info("获取飞书Table ID: country={}, tableId={}", countryCode, tableId);
        return tableId != null && !tableId.isEmpty() ? tableId : "tbl5WHAQbm0f6JyE";
    }

    private Map<String, FieldMapping> getFieldMappings(String countryCode) {
        try {
            List<Map<String, Object>> mappingsConfig = configService.getFieldMapping(countryCode);
            Map<String, FieldMapping> mappings = new java.util.HashMap<>();
            
            for (Map<String, Object> mapping : mappingsConfig) {
                String aiField = mapping.get("aiField") != null ? mapping.get("aiField").toString() : "";
                String feishuField = mapping.get("feishuField") != null ? mapping.get("feishuField").toString() : "";
                String type = mapping.get("type") != null ? mapping.get("type").toString() : "string";
                boolean required = mapping.get("required") != null ? Boolean.parseBoolean(mapping.get("required").toString()) : false;
                
                mappings.put(aiField, new FieldMapping(feishuField, type, required));
            }
            
            return mappings;
        } catch (Exception e) {
            log.warn("从配置文件读取字段映射失败，使用默认映射: {}", e.getMessage());
            return getDefaultFieldMappings();
        }
    }
    
    private Map<String, FieldMapping> getDefaultFieldMappings() {
        Map<String, FieldMapping> mappings = new java.util.HashMap<>();
        
        mappings.put("NO", new FieldMapping("NO", "string", true));
        mappings.put("NOM_PRENOM", new FieldMapping("NOM PRENOM", "string", false));
        mappings.put("AGENCE_INTERIMAIRE", new FieldMapping("AGENCE D'INTERIMAIR", "string", false));
        mappings.put("HORAIRES_DU_TRAVAIL", new FieldMapping("HORAIRES DU TRAVAI", "string", false));
        mappings.put("Date", new FieldMapping("Date", "date", true));
        mappings.put("ARRIVEE_DATETIME", new FieldMapping("ARRIVE", "datetime", true));
        mappings.put("DEPAR_DATETIME", new FieldMapping("DEPAR", "datetime", true));
        mappings.put("PAUSE", new FieldMapping("PAUS", "number", true));
        mappings.put("CHECKER", new FieldMapping("CHECKER", "string", false));
        mappings.put("SmartMark", new FieldMapping("Mark", "string", false));
        mappings.put("TASK_ID", new FieldMapping("任务id", "string", false));
        mappings.put("UPLOADED_BY", new FieldMapping("上传人员", "user", false));
        
        return mappings;
    }

    private static class FieldMapping {
        String feishuField;
        String type;
        boolean required;

        FieldMapping(String feishuField, String type, boolean required) {
            this.feishuField = feishuField;
            this.type = type;
            this.required = required;
        }
    }

    public boolean validateConnection(String appToken, String tableId) {
        log.info("验证飞书多维表连接: appToken={}, tableId={}", appToken, tableId);

        try {
            String token = getAccessToken();

            // 调用飞书 API 获取表信息
            Request request = new Request.Builder()
                    .url("https://open.feishu.cn/open-apis/bitable/v1/apps/" + appToken + "/tables/" + tableId)
                    .header("Authorization", "Bearer " + token)
                    .get()
                    .build();

            Response response = httpClient.newCall(request).execute();
            String responseBody = response.body().string();
            log.info("验证连接响应: {}", responseBody);

            if (!response.isSuccessful()) {
                log.error("验证连接失败: HTTP {}", response.code());
                return false;
            }

            JSONObject result = JSON.parseObject(responseBody);
            Integer code = result.getInteger("code");

            if (code != null && code != 0) {
                log.error("验证连接失败: API code={}", code);
                return false;
            }

            log.info("验证连接成功");
            return true;

        } catch (Exception e) {
            log.error("验证连接异常", e);
            return false;
        }
    }
}
