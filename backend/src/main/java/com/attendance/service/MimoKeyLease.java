package com.attendance.service;

/**
 * 从 {@link MimoKeyPool} 借用的单个 MiMo API Key（含池内序号，用于按 Key 限流）。
 */
public final class MimoKeyLease {

    private final String apiKey;
    private final int keyIndex;

    MimoKeyLease(String apiKey, int keyIndex) {
        this.apiKey = apiKey;
        this.keyIndex = keyIndex;
    }

    public String getApiKey() {
        return apiKey;
    }

    public int getKeyIndex() {
        return keyIndex;
    }
}
