package com.attendance.security;

import com.attendance.config.JwtProperties;
import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * HMAC-signed OAuth state for CSRF protection (works with stateless JWT sessions).
 */
@Component
public class OAuthStateService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Autowired
    private JwtProperties jwtProperties;

    public String createState() {
        String nonce = UUID.randomUUID().toString();
        return nonce + "." + sign(nonce);
    }

    public void validateState(String state) {
        if (state == null || state.trim().isEmpty()) {
            throw new IllegalArgumentException("缺少 OAuth state");
        }
        int dot = state.lastIndexOf('.');
        if (dot <= 0 || dot >= state.length() - 1) {
            throw new IllegalArgumentException("OAuth state 格式无效");
        }
        String nonce = state.substring(0, dot);
        String signature = state.substring(dot + 1);
        String expected = sign(nonce);
        if (!expected.equals(signature)) {
            throw new IllegalArgumentException("OAuth state 校验失败");
        }
    }

    private String sign(String nonce) {
        String secret = jwtProperties.getSecret();
        if (secret == null || secret.trim().isEmpty()) {
            secret = "dev-oauth-state-fallback";
        }
        return new HmacUtils(HMAC_ALGORITHM, secret.getBytes(StandardCharsets.UTF_8)).hmacHex(nonce);
    }
}
