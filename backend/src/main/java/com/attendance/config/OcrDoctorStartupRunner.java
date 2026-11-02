package com.attendance.config;

import com.attendance.recognition.StructuredRecognitionPipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 启动时跑 dsh-ocr-local --doctor；失败只打日志，识别时再按 fallback-legacy 处理。
 */
@Component
public class OcrDoctorStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OcrDoctorStartupRunner.class);

    private final RecognitionProperties properties;
    private final StructuredRecognitionPipeline pipeline;

    public OcrDoctorStartupRunner(RecognitionProperties properties,
                                  StructuredRecognitionPipeline pipeline) {
        this.properties = properties;
        this.pipeline = pipeline;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isOcrPrimary()) {
            log.info("识别管线 {}，跳过 dsh-ocr-local doctor", properties.getPipeline());
            return;
        }
        boolean ok = pipeline.doctorOk();
        if (ok) {
            log.info("ocr_primary 可用 (dsh headless extract)");
        } else if (properties.getOcr().isFallbackLegacyOnUnavailable()) {
            log.warn("ocr_primary 未就绪，将回退 legacy: {}", pipeline.doctorMessage());
        } else {
            log.error("ocr_primary 未就绪且未允许回退: {}", pipeline.doctorMessage());
        }
    }
}
