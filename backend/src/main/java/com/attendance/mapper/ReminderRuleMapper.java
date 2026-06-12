package com.attendance.mapper;

import com.attendance.entity.ReminderRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReminderRuleMapper {

    List<ReminderRule> selectAll();

    List<ReminderRule> selectEnabled();

    ReminderRule selectById(@Param("id") String id);

    int insertRule(ReminderRule rule);

    int updateRule(ReminderRule rule);

    int updateEnabled(@Param("id") String id, @Param("enabled") boolean enabled);

    int updateLastRun(@Param("id") String id,
                      @Param("hitCount") int hitCount,
                      @Param("sentCount") int sentCount);

    int deleteById(@Param("id") String id);

    List<String> selectRecipientUserIds(@Param("ruleId") String ruleId);

    int deleteRecipients(@Param("ruleId") String ruleId);

    int insertRecipient(@Param("ruleId") String ruleId, @Param("userId") String userId);
}
