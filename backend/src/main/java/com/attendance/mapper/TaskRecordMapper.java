package com.attendance.mapper;

import com.attendance.dto.internal.AgencyBillingRow;
import com.attendance.entity.TaskRecord;
import com.attendance.security.DataScopeContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface TaskRecordMapper {

    int deleteByTaskId(@Param("taskId") String taskId);

    int batchInsert(@Param("rows") List<TaskRecord> rows);

    int upsertBatch(@Param("rows") List<TaskRecord> rows);

    int deleteByTaskIdExceptKeys(@Param("taskId") String taskId, @Param("rowKeys") List<String> rowKeys);

    List<TaskRecord> selectRecordPage(@Param("scope") DataScopeContext scope,
                                      @Param("status") String status,
                                      @Param("conditions") List<Map<String, String>> conditions,
                                      @Param("offset") long offset,
                                      @Param("size") long size);

    long countRecords(@Param("scope") DataScopeContext scope,
                      @Param("status") String status,
                      @Param("conditions") List<Map<String, String>> conditions);

    List<TaskRecord> selectForExport(@Param("scope") DataScopeContext scope,
                                       @Param("status") String status,
                                       @Param("conditions") List<Map<String, String>> conditions,
                                       @Param("offset") long offset,
                                       @Param("size") int size);

    List<Map<String, Object>> selectDuplicateBaseline(@Param("excludeTaskId") String excludeTaskId,
                                                      @Param("statuses") List<String> statuses,
                                                      @Param("workDates") List<String> workDates,
                                                      @Param("baseNames") List<String> baseNames,
                                                      @Param("scope") DataScopeContext scope);

    long countByTaskId(@Param("taskId") String taskId);

    long countRecordsMatchingScope(@Param("taskId") String taskId,
                                   @Param("countryMatchTokens") List<String> countryMatchTokens,
                                   @Param("warehouses") List<String> warehouses,
                                   @Param("agencies") List<String> agencies);

    List<AgencyBillingRow> selectAgencyBillingSlimRows(@Param("scope") DataScopeContext scope,
                                                       @Param("startDate") String startDate,
                                                       @Param("endDate") String endDate,
                                                       @Param("regionCodes") List<String> regionCodes,
                                                       @Param("warehouseKeys") List<String> warehouseKeys);
}
