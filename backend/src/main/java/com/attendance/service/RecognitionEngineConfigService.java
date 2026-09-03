package com.attendance.service;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.config.DeepSeekProperties;
import com.attendance.config.MimoProperties;
import com.attendance.dto.RecognitionEngineConfigDTO;
import com.attendance.mapper.PluginConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RecognitionEngineConfigService {

    private static final Logger log = LoggerFactory.getLogger(RecognitionEngineConfigService.class);

    public static final String CONFIG_KEY = "recognition_engine";
    public static final String ENGINE_MIMO = "mimo";
    public static final String ENGINE_DEEPSEEK = "deepseek";

    @Autowired
    private PluginConfigMapper pluginConfigMapper;

    @Autowired
    private MimoProperties mimoProperties;

    @Autowired
    private DeepSeekProperties deepSeekProperties;

    @Autowired
    private MimoKeyPool mimoKeyPool;

    @Autowired
    private DeepSeekKeyPool deepSeekKeyPool;

    /** 每次从 DB 读取，保存后下一笔识别即生效，无需重启。 */
    public String getEngine() {
        return loadEngineFromDb();
    }

    public RecognitionEngineConfigDTO getConfig() {
        RecognitionEngineConfigDTO dto = new RecognitionEngineConfigDTO();
        dto.setEngine(getEngine());
        dto.setMimoConfigured(mimoKeyPool.isConfigured());
        dto.setDeepseekConfigured(deepSeekKeyPool.isConfigured());
        dto.setMimoModel(resolveModel(mimoProperties.getModel(), "mimo-v2.5"));
        dto.setDeepseekModel(resolveModel(deepSeekProperties.getModel(), "deepseek-v4-flash-vision-exp"));
        return dto;
    }

    public void saveEngine(String engine) {
        String normalized = normalizeEngine(engine);
        if (normalized == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的识别引擎: " + engine);
        }
        if (ENGINE_DEEPSEEK.equals(normalized) && !deepSeekKeyPool.isConfigured()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "DeepSeek 未配置 API Key，无法切换");
        }
        if (ENGINE_MIMO.equals(normalized) && !mimoKeyPool.isConfigured()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "MiMo 未配置 API Key，无法切换");
        }
        String previous = loadEngineFromDb();
        pluginConfigMapper.upsertValue(
                CONFIG_KEY,
                normalized,
                "string",
                "识别模型引擎（mimo / deepseek，全局统一，不区分国家）");
        if (!normalized.equals(previous)) {
            log.info("识别引擎已切换: {} -> {}，下一笔识别任务立即生效（无需重启）", previous, normalized);
        }
    }

    public boolean isEngineConfigured(String engine) {
        if (ENGINE_DEEPSEEK.equals(engine)) {
            return deepSeekKeyPool.isConfigured();
        }
        return mimoKeyPool.isConfigured();
    }

    private String loadEngineFromDb() {
        String raw = pluginConfigMapper.selectValue(CONFIG_KEY);
        String normalized = normalizeEngine(raw);
        return normalized != null ? normalized : ENGINE_MIMO;
    }

    private static String normalizeEngine(String engine) {
        if (engine == null || engine.trim().isEmpty()) {
            return null;
        }
        String value = engine.trim().toLowerCase();
        if (ENGINE_MIMO.equals(value) || "mimo2".equals(value) || "mimo-v2.5".equals(value)) {
            return ENGINE_MIMO;
        }
        if (ENGINE_DEEPSEEK.equals(value) || "deepseek".equals(value)) {
            return ENGINE_DEEPSEEK;
        }
        return null;
    }

    private static String resolveModel(String model, String fallback) {
        if (model == null || model.trim().isEmpty()) {
            return fallback;
        }
        return model.trim();
    }
}
