package com.attendance.mapper;

import com.attendance.entity.Employee;
import com.attendance.security.DataScopeContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface EmployeeMapper {

    Employee selectByIdentity(@Param("regionCode") String regionCode,
                              @Param("agencyKey") String agencyKey,
                              @Param("matchName") String matchName);

    int insertEmployee(Employee employee);

    int updateLastSeen(@Param("id") Long id,
                       @Param("displayName") String displayName,
                       @Param("lastAttendanceDate") LocalDate lastAttendanceDate,
                       @Param("lastSeenAt") java.time.LocalDateTime lastSeenAt);

    int ensureSerialCounter(@Param("regionCode") String regionCode);

    int incrementSerialCounter(@Param("regionCode") String regionCode);

    Integer selectLastInsertId();

    long countEmployees(@Param("scope") DataScopeContext scope,
                        @Param("regionCodes") List<String> regionCodes,
                        @Param("keyword") String keyword);

    List<Employee> selectEmployeePage(@Param("scope") DataScopeContext scope,
                                      @Param("regionCodes") List<String> regionCodes,
                                      @Param("keyword") String keyword,
                                      @Param("offset") long offset,
                                      @Param("size") long size);

    List<Map<String, Object>> selectWeeklyAttendanceRows(@Param("scope") DataScopeContext scope,
                                                           @Param("regionCodes") List<String> regionCodes,
                                                           @Param("startDate") String startDate,
                                                           @Param("endDate") String endDate,
                                                           @Param("keyword") String keyword);

    List<Map<String, String>> selectDistinctWorkRegionOptions();
}
