package com.attendance;

import com.attendance.entity.Task;
import com.attendance.mapper.TaskMapper;
import com.attendance.service.TaskService;
import com.attendance.util.RecordNoGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskMapper taskMapper;

    @Mock
    private RecordNoGenerator recordNoGenerator;

    @InjectMocks
    private TaskService taskService;

    private Task testTask;

    @BeforeEach
    void setUp() {
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
        List<Task> tasks = Arrays.asList(testTask);
        when(taskMapper.selectTaskList(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(tasks);
        when(taskMapper.countTaskList(anyString(), anyString(), anyString(), anyString())).thenReturn(1L);

        List<Task> result = taskService.getTaskList(null, null, null, 0, 20);

        assertNotNull(result);
        assertEquals(1, result.size());
        verify(taskMapper).selectTaskList(anyString(), anyString(), anyString(), anyString(), anyLong(), anyLong());
    }

    @Test
    void testUpdateTaskRawData() {
        String rawData = "[{\"NO\":\"001\"}]";
        String aiRawOutput = "raw output";
        
        when(taskMapper.updateTaskRawData(anyString(), anyString(), anyString())).thenReturn(1);

        taskService.updateTaskRawData("20260520_001", rawData, aiRawOutput);

        verify(taskMapper).updateTaskRawData("20260520_001", rawData, aiRawOutput);
    }

    @Test
    void testConfirmTask() {
        Task processedTask = new Task();
        processedTask.setTaskId("20260520_001");
        processedTask.setStatus("processed");
        
        when(taskMapper.selectTaskByTaskId("20260520_001")).thenReturn(processedTask);
        when(taskMapper.updateTaskConfirmedData(anyString(), anyString())).thenReturn(1);

        List<java.util.Map<String, Object>> data = Arrays.asList(
            new java.util.HashMap<String, Object>() {{ put("NO", "001"); }}
        );
        
        taskService.confirmTask("20260520_001", data);

        verify(taskMapper).updateTaskConfirmedData(eq("20260520_001"), anyString());
    }
}
