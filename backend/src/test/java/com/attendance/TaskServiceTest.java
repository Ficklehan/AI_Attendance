package com.attendance;

import com.attendance.entity.Task;
import com.attendance.entity.TaskListRow;
import com.attendance.mapper.TaskMapper;
import com.attendance.mapper.TaskRecordMapper;
import com.attendance.entity.User;
import com.attendance.security.DataScopeContext;
import com.attendance.security.TaskAccessService;
import com.attendance.service.ConfigService;
import com.attendance.service.ConfirmValidationService;
import com.attendance.service.DataScopeService;
import com.attendance.service.EmployeeService;
import com.attendance.service.FeishuCountryConfigService;
import com.attendance.service.FeishuSyncService;
import com.attendance.service.NightShiftConfigService;
import com.attendance.service.ReminderScheduleService;
import com.attendance.service.TaskRecordSyncService;
import com.attendance.service.TaskExcelExportService;
import com.attendance.service.TaskRecognitionLifecycleService;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
    private DataScopeService dataScopeService;

    @Mock
    private FeishuSyncService feishuSyncService;

    @Mock
    private ConfigService configService;

    @Mock
    private ConfirmValidationService confirmValidationService;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private FeishuCountryConfigService feishuCountryConfigService;

    @Mock
    private NightShiftConfigService nightShiftConfigService;

    @Mock
    private ReminderScheduleService reminderScheduleService;

    @Mock
    private com.attendance.service.PermissionService permissionService;

    @Mock
    private com.attendance.service.UserNotificationService userNotificationService;

    @Mock
    private com.attendance.mapper.UserMapper userMapper;

    @Mock
    private TaskRecognitionLifecycleService taskRecognitionLifecycleService;

    @Mock
    private TaskExcelExportService taskExcelExportService;

    @InjectMocks
    private TaskService taskService;

    private static final DateTimeFormatter TASK_ID_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private Task testTask;
    private DataScopeContext allUsersScope;

    @BeforeEach
    void setUp() {
        when(taskAccessService.requireCurrentUserId()).thenReturn("user001");
        allUsersScope = DataScopeContext.allUsers();
        when(dataScopeService.resolveForCurrentUser()).thenReturn(allUsersScope);

        testTask = new Task();
        testTask.setTaskId("20260520_001");
        testTask.setUserId("user001");
        testTask.setFileKey("test.jpg");
        testTask.setStatus("processing");
    }

    @Test
    void testCreateTask() {
        String today = LocalDate.now().format(TASK_ID_DATE);
        when(taskMapper.selectMaxTaskIdForDate(today)).thenReturn(null);
        when(recordNoGenerator.generate(null)).thenReturn(today + "_001");
        ReflectionTestUtils.setField(taskService, "self", taskService);
        when(taskMapper.insertTask(any())).thenReturn(1);

        Task result = taskService.createTask("test.jpg");

        assertNotNull(result);
        assertEquals(today + "_001", result.getTaskId());
        assertEquals("test.jpg", result.getFileKey());
        assertEquals("processing", result.getStatus());

        verify(taskMapper).insertTask(any(Task.class));
    }

    @Test
    void testCreateTaskRetriesOnDuplicateTaskId() {
        String today = LocalDate.now().format(TASK_ID_DATE);
        String conflictId = today + "_007";
        String nextId = today + "_008";
        when(taskMapper.selectMaxTaskIdForDate(today)).thenReturn(today + "_006");
        when(recordNoGenerator.generate(today + "_006")).thenReturn(conflictId);
        when(recordNoGenerator.nextAfter(conflictId)).thenReturn(nextId);
        ReflectionTestUtils.setField(taskService, "self", taskService);
        when(taskMapper.insertTask(any(Task.class)))
                .thenThrow(new DuplicateKeyException("duplicate"))
                .thenReturn(1);

        Task result = taskService.createTask("test.jpg");

        assertEquals(nextId, result.getTaskId());
        verify(taskMapper, times(2)).insertTask(any(Task.class));
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
        when(taskMapper.selectTaskList(any(DataScopeContext.class), isNull(), isNull(), isNull(), anyLong(), anyLong()))
                .thenReturn(tasks);
        when(taskMapper.countTaskList(any(DataScopeContext.class), isNull(), isNull(), isNull())).thenReturn(1L);

        List<TaskListRow> result = taskService.getTaskList(null, null, null, 0, 20);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(taskMapper).selectTaskList(any(DataScopeContext.class), isNull(), isNull(), isNull(), anyLong(), anyLong());
    }

    @Test
    void testUpdateTaskRawData() {
        String rawData = "[{\"NO\":\"001\"}]";
        String aiRawOutput = "raw output";

        taskService.updateTaskRawData("20260520_001", rawData, aiRawOutput);

        verify(taskRecognitionLifecycleService).updateTaskRawData("20260520_001", rawData, aiRawOutput);
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
        when(nightShiftConfigService.getConfigForCountry(anyString())).thenReturn(null);
        when(feishuCountryConfigService.isSyncEnabled(anyString())).thenReturn(false);
        doNothing().when(confirmValidationService).validateConfirmRecords(anyList());
        doNothing().when(employeeService).assignEmployeeOnConfirm(anyMap(), anyString());

        java.util.Map<String, Object> record = new java.util.HashMap<>();
        record.put("NOM_PRENOM", "Test User");
        record.put("Date", "2026-05-20");
        List<java.util.Map<String, Object>> data = Arrays.asList(record);

        taskService.confirmTask("20260520_001", data);

        verify(taskMapper).updateTaskConfirmedData(eq("20260520_001"), anyString());
        verify(taskRecordSyncService).syncFromTaskId("20260520_001");
    }

    @Test
    void checkDuplicateNames_matchesWhenPaysLabelDiffersFromCountryKey() {
        when(taskAccessService.requireViewableTask("task-new")).thenReturn(testTask);

        java.util.Map<String, Object> baseline = new java.util.HashMap<>();
        baseline.put("sourceTaskId", "task-old");
        baseline.put("NO", "1");
        baseline.put("NOM_PRENOM", "Jean Dupont");
        baseline.put("baseName", "JEAN DUPONT");
        baseline.put("paysKey", "FR");
        baseline.put("entrepotKey", "WH1");
        baseline.put("dateKey", "2026-07-08");
        baseline.put("agencyKey", "AG1");

        when(taskRecordMapper.selectDuplicateBaseline(
                eq("task-new"),
                anyList(),
                anyList(),
                anyList(),
                eq(allUsersScope)))
                .thenReturn(java.util.Collections.singletonList(baseline));

        java.util.Map<String, Object> current = new java.util.HashMap<>();
        current.put("_rowKey", "task-new-0");
        current.put("NOM_PRENOM", "Jean Dupont");
        current.put("Pays", "France");
        current.put("Entrepot", "WH1");
        current.put("Date", "2026-07-08");
        current.put("AGENCE_INTERIMAIRE", "AG1");

        java.util.Map<String, Object> result = taskService.checkDuplicateNamesAgainstConfirmed(
                "task-new",
                java.util.Collections.singletonList(current),
                "confirmed_only");

        @SuppressWarnings("unchecked")
        List<java.util.Map<String, Object>> duplicates =
                (List<java.util.Map<String, Object>>) result.get("duplicates");
        assertNotNull(duplicates);
        assertEquals(1, duplicates.size());
        assertEquals("task-new-0", duplicates.get(0).get("rowKey"));
    }

    @Test
    void deleteConfirmedTask_requiresReason() {
        Task confirmed = new Task();
        confirmed.setTaskId("20260520_001");
        confirmed.setStatus("confirmed");
        confirmed.setUserId("user001");
        when(taskAccessService.requireOwnedTask("20260520_001")).thenReturn(confirmed);
        when(taskAccessService.requireCurrentUserId()).thenReturn("user001");
        when(userMapper.selectUserById("user001")).thenReturn(new User());
        when(permissionService.hasPermission(any(), eq("taskDeleteConfirmed"))).thenReturn(true);

        assertThrows(com.attendance.common.BusinessException.class, () -> {
            taskService.deleteTask("20260520_001", "   ");
        });
    }

    @Test
    void deleteProcessedTask_allowsEmptyReason() {
        Task processed = new Task();
        processed.setTaskId("20260520_001");
        processed.setStatus("processed");
        processed.setUserId("user001");
        when(taskAccessService.requireOwnedTask("20260520_001")).thenReturn(processed);

        assertDoesNotThrow(() -> taskService.deleteTask("20260520_001", null));
        verify(taskMapper).deleteTaskByTaskId("20260520_001");
    }

    @Test
    void deleteConfirmedTask_requiresPermission() {
        Task confirmed = new Task();
        confirmed.setTaskId("20260520_001");
        confirmed.setStatus("confirmed");
        confirmed.setUserId("user001");
        when(taskAccessService.requireOwnedTask("20260520_001")).thenReturn(confirmed);
        when(taskAccessService.requireCurrentUserId()).thenReturn("user001");
        when(userMapper.selectUserById("user001")).thenReturn(new User());
        when(permissionService.hasPermission(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("taskDeleteConfirmed")))
                .thenReturn(false);

        assertThrows(com.attendance.common.BusinessException.class, () -> {
            taskService.deleteTask("20260520_001", "duplicate upload");
        });
    }
}
