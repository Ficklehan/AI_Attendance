package com.attendance;

import com.attendance.entity.Task;
import com.attendance.entity.TaskListRow;
import com.attendance.mapper.TaskMapper;
import com.attendance.mapper.TaskRecordMapper;
import com.attendance.entity.User;
import com.attendance.security.AdminAuthService;
import com.attendance.security.TaskAccessService;
import com.attendance.service.ConfigService;
import com.attendance.service.FeishuSyncService;
import com.attendance.service.TaskRecordSyncService;
import com.attendance.service.TaskService;
import com.attendance.util.RecordNoGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceTest {

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private RecordNoGenerator recordNoGenerator;

    @Mock
    private TaskRecordMapper taskRecordMapper;

    @Mock
    private TaskRecordSyncService taskRecordSyncService;

    @Mock
    private TaskAccessService taskAccessService;

    @Mock
    private AdminAuthService adminAuthService;

    @Mock
    private FeishuSyncService feishuSyncService;

    @Mock
    private ConfigService configService;

    @Mock
    private com.attendance.mapper.UserMapper userMapper;

    @InjectMocks
    private TaskService taskService;

    private Task testTask;

    @BeforeEach
    void setUp() {
        when(taskAccessService.requireCurrentUserId()).thenReturn("user001");
        when(adminAuthService.isCurrentUserAdmin()).thenReturn(true);

        testTask = new Task();
        testTask.setTaskId("20260520_001");
        testTask.setUserId("user001");
        testTask.setFileKey("test.jpg");
        testTask.setStatus("processing");
    }

    @Test
    void testCreateTask() {
        when(taskMapper.selectLastTaskId()).thenReturn(null);
        when(recordNoGenerator.generate(any())).thenReturn("20260520_001");
        when(taskMapper.insertTask(any())).thenReturn(1);

        Task result = taskService.createTask("test.jpg");

        assertNotNull(result);
        assertEquals("20260520_001", result.getTaskId());
        assertEquals("test.jpg", result.getFileKey());
        assertEquals("processing", result.getStatus());
        
        verify(taskMapper).insertTask(any(Task.class));
    }

    @Test
    void testGetTaskById() {
        when(taskMapper.selectTaskByTaskId("20260520_001")).thenReturn(testTask);

        Task result = taskService.getTaskById("20260520_001");

        assertNotNull(result);
        assertEquals("20260520_001", result.getTaskId());
        verify(taskMapper).selectTaskByTaskId("20260520_001");
    }

    @Test
    void testGetTaskByIdNotFound() {
        when(taskMapper.selectTaskByTaskId("nonexistent")).thenReturn(null);

        assertThrows(com.attendance.common.BusinessException.class, () -> {
            taskService.getTaskById("nonexistent");
        });
    }

    @Test
    void testGetTaskList() {
        TaskListRow row = new TaskListRow();
        row.setTaskId("20260520_001");
        List<TaskListRow> tasks = Arrays.asList(row);
        when(taskMapper.selectTaskList(isNull(), isNull(), isNull(), isNull(), anyLong(), anyLong()))
                .thenReturn(tasks);
        when(taskMapper.countTaskList(isNull(), isNull(), isNull(), isNull())).thenReturn(1L);

        List<TaskListRow> result = taskService.getTaskList(null, null, null, 0, 20);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(taskMapper).selectTaskList(isNull(), isNull(), isNull(), isNull(), anyLong(), anyLong());
    }

    @Test
    void testUpdateTaskRawData() {
        String rawData = "[{\"NO\":\"001\"}]";
        String aiRawOutput = "raw output";
        
        when(taskMapper.updateTaskRawData(anyString(), anyString(), anyString(), anyInt())).thenReturn(1);

        taskService.updateTaskRawData("20260520_001", rawData, aiRawOutput);

        verify(taskMapper).updateTaskRawData(eq("20260520_001"), eq(rawData), eq(aiRawOutput), eq(1));
        verify(taskRecordSyncService).syncFromTaskId("20260520_001");
    }

    @Test
    void testConfirmTask() {
        Task processedTask = new Task();
        processedTask.setTaskId("20260520_001");
        processedTask.setStatus("processed");
        processedTask.setUserId("user001");

        when(taskAccessService.requireOwnedTask("20260520_001")).thenReturn(processedTask);
        User user = new User();
        user.setId("user001");
        user.setUsername("tester");
        when(userMapper.selectUserById("user001")).thenReturn(user);
        when(configService.getCurrentCountry()).thenReturn("DEFAULT");

        java.util.Map<String, Object> record = new java.util.HashMap<>();
        record.put("NOM_PRENOM", "Test User");
        record.put("Date", "2026-05-20");
        List<java.util.Map<String, Object>> data = Arrays.asList(record);

        taskService.confirmTask("20260520_001", data);

        verify(taskMapper).updateTaskConfirmedData(eq("20260520_001"), anyString());
        verify(taskRecordSyncService).syncFromTaskId("20260520_001");
    }
}
