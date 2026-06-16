package com.attendance.security;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-user rate limit for expensive endpoints (upload, recognition, chat image).
 * Runs after JWT authentication so limits are keyed by user id.
 */
@Component
public class ExpensiveApiRateLimitFilter extends OncePerRequestFilter {

    private static final long WINDOW_MS = 60_000L;
    private static final int UPLOAD_LIMIT = 30;
    private static final int CHAT_IMAGE_LIMIT = 15;

    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        int limit = resolveLimit(path);
        if (limit <= 0) {
            filterChain.doFilter(request, response);
            return;
        }
        String clientKey = resolveClientKey(request, path);
        if (isLimited(clientKey, limit)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private int resolveLimit(String path) {
        if (path == null) {
            return 0;
        }
        if (path.contains("/chat/image")) {
            return CHAT_IMAGE_LIMIT;
        }
        if (path.contains("/upload-async")
                || path.contains("/upload-stream")
                || path.contains("/recognize")) {
            return UPLOAD_LIMIT;
        }
        return 0;
    }

    private String resolveClientKey(HttpServletRequest request, String path) {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId != null && !userId.isEmpty()) {
            return "u:" + userId + "|" + path;
        }
        return "ip:" + request.getRemoteAddr() + "|" + path;
    }

    private boolean isLimited(String clientKey, int maxRequests) {
        long now = System.currentTimeMillis();
        Deque<Long> window = buckets.computeIfAbsent(clientKey, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() >= maxRequests) {
                return true;
            }
            window.addLast(now);
            return false;
        }
    }
}
