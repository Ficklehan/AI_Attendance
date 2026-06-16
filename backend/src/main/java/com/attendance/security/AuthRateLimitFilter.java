package com.attendance.security;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
 * Basic in-memory rate limit for auth endpoints (brute-force / registration spam).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 20;
    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!isAuthRateLimitedPath(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        String clientKey = resolveClientKey(request, path);
        if (isLimited(clientKey)) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAuthRateLimitedPath(String path) {
        return path != null && (
                path.endsWith("/auth/login")
                        || path.endsWith("/auth/register")
                        || path.endsWith("/feishu-auth/exchange"));
    }

    private String resolveClientKey(HttpServletRequest request, String path) {
        return request.getRemoteAddr() + "|" + path;
    }

    private boolean isLimited(String clientKey) {
        long now = System.currentTimeMillis();
        Deque<Long> window = buckets.computeIfAbsent(clientKey, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() >= MAX_REQUESTS) {
                return true;
            }
            window.addLast(now);
            return false;
        }
    }
}
