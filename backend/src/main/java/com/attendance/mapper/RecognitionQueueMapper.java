package com.attendance.mapper;

import com.attendance.entity.RecognitionQueueJob;
import org.apache.ibatis.annotations.Param;

public interface RecognitionQueueMapper {

    int insertJob(RecognitionQueueJob job);

    RecognitionQueueJob selectById(@Param("id") String id);

    RecognitionQueueJob selectOldestPending();

    int markRunning(@Param("id") String id, @Param("instanceId") String instanceId);

    int countActiveByTaskId(@Param("taskId") String taskId);

    int markCompleted(@Param("id") String id);

    int markFailed(@Param("id") String id);

    int countPending();

    int requeue(@Param("id") String id);
}
