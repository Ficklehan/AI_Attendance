package com.attendance.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReminderFeishuMessageMapper {

    String selectMessageId(@Param("userId") String userId, @Param("ruleId") String ruleId);

    int upsertMessageId(@Param("userId") String userId,
                        @Param("ruleId") String ruleId,
                        @Param("feishuMessageId") String feishuMessageId);

    int deleteMessageId(@Param("userId") String userId, @Param("ruleId") String ruleId);
}
