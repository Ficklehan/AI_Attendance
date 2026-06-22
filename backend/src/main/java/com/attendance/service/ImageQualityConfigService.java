package com.attendance.service;

import com.alibaba.fastjson.JSON;
import com.attendance.dto.ImageQualityConfigDTO;
import com.attendance.mapper.PluginConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ImageQualityConfigService {

    static final String CONFIG_KEY = "image_quality_config";

    private volatile ImageQualityConfigDTO cachedConfig;
    private volatile long cachedConfigAtMs;

    @Autowired
    private PluginConfigMapper pluginConfigMapper;

    public ImageQualityConfigDTO getConfig() {
        long now = System.currentTimeMillis();
        ImageQualityConfigDTO hit = cachedConfig;
        if (hit != null && now - cachedConfigAtMs < 60_000L) {
            return hit;
        }
        ImageQualityConfigDTO loaded = loadConfigFromDb();
        cachedConfig = loaded;
        cachedConfigAtMs = now;
        return loaded;
    }

    private ImageQualityConfigDTO loadConfigFromDb() {
        String raw = pluginConfigMapper.selectValue(CONFIG_KEY);
        if (raw == null || raw.trim().isEmpty()) {
            return ImageQualityConfigDTO.defaults();
        }
        try {
            return sanitize(JSON.parseObject(raw, ImageQualityConfigDTO.class));
        } catch (Exception e) {
            return ImageQualityConfigDTO.defaults();
        }
    }

    public void saveConfig(ImageQualityConfigDTO incoming) {
        pluginConfigMapper.upsertValue(
                CONFIG_KEY,
                JSON.toJSONString(sanitize(incoming)),
                "json",
                "图片清晰度检测规则");
        cachedConfig = null;
    }

    ImageQualityConfigDTO sanitize(ImageQualityConfigDTO incoming) {
        ImageQualityConfigDTO dto = ImageQualityConfigDTO.defaults();
        if (incoming == null) {
            return dto;
        }
        dto.setEnabled(incoming.isEnabled());
        dto.setPreUploadSharpnessEnabled(incoming.isPreUploadSharpnessEnabled());
        dto.setMinLaplacianVariance(clamp(incoming.getMinLaplacianVariance(), 10.0, 500.0, dto.getMinLaplacianVariance()));

        int blockBlur = clampPercent(incoming.getBlockBlurRowPercent(), dto.getBlockBlurRowPercent());
        int blockUnknown = clampPercent(incoming.getBlockUnknownFieldPercent(), dto.getBlockUnknownFieldPercent());
        int warnBlur = clampPercent(incoming.getWarnBlurRowPercent(), dto.getWarnBlurRowPercent());
        int warnUnknown = clampPercent(incoming.getWarnUnknownFieldPercent(), dto.getWarnUnknownFieldPercent());

        if (warnBlur >= blockBlur) {
            warnBlur = Math.max(0, blockBlur - 5);
        }
        if (warnUnknown >= blockUnknown) {
            warnUnknown = Math.max(0, blockUnknown - 5);
        }

        dto.setBlockBlurRowPercent(blockBlur);
        dto.setBlockUnknownFieldPercent(blockUnknown);
        dto.setBlockFewRowsMaxEffective(clamp(incoming.getBlockFewRowsMaxEffective(), 0, 20, dto.getBlockFewRowsMaxEffective()));
        dto.setBlockFewRowsUnknownPercent(clampPercent(incoming.getBlockFewRowsUnknownPercent(), dto.getBlockFewRowsUnknownPercent()));
        dto.setBlockMalformedRowPercent(clampMalformedPercent(incoming.getBlockMalformedRowPercent(), dto.getBlockMalformedRowPercent()));
        dto.setWarnBlurRowPercent(warnBlur);
        dto.setWarnUnknownFieldPercent(warnUnknown);
        dto.setPostRecognitionQualityEnabled(incoming.isPostRecognitionQualityEnabled());
        String statsScope = normalizeDenominator(incoming.getBlurRateDenominator(), null);
        if (statsScope == null) {
            statsScope = normalizeDenominator(
                    incoming.getUnknownRateScope(), ImageQualityConfigDTO.DENOMINATOR_EFFECTIVE_ROWS);
        }
        dto.setBlurRateDenominator(statsScope);
        dto.setUnknownRateScope(statsScope);
        dto.setUnknownRateExcludeAbsent(incoming.isUnknownRateExcludeAbsent());
        return dto;
    }

    private static String normalizeDenominator(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        if (ImageQualityConfigDTO.DENOMINATOR_ALL_ROWS.equalsIgnoreCase(value.trim())) {
            return ImageQualityConfigDTO.DENOMINATOR_ALL_ROWS;
        }
        return ImageQualityConfigDTO.DENOMINATOR_EFFECTIVE_ROWS;
    }

    private static int clampPercent(int value, int fallback) {
        if (value <= 0) {
            return fallback;
        }
        return Math.min(100, value);
    }

    /** 0 表示关闭畸形行整单拦截 */
    private static int clampMalformedPercent(int value, int fallback) {
        if (value < 0) {
            return fallback;
        }
        return Math.min(100, value);
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min) {
            return fallback;
        }
        return Math.min(max, value);
    }

    private static double clamp(double value, double min, double max, double fallback) {
        if (value < min) {
            return fallback;
        }
        return Math.min(max, value);
    }
}
