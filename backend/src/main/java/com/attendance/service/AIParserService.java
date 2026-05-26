package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.config.MimoProperties;
import com.attendance.service.MarkdownConfigService;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AIParserService {

    private static final Logger log = LoggerFactory.getLogger(AIParserService.class);

    @Autowired
    private MimoProperties mimoProperties;

    @Autowired
    private MarkdownConfigService markdownConfigService;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public interface ParseCallback {
        void onRecord(JSONObject record);
        void onComplete(int totalCount);
        void onError(Exception e);
    }

    public String saveUploadedFile(byte[] fileBytes, String originalFilename) throws IOException {
        String uploadPath = "./uploads";
        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String ext = originalFilename != null ? 
            originalFilename.substring(originalFilename.lastIndexOf(".")) : ".jpg";
        String filename = timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + ext;

        Path filePath = Paths.get(uploadPath, filename);
        Files.write(filePath, fileBytes);

        return filename;
    }

    public void parseImageStreamByLine(String base64Image, ParseCallback callback) {
        try {
            String aiPrompt = markdownConfigService.getAiPrompt();
            String continuePrompt = markdownConfigService.getContinuePrompt();
            
            if (aiPrompt == null || aiPrompt.trim().isEmpty()) {
                log.warn("AI提示词未配置，使用默认提示词");
                aiPrompt = "识别法国考勤表格，逐行返回单个 JSON 数组。规则：1.只返回真实数据，禁止编造 2.标记手写、模糊、正常 3.判断夜班 4.每一行就是一条记录，格式为：[NO,姓名,中介,班次,日期,到达,离开,休息,检查器,标记,已删除] 5.不要把所有记录包在一个大数组里！";
            }

            if (continuePrompt == null || continuePrompt.trim().isEmpty()) {
                continuePrompt = "请接续上文继续输出，不要重复已有内容，保持相同格式。";
            }

            parseImageStreamByLineWithConfig(base64Image, "image/jpeg", aiPrompt, continuePrompt, callback);
        } catch (Exception e) {
            log.error("AI解析初始化失败", e);
            callback.onError(e);
        }
    }
    
    public void parseImageStreamByLineFromBytes(byte[] imageBytes, String originalFilename, ParseCallback callback) {
        try {
            // 验证并处理图片
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = detectImageMimeType(imageBytes, originalFilename);
            log.info("准备上传图片到 Mimo，文件名: {}, MIME类型: {}, 大小: {} bytes", originalFilename, mimeType, imageBytes.length);
            
            String aiPrompt = markdownConfigService.getAiPrompt();
            String continuePrompt = markdownConfigService.getContinuePrompt();
            
            if (aiPrompt == null || aiPrompt.trim().isEmpty()) {
                log.warn("AI提示词未配置，使用默认提示词");
                aiPrompt = "识别法国考勤表格，逐行返回单个 JSON 数组。规则：1.只返回真实数据，禁止编造 2.标记手写、模糊、正常 3.判断夜班 4.每一行就是一条记录，格式为：[NO,姓名,中介,班次,日期,到达,离开,休息,检查器,标记,已删除] 5.不要把所有记录包在一个大数组里！";
            }

            if (continuePrompt == null || continuePrompt.trim().isEmpty()) {
                continuePrompt = "请接续上文继续输出，不要重复已有内容，保持相同格式。";
            }
            
            parseImageStreamByLineWithConfig(base64Image, mimeType, aiPrompt, continuePrompt, callback);
        } catch (Exception e) {
            log.error("AI解析初始化失败", e);
            callback.onError(e);
        }
    }

    private String detectImageMimeType(byte[] imageBytes, String filename) {
        // 首先尝试通过文件头检测
        if (imageBytes.length >= 8) {
            // JPEG: FF D8 FF
            if (imageBytes[0] == (byte)0xFF && imageBytes[1] == (byte)0xD8 && imageBytes[2] == (byte)0xFF) {
                return "image/jpeg";
            }
            // PNG: 89 50 4E 47 0D 0A 1A 0A
            if (imageBytes[0] == (byte)0x89 && imageBytes[1] == (byte)0x50 && imageBytes[2] == (byte)0x4E && 
                imageBytes[3] == (byte)0x47 && imageBytes[4] == (byte)0x0D && imageBytes[5] == (byte)0x0A && 
                imageBytes[6] == (byte)0x1A && imageBytes[7] == (byte)0x0A) {
                return "image/png";
            }
            // GIF: 47 49 46 38
            if (imageBytes[0] == (byte)0x47 && imageBytes[1] == (byte)0x49 && imageBytes[2] == (byte)0x46 && 
                imageBytes[3] == (byte)0x38) {
                return "image/gif";
            }
            // BMP: 42 4D
            if (imageBytes[0] == (byte)0x42 && imageBytes[1] == (byte)0x4D) {
                return "image/bmp";
            }
            // WebP: RIFF ... WEBP
            if (imageBytes.length >= 12 && 
                imageBytes[0] == (byte)0x52 && imageBytes[1] == (byte)0x49 && 
                imageBytes[2] == (byte)0x46 && imageBytes[3] == (byte)0x46 && 
                imageBytes[8] == (byte)0x57 && imageBytes[9] == (byte)0x45 && 
                imageBytes[10] == (byte)0x42 && imageBytes[11] == (byte)0x50) {
                return "image/webp";
            }
        }
        
        // 如果文件头检测失败，尝试通过文件扩展名检测
        if (filename != null) {
            String lowerFilename = filename.toLowerCase();
            if (lowerFilename.endsWith(".jpg") || lowerFilename.endsWith(".jpeg")) {
                return "image/jpeg";
            }
            if (lowerFilename.endsWith(".png")) {
                return "image/png";
            }
            if (lowerFilename.endsWith(".gif")) {
                return "image/gif";
            }
            if (lowerFilename.endsWith(".bmp")) {
                return "image/bmp";
            }
            if (lowerFilename.endsWith(".webp")) {
                return "image/webp";
            }
        }
        
        // 默认返回jpeg
        log.warn("无法检测图片类型，默认使用image/jpeg");
        return "image/jpeg";
    }



    private void parseImageStreamByLineWithConfig(String base64Image, String mimeType, String aiPrompt, 
                                                   String continuePrompt, ParseCallback callback) {
        try {
            List<JSONObject> extractedRecords = new ArrayList<>();
            Set<String> seenRecords = new HashSet<>();
            List<JSONObject> messages = new ArrayList<>();

            JSONObject firstUserMsg = new JSONObject();
            firstUserMsg.put("role", "user");
            
            JSONArray contentArray = new JSONArray();
            
            JSONObject textPart = new JSONObject();
            textPart.put("type", "text");
            textPart.put("text", aiPrompt);
            contentArray.add(textPart);
            
            JSONObject imagePart = new JSONObject();
            imagePart.put("type", "image_url");
            
            JSONObject imageUrlObj = new JSONObject();
            // 确保是纯 base64，不带 data URL 前缀
            String cleanBase64 = base64Image;
            if (cleanBase64.startsWith("data:")) {
                int commaIndex = cleanBase64.indexOf(",");
                if (commaIndex > 0) {
                    cleanBase64 = cleanBase64.substring(commaIndex + 1);
                }
            }
            imageUrlObj.put("url", "data:" + mimeType + ";base64," + cleanBase64);
            imagePart.put("image_url", imageUrlObj);
            contentArray.add(imagePart);
            
            firstUserMsg.put("content", contentArray);
            messages.add(firstUserMsg);

            boolean hasMore = true;
            int currentRound = 0;
            int maxRounds = 5;

            while (hasMore && currentRound < maxRounds) {
                currentRound++;
                log.info("🤖 调用 MiMo 流式 API - 第 " + currentRound + " 轮");

                final int currentRoundFinal = currentRound;

                String finishReason = callMiMoStream(messages, extractedRecords, seenRecords, callback);

                if ("stop".equals(finishReason)) {
                    hasMore = false;
                    break;
                } else if ("length".equals(finishReason)) {
                    log.warn("⚠️ 响应被截断，自动请求续写");
                    
                    JSONObject assistantMsg = new JSONObject();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", "");
                    messages.add(assistantMsg);

                    JSONObject continueUserMsg = new JSONObject();
                    continueUserMsg.put("role", "user");
                    continueUserMsg.put("content", continuePrompt);
                    messages.add(continueUserMsg);
                    
                    continue;
                } else {
                    hasMore = false;
                    break;
                }
            }

            log.info("✅ AI识别完全结束，共识别 {} 条记录", extractedRecords.size());
            callback.onComplete(extractedRecords.size());

        } catch (Exception e) {
            log.error("❌ MiMo API 调用失败", e);
            callback.onError(e);
        }
    }

    private String callMiMoStream(List<JSONObject> messages, List<JSONObject> extractedRecords,
                                  Set<String> seenRecords, ParseCallback callback) {
        String finishReason = null;
        try {
            String apiKey = mimoProperties.getApiKey();
            String apiUrl = mimoProperties.getApiUrl();
            
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("MIMO_API_KEY 未配置");
            }

            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "mimo-v2-omni");
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 8192);
            requestBody.put("stream", true);
            requestBody.put("temperature", 0.7);

            Request request = new Request.Builder()
                    .url(apiUrl + "/chat/completions")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(RequestBody.create(requestBody.toJSONString(), 
                            MediaType.parse("application/json")))
                    .build();

            Response response = client.newCall(request).execute();
            ResponseBody responseBody = response.body();
            
            if (!response.isSuccessful()) {
                String errorContent = "";
                if (responseBody != null) {
                    try {
                        errorContent = responseBody.string();
                    } catch (IOException e) {
                        errorContent = "(无法读取错误响应)";
                    }
                }
                log.error("❌ Mimo API 请求失败！状态码: {}, 错误内容: {}", response.code(), errorContent);
                throw new IOException("API请求失败: " + response + " - 错误: " + errorContent);
            }

            if (responseBody == null) {
                throw new IOException("响应为空");
            }

            InputStream inputStream = responseBody.byteStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            StringBuffer fullText = new StringBuffer();
            StringBuffer roundText = new StringBuffer();
            StringBuffer accumulatedText = new StringBuffer();
            int lastScanPos = 0;
            int lastPushedCount = 0;

            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data: ")) continue;
                String data = line.substring(6);

                if (data.trim().equals("[DONE]")) {
                    finishReason = "stop";
                    break;
                }

                try {
                    JSONObject parsed = JSON.parseObject(data);
                    JSONArray choices = parsed.getJSONArray("choices");
                    if (choices != null && choices.size() > 0) {
                        JSONObject choice = choices.getJSONObject(0);
                        
                        if (choice.containsKey("finish_reason") && 
                            choice.get("finish_reason") != null) {
                            finishReason = choice.getString("finish_reason");
                        }

                        JSONObject delta = choice.getJSONObject("delta");
                        if (delta != null && delta.containsKey("content")) {
                            String deltaContent = delta.getString("content");
                            if (deltaContent != null) {
                                roundText.append(deltaContent);
                                fullText.append(deltaContent);
                                accumulatedText.append(deltaContent);

                                int newScanPos = tryParseAndPushRecords(
                                    accumulatedText.toString(), 
                                    extractedRecords, 
                                    lastPushedCount, 
                                    callback, 
                                    lastScanPos, 
                                    seenRecords
                                );
                                lastScanPos = newScanPos;
                                lastPushedCount = extractedRecords.size();
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("⚠️ 解析 SSE 数据失败", e);
                }
            }

            log.info("📦 本轮完成，获取 {} 字符", roundText.length());

            int finalScanPos = tryParseAndPushRecords(
                accumulatedText.toString(), 
                extractedRecords, 
                lastPushedCount, 
                callback, 
                lastScanPos, 
                seenRecords
            );

        } catch (Exception e) {
            log.error("调用 MiMo API 失败", e);
            throw new RuntimeException(e);
        }
        return finishReason;
    }

    private int tryParseAndPushRecords(String accumulatedText, List<JSONObject> extractedRecords,
                                       int lastPushedCount, ParseCallback callback,
                                       int lastScanPos, Set<String> seenRecords) {
        int pos = lastScanPos;
        int maxRecordEnd = lastScanPos;

        while (true) {
            int recordStart = accumulatedText.indexOf('[', pos);
            if (recordStart == -1) break;

            int recordEnd = findMatchingBracket(accumulatedText, recordStart);
            if (recordEnd == -1) break;

            maxRecordEnd = Math.max(maxRecordEnd, recordEnd + 1);

            String recordStr = accumulatedText.substring(recordStart, recordEnd + 1);

            try {
                Object parsed = JSON.parse(recordStr);
                if (parsed instanceof JSONArray) {
                    JSONArray itemArray = (JSONArray) parsed;
                    if (itemArray.size() >= 2) {
                        String itemNo = itemArray.size() > 0 ? 
                            String.valueOf(itemArray.get(0)) : "";
                        String itemName = itemArray.size() > 1 ? 
                            String.valueOf(itemArray.get(1)) : "";
                        String recordKey = itemNo + "|" + itemName;

                        if (!seenRecords.contains(recordKey)) {
                            JSONObject normalized = normalizeRecord(itemArray);
                            extractedRecords.add(normalized);
                            seenRecords.add(recordKey);

                            log.info("✅ 找到新记录 #{}: {} - {}", 
                                extractedRecords.size(), normalized.getString("NO"), 
                                normalized.getString("NOM_PRENOM"));

                            callback.onRecord(normalized);
                        }
                    }
                }
            } catch (Exception e) {
                // 单条记录解析失败，继续下一个
            }

            pos = recordEnd + 1;
        }

        if (extractedRecords.isEmpty()) {
            parseAsWholeArray(accumulatedText, extractedRecords, lastPushedCount, callback, seenRecords);
        }

        return maxRecordEnd;
    }

    private void parseAsWholeArray(String accumulatedText, List<JSONObject> extractedRecords,
                                   int lastPushedCount, ParseCallback callback, Set<String> seenRecords) {
        int startIdx = accumulatedText.indexOf('[');
        int endIdx = accumulatedText.lastIndexOf(']');

        if (startIdx != -1 && endIdx != -1 && endIdx > startIdx) {
            String jsonStr = accumulatedText.substring(startIdx, endIdx + 1);
            try {
                Object parsed = JSON.parse(jsonStr);
                if (parsed instanceof JSONArray) {
                    JSONArray arr = (JSONArray) parsed;
                    for (int i = lastPushedCount; i < arr.size(); i++) {
                        Object item = arr.get(i);
                        if (item instanceof JSONArray) {
                            JSONArray itemArray = (JSONArray) item;
                            String itemNo = itemArray.size() > 0 ? 
                                String.valueOf(itemArray.get(0)) : "";
                            String itemName = itemArray.size() > 1 ? 
                                String.valueOf(itemArray.get(1)) : "";
                            String recordKey = itemNo + "|" + itemName;

                            if (!seenRecords.contains(recordKey)) {
                                JSONObject normalized = normalizeRecord(itemArray);
                                extractedRecords.add(normalized);
                                seenRecords.add(recordKey);

                                log.info("✅ 找到新记录 #{}: {} - {}", 
                                    extractedRecords.size(), normalized.getString("NO"), 
                                    normalized.getString("NOM_PRENOM"));

                                callback.onRecord(normalized);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // 完整数组解析失败，忽略
            }
        }
    }

    private int findMatchingBracket(String text, int startPos) {
        int depth = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = startPos; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escapeNext) {
                escapeNext = false;
                continue;
            }
            if (c == '\\') {
                escapeNext = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (!inString) {
                if (c == '[') depth++;
                if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private JSONObject normalizeRecord(JSONArray record) {
        JSONObject normalized = new JSONObject();
        normalized.put("NO", record.size() > 0 ? record.get(0) : "");
        normalized.put("NOM_PRENOM", record.size() > 1 ? record.get(1) : "");
        normalized.put("AGENCE_INTERIMAIRE", record.size() > 2 ? record.get(2) : "");
        normalized.put("HORAIRES_DU_TRAVAIL", record.size() > 3 ? record.get(3) : "");
        normalized.put("Date", record.size() > 4 ? normalizeDate(String.valueOf(record.get(4))) : "");
        normalized.put("ARRIVEE", record.size() > 5 ? record.get(5) : "");
        normalized.put("DEPAR", record.size() > 6 ? record.get(6) : "");
        normalized.put("PAUSE", record.size() > 7 ? record.get(7) : "");
        normalized.put("CHECKER", record.size() > 8 ? record.get(8) : "");
        normalized.put("Mark", record.size() > 9 ? record.get(9) : "");
        normalized.put("isDeleted", record.size() > 10 ? record.get(10) : false);

        JSONObject anomalies = detectAnomalies(normalized);
        normalized.put("isDeleted", anomalies.getBoolean("isDeleted"));
        normalized.put("riskLevel", anomalies.getString("riskLevel"));
        normalized.put("anomalies", anomalies.getJSONArray("anomalies"));

        normalized.put("SmartMark", generateSmartMark(normalized));

        String baseDate = normalized.getString("Date");
        String arrive = normalized.getString("ARRIVEE");
        String depart = normalized.getString("DEPAR");

        if (baseDate != null && !baseDate.isEmpty() && 
            arrive != null && !arrive.isEmpty() && 
            depart != null && !depart.isEmpty()) {
            String normalizedArrive = normalizeTime(arrive);
            String normalizedDepart = normalizeTime(depart);

            int arriveHour = Integer.parseInt(normalizedArrive.split(":")[0]);
            int departHour = Integer.parseInt(normalizedDepart.split(":")[0]);

            if (arriveHour >= 18 && departHour <= 12) {
                normalized.put("ARRIVEE_DATE", baseDate);
                normalized.put("DEPAR_DATE", addDays(baseDate, 1));
            } else {
                normalized.put("ARRIVEE_DATE", baseDate);
                normalized.put("DEPAR_DATE", baseDate);
            }

            String arriveDateStr = normalized.getString("ARRIVEE_DATE");
            String departDateStr = normalized.getString("DEPAR_DATE");

            if (arriveDateStr != null) {
                normalized.put("ARRIVEE_DATETIME", arriveDateStr + " " + normalizedArrive);
            }
            if (departDateStr != null) {
                normalized.put("DEPAR_DATETIME", departDateStr + " " + normalizedDepart);
            }
        }

        return normalized;
    }

    private String normalizeDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return dateStr;
        }
        String str = dateStr.trim();

        if (str.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return str;
        }

        Pattern pattern1 = Pattern.compile("^(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})$");
        Matcher matcher1 = pattern1.matcher(str);
        if (matcher1.matches()) {
            String year = matcher1.group(1);
            String month = String.format("%02d", Integer.parseInt(matcher1.group(2)));
            String day = String.format("%02d", Integer.parseInt(matcher1.group(3)));
            return year + "-" + month + "-" + day;
        }

        Pattern pattern2 = Pattern.compile("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})$");
        Matcher matcher2 = pattern2.matcher(str);
        if (matcher2.matches()) {
            String part1 = matcher2.group(1);
            String part2 = matcher2.group(2);
            String year = matcher2.group(3);
            String month, day;
            if (Integer.parseInt(part1) > 12) {
                day = String.format("%02d", Integer.parseInt(part1));
                month = String.format("%02d", Integer.parseInt(part2));
            } else {
                month = String.format("%02d", Integer.parseInt(part1));
                day = String.format("%02d", Integer.parseInt(part2));
            }
            return year + "-" + month + "-" + day;
        }

        Pattern pattern3 = Pattern.compile("^(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2})$");
        Matcher matcher3 = pattern3.matcher(str);
        if (matcher3.matches()) {
            String part1 = matcher3.group(1);
            String part2 = matcher3.group(2);
            String year = "20" + matcher3.group(3);
            String month, day;
            if (Integer.parseInt(part1) > 12) {
                day = String.format("%02d", Integer.parseInt(part1));
                month = String.format("%02d", Integer.parseInt(part2));
            } else {
                month = String.format("%02d", Integer.parseInt(part1));
                day = String.format("%02d", Integer.parseInt(part2));
            }
            return year + "-" + month + "-" + day;
        }

        return str;
    }

    private String normalizeTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return timeStr;
        }
        String str = timeStr.trim();

        if (str.matches("\\d{1,2}:\\d{2}")) {
            String[] parts = str.split(":");
            String hour = String.format("%02d", Integer.parseInt(parts[0]));
            return hour + ":" + parts[1];
        }

        Pattern patternH = Pattern.compile("^(\\d{1,2})[hH]$");
        Matcher matcherH = patternH.matcher(str);
        if (matcherH.matches()) {
            String hour = String.format("%02d", Integer.parseInt(matcherH.group(1)));
            return hour + ":00";
        }

        Pattern patternHM = Pattern.compile("^(\\d{1,2})[hH](\\d{1,2})$");
        Matcher matcherHM = patternHM.matcher(str);
        if (matcherHM.matches()) {
            String hour = String.format("%02d", Integer.parseInt(matcherHM.group(1)));
            String minute = String.format("%02d", Integer.parseInt(matcherHM.group(2)));
            return hour + ":" + minute;
        }

        Pattern patternComma = Pattern.compile("^(\\d{1,2})[,.](\\d{1,2})$");
        Matcher matcherComma = patternComma.matcher(str);
        if (matcherComma.matches()) {
            String hour = String.format("%02d", Integer.parseInt(matcherComma.group(1)));
            String minute = String.format("%02d", Integer.parseInt(matcherComma.group(2)));
            return hour + ":" + minute;
        }

        Pattern pattern4Digits = Pattern.compile("^(\\d{2})(\\d{2})$");
        Matcher matcher4Digits = pattern4Digits.matcher(str);
        if (matcher4Digits.matches()) {
            return matcher4Digits.group(1) + ":" + matcher4Digits.group(2);
        }

        Pattern pattern1Digit = Pattern.compile("^(\\d)$");
        Matcher matcher1Digit = pattern1Digit.matcher(str);
        if (matcher1Digit.matches()) {
            String hour = String.format("%02d", Integer.parseInt(matcher1Digit.group(1)));
            return hour + ":00";
        }

        return str;
    }

    private String addDays(String dateStr, int days) {
        if (dateStr == null) return dateStr;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            Calendar cal = Calendar.getInstance();
            cal.setTime(sdf.parse(dateStr));
            cal.add(Calendar.DAY_OF_MONTH, days);
            return sdf.format(cal.getTime());
        } catch (Exception e) {
            log.error("日期计算失败", e);
            return dateStr;
        }
    }

    private JSONObject detectAnomalies(JSONObject record) {
        JSONObject result = new JSONObject();
        JSONArray anomalies = new JSONArray();
        String riskLevel = "none";

        Map<String, String> fieldNames = new HashMap<>();
        fieldNames.put("NO", "工号未识别");
        fieldNames.put("Date", "日期未识别");
        fieldNames.put("ARRIVEE", "到达时间未识别");
        fieldNames.put("DEPAR", "离开时间未识别");
        fieldNames.put("PAUSE", "休息时间未识别");

        List<String> requiredFields = Arrays.asList("NO", "Date", "ARRIVEE", "DEPAR", "PAUSE");

        for (String field : requiredFields) {
            Object value = record.get(field);
            String valueStr = value == null ? "" : String.valueOf(value);
            if (value == null || valueStr.trim().isEmpty() || "null".equals(valueStr)) {
                anomalies.add(fieldNames.getOrDefault(field, field + "缺失"));
            }
        }

        boolean isDeleted = record.getBooleanValue("isDeleted");
        result.put("isDeleted", isDeleted);

        if (isDeleted) {
            riskLevel = "high";
            anomalies.add("记录已删除");
        } else if (anomalies.size() > 0) {
            riskLevel = "high";
        }

        result.put("anomalies", anomalies);
        result.put("riskLevel", riskLevel);

        return result;
    }

    private String detectShiftType(String arriveTime, String departTime) {
        String normalizedArrive = normalizeTime(arriveTime);
        String normalizedDepart = normalizeTime(departTime);
        if (normalizedArrive == null || normalizedDepart == null) return null;

        try {
            int arriveHour = Integer.parseInt(normalizedArrive.split(":")[0]);
            int departHour = Integer.parseInt(normalizedDepart.split(":")[0]);

            if (arriveHour >= 20 || departHour < 6) {
                return "夜班";
            }
            if (arriveHour >= 6 && arriveHour < 12 && departHour >= 12 && departHour < 18) {
                return "早班";
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private JSONObject detectRecordQuality(JSONObject record) {
        JSONObject quality = new JSONObject();
        quality.put("isHandwritten", false);
        quality.put("isBlurry", false);
        quality.put("isNormal", true);
        quality.put("qualityNotes", new JSONArray());

        String text = (record.getString("NOM_PRENOM") != null ? record.getString("NOM_PRENOM") : "") + " " +
                      (record.getString("NO") != null ? record.getString("NO") : "") + " " +
                      (record.getString("AGENCE_INTERIMAIRE") != null ? record.getString("AGENCE_INTERIMAIRE") : "") + " " +
                      (record.getString("Mark") != null ? record.getString("Mark") : "");
        text = text.toLowerCase();

        List<String> blurryPatterns = Arrays.asList("???", "模糊", "不清楚", "illisible", "inconnu", "non visible", "efface");
        for (String pattern : blurryPatterns) {
            if (text.contains(pattern)) {
                quality.put("isBlurry", true);
                quality.put("isNormal", false);
                quality.getJSONArray("qualityNotes").add("内容模糊或无法识别");
                break;
            }
        }

        List<String> handwrittenPatterns = Arrays.asList("手写", "handwritten", "ecrit main", "manuscrit");
        for (String pattern : handwrittenPatterns) {
            if (text.contains(pattern)) {
                quality.put("isHandwritten", true);
                quality.put("isNormal", false);
                quality.getJSONArray("qualityNotes").add("手写内容");
                break;
            }
        }

        return quality;
    }

    private String generateSmartMark(JSONObject record) {
        // 如果记录被标记为已删除，只返回"已删除"，不影响其他规则
        if (record.containsKey("isDeleted") && record.getBooleanValue("isDeleted")) {
            return "已删除";
        }

        if (record.containsKey("Mark") && record.getString("Mark") != null && 
            !record.getString("Mark").trim().isEmpty()) {
            return record.getString("Mark").trim();
        }

        String arriveTime = record.getString("ARRIVEE");
        String departTime = record.getString("DEPAR");
        boolean isArriveEmpty = arriveTime == null || arriveTime.trim().isEmpty() || "???".equals(arriveTime.trim());
        boolean isDepartEmpty = departTime == null || departTime.trim().isEmpty() || "???".equals(departTime.trim());
        boolean isAbsent = isArriveEmpty && isDepartEmpty;

        if (isAbsent) {
            return "未出勤";
        }

        JSONObject quality = detectRecordQuality(record);
        String qualityMark;
        if (quality.getBooleanValue("isHandwritten")) {
            qualityMark = "手写";
        } else if (quality.getBooleanValue("isBlurry")) {
            qualityMark = "模糊";
        } else {
            qualityMark = "正常";
        }

        String shiftType = detectShiftType(arriveTime, departTime);
        if ("夜班".equals(shiftType)) {
            return qualityMark + ";夜班";
        }

        return qualityMark;
    }
}
