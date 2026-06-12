package com.attendance.dto.response;

import com.attendance.dto.ConfirmValidationConfigDTO;
import com.attendance.dto.NightShiftConfigDTO;

public class SystemConfigDTO {
    private boolean notificationEnabled;
    private ConfirmValidationConfigDTO confirmValidation;
    private NightShiftConfigDTO nightShift;

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

    public NightShiftConfigDTO getNightShift() {
        return nightShift;
    }

    public void setNightShift(NightShiftConfigDTO nightShift) {
        this.nightShift = nightShift;
    }
}
