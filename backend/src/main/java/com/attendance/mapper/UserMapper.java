package com.attendance.mapper;

import com.attendance.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    User selectUserById(@Param("id") String id);

    String selectUserStatusById(@Param("id") String id);

    User selectUserByUsername(@Param("username") String username);

    User selectUserByEmail(@Param("email") String email);

    List<User> selectUserList(@Param("offset") long offset, @Param("size") long size,
                              @Param("keyword") String keyword,
                              @Param("role") String role,
                              @Param("excludeRole") String excludeRole);

    long countUser(@Param("keyword") String keyword,
                   @Param("role") String role,
                   @Param("excludeRole") String excludeRole);

    int updateUserRole(@Param("id") String id, @Param("role") String role);

    int insertUser(User user);

    int updateUser(User user);

    int updateUserLastLogin(@Param("id") String id);

    int deleteUserById(@Param("id") String id);

    int existsByUsername(@Param("username") String username);

    int existsByEmail(@Param("email") String email);

    User selectUserByFeishuUserId(@Param("feishuUserId") String feishuUserId);

    int existsByFeishuUserId(@Param("feishuUserId") String feishuUserId);

    long countActiveByRole(@Param("role") String role);

    List<User> selectActiveUsers();
}
