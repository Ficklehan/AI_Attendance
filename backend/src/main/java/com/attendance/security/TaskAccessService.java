package com.attendance.security;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.entity.Task;
import com.attendance.mapper.TaskMapper;
import com.attendance.service.DataScopeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TaskAccessService {

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private DataScopeService dataScopeService;

    public String requireCurrentUserId() {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null || userId.isEmpty()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, ErrorKeys.LOGIN_REQUIRED);
        }
        return userId;
    }

    public void requireTaskOwner(Task task) {
        String userId = requireCurrentUserId();
        if (!dataScopeService.canAccessTask(userId, task)) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.TASK_ACCESS_DENIED);
        }
    }

    public Task requireOwnedTask(String taskId) {
        Task task = taskMapper.selectTaskByTaskId(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND, ErrorKeys.TASK_NOT_FOUND);
        }
        requireTaskOwner(task);
        return task;
    }

    public void requireFileAccess(String fileKey) {
        String userId = requireCurrentUserId();
        requireFileAccessForUser(fileKey, userId);
    }

    public void requireFileAccessForUser(String fileKey, String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, ErrorKeys.LOGIN_REQUIRED);
        }
        DataScopeContext scope = dataScopeService.resolveForUserId(userId);
        Task task = taskMapper.selectTaskByFileKeyForScope(fileKey, scope);
        if (task == null) {
            task = taskMapper.selectTaskByFileKeyAndUserId(fileKey, userId);
        }
        if (task == null) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.FILE_ACCESS_DENIED);
        }
    }
}
