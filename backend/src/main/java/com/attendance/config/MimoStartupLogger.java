package com.attendance.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MimoStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(MimoStartupLogger.class);

    private final MimoProperties mimoProperties;

    public MimoStartupLogger(MimoProperties mimoProperties) {
        this.mimoProperties = mimoProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String apiKey = mimoProperties.getApiKey();
        boolean configured = apiKey != null && !apiKey.trim().isEmpty();
        if (configured) {
            log.info("MiMo AI 已配置，将使用 PC/服务端提示词调用真实识别 API: {}, model={}",
                    mimoProperties.getApiUrl(), mimoProperties.getModel());
        } else {
            log.warn("MiMo AI 未配置（MIMO_API_KEY 为空）。识别请求将失败，请在 backend/.env 设置 MIMO_API_KEY 后重启。");
            log.warn("未设置 ALLOW_SIMULATED_RECOGNITION=true 时，不会使用模拟数据，避免与真实识别混淆。");
        }
    }
}
