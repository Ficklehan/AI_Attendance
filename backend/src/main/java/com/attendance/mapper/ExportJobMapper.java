package com.attendance.mapper;

import com.attendance.entity.ExportJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ExportJobMapper {

    int insert(ExportJob job);

    ExportJob selectById(@Param("id") String id);

    ExportJob selectByIdAndUserId(@Param("id") String id, @Param("userId") String userId);

    List<ExportJob> selectByUserId(@Param("userId") String userId,
                                   @Param("scope") String scope,
                                   @Param("offset") long offset,
                                   @Param("size") long size);

    long countByUserId(@Param("userId") String userId, @Param("scope") String scope);

    long countActiveByUserId(@Param("userId") String userId);

    int dismissFinishedByUserId(@Param("userId") String userId);

    int updateStatus(@Param("id") String id,
                     @Param("status") String status,
                     @Param("errorMessage") String errorMessage);

    int updateCompleted(@Param("id") String id,
                        @Param("status") String status,
                        @Param("fileName") String fileName,
                        @Param("filePath") String filePath,
                        @Param("rowCount") long rowCount,
                        @Param("errorMessage") String errorMessage);

    int markDownloaded(@Param("id") String id, @Param("userId") String userId);
}
