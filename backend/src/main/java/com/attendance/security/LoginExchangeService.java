package com.attendance.security;

import com.attendance.dto.response.LoginResponse;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One-time login exchange codes for OAuth callbacks (avoids JWT in browser URLs).
 */
@Service
public class LoginExchangeService {

    private static final long TTL_MS = 60_000L;

    private final ConcurrentHashMap<String, Entry> pending = new ConcurrentHashMap<>();

    public String issue(LoginResponse loginResponse) {
        purgeExpired();
        String code = UUID.randomUUID().toString().replace("-", "");
        pending.put(code, new Entry(loginResponse, System.currentTimeMillis() + TTL_MS));
        return code;
    }

    public LoginResponse consume(String code) {
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        purgeExpired();
        Entry entry = pending.remove(code.trim());
        if (entry == null || entry.expiresAtMs < System.currentTimeMillis()) {
            return null;
        }
        return entry.loginResponse;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Entry>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Entry> item = iterator.next();
            if (item.getValue().expiresAtMs < now) {
                iterator.remove();
            }
        }
    }

    private static final class Entry {
        private final LoginResponse loginResponse;
        private final long expiresAtMs;

        private Entry(LoginResponse loginResponse, long expiresAtMs) {
            this.loginResponse = loginResponse;
            this.expiresAtMs = expiresAtMs;
        }
    }
}
