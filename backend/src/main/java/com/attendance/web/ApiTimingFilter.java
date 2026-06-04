package com.attendance.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class ApiTimingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ApiTimingFilter.class);

    private final long thresholdMs;

    public ApiTimingFilter(long thresholdMs) {
        this.thresholdMs = Math.max(200L, thresholdMs);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        long start = System.currentTimeMillis();
        try {
            chain.doFilter(request, response);
        } finally {
            if (request instanceof HttpServletRequest) {
                HttpServletRequest req = (HttpServletRequest) request;
                long cost = System.currentTimeMillis() - start;
                String uri = req.getRequestURI();
                if (cost >= thresholdMs && uri != null && !uri.contains("/actuator")) {
                    log.warn("慢接口 {}ms {} {}", cost, req.getMethod(), uri);
                }
            }
        }
    }
}
