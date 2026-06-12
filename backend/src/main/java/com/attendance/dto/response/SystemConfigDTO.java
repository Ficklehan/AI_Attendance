package com.attendance.dto.response;

import com.attendance.dto.ConfirmValidationConfigDTO;

public class SystemConfigDTO {
    private boolean notificationEnabled;
    private ConfirmValidationConfigDTO confirmValidation;

    public boolean isNotificationEnabled() {
        return notificationEnabled;
    }

    public void setNotificationEnabled(boolean notificationEnabled) {
        this.notificationEnabled = notificationEnabled;
    }

    public ConfirmValidationConfigDTO getConfirmValidation() {
        return confirmValidation;
    }

    public void setConfirmValidation(ConfirmValidationConfigDTO confirmValidation) {
        this.confirmValidation = confirmValidation;
    }
}
