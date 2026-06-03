package com.attendance.config;

import com.attendance.service.MarkdownConfigService;
import com.attendance.service.RecognitionPromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 应用启动时把内置标准提示词写入数据库，避免依赖外部 prompts.md 文件状态。
 */
@Component
@Order(20)
public class PromptDatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PromptDatabaseBootstrap.class);

    @Autowired
    private RecognitionPromptService recognitionPromptService;

    @Autowired
    private PromptProperties promptProperties;

    @Autowired
    private MarkdownConfigService markdownConfigService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        if (!promptProperties.isSeedOnStartup()) {
            log.info("attendance.prompt.seed-on-startup=false，跳过提示词数据库播种");
            return;
        }

        try {
            ensurePromptTableExists();
            long rows = recognitionPromptService.countRows();
            boolean force = promptProperties.isForceSeedOnStartup();
            boolean legacy = recognitionPromptService.isLegacyPromptInDatabase();
            boolean missingPageNum = recognitionPromptService.isMissingPageNumPromptInDatabase();
            boolean outdatedSeed = recognitionPromptService.isOutdatedSeedInDatabase();

            if (rows == 0 && promptProperties.isImportMarkdownWhenEmpty()) {
                try {
                    markdownConfigService.refreshPromptsFromDisk(false);
                    String md = markdownConfigService.getPromptContent();
                    if (md != null && !md.trim().isEmpty() && !MarkdownConfigService.isLegacyPromptsFile(md)) {
                        int imported = recognitionPromptService.seedFromMarkdownContent(md, true);
                        log.info("数据库无提示词，已从 base-config/prompts.md 导入 {} 个国家", imported);
                        rows = recognitionPromptService.countRows();
                    }
                } catch (Exception e) {
                    log.warn("从 prompts.md 导入失败，将使用内置 canonical 模板", e);
                }
            }

            if (rows == 0 || force || legacy || missingPageNum || outdatedSeed) {
                boolean useForce = force || legacy || missingPageNum || outdatedSeed || rows == 0;
                int seeded = recognitionPromptService.seedFromCanonical(useForce);
                log.info("提示词数据库播种: rows={}, legacy={}, missingPageNum={}, outdatedSeed={}, force={}, seeded={}",
                        rows, legacy, missingPageNum, outdatedSeed, useForce, seeded);
            } else {
                recognitionPromptService.seedFromCanonical(false);
                log.info("提示词数据库已存在且非旧版，仅补全缺失国家/版本（不覆盖用户自定义）");
            }
        } catch (Exception e) {
            log.error("提示词数据库播种失败，识别功能可能不可用", e);
        }
    }

    private void ensurePromptTableExists() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS recognition_prompt ("
                + "country_code VARCHAR(16) NOT NULL PRIMARY KEY COMMENT 'default 或 CN/FR/NL/IT 等',"
                + "ai_prompt MEDIUMTEXT NOT NULL COMMENT '主要识别提示词',"
                + "continue_prompt TEXT NOT NULL COMMENT '继续输出提示词',"
                + "seed_version INT NOT NULL DEFAULT 1 COMMENT '内置模板版本号',"
                + "user_modified TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=用户曾在配置页保存过',"
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='识别提示词表'");
    }
}
