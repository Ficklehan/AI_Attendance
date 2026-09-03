package com.attendance.dto;

public class RecognitionEngineConfigDTO {
    private String engine;
    private boolean mimoConfigured;
    private boolean deepseekConfigured;
    private String mimoModel;
    private String deepseekModel;

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public boolean isMimoConfigured() {
        return mimoConfigured;
    }

    public void setMimoConfigured(boolean mimoConfigured) {
        this.mimoConfigured = mimoConfigured;
    }

    public boolean isDeepseekConfigured() {
        return deepseekConfigured;
    }

    public void setDeepseekConfigured(boolean deepseekConfigured) {
        this.deepseekConfigured = deepseekConfigured;
    }

    public String getMimoModel() {
        return mimoModel;
    }

    public void setMimoModel(String mimoModel) {
        this.mimoModel = mimoModel;
    }

    public String getDeepseekModel() {
        return deepseekModel;
    }

    public void setDeepseekModel(String deepseekModel) {
        this.deepseekModel = deepseekModel;
    }
}
