package com.attendance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "attendance.prompt")
public class PromptProperties {

    /**
     * 内置标准模板版本；升高后启动时会刷新未自定义的国家提示词。
     */
    private int seedVersion = 2;

    /**
     * 应用启动时是否从内置模板写入数据库。
     */
    private boolean seedOnStartup = true;

    /**
     * 启动时是否强制覆盖所有国家提示词（等同重新执行初始化脚本后的效果）。
     */
    private boolean forceSeedOnStartup = false;

    /**
     * 数据库无数据时，是否尝试从 base-config/prompts.md 导入一次。
     */
    private boolean importMarkdownWhenEmpty = true;

    public int getSeedVersion() {
        return seedVersion;
    }

    public void setSeedVersion(int seedVersion) {
        this.seedVersion = seedVersion;
    }

    public boolean isSeedOnStartup() {
        return seedOnStartup;
    }

    public void setSeedOnStartup(boolean seedOnStartup) {
        this.seedOnStartup = seedOnStartup;
    }

    public boolean isForceSeedOnStartup() {
        return forceSeedOnStartup;
    }

    public void setForceSeedOnStartup(boolean forceSeedOnStartup) {
        this.forceSeedOnStartup = forceSeedOnStartup;
    }

    public boolean isImportMarkdownWhenEmpty() {
        return importMarkdownWhenEmpty;
    }

    public void setImportMarkdownWhenEmpty(boolean importMarkdownWhenEmpty) {
        this.importMarkdownWhenEmpty = importMarkdownWhenEmpty;
    }
}
