package com.attendance.dto.request;

import com.attendance.dto.ConfirmValidationConfigDTO;

public class SystemConfigRequest {

    private Boolean notificationEnabled;
    private ConfirmValidationConfigDTO confirmValidation;

    public Boolean getNotificationEnabled() {
        return notificationEnabled;
    }

    public void setNotificationEnabled(Boolean notificationEnabled) {
        this.notificationEnabled = notificationEnabled;
    }

    public ConfirmValidationConfigDTO getConfirmValidation() {
        return confirmValidation;
    }

    public void setConfirmValidation(ConfirmValidationConfigDTO confirmValidation) {
        this.confirmValidation = confirmValidation;
    }
}
