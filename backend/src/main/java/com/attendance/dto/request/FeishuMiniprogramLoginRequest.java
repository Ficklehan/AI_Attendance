package com.attendance.dto.request;

import javax.validation.constraints.NotBlank;

public class FeishuMiniprogramLoginRequest {

    @NotBlank(message = "授权码不能为空")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
