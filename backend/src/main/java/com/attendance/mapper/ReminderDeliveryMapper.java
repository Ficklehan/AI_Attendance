package com.attendance.mapper;

import com.attendance.entity.ReminderDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReminderDeliveryMapper {

    int existsDelivery(@Param("ruleId") String ruleId,
                       @Param("taskId") String taskId,
                       @Param("userId") String userId,
                       @Param("periodBucket") String periodBucket);

    int insertDelivery(ReminderDelivery delivery);

    int countByUserAndTask(@Param("userId") String userId, @Param("taskId") String taskId);
}
