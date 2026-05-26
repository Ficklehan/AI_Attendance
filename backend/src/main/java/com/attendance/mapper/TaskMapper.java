package com.attendance.mapper;

import com.attendance.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskMapper {

    Task selectTaskByTaskId(@Param("taskId") String taskId);

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

    String selectLastTaskId();

    int insertTask(Task task);

    int updateTask(Task task);

    int updateTaskStatus(@Param("taskId") String taskId, @Param("status") String status);

    int updateTaskRawData(@Param("taskId") String taskId, @Param("rawData") String rawData,
                          @Param("aiRawOutput") String aiRawOutput);

    int updateTaskConfirmedData(@Param("taskId") String taskId, @Param("confirmedData") String confirmedData);

    void updateTaskImageUrls(@Param("taskId") String taskId, @Param("imageUrls") String imageUrls);

    void updateTaskAnomalySummary(@Param("taskId") String taskId, @Param("anomalySummary") String anomalySummary);

    int deleteTaskByTaskId(@Param("taskId") String taskId);
}
