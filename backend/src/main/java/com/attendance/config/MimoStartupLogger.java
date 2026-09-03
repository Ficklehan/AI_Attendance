package com.attendance.config;

import com.attendance.service.RecognitionEngineConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MimoStartupLogger {

    private static final Logger log = LoggerFactory.getLogger(MimoStartupLogger.class);

    private final MimoProperties mimoProperties;
    private final DeepSeekProperties deepSeekProperties;
    private final RecognitionEngineConfigService recognitionEngineConfigService;

    public MimoStartupLogger(MimoProperties mimoProperties,
                             DeepSeekProperties deepSeekProperties,
                             RecognitionEngineConfigService recognitionEngineConfigService) {
        this.mimoProperties = mimoProperties;
        this.deepSeekProperties = deepSeekProperties;
        this.recognitionEngineConfigService = recognitionEngineConfigService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        List<String> mimoKeys = mimoProperties.getResolvedApiKeys();
        if (!mimoKeys.isEmpty()) {
            log.info("MiMo AI 已配置: {} 个 Key, api={}, model={}",
                    mimoKeys.size(), mimoProperties.getApiUrl(), mimoProperties.getModel());
        } else {
            log.warn("MiMo AI 未配置（MIMO_API_KEYS / MIMO_API_KEY 为空）。");
        }

        List<String> deepseekKeys = deepSeekProperties.getResolvedApiKeys();
        if (!deepseekKeys.isEmpty()) {
            log.info("DeepSeek AI 已配置: {} 个 Key, api={}, model={}",
                    deepseekKeys.size(), deepSeekProperties.getApiUrl(), deepSeekProperties.getModel());
        } else {
            log.warn("DeepSeek AI 未配置（DEEPSEEK_API_KEYS / DEEPSEEK_API_KEY 为空）。");
        }

        String activeEngine = recognitionEngineConfigService.getEngine();
        log.info("当前识别引擎: {}（可在管理后台 AI 设置中切换）", activeEngine);

        if (!recognitionEngineConfigService.isEngineConfigured(activeEngine)) {
            log.warn("当前识别引擎 {} 未配置 API Key，识别请求将失败。请在 backend/.env 配置后重启。", activeEngine);
            log.warn("未设置 ALLOW_SIMULATED_RECOGNITION=true 时，不会使用模拟数据，避免与真实识别混淆。");
        }
    }
}
