package com.attendance.mapper;

import com.attendance.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AuditLogMapper {

    List<AuditLog> selectAuditLogList(@Param("userId") String userId,
                                      @Param("action") String action,
                                      @Param("startDate") String startDate,
                                      @Param("endDate") String endDate,
                                      @Param("offset") long offset,
                                      @Param("size") long size);

    long countAuditLogList(@Param("userId") String userId,
                           @Param("action") String action,
                           @Param("startDate") String startDate,
                           @Param("endDate") String endDate);

    int insertAuditLog(AuditLog auditLog);
}
