package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.attendance.dto.ImageQualityAssessment;
import com.attendance.common.BusinessException;
import com.attendance.common.ErrorKeys;
import com.attendance.common.ErrorCode;
import com.attendance.config.MimoProperties;
import com.attendance.config.DeepSeekProperties;
import com.attendance.storage.FileStorage;
import com.attendance.service.MarkdownConfigService;
import com.attendance.util.PageNumberNormalizer;
import com.attendance.util.RecordCountryDefaults;
import com.attendance.util.RecordFeishuPrepareSupport;
import com.attendance.util.RecognizedAgencyShiftCorrector;
import com.attendance.util.RecognizedFieldSanitizer;
import com.attendance.util.RecognizedRecordShapeSupport;
import com.attendance.util.RecognizedTimeNormalizer;
import com.attendance.util.RecognizedTextNormalizer;
import com.attendance.util.RecognizedDateNormalizer;
import com.attendance.util.NightShiftMarkSupport;
import com.attendance.util.SignatureMarkResolver;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale;

@Service
public class AIParserService {

    private static final Logger log = LoggerFactory.getLogger(AIParserService.class);

    /** 标记列合法分类值（提示词定义的枚举，模型输出中文）。 */
    private static final Set<String> BASE_MARK_TOKENS =
            new HashSet<>(Arrays.asList("手写", "模糊", "正常", "未出勤"));
    /** 标记列可接受的全部系统 token（含系统重算/删除态）。 */
    private static final Set<String> KNOWN_MARK_TOKENS =
            new HashSet<>(Arrays.asList("手写", "模糊", "正常", "未出勤", "夜班", "已删除"));

    @Autowired
    private MimoProperties mimoProperties;

    @Autowired
    private DeepSeekProperties deepSeekProperties;

    @Autowired
    private MarkdownConfigService markdownConfigService;

    @Autowired
    private RecognitionPromptGuard recognitionPromptGuard;

    @Autowired
    private RecognitionQualityGuard recognitionQualityGuard;

    @Autowired
    private FileStorage fileStorage;

    @Autowired
    private NightShiftConfigService nightShiftConfigService;

    @Autowired
    private RecognitionCoordinator recognitionCoordinator;

    @Autowired
    private RecognitionModelRuntime recognitionModelRuntime;

    private final ThreadLocal<MimoKeyLease> activeKeyLease = new ThreadLocal<>();

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

    public interface ParseCallback {
        void onRecord(JSONObject record);
        void onComplete(int totalCount);
        void onError(Exception e);
    }

    public String saveUploadedFile(byte[] fileBytes, String originalFilename) throws IOException {
        String filename = fileStorage.save(fileBytes, originalFilename);
        log.info("上传图片已保存: {} (remote={})", filename, fileStorage.isRemote());
        return filename;
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
            workingCountryForPays.set(workingCountry != null && !workingCountry.trim().isEmpty()
                    ? workingCountry.trim().toUpperCase()
                    : resolveCountry(promptCountry));
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = detectImageMimeType(imageBytes, originalFilename);
            log.info("准备上传图片到 {}，文件名: {}, MIME类型: {}, 大小: {} bytes",
                    recognitionModelRuntime.displayEngineName(), originalFilename, mimeType, imageBytes.length);

            PromptBundle prompts = loadRecognitionPrompts(promptCountry, trace);
            if (trace != null) {
                JSONObject req = new JSONObject();
                req.put("mimeType", mimeType);
                req.put("imageBytes", imageBytes.length);
                req.put("base64Chars", base64Image.length());
                req.put("dataUrlPrefix", "data:" + mimeType + ";base64,");
                req.put("model", recognitionModelRuntime.getModel());
                req.put("apiUrl", recognitionModelRuntime.getApiUrl());
                req.put("temperature", recognitionModelRuntime.getTemperature());
                req.put("maxTokens", recognitionModelRuntime.getMaxTokens());
                req.put("topP", recognitionModelRuntime.getTopP());
                req.put("engine", recognitionModelRuntime.getActiveEngine());
                req.put("promptCountry", lastPromptCountry);
                req.put("promptSection", lastPromptSection);
                req.put("promptLength", prompts.aiPrompt.length());
                req.put("promptPreview", RecognitionTrace.preview(prompts.aiPrompt, 800));
                req.put("continuePromptLength", prompts.continuePrompt.length());
                req.put("continuePromptPreview", RecognitionTrace.preview(prompts.continuePrompt, 400));
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
        if (country != null && !country.trim().isEmpty()) {
            return country.trim().toUpperCase();
        }
        String current = markdownConfigService.getCurrentCountry();
        return (current != null && !current.trim().isEmpty()) ? current.toUpperCase() : "default";
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

        if (aiPrompt == null || aiPrompt.trim().isEmpty()) {
            log.warn("国家 {} 提示词为空，回退 default", configCountry);
            configCountry = "default";
            aiPrompt = markdownConfigService.getAiPrompt("default");
            continuePrompt = markdownConfigService.getContinuePrompt("default");
        }
        if (continuePrompt == null || continuePrompt.trim().isEmpty()) {
            continuePrompt = markdownConfigService.getContinuePrompt("default");
        }

        if (aiPrompt == null || aiPrompt.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_PARSE_ERROR, ErrorKeys.AI_PROMPT_NOT_FOUND);
        }
        if (continuePrompt == null || continuePrompt.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_PARSE_ERROR, ErrorKeys.AI_CONTINUE_PROMPT_NOT_FOUND);
        }

        String apiPrompt = recognitionPromptGuard.preparePromptForApi(aiPrompt, recognitionQualityGuard);
        continuePrompt = recognitionPromptGuard.preparePromptForApi(continuePrompt.trim(), null);
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
        Set<Integer> failedKeyIndices = new HashSet<>();
        Exception lastError = null;
        int poolSize = Math.max(1, recognitionModelRuntime.getKeyPoolSize());

        while (failedKeyIndices.size() < poolSize) {
            MimoKeyLease lease;
            try {
                lease = recognitionModelRuntime.acquireKeyExcluding(failedKeyIndices);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                callback.onError(new IOException(recognitionModelRuntime.displayEngineName() + " Key 池等待被中断", ie));
                return;
            } catch (BusinessException be) {
                callback.onError(lastError != null ? lastError : be);
                return;
            }

            activeKeyLease.set(lease);
            try {
                parseImageStreamByLineWithConfigInner(
                        base64Image, mimeType, aiPrompt, continuePrompt, callback, trace);
                return;
            } catch (Exception e) {
                lastError = e;
                boolean canFailover = RecognitionRetrySupport.isKeyFailover(e)
                        && failedKeyIndices.size() < poolSize - 1;
                if (canFailover) {
                    failedKeyIndices.add(lease.getKeyIndex());
                    log.warn("{} Key #{} 不可用，切换下一个 Key ({}/{}): {}",
                            recognitionModelRuntime.displayEngineName(),
                            lease.getKeyIndex(), failedKeyIndices.size(), poolSize, e.getMessage());
                    if (trace != null) {
                        JSONObject meta = new JSONObject();
                        meta.put("failedKeyIndex", lease.getKeyIndex());
                        meta.put("tried", failedKeyIndices.size());
                        meta.put("poolSize", poolSize);
                        meta.put("message", e.getMessage());
                        trace.step("model_key_failover", meta);
                    }
                    continue;
                }
                log.error("❌ {} API 调用失败", recognitionModelRuntime.displayEngineName(), e);
                if (trace != null) {
                    trace.step("model_error", "message", e.getMessage());
                }
                callback.onError(e);
                return;
            } finally {
                activeKeyLease.remove();
            }
        }

        if (lastError != null) {
            log.error("❌ 所有 {} Key 均不可用", recognitionModelRuntime.displayEngineName(), lastError);
            if (trace != null) {
                trace.step("model_error", "message", lastError.getMessage());
            }
            callback.onError(lastError);
        }
    }

    private void parseImageStreamByLineWithConfigInner(String base64Image, String mimeType, String aiPrompt,
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
            int maxRounds = 8;
            StreamRoundOutcome roundOutcome = null;

            while (hasMore && currentRound < maxRounds) {
                currentRound++;
                log.info("🤖 调用 {} 流式 API - 第 {} 轮",
                        recognitionModelRuntime.displayEngineName(), currentRound);
                if (trace != null) {
                    trace.step("model_round_start", "round", currentRound);
                }

                try {
                    roundOutcome = callMiMoStream(
                            messages, extractedRecords, seenRecords, callback, trace, currentRound);
                } catch (Exception streamError) {
                    if (RecognitionRetrySupport.isRetryable(streamError)
                            && (!extractedRecords.isEmpty()
                            || (roundOutcome != null && !roundOutcome.roundText.isEmpty()))) {
                        log.warn("⚠️ 流中断，转入续写轮: {}", streamError.getMessage());
                        appendContinuationMessages(messages, continuePrompt,
                                roundOutcome != null ? roundOutcome.roundText : "");
                        continue;
                    }
                    throw streamError;
                }
                String finishReason = roundOutcome.finishReason;

                if (extractedRecords.isEmpty()) {
                    flushExtractAllRecords(lastRecognitionRawText, extractedRecords, seenRecords, callback);
                }

                if ("stop".equals(finishReason)) {
                    hasMore = false;
                    break;
                } else if ("length".equals(finishReason)) {
                    log.warn("⚠️ 响应被截断，自动请求续写（已保留本轮 {} 字符）",
                            roundOutcome.roundText.length());
                    appendContinuationMessages(messages, continuePrompt, roundOutcome.roundText);
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
                log.warn("拒绝照抄提示词示例的识别结果");
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

            if (recognitionQualityGuard.looksTooManyMalformedRecords(extractedRecords)) {
                double ratio = RecognizedRecordShapeSupport.malformedRatio(extractedRecords);
                log.warn("拒绝畸形行过多的识别结果: malformedRatio={}%", Math.round(ratio * 100));
                if (trace != null) {
                    JSONObject meta = new JSONObject();
                    meta.put("messageKey", ErrorKeys.AI_MALFORMED_RECORDS);
                    meta.put("malformedRatio", ratio);
                    meta.put("malformedCount", malformedCount(extractedRecords));
                    meta.put("recordCount", extractedRecords.size());
                    trace.step("malformed_ratio_rejected", meta);
                }
                callback.onError(new BusinessException(
                        ErrorCode.AI_PARSE_ERROR,
                        ErrorKeys.AI_MALFORMED_RECORDS,
                        recognitionQualityGuard.malformedRatioMessageArgs(extractedRecords)));
                return;
            }

            ImageQualityAssessment imageQuality = recognitionQualityGuard.assessImageReadability(extractedRecords);
            // 识别后模糊/可读性检测仅作评估，不再拦截；过糊只在上传前拒图
            if (imageQuality.isBlock()) {
                log.warn("识别后质量偏低但仍放行: blur={}%, unknown={}%, reason={}",
                        imageQuality.getBlurPercent(), imageQuality.getUnknownPercent(),
                        imageQuality.getBlockReason());
                if (trace != null) {
                    JSONObject meta = new JSONObject();
                    meta.put("blurPercent", imageQuality.getBlurPercent());
                    meta.put("unknownPercent", imageQuality.getUnknownPercent());
                    meta.put("blockReason", imageQuality.getBlockReason() != null
                            ? String.valueOf(imageQuality.getBlockReason()) : null);
                    meta.put("released", true);
                    trace.step("image_quality_soft_pass", meta);
                }
            }

            log.info("✅ AI识别完全结束，共识别 {} 条记录", extractedRecords.size());
            callback.onComplete(extractedRecords.size());

        } catch (Exception e) {
            log.error("❌ MiMo 流式解析失败", e);
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
            return new BusinessException(ErrorCode.AI_PARSE_ERROR, ErrorKeys.AI_INVALID_JSON, Collections.singletonMap("preview", preview));
        }
        return new BusinessException(ErrorCode.AI_PARSE_ERROR, ErrorKeys.AI_NO_PARSEABLE_RECORDS, Collections.singletonMap("preview", preview));
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

    private void appendContinuationMessages(List<JSONObject> messages, String continuePrompt, String roundText) {
        JSONObject assistantMsg = new JSONObject();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", roundText != null ? roundText : "");
        messages.add(assistantMsg);

        JSONObject continueUserMsg = new JSONObject();
        continueUserMsg.put("role", "user");
        continueUserMsg.put("content", continuePrompt);
        messages.add(continueUserMsg);
    }

    private StreamRoundOutcome callMiMoStream(List<JSONObject> messages, List<JSONObject> extractedRecords,
                                  Set<String> seenRecords, ParseCallback callback,
                                  RecognitionTrace trace, int round) throws Exception {
        Exception lastError = null;
        for (int attempt = 0; attempt < RecognitionRetrySupport.MAX_STREAM_ATTEMPTS; attempt++) {
            try {
                if (attempt > 0) {
                    log.warn("MiMo 流同轮重试 {}/{} (round={})",
                            attempt + 1, RecognitionRetrySupport.MAX_STREAM_ATTEMPTS, round);
                    if (trace != null) {
                        JSONObject retryMeta = new JSONObject();
                        retryMeta.put("round", round);
                        retryMeta.put("attempt", attempt + 1);
                        trace.step("model_stream_retry", retryMeta);
                    }
                    RecognitionRetrySupport.sleepBeforeRetry(attempt - 1);
                }
                return callMiMoStreamOnce(messages, extractedRecords, seenRecords, callback, trace, round);
            } catch (Exception e) {
                lastError = e;
                if (!RecognitionRetrySupport.isRetryable(e)
                        || attempt >= RecognitionRetrySupport.MAX_STREAM_ATTEMPTS - 1) {
                    throw e;
                }
            }
        }
        throw lastError != null ? lastError : new IOException("MiMo stream failed");
    }

    private String resolveActiveApiKey() {
        MimoKeyLease lease = activeKeyLease.get();
        if (lease != null) {
            return lease.getApiKey();
        }
        List<String> keys = recognitionModelRuntime.isMimoEngine()
                ? mimoProperties.getResolvedApiKeys()
                : deepSeekProperties.getResolvedApiKeys();
        if (!keys.isEmpty()) {
            return keys.get(0);
        }
        if (recognitionModelRuntime.isMimoEngine()) {
            String single = mimoProperties.getApiKey();
            return single != null ? single.trim() : "";
        }
        String single = deepSeekProperties.getApiKey();
        return single != null ? single.trim() : "";
    }

    private StreamRoundOutcome callMiMoStreamOnce(List<JSONObject> messages, List<JSONObject> extractedRecords,
                                  Set<String> seenRecords, ParseCallback callback,
                                  RecognitionTrace trace, int round) throws Exception {
        String finishReason = null;
        String roundText = "";
        try {
            String apiKey = resolveActiveApiKey();
            String apiUrl = recognitionModelRuntime.getApiUrl();
            String engineName = recognitionModelRuntime.displayEngineName();

            if (apiKey == null || apiKey.trim().isEmpty()) {
                throw new IllegalStateException(engineName + " API Key 未配置");
            }

            JSONObject requestBody = new JSONObject();
            String model = recognitionModelRuntime.getModel();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", recognitionModelRuntime.getMaxTokens());
            requestBody.put("stream", true);
            requestBody.put("temperature", recognitionModelRuntime.getTemperature());
            requestBody.put("top_p", recognitionModelRuntime.getTopP());
            // MiMo / DeepSeek V4 均可能默认开启 thinking；识图 OCR 必须关闭，否则推理占满 max_tokens、无有效 JSON 行
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
                log.error("❌ {} API 请求失败！状态码: {}, 错误内容: {}", engineName, response.code(), errorContent);
                throw new MimoApiException(response.code(),
                        "API请求失败: " + response.code() + " - 错误: " + errorContent);
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
            // 仅在本轮尚未解析出任何行时做修补重扫，避免外层数组+行数组被吃两遍
            if (extractedRecords.isEmpty()) {
                flushExtractAllRecords(accumulatedText.toString(), extractedRecords, seenRecords, callback);
            }

        } catch (Exception e) {
            log.error("调用 {} API 失败", recognitionModelRuntime.displayEngineName(), e);
            throw e;
        }
        return new StreamRoundOutcome(finishReason, roundText);
    }

    private void flushExtractAllRecords(String raw, List<JSONObject> extractedRecords,
                                        Set<String> seenRecords, ParseCallback callback) {
        if (raw == null || raw.trim().isEmpty()) {
            return;
        }
        String cleaned = stripMarkdownFences(raw);
        cleaned = repairMissingRowClosingBrackets(cleaned);
        tryParseAndPushRecords(cleaned, extractedRecords, 0, callback, 0, seenRecords);
        parseAsWholeArray(cleaned, extractedRecords, 0, callback, seenRecords);
    }

    /**
     * 模型流式输出时常见错误：每行数组未写闭合 ] 就直接换行开始下一行，例如
     * ["41",...,"","","","",\n["42",...  → 需在换行前的 [ 之前补上 ]。
     * <p>注意：合法的 {@code ],\n[} / {@code ]\n[} / 外层 {@code [\n[} 不得被改写，
     * 否则一张表会被拆成两套解析路径并出现双倍行数。
     */
    static String repairMissingRowClosingBrackets(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 缺 ] 且行尾是 "," 再换行开下一行：["41",...,"",\n["42" → ["41",...,""],\n["42"
        String repaired = text.replaceAll("(?<!\\]),\\r?\\n(\\s*)\\[", "],\n$1[");
        // 缺 ] 且行尾直接换行开下一行：...false\n["42" / ...""\n["42"
        repaired = repaired.replaceAll("(?<=(?:\"|true|false|\\d))\\r?\\n(\\s*)\\[", "]\n$1[");
        repaired = repaired.trim();
        repaired = RecognizedRecordShapeSupport.repairStickyRowBoundaries(repaired);
        if (repaired.startsWith("[") && !repaired.endsWith("]")) {
            int lastOpen = repaired.lastIndexOf('[');
            if (lastOpen >= 0 && findMatchingBracket(repaired, lastOpen) == -1) {
                repaired = repaired + "]";
            }
        }
        return repaired;
    }

    private String stripMarkdownFences(String text) {
        return text.replaceAll("(?s)```[a-zA-Z]*\\s*", "").replace("```", "").trim();
    }

    private static String buildRecordDedupKey(JSONArray itemArray) {
        if (itemArray == null || itemArray.isEmpty()) {
            return "";
        }
        // 标准 15 列：[NO, Pays, Entrepot, Date, NOM_PRENOM, ...]
        // 旧逻辑误用 index=1（Pays）作姓名，同国多行易撞车或在不同解析形态下去重失效。
        String itemNo = String.valueOf(itemArray.get(0));
        String itemDate = itemArray.size() > 3 ? String.valueOf(itemArray.get(3)) : "";
        String itemName = itemArray.size() > 4
                ? String.valueOf(itemArray.get(4))
                : (itemArray.size() > 1 ? String.valueOf(itemArray.get(1)) : "");
        boolean unknownNo = isUnknownCell(itemNo);
        boolean unknownName = isUnknownCell(itemName);
        if (unknownNo && unknownName && itemArray.size() >= 7) {
            return itemNo + "|" + itemDate + "|" + itemName + "|"
                    + itemArray.get(5) + "|" + itemArray.get(6);
        }
        return itemNo + "|" + itemDate + "|" + itemName;
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

    /** 模型有时输出外层数组包裹多行：[[row],[row],...]，需拆成行再入队。 */
    private static boolean isNestedRowArray(JSONArray itemArray) {
        if (itemArray == null || itemArray.isEmpty()) {
            return false;
        }
        int nested = 0;
        for (int i = 0; i < itemArray.size(); i++) {
            if (itemArray.get(i) instanceof JSONArray) {
                nested++;
            }
        }
        return nested > 0 && nested == itemArray.size();
    }

    private void pushRecordArray(JSONArray itemArray, List<JSONObject> extractedRecords,
                               Set<String> seenRecords, ParseCallback callback) {
        if (itemArray == null || itemArray.size() < 2) {
            return;
        }
        if (isNestedRowArray(itemArray)) {
            for (int i = 0; i < itemArray.size(); i++) {
                pushRecordArray((JSONArray) itemArray.get(i), extractedRecords, seenRecords, callback);
            }
            return;
        }
        List<JSONArray> expanded = RecognizedRecordShapeSupport.expandMergedRowArrays(itemArray);
        boolean mergedSixteen = itemArray.size() == 16
                && RecognizedRecordShapeSupport.looksLikeMergedBlob(String.valueOf(itemArray.get(0)));
        JSONArray recoveredFirst = mergedSixteen
                ? RecognizedRecordShapeSupport.trySplitMergedBlob(String.valueOf(itemArray.get(0)))
                : null;
        for (int i = 0; i < expanded.size(); i++) {
            JSONArray candidate = expanded.get(i);
            if (isNestedRowArray(candidate)) {
                pushRecordArray(candidate, extractedRecords, seenRecords, callback);
                continue;
            }
            boolean recoveredFromMerge = mergedSixteen && i == 0 && recoveredFirst != null
                    && candidate.toJSONString().equals(recoveredFirst.toJSONString());
            pushSingleRecordArray(candidate, recoveredFromMerge, extractedRecords, seenRecords, callback);
        }
    }

    private void pushSingleRecordArray(JSONArray itemArray, boolean recoveredFromMerge,
                                       List<JSONObject> extractedRecords, Set<String> seenRecords,
                                       ParseCallback callback) {
        if (itemArray == null || itemArray.size() < 2) {
            return;
        }
        if (itemArray.get(0) instanceof JSONArray) {
            // 防御：嵌套行不应走到单行归一化
            pushRecordArray(itemArray, extractedRecords, seenRecords, callback);
            return;
        }
        if (recognitionPromptGuard.isPromptExampleArray(itemArray)) {
            log.warn("跳过提示词示例或表头占位行，不作为识别结果: {}", itemArray.toJSONString());
            return;
        }
        int rawFieldCount = itemArray.size();
        String recordKey = buildRecordDedupKey(itemArray);
        if (seenRecords.contains(recordKey)) {
            return;
        }
        JSONObject normalized = normalizeRecord(itemArray);
        if (recognitionPromptGuard.isPromptExampleRecord(normalized)) {
            log.warn("跳过提示词示例记录: {} - {}", normalized.getString("NO"), normalized.getString("NOM_PRENOM"));
            return;
        }
        if (recoveredFromMerge || RecognizedRecordShapeSupport.isNormalizedShapeMalformed(normalized, rawFieldCount)) {
            String reason = recoveredFromMerge ? "merged_row_recovered" : "invalid_field_shape";
            RecognizedRecordShapeSupport.markMalformed(normalized, reason);
            log.warn("畸形行已保留并标记: NO={}, reason={}", normalized.getString("NO"), reason);
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
            if (recordEnd == -1) {
                // 外层 [ 尚未闭合时，跳过该 [ 继续找已闭合的内层行，避免整段卡死
                pos = recordStart + 1;
                continue;
            }

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

    private static int findMatchingBracket(String text, int startPos) {
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
        if (current.length() > 0) {
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
            normalized.put("Date", RecognizedDateNormalizer.normalizeDate(String.valueOf(record.get(3))));
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
            normalized.put("PAGE_NUM", record.size() > 14
                    ? PageNumberNormalizer.sanitize(String.valueOf(record.get(14))) : "");
        } else {
            normalized.put("NO", record.size() > 0 ? record.get(0) : "");
            normalized.put("Pays", "");
            normalized.put("Entrepot", "");
            normalized.put("NOM_PRENOM", record.size() > 1 ? record.get(1) : "");
            normalized.put("AGENCE_INTERIMAIRE", record.size() > 2 ? record.get(2) : "");
            normalized.put("HORAIRES_DU_TRAVAIL", record.size() > 3 ? record.get(3) : "");
            normalized.put("Date", record.size() > 4
                    ? RecognizedDateNormalizer.normalizeDate(String.valueOf(record.get(4))) : "");
            normalized.put("ARRIVEE", record.size() > 5 ? record.get(5) : "");
            normalized.put("DEPAR", record.size() > 6 ? record.get(6) : "");
            normalized.put("PAUSE", record.size() > 7 ? normalizePauseMinutes(record.get(7)) : "");
            normalized.put("SIGNATURE", record.size() > 8 ? record.get(8) : "");
            normalized.put("Observations", "");
            normalized.put("Mark", record.size() > 9 ? record.get(9) : "");
            normalized.put("isDeleted", record.size() > 10 ? record.get(10) : false);
            normalized.put("PAGE_NUM", "");
        }
        if (!normalized.containsKey("PAGE_NUM")) {
            normalized.put("PAGE_NUM", "");
        }
        normalized.put("PAGE_NUM", PageNumberNormalizer.sanitize(normalized.getString("PAGE_NUM")));
        RecognizedAgencyShiftCorrector.correctSwappedFields(normalized);
        RecognizedTextNormalizer.normalizeRecordTextFields(normalized);
        normalized.put("Entrepot", RecognizedTextNormalizer.normalizeLabelText(normalized.getString("Entrepot")));
        normalized.put("Date", RecognizedDateNormalizer.normalizeDate(normalized.getString("Date")));
        normalized.put("HORAIRES_DU_TRAVAIL",
                RecognizedTimeNormalizer.normalizeShiftSchedule(normalized.getString("HORAIRES_DU_TRAVAIL")));
        normalized.put("ARRIVEE", RecognizedTimeNormalizer.normalizeClockTime(normalized.getString("ARRIVEE")));
        normalized.put("DEPAR", RecognizedTimeNormalizer.normalizeClockTime(normalized.getString("DEPAR")));
        String rawAiSignature = normalized.getString("SIGNATURE");
        normalized.put("SIGNATURE_RAW", rawAiSignature);

        JSONObject anomalies = detectAnomalies(normalized);
        normalized.put("isDeleted", anomalies.getBoolean("isDeleted"));
        normalized.put("riskLevel", anomalies.getString("riskLevel"));
        normalized.put("anomalies", anomalies.getJSONArray("anomalies"));

        salvageMisplacedMarkColumn(normalized);
        normalized.put("SmartMark", generateSmartMark(normalized));

        String signatureMark = SignatureMarkResolver.resolveFromAiOutput(
                rawAiSignature,
                normalized.getBooleanValue("isDeleted"),
                normalized.getString("SmartMark"),
                normalized.getString("ARRIVEE"),
                normalized.getString("DEPAR"),
                normalized.getString("Mark"));
        normalized.put("SIGNATURE", signatureMark);
        normalized.put("CHECKER", signatureMark);

        RecordFeishuPrepareSupport.enrichDatetimeFields(normalized);
        RecordFeishuPrepareSupport.enrichWorkHours(normalized);

        RecognizedFieldSanitizer.annotateAndSanitizeRecord(normalized);
        return normalized;
    }

    private static boolean isClockTime(String value) {
        return value != null && value.matches("\\d{1,2}:\\d{2}");
    }

    private Object normalizePauseMinutes(Object pauseValue) {
        if (pauseValue == null) {
            return "";
        }
        String raw = String.valueOf(pauseValue).trim();
        if (RecognizedFieldSanitizer.isUnrecognized(raw)) {
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

        return "";
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

    private String detectShiftType(String arriveTime, String departTime, String shiftSchedule) {
        if (NightShiftMarkSupport.shouldMarkNightShift(
                arriveTime, departTime, shiftSchedule, nightShiftConfigService.getConfigForCountry(lastPromptCountry))) {
            return "夜班";
        }
        String normalizedArrive = RecognizedTimeNormalizer.normalizeClockTime(arriveTime);
        String normalizedDepart = RecognizedTimeNormalizer.normalizeClockTime(departTime);
        if (normalizedArrive == null || normalizedDepart == null) {
            return null;
        }
        try {
            int arriveHour = Integer.parseInt(normalizedArrive.split(":")[0]);
            int departHour = Integer.parseInt(normalizedDepart.split(":")[0]);
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

    /**
     * 模型可能把「备注」内容错放进「标记」列（尤其表格同时存在物理备注/标记两列时）。
     * 标记列仅承载系统分类枚举，此处把其中的自由文本剥离：备注为空时回填到备注列，
     * 并将标记列仅保留合法分类 token，避免标记信息被备注覆盖、备注丢失。
     */
    static void salvageMisplacedMarkColumn(JSONObject record) {
        if (record == null) {
            return;
        }
        String mark = record.getString("Mark");
        if (mark == null || mark.trim().isEmpty()) {
            return;
        }
        List<String> classification = new ArrayList<>();
        List<String> freeText = new ArrayList<>();
        for (String part : mark.split("[;；,，]")) {
            String token = part == null ? "" : part.trim();
            if (token.isEmpty()) {
                continue;
            }
            if (isKnownMarkToken(token)) {
                classification.add(token);
            } else {
                freeText.add(token);
            }
        }
        if (freeText.isEmpty()) {
            return;
        }
        String observations = record.getString("Observations");
        if (observations == null || observations.trim().isEmpty()) {
            record.put("Observations", String.join(";", freeText));
        }
        record.put("Mark", String.join(";", classification));
    }

    private static boolean isKnownMarkToken(String token) {
        if (token == null) {
            return false;
        }
        String trimmed = token.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return KNOWN_MARK_TOKENS.contains(trimmed)
                || SignatureMarkResolver.isSignatureMarkToken(trimmed)
                || NightShiftMarkSupport.isNightShiftMarkToken(trimmed);
    }

    private String generateSmartMark(JSONObject record) {
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

        JSONObject quality = detectRecordQuality(record);
        List<String> marks = new ArrayList<>();

        String existingMark = record.containsKey("Mark") && record.getString("Mark") != null
                ? record.getString("Mark").trim()
                : "";
        if (!existingMark.isEmpty()) {
            for (String part : existingMark.split("[;；,，]")) {
                String mark = part == null ? "" : part.trim();
                // 仅保留合法分类值，避免模型误放进「标记」列的备注等自由文本污染/覆盖标记
                if (mark.isEmpty() || !BASE_MARK_TOKENS.contains(mark)) {
                    continue;
                }
                if (!marks.contains(mark)) {
                    marks.add(mark);
                }
            }
        }

        if (isAbsent) {
            if (!marks.contains("未出勤")) {
                marks.add("未出勤");
            }
        } else if (marks.isEmpty()) {
            if (quality.getBooleanValue("isHandwritten")) {
                marks.add("手写");
            } else if (quality.getBooleanValue("isBlurry")) {
                marks.add("模糊");
            } else {
                marks.add("正常");
            }
        } else {
            if (quality.getBooleanValue("isHandwritten") && !marks.contains("手写")) {
                marks.add("手写");
            }
        }

        String shiftSchedule = record.getString("HORAIRES_DU_TRAVAIL");
        if (!isAbsent) {
            String shiftType = detectShiftType(arriveTime, departTime, shiftSchedule);
            if ("夜班".equals(shiftType) && !marks.contains("夜班")) {
                marks.add("夜班");
            }
        }

        return String.join(";", marks);
    }

    private static int malformedCount(List<JSONObject> records) {
        if (records == null) {
            return 0;
        }
        int count = 0;
        for (JSONObject record : records) {
            if (record != null && record.getBooleanValue(RecognizedRecordShapeSupport.PARSE_MALFORMED_KEY)) {
                count++;
            }
        }
        return count;
    }
}
