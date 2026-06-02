package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.common.BusinessException;
import com.attendance.common.ErrorKeys;
import com.attendance.common.ErrorCode;
import com.attendance.config.MimoProperties;
import com.attendance.service.MarkdownConfigService;
import com.attendance.util.RecordCountryDefaults;
import com.attendance.util.RecognizedFieldSanitizer;
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
import java.util.Locale;

@Service
public class AIParserService {

    private static final Logger log = LoggerFactory.getLogger(AIParserService.class);
    private static final String UPLOAD_DIR = "./uploads";
    private static final Pattern UPLOAD_SERIAL_PATTERN = Pattern.compile("^(\\d{8})_(\\d{4})\\.[^.]+$");
    private static final String HANDWRITING_MARK_RULE = "\n\n【强制输出结构与标记规则】\n"
            + "- 必须按新表头顺序逐行返回单个 JSON 数组：[NO,Pays,Entrepot,Date,NOM_PRENOM,AGENCE_INTERIMAIRE,HORAIRES_DU_TRAVAIL,ARRIVEE,DEPAR,PAUSE,SIGNATURE,Observations,标记,已删除]。\n"
            + "- 表头语言可能是中文、法语、荷兰语或意大利语，但字段顺序一致；上述列名仅用于理解含义，禁止作为单元格数据输出。\n"
            + "- 国家/Pays/Country/Paese 输出到 Pays；仓库/Entrepôt/Warehouse/Magazzino 输出到 Entrepot；员工签名/SIGNATURE/Signature/Firma 输出到 SIGNATURE；备注/Observations/Remarks/Osservazioni 输出到 Observations。\n"
            + "- Entrepot 只能从图片读取，未识别到或看不清时必须留空，禁止猜测或套用示例仓库代码。\n"
            + "- 必须逐行观察工号(NO)和姓名(NOM_PRENOM)两个单元格的视觉笔迹。\n"
            + "- 只要工号或姓名任一单元格是手写笔迹，第13个字段标记必须包含\"手写\"，不得输出\"正常\"或\"正常;夜班\"。\n"
            + "- 手写与夜班同时存在时输出\"手写;夜班\"；手写与模糊同时存在时输出\"手写;模糊\"。\n"
            + "- 只有工号和姓名两列都不是手写、也不是模糊/未出勤时，标记才允许为\"正常\"。\n"
            + "- 休息字段(PAUSE)必须只输出分钟数值，不带单位；30min、30mn、0h30、00:30、30 minutes 都输出 30。";

    @Autowired
    private MimoProperties mimoProperties;

    @Autowired
    private MarkdownConfigService markdownConfigService;

    @Autowired
    private RecognitionPromptGuard recognitionPromptGuard;

    @Autowired
    private RecognitionQualityGuard recognitionQualityGuard;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    /** 最近一次 MiMo 原始文本，便于 0 条时排查 */
    private volatile String lastRecognitionRawText = "";
    private volatile String lastPromptCountry = "default";
    private final ThreadLocal<String> workingCountryForPays = new ThreadLocal<>();
    private volatile String lastPromptSection = "";

    private final Object uploadNameLock = new Object();
    private String uploadSerialDate = "";
    private int uploadSerialCounter = 0;

    public interface ParseCallback {
        void onRecord(JSONObject record);
        void onComplete(int totalCount);
        void onError(Exception e);
    }

    public String saveUploadedFile(byte[] fileBytes, String originalFilename) throws IOException {
        File dir = new File(UPLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String ext = resolveUploadExtension(originalFilename);
        String filename = allocateUploadFilename(ext);

        Path filePath = Paths.get(UPLOAD_DIR, filename);
        Files.write(filePath, fileBytes);
        log.info("上传图片已保存: {}", filename);

        return filename;
    }

    private String allocateUploadFilename(String ext) throws IOException {
        String datePrefix = new SimpleDateFormat("yyyyMMdd").format(new Date());
        synchronized (uploadNameLock) {
            if (!datePrefix.equals(uploadSerialDate)) {
                uploadSerialDate = datePrefix;
                uploadSerialCounter = scanMaxUploadSerialForDate(datePrefix);
            }
            while (uploadSerialCounter >= 9999) {
                throw new IOException("当日上传流水号已达上限（9999）");
            }
            uploadSerialCounter++;
            String filename = String.format(Locale.ROOT, "%s_%04d%s", datePrefix, uploadSerialCounter, ext);
            Path target = Paths.get(UPLOAD_DIR, filename);
            if (Files.exists(target)) {
                uploadSerialCounter = scanMaxUploadSerialForDate(datePrefix);
                uploadSerialCounter++;
                filename = String.format(Locale.ROOT, "%s_%04d%s", datePrefix, uploadSerialCounter, ext);
                target = Paths.get(UPLOAD_DIR, filename);
                if (Files.exists(target)) {
                    throw new IOException("上传文件名冲突: " + filename);
                }
            }
            return filename;
        }
    }

    private int scanMaxUploadSerialForDate(String datePrefix) {
        File dir = new File(UPLOAD_DIR);
        if (!dir.isDirectory()) {
            return 0;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        int max = 0;
        for (File file : files) {
            Matcher matcher = UPLOAD_SERIAL_PATTERN.matcher(file.getName());
            if (!matcher.matches() || !datePrefix.equals(matcher.group(1))) {
                continue;
            }
            int serial = Integer.parseInt(matcher.group(2));
            if (serial > max) {
                max = serial;
            }
        }
        return max;
    }

    private String resolveUploadExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return ".jpg";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot >= originalFilename.length() - 1) {
            return ".jpg";
        }
        String ext = originalFilename.substring(dot).toLowerCase(Locale.ROOT);
        if (ext.length() > 8 || ext.contains("/") || ext.contains("\\")) {
            return ".jpg";
        }
        return ext;
    }

    public void parseImageStreamByLine(String base64Image, ParseCallback callback) {
        parseImageStreamByLine(base64Image, null, callback);
    }

    public void parseImageStreamByLine(String base64Image, String country, ParseCallback callback) {
        try {
            PromptBundle prompts = loadRecognitionPrompts(country);
            parseImageStreamByLineWithConfig(base64Image, "image/jpeg", prompts.aiPrompt, prompts.continuePrompt, callback);
        } catch (Exception e) {
            log.error("AI解析初始化失败", e);
            callback.onError(e);
        }
    }
    
    public void parseImageStreamByLineFromBytes(byte[] imageBytes, String originalFilename, ParseCallback callback) {
        parseImageStreamByLineFromBytes(imageBytes, originalFilename, null, callback);
    }

    public void parseImageStreamByLineFromBytes(byte[] imageBytes, String originalFilename, String country, ParseCallback callback) {
        parseImageStreamByLineFromBytes(imageBytes, originalFilename, country, country, callback, null);
    }

    public void parseImageStreamByLineFromBytes(byte[] imageBytes, String originalFilename, String country,
                                                ParseCallback callback, RecognitionTrace trace) {
        parseImageStreamByLineFromBytes(imageBytes, originalFilename, country, country, callback, trace);
    }

    public void parseImageStreamByLineFromBytes(byte[] imageBytes, String originalFilename, String promptCountry,
                                                String workingCountry, ParseCallback callback, RecognitionTrace trace) {
        try {
            if (imageBytes != null && imageBytes.length >= 5
                    && imageBytes[0] == '%' && imageBytes[1] == 'P' && imageBytes[2] == 'D' && imageBytes[3] == 'F') {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR, ErrorKeys.PDF_CONVERT_FAILED);
            }
            workingCountryForPays.set(workingCountry != null && !workingCountry.isBlank()
                    ? workingCountry.trim().toUpperCase()
                    : resolveCountry(promptCountry));
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = detectImageMimeType(imageBytes, originalFilename);
            log.info("准备上传图片到 Mimo，文件名: {}, MIME类型: {}, 大小: {} bytes", originalFilename, mimeType, imageBytes.length);

            PromptBundle prompts = loadRecognitionPrompts(promptCountry, trace);
            if (trace != null) {
                JSONObject req = new JSONObject();
                req.put("mimeType", mimeType);
                req.put("imageBytes", imageBytes.length);
                req.put("base64Chars", base64Image.length());
                req.put("dataUrlPrefix", "data:" + mimeType + ";base64,");
                req.put("model", mimoProperties.getModel());
                req.put("apiUrl", mimoProperties.getApiUrl());
                req.put("temperature", mimoProperties.getTemperature());
                req.put("maxTokens", mimoProperties.getMaxTokens());
                req.put("topP", mimoProperties.getTopP());
                req.put("promptCountry", lastPromptCountry);
                req.put("promptSection", lastPromptSection);
                req.put("promptLength", prompts.aiPrompt.length());
                req.put("promptPreview", RecognitionTrace.preview(prompts.aiPrompt, 800));
                req.put("promptFull", prompts.aiPrompt);
                req.put("continuePromptLength", prompts.continuePrompt.length());
                req.put("continuePromptFull", prompts.continuePrompt);
                req.put("messageHasImagePart", true);
                trace.step("model_request", req);
            }
            parseImageStreamByLineWithConfig(base64Image, mimeType, prompts.aiPrompt, prompts.continuePrompt, callback, trace);
        } catch (Exception e) {
            log.error("AI解析初始化失败", e);
            if (trace != null) {
                trace.step("model_error", "message", e.getMessage());
            }
            callback.onError(e);
        } finally {
            workingCountryForPays.remove();
        }
    }

    private String resolveCountry(String country) {
        if (country != null && !country.isBlank()) {
            return country.trim().toUpperCase();
        }
        String current = markdownConfigService.getCurrentCountry();
        return (current != null && !current.isBlank()) ? current.toUpperCase() : "default";
    }

    private static final class PromptBundle {
        final String aiPrompt;
        final String continuePrompt;

        PromptBundle(String aiPrompt, String continuePrompt) {
            this.aiPrompt = aiPrompt;
            this.continuePrompt = continuePrompt;
        }
    }

    public String getLastPromptCountry() {
        return lastPromptCountry;
    }

    public String getLastPromptSection() {
        return lastPromptSection;
    }

    private PromptBundle loadRecognitionPrompts(String country) {
        return loadRecognitionPrompts(country, null);
    }

    private PromptBundle loadRecognitionPrompts(String country, RecognitionTrace trace) {
        String requestedCountry = resolveCountry(country);
        String configCountry = markdownConfigService.resolveEffectiveCountry(requestedCountry);
        String aiPrompt = markdownConfigService.getAiPrompt(configCountry);
        String continuePrompt = markdownConfigService.getContinuePrompt(configCountry);

        if (aiPrompt == null || aiPrompt.isBlank()) {
            log.warn("国家 {} 提示词为空，回退 default", configCountry);
            configCountry = "default";
            aiPrompt = markdownConfigService.getAiPrompt("default");
            continuePrompt = markdownConfigService.getContinuePrompt("default");
        }
        if (continuePrompt == null || continuePrompt.isBlank()) {
            continuePrompt = markdownConfigService.getContinuePrompt("default");
        }

        if (aiPrompt == null || aiPrompt.isBlank()) {
            throw new BusinessException(ErrorCode.AI_PARSE_ERROR, ErrorKeys.AI_PROMPT_NOT_FOUND);
        }
        if (continuePrompt == null || continuePrompt.isBlank()) {
            throw new BusinessException(ErrorCode.AI_PARSE_ERROR, ErrorKeys.AI_CONTINUE_PROMPT_NOT_FOUND);
        }

        String apiPrompt = recognitionPromptGuard.preparePromptForApi(aiPrompt, recognitionQualityGuard)
                + HANDWRITING_MARK_RULE;
        continuePrompt = continuePrompt.trim() + HANDWRITING_MARK_RULE;
        String section = markdownConfigService.describePromptSection(configCountry);
        log.info("AI识别提示词: requestCountry={}, effectiveCountry={}, section={}, configLen={}, apiLen={}",
                requestedCountry, configCountry, section, aiPrompt.length(), apiPrompt.length());
        log.info("提示词摘要: {}", apiPrompt.substring(0, Math.min(120, apiPrompt.length())).replace('\n', ' '));

        lastPromptCountry = configCountry;
        lastPromptSection = section;
        return new PromptBundle(apiPrompt.trim(), continuePrompt.trim());
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
        parseImageStreamByLineWithConfig(base64Image, mimeType, aiPrompt, continuePrompt, callback, null);
    }

    private void parseImageStreamByLineWithConfig(String base64Image, String mimeType, String aiPrompt,
                                                   String continuePrompt, ParseCallback callback,
                                                   RecognitionTrace trace) {
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
            StreamRoundOutcome roundOutcome = null;

            while (hasMore && currentRound < maxRounds) {
                currentRound++;
                log.info("🤖 调用 MiMo 流式 API - 第 " + currentRound + " 轮");
                if (trace != null) {
                    trace.step("model_round_start", "round", currentRound);
                }

                roundOutcome = callMiMoStream(
                        messages, extractedRecords, seenRecords, callback, trace, currentRound);
                String finishReason = roundOutcome.finishReason;

                if (extractedRecords.isEmpty()) {
                    flushExtractAllRecords(lastRecognitionRawText, extractedRecords, seenRecords, callback);
                }

                if ("stop".equals(finishReason)) {
                    hasMore = false;
                    break;
                } else if ("length".equals(finishReason)) {
                    log.warn("⚠️ 响应被截断，自动请求续写（已保留本轮 {} 字符）",
                            roundOutcome != null ? roundOutcome.roundText.length() : 0);

                    JSONObject assistantMsg = new JSONObject();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", roundOutcome != null ? roundOutcome.roundText : "");
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

            if (extractedRecords.isEmpty()) {
                String preview = truncateForLog(lastRecognitionRawText, 500);
                log.error("❌ 模型未返回可解析记录，原始回复({}字): {}", 
                        lastRecognitionRawText != null ? lastRecognitionRawText.length() : 0, preview);
                if (trace != null) {
                    JSONObject empty = new JSONObject();
                    empty.put("rawLength", lastRecognitionRawText != null ? lastRecognitionRawText.length() : 0);
                    empty.put("rawPreview", RecognitionTrace.preview(lastRecognitionRawText, 2000));
                    empty.put("rawFull", lastRecognitionRawText != null ? lastRecognitionRawText : "");
                    trace.step("model_response_empty", empty);
                }
                BusinessException parseError = buildEmptyParseException(lastRecognitionRawText, preview);
                callback.onError(parseError);
                return;
            }

            if (trace != null) {
                JSONObject resp = new JSONObject();
                resp.put("rawLength", lastRecognitionRawText != null ? lastRecognitionRawText.length() : 0);
                resp.put("rawPreview", RecognitionTrace.preview(lastRecognitionRawText, 2000));
                resp.put("rawFull", lastRecognitionRawText != null ? lastRecognitionRawText : "");
                resp.put("parsedRecordCount", extractedRecords.size());
                trace.step("model_response", resp);
            }

            if (recognitionQualityGuard.looksFabricated(extractedRecords)) {
                log.warn("拒绝疑似编造结果");
                if (trace != null) {
                    trace.step("fabricated_rejected", "messageKey", ErrorKeys.AI_FABRICATED);
                }
                callback.onError(new BusinessException(ErrorCode.AI_PARSE_ERROR, ErrorKeys.AI_FABRICATED));
                return;
            }

            if (recognitionQualityGuard.looksUnreadableWithGuessedTimes(extractedRecords)) {
                log.warn("拒绝低质量读图结果");
                if (trace != null) {
                    trace.step("low_quality_rejected", "messageKey", ErrorKeys.AI_UNREADABLE_TIMES);
                }
                callback.onError(new BusinessException(ErrorCode.AI_PARSE_ERROR, ErrorKeys.AI_UNREADABLE_TIMES));
                return;
            }

            log.info("✅ AI识别完全结束，共识别 {} 条记录", extractedRecords.size());
            callback.onComplete(extractedRecords.size());

        } catch (Exception e) {
            log.error("❌ MiMo API 调用失败", e);
            if (trace != null) {
                trace.step("model_error", "message", e.getMessage());
            }
            callback.onError(e);
        }
    }

    public String getLastRecognitionRawText() {
        return lastRecognitionRawText != null ? lastRecognitionRawText : "";
    }

    private BusinessException buildEmptyParseException(String raw, String preview) {
        if (recognitionPromptGuard.looksLikeHeaderEcho(raw)) {
            return new BusinessException(ErrorCode.AI_PARSE_ERROR, ErrorKeys.AI_HEADER_ECHO);
        }
        if (raw != null && raw.contains("[") && !raw.contains("\"")) {
            return new BusinessException(ErrorCode.AI_PARSE_ERROR, ErrorKeys.AI_INVALID_JSON, Map.of("preview", preview));
        }
        return new BusinessException(ErrorCode.AI_PARSE_ERROR, ErrorKeys.AI_NO_PARSEABLE_RECORDS, Map.of("preview", preview));
    }

    private static String truncateForLog(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replace('\n', ' ').trim();
        return oneLine.length() <= maxLen ? oneLine : oneLine.substring(0, maxLen) + "...";
    }

    private static final class StreamRoundOutcome {
        final String finishReason;
        final String roundText;

        StreamRoundOutcome(String finishReason, String roundText) {
            this.finishReason = finishReason;
            this.roundText = roundText != null ? roundText : "";
        }
    }

    private StreamRoundOutcome callMiMoStream(List<JSONObject> messages, List<JSONObject> extractedRecords,
                                  Set<String> seenRecords, ParseCallback callback,
                                  RecognitionTrace trace, int round) {
        String finishReason = null;
        String roundText = "";
        try {
            String apiKey = mimoProperties.getApiKey();
            String apiUrl = mimoProperties.getApiUrl();
            
            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException("MIMO_API_KEY 未配置");
            }

            JSONObject requestBody = new JSONObject();
            String model = mimoProperties.getModel();
            if (model == null || model.isBlank()) {
                model = "mimo-v2.5";
            }
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", mimoProperties.getMaxTokens());
            requestBody.put("stream", true);
            requestBody.put("temperature", mimoProperties.getTemperature());
            requestBody.put("top_p", mimoProperties.getTopP());
            JSONObject thinking = new JSONObject();
            thinking.put("type", "disabled");
            requestBody.put("thinking", thinking);

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

            StringBuilder accumulatedText = new StringBuilder();
            final int[] lastScanPos = {0};
            final int[] lastPushedCount = {0};

            MimoStreamParser.RoundResult streamResult = MimoStreamParser.consume(
                    responseBody.byteStream(),
                    delta -> {
                        accumulatedText.append(delta);
                        int newScanPos = tryParseAndPushRecords(
                                accumulatedText.toString(),
                                extractedRecords,
                                lastPushedCount[0],
                                callback,
                                lastScanPos[0],
                                seenRecords);
                        lastScanPos[0] = newScanPos;
                        lastPushedCount[0] = extractedRecords.size();
                    });

            roundText = streamResult.getRoundText();
            finishReason = streamResult.getFinishReason();
            if (finishReason == null) {
                finishReason = roundText.isEmpty() ? null : "stop";
            }

            lastRecognitionRawText = accumulatedText.toString();
            log.info("📦 本轮完成 finishReason={}, 本轮 {} 字符，累计 {} 字符",
                    finishReason, roundText.length(), accumulatedText.length());
            if (extractedRecords.isEmpty() && !roundText.isEmpty()) {
                log.warn("本轮未解析出记录，回复预览: {}", truncateForLog(lastRecognitionRawText, 400));
            }
            if (trace != null) {
                JSONObject roundDone = new JSONObject();
                roundDone.put("round", round);
                roundDone.put("finishReason", finishReason);
                roundDone.put("roundChars", roundText.length());
                roundDone.put("accumulatedChars", accumulatedText.length());
                roundDone.put("recordsSoFar", extractedRecords.size());
                roundDone.put("roundPreview", RecognitionTrace.preview(roundText, 600));
                roundDone.put("roundTextFull", roundText);
                trace.step("model_round_done", roundDone);
            }

            tryParseAndPushRecords(
                accumulatedText.toString(),
                extractedRecords,
                lastPushedCount[0],
                callback,
                lastScanPos[0],
                seenRecords);
            flushExtractAllRecords(accumulatedText.toString(), extractedRecords, seenRecords, callback);

        } catch (Exception e) {
            log.error("调用 MiMo API 失败", e);
            throw new RuntimeException(e);
        }
        return new StreamRoundOutcome(finishReason, roundText);
    }

    private void flushExtractAllRecords(String raw, List<JSONObject> extractedRecords,
                                        Set<String> seenRecords, ParseCallback callback) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        String cleaned = stripMarkdownFences(raw);
        tryParseAndPushRecords(cleaned, extractedRecords, 0, callback, 0, seenRecords);
        parseAsWholeArray(cleaned, extractedRecords, 0, callback, seenRecords);
    }

    private String stripMarkdownFences(String text) {
        return text.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replace("```", "").trim();
    }

    private static String buildRecordDedupKey(JSONArray itemArray, String itemNo, String itemName) {
        boolean unknownNo = isUnknownCell(itemNo);
        boolean unknownName = isUnknownCell(itemName);
        if (unknownNo && unknownName && itemArray.size() >= 7) {
            return itemNo + "|" + itemName + "|"
                    + itemArray.get(4) + "|" + itemArray.get(5) + "|" + itemArray.get(6);
        }
        return itemNo + "|" + itemName;
    }

    private static boolean isUnknownCell(String value) {
        if (value == null) {
            return true;
        }
        String t = value.trim();
        return t.isEmpty()
                || "???".equals(t)
                || "??".equals(t)
                || "illegible".equalsIgnoreCase(t);
    }

    private void pushRecordArray(JSONArray itemArray, List<JSONObject> extractedRecords,
                               Set<String> seenRecords, ParseCallback callback) {
        if (itemArray == null || itemArray.size() < 2) {
            return;
        }
        if (recognitionPromptGuard.isPromptExampleArray(itemArray)) {
            log.warn("跳过提示词示例或表头占位行，不作为识别结果: {}", itemArray.toJSONString());
            return;
        }
        String itemNo = itemArray.size() > 0 ? String.valueOf(itemArray.get(0)) : "";
        String itemName = itemArray.size() > 1 ? String.valueOf(itemArray.get(1)) : "";
        String recordKey = buildRecordDedupKey(itemArray, itemNo, itemName);
        if (seenRecords.contains(recordKey)) {
            return;
        }
        JSONObject normalized = normalizeRecord(itemArray);
        if (recognitionPromptGuard.isPromptExampleRecord(normalized)) {
            log.warn("跳过提示词示例记录: {} - {}", normalized.getString("NO"), normalized.getString("NOM_PRENOM"));
            return;
        }
        RecordCountryDefaults.applyMissingPays(normalized, workingCountryForPays.get());
        extractedRecords.add(normalized);
        seenRecords.add(recordKey);
        log.info("✅ 找到新记录 #{}: {} - {}",
                extractedRecords.size(), normalized.getString("NO"), normalized.getString("NOM_PRENOM"));
        callback.onRecord(normalized);
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
                    pushRecordArray((JSONArray) parsed, extractedRecords, seenRecords, callback);
                }
            } catch (Exception e) {
                JSONArray relaxed = tryParseRelaxedRecordArray(recordStr);
                if (relaxed != null) {
                    pushRecordArray(relaxed, extractedRecords, seenRecords, callback);
                }
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
                            pushRecordArray((JSONArray) item, extractedRecords, seenRecords, callback);
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

    /**
     * 容错解析：模型有时输出 [1, Italia, Milano, ...] 这类未加引号的伪 JSON。
     */
    private JSONArray tryParseRelaxedRecordArray(String recordStr) {
        if (recordStr == null) {
            return null;
        }
        String trimmed = recordStr.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]") || trimmed.length() < 3) {
            return null;
        }
        String inner = trimmed.substring(1, trimmed.length() - 1);
        List<String> tokens = splitRelaxedArrayFields(inner);
        if (tokens.isEmpty()) {
            return null;
        }
        JSONArray arr = new JSONArray();
        for (String token : tokens) {
            arr.add(parseRelaxedToken(token.trim()));
        }
        return arr;
    }

    private static List<String> splitRelaxedArrayFields(String inner) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int parenDepth = 0;
        boolean inQuote = false;
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '"' && (i == 0 || inner.charAt(i - 1) != '\\')) {
                inQuote = !inQuote;
                current.append(c);
                continue;
            }
            if (!inQuote) {
                if (c == '(') {
                    parenDepth++;
                } else if (c == ')') {
                    parenDepth = Math.max(0, parenDepth - 1);
                } else if (c == ',' && parenDepth == 0) {
                    result.add(current.toString());
                    current = new StringBuilder();
                    continue;
                }
            }
            current.append(c);
        }
        if (!current.isEmpty()) {
            result.add(current.toString());
        }
        return result;
    }

    private static Object parseRelaxedToken(String token) {
        if (token.isEmpty()) {
            return "";
        }
        if ("true".equalsIgnoreCase(token)) {
            return true;
        }
        if ("false".equalsIgnoreCase(token)) {
            return false;
        }
        if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
            return token.substring(1, token.length() - 1);
        }
        if (token.matches("-?\\d+")) {
            try {
                return Integer.parseInt(token);
            } catch (NumberFormatException ignored) {
                return token;
            }
        }
        return token;
    }

    private JSONObject normalizeRecord(JSONArray record) {
        JSONObject normalized = new JSONObject();
        if (record.size() >= 14) {
            normalized.put("NO", record.get(0));
            normalized.put("Pays", record.get(1));
            normalized.put("Entrepot", record.get(2));
            normalized.put("Date", normalizeDate(String.valueOf(record.get(3))));
            normalized.put("NOM_PRENOM", record.get(4));
            normalized.put("AGENCE_INTERIMAIRE", record.get(5));
            normalized.put("HORAIRES_DU_TRAVAIL", record.get(6));
            normalized.put("ARRIVEE", record.get(7));
            normalized.put("DEPAR", record.get(8));
            normalized.put("PAUSE", normalizePauseMinutes(record.get(9)));
            normalized.put("SIGNATURE", record.get(10));
            normalized.put("Observations", record.get(11));
            normalized.put("Mark", record.get(12));
            normalized.put("isDeleted", record.get(13));
        } else {
            normalized.put("NO", record.size() > 0 ? record.get(0) : "");
            normalized.put("Pays", "");
            normalized.put("Entrepot", "");
            normalized.put("NOM_PRENOM", record.size() > 1 ? record.get(1) : "");
            normalized.put("AGENCE_INTERIMAIRE", record.size() > 2 ? record.get(2) : "");
            normalized.put("HORAIRES_DU_TRAVAIL", record.size() > 3 ? record.get(3) : "");
            normalized.put("Date", record.size() > 4 ? normalizeDate(String.valueOf(record.get(4))) : "");
            normalized.put("ARRIVEE", record.size() > 5 ? record.get(5) : "");
            normalized.put("DEPAR", record.size() > 6 ? record.get(6) : "");
            normalized.put("PAUSE", record.size() > 7 ? normalizePauseMinutes(record.get(7)) : "");
            normalized.put("SIGNATURE", record.size() > 8 ? record.get(8) : "");
            normalized.put("Observations", "");
            normalized.put("Mark", record.size() > 9 ? record.get(9) : "");
            normalized.put("isDeleted", record.size() > 10 ? record.get(10) : false);
        }
        normalized.put("Entrepot", RecognizedFieldSanitizer.sanitizeOptionalText(normalized.getString("Entrepot")));
        normalized.put("CHECKER", normalized.getString("SIGNATURE"));

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

    private Object normalizePauseMinutes(Object pauseValue) {
        if (pauseValue == null) {
            return "";
        }
        String raw = String.valueOf(pauseValue).trim();
        if (raw.isEmpty()
                || "null".equalsIgnoreCase(raw)
                || "???".equals(raw)
                || "??".equals(raw)
                || "illegible".equalsIgnoreCase(raw)) {
            return "";
        }

        String normalized = raw.toLowerCase(Locale.ROOT)
                .replace(',', '.')
                .replaceAll("\\s+", "");
        normalized = normalized
                .replace("minutes", "min")
                .replace("minute", "min")
                .replace("mins", "min")
                .replace("mn", "min");

        try {
            Matcher hourMinute = Pattern.compile("^(\\d+(?:\\.\\d+)?)h(\\d+(?:\\.\\d+)?)?(?:min|m)?$").matcher(normalized);
            if (hourMinute.matches()) {
                double hours = Double.parseDouble(hourMinute.group(1));
                double minutes = hourMinute.group(2) == null || hourMinute.group(2).isEmpty()
                        ? 0
                        : Double.parseDouble(hourMinute.group(2));
                return (int) Math.round(hours * 60 + minutes);
            }

            Matcher colon = Pattern.compile("^(\\d{1,2}):(\\d{1,2})$").matcher(normalized);
            if (colon.matches()) {
                return Integer.parseInt(colon.group(1)) * 60 + Integer.parseInt(colon.group(2));
            }

            Matcher minute = Pattern.compile("^(\\d+(?:\\.\\d+)?)(?:min|m)$").matcher(normalized);
            if (minute.matches()) {
                return (int) Math.round(Double.parseDouble(minute.group(1)));
            }

            Matcher plain = Pattern.compile("^(\\d+(?:\\.\\d+)?)$").matcher(normalized);
            if (plain.matches()) {
                double value = Double.parseDouble(plain.group(1));
                return (int) Math.round(value);
            }
        } catch (Exception e) {
            log.warn("休息时间标准化失败: {}", raw, e);
        }

        return raw;
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
        fieldNames.put("NO", "missing.NO");
        fieldNames.put("Date", "missing.Date");
        fieldNames.put("ARRIVEE", "missing.ARRIVEE");
        fieldNames.put("DEPAR", "missing.DEPAR");
        fieldNames.put("PAUSE", "missing.PAUSE");

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
            anomalies.add("deleted.record");
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

        List<String> blurryPatterns = Arrays.asList("???", "模糊", "不清楚", "illisible", "inconnu", "non visible", "efface", "illegible");
        for (String pattern : blurryPatterns) {
            if (text.contains(pattern)) {
                quality.put("isBlurry", true);
                quality.put("isNormal", false);
                quality.getJSONArray("qualityNotes").add("内容模糊或无法识别");
                break;
            }
        }

        List<String> handwrittenPatterns = Arrays.asList(
                "手写",
                "handwritten",
                "ecrit main",
                "écrit main",
                "ecrit a la main",
                "écrit à la main",
                "manuscrit",
                "manuscrite"
        );
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

        String arriveTime = record.getString("ARRIVEE");
        String departTime = record.getString("DEPAR");
        boolean isArriveEmpty = arriveTime == null
                || arriveTime.trim().isEmpty()
                || "???".equals(arriveTime.trim())
                || "illegible".equalsIgnoreCase(arriveTime.trim());
        boolean isDepartEmpty = departTime == null
                || departTime.trim().isEmpty()
                || "???".equals(departTime.trim())
                || "illegible".equalsIgnoreCase(departTime.trim());
        boolean isAbsent = isArriveEmpty && isDepartEmpty;

        if (isAbsent) {
            return "未出勤";
        }

        JSONObject quality = detectRecordQuality(record);
        String existingMark = record.containsKey("Mark") && record.getString("Mark") != null
                ? record.getString("Mark").trim()
                : "";
        if (!existingMark.isEmpty()) {
            List<String> marks = new ArrayList<>();
            for (String part : existingMark.split("[;；,，]")) {
                String mark = part == null ? "" : part.trim();
                if (!mark.isEmpty() && !marks.contains(mark)) {
                    marks.add(mark);
                }
            }
            if (quality.getBooleanValue("isHandwritten") && !marks.contains("手写")) {
                marks.add("手写");
            }
            return String.join(";", marks);
        }

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
