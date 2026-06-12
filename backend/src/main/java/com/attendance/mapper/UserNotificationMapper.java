package com.attendance.mapper;

import com.attendance.entity.UserNotification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserNotificationMapper {

    List<UserNotification> selectByUser(@Param("userId") String userId,
                                        @Param("offset") long offset,
                                        @Param("size") long size);

    long countByUser(@Param("userId") String userId);

    long countUnread(@Param("userId") String userId);

    UserNotification selectById(@Param("id") String id);

    UserNotification selectUnreadByUserAndRule(@Param("userId") String userId,
                                             @Param("ruleId") String ruleId,
                                             @Param("periodBucket") String periodBucket);

    int deleteUnreadByUserAndRule(@Param("userId") String userId,
                                  @Param("ruleId") String ruleId,
                                  @Param("periodBucket") String periodBucket);

    int insertNotification(UserNotification notification);

    int updateFeishuMessageId(@Param("id") String id,
                              @Param("userId") String userId,
                              @Param("feishuMessageId") String feishuMessageId);

    int markRead(@Param("id") String id, @Param("userId") String userId);

    int markAllRead(@Param("userId") String userId);

    int deleteByIdForUser(@Param("id") String id, @Param("userId") String userId);

    int deleteAllForUser(@Param("userId") String userId);

    int deleteByTaskIdInLink(@Param("taskId") String taskId);

    java.util.List<UserNotification> selectRecentByUser(@Param("userId") String userId, @Param("limit") int limit);
}
