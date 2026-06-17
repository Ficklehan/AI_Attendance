package com.attendance.util;

import com.attendance.service.ConfigService;

/**
 * 解析请求中的国家配置。
 */
public final class CountryResolver {

    private CountryResolver() {
    }

    /** 通用：优先 X-Country / 参数，否则服务端当前工作国家。 */
    public static String resolve(String headerCountry, String paramCountry, ConfigService configService) {
        String fromRequest = firstNonBlank(headerCountry, paramCountry);
        if (fromRequest != null) {
            return normalize(fromRequest);
        }
        return serverCurrentCountry(configService);
    }

    /**
     * 识别用国家：小程序上传时通过 formData/X-Country 传入则优先使用；
     * PC 不传参时使用服务端「当前工作国家」。
     */
    public static String resolveForRecognition(String headerCountry, String paramCountry,
                                               ConfigService configService,
                                               org.slf4j.Logger log) {
        String fromRequest = firstNonBlank(headerCountry, paramCountry);
        if (fromRequest != null) {
            String normalized = normalize(fromRequest);
            if (log != null) {
                String server = serverCurrentCountry(configService);
                if (!normalized.equals(server)) {
                    log.info("识别国家：使用客户端指定 {}（服务端当前为 {}）", normalized, server);
                }
            }
            return normalized;
        }
        String server = serverCurrentCountry(configService);
        if (log != null) {
            log.info("识别国家：使用服务端当前工作国家 {}", server);
        }
        return server;
    }

    private static String serverCurrentCountry(ConfigService configService) {
        String current = configService.getCurrentCountry();
        if (current != null && !current.trim().isEmpty()) {
            return normalize(current);
        }
        return "default";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    public static String normalize(String country) {
        String trimmed = country.trim();
        if ("default".equalsIgnoreCase(trimmed)) {
            return "default";
        }
        return trimmed.toUpperCase();
    }
}
