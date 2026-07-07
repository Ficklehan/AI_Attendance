package com.attendance;

import com.attendance.entity.Task;
import com.attendance.mapper.TaskRecordMapper;
import com.attendance.service.TaskRecordSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TaskRecordSyncServiceTest {

    @Mock
    private TaskRecordMapper taskRecordMapper;

    @InjectMocks
    private TaskRecordSyncService taskRecordSyncService;

    @Test
    void syncFromTask_skipsNonConfirmedStatus() {
        Task task = new Task();
        task.setTaskId("20260703_001");
        task.setStatus("failed");
        task.setRawData("[{\"NOM_PRENOM\":\"A\"}]");

        taskRecordSyncService.syncFromTask(task);

        verify(taskRecordMapper).deleteByTaskId("20260703_001");
        verify(taskRecordMapper, never()).upsertBatch(org.mockito.ArgumentMatchers.anyList());
    }
}
