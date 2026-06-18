package com.attendance.mapper;

import com.attendance.entity.ReminderSchedule;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ReminderScheduleMapper {

    int insertSchedule(ReminderSchedule schedule);

    int markSent(@Param("id") String id);

    int markCancelled(@Param("id") String id);

    int cancelPendingByTask(@Param("taskId") String taskId);

    int cancelPendingByRule(@Param("ruleId") String ruleId);

    int cancelPendingByRuleAndTask(@Param("ruleId") String ruleId, @Param("taskId") String taskId);

    int deleteReschedulableByRule(@Param("ruleId") String ruleId);

    int deleteReschedulableByTask(@Param("taskId") String taskId);

    int cancelAllPending();

    List<ReminderSchedule> selectDuePending(@Param("now") LocalDateTime now, @Param("limit") int limit);

    ReminderSchedule selectPendingForRecipientPeriod(@Param("ruleId") String ruleId,
                                                     @Param("taskId") String taskId,
                                                     @Param("userId") String userId,
                                                     @Param("periodBucket") String periodBucket);

    ReminderSchedule selectForRecipientPeriod(@Param("ruleId") String ruleId,
                                              @Param("taskId") String taskId,
                                              @Param("userId") String userId,
                                              @Param("periodBucket") String periodBucket);

    int updateReschedule(ReminderSchedule schedule);
}
