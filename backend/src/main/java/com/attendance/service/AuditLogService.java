package com.attendance.service;

import com.attendance.entity.AuditLog;
import com.attendance.mapper.AuditLogMapper;
import com.attendance.security.SecurityUtils;
import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuditLogService {
    
    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    @Autowired
    private AuditLogMapper auditLogMapper;

    @Async
    public void log(String action, String targetType, String targetId, Object details) {
        try {
            AuditLog auditLog = new AuditLog();
            String userId = "anonymous";
            String username = "anonymous";
            try {
                String currentUserId = SecurityUtils.getCurrentUserId();
                String currentUsername = SecurityUtils.getCurrentUsername();
                if (currentUserId != null && !currentUserId.isEmpty()) {
                    userId = currentUserId;
                }
                if (currentUsername != null && !currentUsername.isEmpty()) {
                    username = currentUsername;
                }
            } catch (Exception e) {
                log.debug("无法获取当前用户，使用默认值: {}", e.getMessage());
            }
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setAction(action);
            auditLog.setTargetType(targetType);
            auditLog.setTargetId(targetId);
            auditLog.setDetails(details != null ? JSON.toJSONString(details) : null);

            auditLogMapper.insertAuditLog(auditLog);
            log.debug("审计日志记录: action={}, target={}", action, targetId);
        } catch (Exception e) {
            log.error("记录审计日志失败", e);
        }
    }

    public List<AuditLog> getAuditLogList(String userId, String action, 
                                          String startDate, String endDate,
                                          long offset, long size) {
        return auditLogMapper.selectAuditLogList(userId, action, startDate, endDate, offset, size);
    }

    public long countAuditLogList(String userId, String action, String startDate, String endDate) {
        return auditLogMapper.countAuditLogList(userId, action, startDate, endDate);
    }
}