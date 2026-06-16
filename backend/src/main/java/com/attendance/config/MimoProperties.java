package com.attendance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "mimo")
public class MimoProperties {
    private String apiKey;
    /** 逗号分隔的多个 API Key，优先于单 Key */
    private String apiKeys;
    private String apiUrl;
    private String model = "mimo-v2.5";
    private double temperature = 0.3;
    private int maxTokens = 8192;
    private double topP = 0.9;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiKeys() {
        return apiKeys;
    }

    public void setApiKeys(String apiKeys) {
        this.apiKeys = apiKeys;
    }

    /**
     * 解析后的 Key 列表：MIMO_API_KEYS > MIMO_API_KEY_1..N > MIMO_API_KEY。
     */
    public List<String> getResolvedApiKeys() {
        List<String> resolved = new ArrayList<>();
        if (apiKeys != null && !apiKeys.trim().isEmpty()) {
            appendUniqueKeys(resolved, apiKeys.split(","));
        }
        if (resolved.isEmpty()) {
            for (int i = 1; i <= 32; i++) {
                String numbered = resolveNumberedApiKey(i);
                if (numbered == null || numbered.isEmpty()) {
                    break;
                }
                if (!resolved.contains(numbered)) {
                    resolved.add(numbered);
                }
            }
        }
        if (resolved.isEmpty() && apiKey != null && !apiKey.trim().isEmpty()) {
            resolved.add(apiKey.trim());
        }
        return resolved;
    }

    private static void appendUniqueKeys(List<String> resolved, String[] parts) {
        for (String part : parts) {
            String trimmed = part != null ? part.trim() : "";
            if (!trimmed.isEmpty() && !resolved.contains(trimmed)) {
                resolved.add(trimmed);
            }
        }
    }

    private static String resolveNumberedApiKey(int index) {
        String propertyKey = "MIMO_API_KEY_" + index;
        String fromProperty = System.getProperty(propertyKey);
        if (fromProperty != null && !fromProperty.trim().isEmpty()) {
            return fromProperty.trim();
        }
        String fromEnv = System.getenv(propertyKey);
        if (fromEnv != null && !fromEnv.trim().isEmpty()) {
            return fromEnv.trim();
        }
        return null;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public double getTopP() {
        return topP;
    }

    public void setTopP(double topP) {
        this.topP = topP;
    }
}