package com.attendance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface RoleDataScopeMapper {

    String selectScopeType(@Param("role") String role);

    List<Map<String, String>> selectRulesByRole(@Param("role") String role);

    List<String> selectAllRoles();

    int upsertScopeType(@Param("role") String role, @Param("scopeType") String scopeType);

    int deleteRulesByRole(@Param("role") String role);

    int deleteScopeByRole(@Param("role") String role);

    int insertRule(@Param("role") String role,
                   @Param("dimension") String dimension,
                   @Param("value") String value);

    List<Map<String, String>> selectDistinctCountryOptions();

    List<Map<String, String>> selectDistinctWarehouseOptions();

    List<Map<String, String>> selectDistinctAgencyOptions();
}
