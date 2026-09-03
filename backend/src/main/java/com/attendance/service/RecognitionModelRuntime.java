package com.attendance.service;

import com.attendance.config.DeepSeekProperties;
import com.attendance.config.MimoProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * 当前识别引擎的运行时配置：引擎选择、Key 池轮询、API 参数。
 */
@Service
public class RecognitionModelRuntime {

    @Autowired
    private RecognitionEngineConfigService engineConfigService;

    @Autowired
    private MimoProperties mimoProperties;

    @Autowired
    private DeepSeekProperties deepSeekProperties;

    @Autowired
    private MimoKeyPool mimoKeyPool;

    @Autowired
    private DeepSeekKeyPool deepSeekKeyPool;

    public String getActiveEngine() {
        return engineConfigService.getEngine();
    }

    public boolean isActiveConfigured() {
        return engineConfigService.isEngineConfigured(getActiveEngine());
    }

    public int getKeyPoolSize() {
        if (RecognitionEngineConfigService.ENGINE_DEEPSEEK.equals(getActiveEngine())) {
            return deepSeekKeyPool.getPoolSize();
        }
        return mimoKeyPool.getPoolSize();
    }

    public MimoKeyLease acquireKeyExcluding(Set<Integer> excludeIndices) throws InterruptedException {
        if (RecognitionEngineConfigService.ENGINE_DEEPSEEK.equals(getActiveEngine())) {
            return deepSeekKeyPool.acquireExcluding(excludeIndices);
        }
        return mimoKeyPool.acquireExcluding(excludeIndices);
    }

    public String getApiUrl() {
        if (RecognitionEngineConfigService.ENGINE_DEEPSEEK.equals(getActiveEngine())) {
            return deepSeekProperties.getApiUrl();
        }
        return mimoProperties.getApiUrl();
    }

    public String getModel() {
        if (RecognitionEngineConfigService.ENGINE_DEEPSEEK.equals(getActiveEngine())) {
            String model = deepSeekProperties.getModel();
            return model == null || model.trim().isEmpty() ? "deepseek-v4-flash-vision-exp" : model.trim();
        }
        String model = mimoProperties.getModel();
        return model == null || model.trim().isEmpty() ? "mimo-v2.5" : model.trim();
    }

    public int getMaxTokens() {
        if (RecognitionEngineConfigService.ENGINE_DEEPSEEK.equals(getActiveEngine())) {
            return deepSeekProperties.getMaxTokens();
        }
        return mimoProperties.getMaxTokens();
    }

    public double getTemperature() {
        if (RecognitionEngineConfigService.ENGINE_DEEPSEEK.equals(getActiveEngine())) {
            return deepSeekProperties.getTemperature();
        }
        return mimoProperties.getTemperature();
    }

    public double getTopP() {
        if (RecognitionEngineConfigService.ENGINE_DEEPSEEK.equals(getActiveEngine())) {
            return deepSeekProperties.getTopP();
        }
        return mimoProperties.getTopP();
    }

    public boolean isMimoEngine() {
        return RecognitionEngineConfigService.ENGINE_MIMO.equals(getActiveEngine());
    }

    public String displayEngineName() {
        return RecognitionEngineConfigService.ENGINE_DEEPSEEK.equals(getActiveEngine()) ? "DeepSeek" : "MiMo";
    }
}
