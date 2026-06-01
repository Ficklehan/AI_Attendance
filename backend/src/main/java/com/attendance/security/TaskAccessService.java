package com.attendance.security;

import com.attendance.common.BusinessException;
import com.attendance.common.ErrorCode;
import com.attendance.common.ErrorKeys;
import com.attendance.entity.Task;
import com.attendance.mapper.TaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TaskAccessService {

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private AdminAuthService adminAuthService;

    public String requireCurrentUserId() {
        String userId = SecurityUtils.getCurrentUserId();
        if (userId == null || userId.isEmpty()) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, ErrorKeys.LOGIN_REQUIRED);
        }
        return userId;
    }

    public void requireTaskOwner(Task task) {
        if (adminAuthService.isCurrentUserAdmin()) {
            return;
        }
        String userId = requireCurrentUserId();
        if (task.getUserId() == null || !userId.equals(task.getUserId())) {
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
        requireCurrentUserId();
        Task task;
        if (adminAuthService.isCurrentUserAdmin()) {
            task = taskMapper.selectTaskByFileKey(fileKey);
        } else {
            task = taskMapper.selectTaskOwningFileKey(fileKey, requireCurrentUserId());
        }
        if (task == null) {
            throw new BusinessException(ErrorCode.PERMISSION_DENIED, ErrorKeys.FILE_ACCESS_DENIED);
        }
    }
}
