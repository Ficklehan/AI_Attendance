package com.attendance.service;

import com.attendance.dto.ConfirmValidationConfigDTO;
import com.attendance.dto.NightShiftConfigDTO;
import com.attendance.dto.request.SystemConfigRequest;
import com.attendance.dto.response.SystemConfigDTO;
import com.attendance.mapper.PluginConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PluginConfigService {

    @Autowired
    private PluginConfigMapper pluginConfigMapper;

    @Autowired
    private ConfirmValidationService confirmValidationService;

    @Autowired
    private NightShiftConfigService nightShiftConfigService;

    public boolean isNotificationEnabled() {
        String value = pluginConfigMapper.selectValue(ReminderSupport.NOTIFICATION_CONFIG_KEY);
        if (value == null || value.trim().isEmpty()) {
            return true;
        }
        return "true".equalsIgnoreCase(value.trim()) || "1".equals(value.trim());
    }

    public SystemConfigDTO getSystemConfig() {
        SystemConfigDTO dto = new SystemConfigDTO();
        dto.setNotificationEnabled(isNotificationEnabled());
        dto.setConfirmValidation(confirmValidationService.getConfig());
        dto.setNightShift(nightShiftConfigService.getConfig());
        return dto;
    }

    public void updateSystemConfig(SystemConfigRequest request) {
        if (request == null) {
            return;
        }
        if (request.getNotificationEnabled() != null) {
            pluginConfigMapper.upsertValue(
                    ReminderSupport.NOTIFICATION_CONFIG_KEY,
                    Boolean.toString(request.getNotificationEnabled()),
                    "boolean",
                    "是否启用通知");
        }
        if (request.getConfirmValidation() != null) {
            confirmValidationService.saveConfig(request.getConfirmValidation());
        }
        if (request.getNightShift() != null) {
            nightShiftConfigService.saveConfig(request.getNightShift());
        }
    }

    public void updateNotificationEnabled(boolean enabled) {
        SystemConfigRequest request = new SystemConfigRequest();
        request.setNotificationEnabled(enabled);
        updateSystemConfig(request);
    }
}
