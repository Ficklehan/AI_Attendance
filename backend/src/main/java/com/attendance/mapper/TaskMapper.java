package com.attendance.mapper;

import com.attendance.dto.response.TaskProgressDTO;
import com.attendance.entity.Task;
import com.attendance.entity.TaskListRow;
import com.attendance.security.DataScopeContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskMapper {

    Task selectTaskByTaskId(@Param("taskId") String taskId);

    /** Lightweight row for progress/auth checks (no raw_data). */
    Task selectTaskAccessMeta(@Param("taskId") String taskId);

    TaskProgressDTO selectTaskProgress(@Param("taskId") String taskId);

    Task selectTaskByFileKeyForScope(@Param("fileKey") String fileKey,
                                     @Param("scope") DataScopeContext scope);

    Task selectTaskByFileKeyAndUserId(@Param("fileKey") String fileKey,
                                       @Param("userId") String userId);

    List<TaskListRow> selectTaskList(@Param("scope") DataScopeContext scope,
                                     @Param("status") String status,
                                     @Param("keyword") String keyword,
                                     @Param("keywordField") String keywordField,
                                     @Param("offset") long offset,
                                     @Param("size") long size);

    long countTaskList(@Param("scope") DataScopeContext scope,
                       @Param("status") String status,
                       @Param("keyword") String keyword,
                       @Param("keywordField") String keywordField);

    List<java.util.Map<String, Object>> countTasksGroupByStatus(@Param("scope") DataScopeContext scope);

    /** 当日最大序号任务号（按数字序号，非 created_at） */
    String selectMaxTaskIdForDate(@Param("datePrefix") String datePrefix);

    List<Task> selectTasksByStatuses(@Param("statuses") List<String> statuses);

    int insertTask(Task task);

    int updateTask(Task task);

    int updateTaskStatus(@Param("taskId") String taskId, @Param("status") String status);

    int updateTaskRawData(@Param("taskId") String taskId, @Param("rawData") String rawData,
                          @Param("aiRawOutput") String aiRawOutput,
                          @Param("progressRowCount") int progressRowCount);

    /** 识别进行中写入 partial raw_data，不将 status 置为 processed */
    int updateTaskRawDataProgress(@Param("taskId") String taskId, @Param("rawData") String rawData,
                                  @Param("aiRawOutput") String aiRawOutput,
                                  @Param("progressRowCount") int progressRowCount);

    /** 仅更新进度计数与引擎标记，不写 raw_data */
    int updateTaskRecognitionProgress(@Param("taskId") String taskId,
                                      @Param("progressRowCount") int progressRowCount,
                                      @Param("aiRawOutput") String aiRawOutput);

    int updateTaskConfirmedData(@Param("taskId") String taskId, @Param("confirmedData") String confirmedData);

    /** 校准等场景：同时更新 raw/confirmed，不改变 status */
    int updateTaskRecordPayload(@Param("taskId") String taskId,
                                @Param("rawData") String rawData,
                                @Param("confirmedData") String confirmedData);

    int updateTaskSyncStatus(@Param("taskId") String taskId,
                             @Param("syncStatus") String syncStatus,
                             @Param("syncError") String syncError);

    void updateTaskImageUrls(@Param("taskId") String taskId, @Param("imageUrls") String imageUrls);

    void updateTaskAnomalySummary(@Param("taskId") String taskId, @Param("anomalySummary") String anomalySummary);

    int updateRecognitionCheckpoint(@Param("taskId") String taskId,
                                    @Param("checkpoint") String checkpoint);

    int clearRecognitionCheckpoint(@Param("taskId") String taskId);

    int touchRecognitionHeartbeat(@Param("taskId") String taskId);

    List<String> selectStaleProcessingTaskIds(@Param("staleSeconds") long staleSeconds,
                                                @Param("batchSize") int batchSize);

    List<String> selectZombieProcessingTaskIds(@Param("zombieMinutes") int zombieMinutes,
                                               @Param("batchSize") int batchSize);

    List<Task> selectTasksForDuplicateByStatuses(@Param("excludeTaskId") String excludeTaskId,
                                                 @Param("statuses") List<String> statuses);

    List<Task> selectTasksForRecordView(@Param("scope") DataScopeContext scope,
                                        @Param("status") String status);

    int deleteTaskByTaskId(@Param("taskId") String taskId);
}
