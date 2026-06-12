package com.attendance.dto.request;

import com.attendance.dto.ConfirmValidationConfigDTO;
import com.attendance.dto.NightShiftConfigDTO;

public class SystemConfigRequest {

    private Boolean notificationEnabled;
    private ConfirmValidationConfigDTO confirmValidation;
    private NightShiftConfigDTO nightShift;

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

    public NightShiftConfigDTO getNightShift() {
        return nightShift;
    }

    public void setNightShift(NightShiftConfigDTO nightShift) {
        this.nightShift = nightShift;
    }
}
