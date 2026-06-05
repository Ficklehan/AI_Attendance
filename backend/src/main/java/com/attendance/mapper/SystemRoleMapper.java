package com.attendance.mapper;

import com.attendance.entity.SystemRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SystemRoleMapper {

    List<SystemRole> selectAll();

    SystemRole selectByKey(@Param("roleKey") String roleKey);

    int insert(SystemRole role);

    int updateName(@Param("roleKey") String roleKey, @Param("roleName") String roleName);

    int deleteByKey(@Param("roleKey") String roleKey);

    long countUsersByRole(@Param("roleKey") String roleKey);

    int maxSortOrder();
}
