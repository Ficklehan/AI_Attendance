package com.attendance.dto.request;

import javax.validation.constraints.NotBlank;

public class RecognitionEngineUpdateRequest {

    @NotBlank
    private String engine;

    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }
}
