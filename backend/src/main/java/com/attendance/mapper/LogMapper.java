package com.attendance.mapper;

import com.attendance.entity.Log;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LogMapper {

    Log selectLogById(@Param("id") Long id);

    List<Log> selectLogByTaskId(@Param("taskId") String taskId);

    List<Log> selectLogByLogType(@Param("logType") String logType,
                                  @Param("offset") long offset,
                                  @Param("size") long size);

    int insertLog(Log log);

    int deleteLogByTaskId(@Param("taskId") String taskId);
}
