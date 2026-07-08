package com.attendance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserRoleMapper {

    List<String> selectRoleKeysByUserId(@Param("userId") String userId);

    List<Map<String, Object>> selectRoleKeysByUserIds(@Param("userIds") List<String> userIds);

    int insertUserRole(@Param("userId") String userId, @Param("roleKey") String roleKey);

    int deleteUserRole(@Param("userId") String userId, @Param("roleKey") String roleKey);

    int deleteAllRolesForUser(@Param("userId") String userId);

    long countUsersByRoleKey(@Param("roleKey") String roleKey);

    long countActiveAdmins();

    int existsUserRole(@Param("userId") String userId, @Param("roleKey") String roleKey);
}
