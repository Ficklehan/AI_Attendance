package com.attendance.mapper;

import com.attendance.entity.TaskRecognitionEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskRecognitionEventMapper {

    int insert(TaskRecognitionEvent event);

    Integer selectMaxSeq(@Param("taskId") String taskId);

    List<TaskRecognitionEvent> selectAfterSeq(@Param("taskId") String taskId,
                                              @Param("afterSeq") int afterSeq,
                                              @Param("limit") int limit);

    int deleteByTaskId(@Param("taskId") String taskId);
}
