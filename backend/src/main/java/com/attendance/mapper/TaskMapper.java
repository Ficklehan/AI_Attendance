package com.attendance.mapper;

import com.attendance.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskMapper {

    Task selectTaskByTaskId(@Param("taskId") String taskId);

    Task selectTaskOwningFileKey(@Param("fileKey") String fileKey, @Param("userId") String userId);

    /** 管理员查看任意用户任务附件时使用 */
    Task selectTaskByFileKey(@Param("fileKey") String fileKey);

    List<Task> selectTaskList(@Param("userId") String userId,
                               @Param("status") String status,
                               @Param("keyword") String keyword,
                               @Param("keywordField") String keywordField,
                               @Param("offset") long offset,
                               @Param("size") long size);

    long countTaskList(@Param("userId") String userId,
                       @Param("status") String status,
                       @Param("keyword") String keyword,
                       @Param("keywordField") String keywordField);

    List<java.util.Map<String, Object>> countTasksGroupByStatus(@Param("userId") String userId);

    String selectLastTaskId();

    int insertTask(Task task);

    int updateTask(Task task);

    int updateTaskStatus(@Param("taskId") String taskId, @Param("status") String status);

    int updateTaskRawData(@Param("taskId") String taskId, @Param("rawData") String rawData,
                          @Param("aiRawOutput") String aiRawOutput);

    /** 识别进行中写入 partial raw_data，不将 status 置为 processed */
    int updateTaskRawDataProgress(@Param("taskId") String taskId, @Param("rawData") String rawData,
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

    List<Task> selectTasksForDuplicateByStatuses(@Param("excludeTaskId") String excludeTaskId,
                                                 @Param("statuses") List<String> statuses);

    List<Task> selectTasksForRecordView(@Param("userId") String userId,
                                        @Param("status") String status);

    int deleteTaskByTaskId(@Param("taskId") String taskId);
}
