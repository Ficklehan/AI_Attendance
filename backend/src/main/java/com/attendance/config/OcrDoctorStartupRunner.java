package com.attendance.config;

import com.attendance.recognition.StructuredRecognitionPipeline;
import com.attendance.recognition.harness.CropZoomCli;
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
    private final CropZoomCli cropZoomCli;

    public OcrDoctorStartupRunner(RecognitionProperties properties,
                                  StructuredRecognitionPipeline pipeline,
                                  CropZoomCli cropZoomCli) {
        this.properties = properties;
        this.pipeline = pipeline;
        this.cropZoomCli = cropZoomCli;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isOcrPrimary()) {
            log.info("识别管线 {}，跳过 dsh-ocr-local doctor", properties.getPipeline());
            return;
        }
        boolean ok = pipeline.doctorOk();
        String crop = cropZoomCli.available() && cropZoomCli.resolveBin() != null
                ? cropZoomCli.resolveBin().toString()
                : "missing (Java CropZoomTool fallback)";
        if (ok) {
            log.info("ocr_primary 可用 (vision_ground + 并行 VLM); crop-zoom CLI {}", crop);
        } else if (properties.getOcr().isFallbackLegacyOnUnavailable()) {
            log.warn("ocr_primary 未就绪，将回退 legacy: {}; crop-zoom {}",
                    pipeline.doctorMessage(), crop);
        } else {
            log.error("ocr_primary 未就绪且未允许回退: {}; crop-zoom {}",
                    pipeline.doctorMessage(), crop);
        }
    }
}
