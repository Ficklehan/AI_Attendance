package com.attendance.security;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.security.SigningSecretProvider;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Short-lived HMAC signatures for image URLs (replaces JWT in query strings).
 */
@Service
public class ImageAccessSignatureService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long TTL_SECONDS = 900;

    @Autowired
    private SigningSecretProvider signingSecretProvider;

    public Map<String, Object> sign(String fileKey, String userId) {
        return signWithExpiry(fileKey, userId, Instant.now().getEpochSecond() + TTL_SECONDS);
    }

    public Map<String, Object> signWithExpiry(String fileKey, String userId, long expEpochSecond) {
        String sig = signPayload(fileKey, userId, expEpochSecond);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exp", expEpochSecond);
        result.put("uid", userId);
        result.put("sig", sig);
        return result;
    }

    public Map<String, Map<String, Object>> signAll(List<String> fileKeys, String userId) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        if (fileKeys == null) {
            return out;
        }
        for (String fileKey : fileKeys) {
            if (fileKey == null || fileKey.trim().isEmpty()) {
                continue;
            }
            out.put(fileKey.trim(), sign(fileKey.trim(), userId));
        }
        return out;
    }

    public void validate(String fileKey, String userId, long exp, String sig) {
        if (fileKey == null || userId == null || sig == null) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, ErrorKeys.LOGIN_REQUIRED);
        }
        if (Instant.now().getEpochSecond() > exp) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, ErrorKeys.LOGIN_REQUIRED);
        }
        String expected = signPayload(fileKey.trim(), userId.trim(), exp);
        if (!expected.equals(sig)) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, ErrorKeys.LOGIN_REQUIRED);
        }
    }

    public String appendSignedQuery(String url, String fileKey, String userId) {
        Map<String, Object> parts = sign(fileKey, userId);
        return appendSignedQueryWithExpiry(url, fileKey, userId, ((Number) parts.get("exp")).longValue());
    }

    public String appendSignedQueryWithExpiry(String url, String fileKey, String userId, long expEpochSecond) {
        Map<String, Object> parts = signWithExpiry(fileKey, userId, expEpochSecond);
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "exp=" + parts.get("exp")
                + "&uid=" + encode(parts.get("uid").toString())
                + "&sig=" + encode(parts.get("sig").toString());
    }

    private String signPayload(String fileKey, String userId, long exp) {
        String payload = fileKey + "|" + userId + "|" + exp;
        return new HmacUtils(HMAC_ALGORITHM, signingKey()).hmacHex(payload);
    }

    private byte[] signingKey() {
        return signingSecretProvider.getSecretBytes();
    }

    private static String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}
