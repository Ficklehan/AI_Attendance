package com.attendance.mapper;

import com.attendance.entity.ReminderDelivery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ReminderDeliveryMapper {

    int existsDelivery(@Param("ruleId") String ruleId,
                       @Param("taskId") String taskId,
                       @Param("userId") String userId,
                       @Param("periodBucket") String periodBucket);

    /** 一次取出某规则/任务/用户已投递的 period_bucket，避免补发扫描逐周期 EXISTS。 */
    List<String> selectDeliveredPeriodBuckets(@Param("ruleId") String ruleId,
                                              @Param("taskId") String taskId,
                                              @Param("userId") String userId);

    /** 按规则批量加载投递桶，扫描时内存分组，避免 per-(task,user) 往返。 */
    List<Map<String, Object>> selectDeliveredBucketsByRule(@Param("ruleId") String ruleId);

    /** 仅加载扫描任务池内的投递桶，避免百万级历史 period 全表读入。 */
    List<Map<String, Object>> selectDeliveredBucketsByRuleAndTasks(@Param("ruleId") String ruleId,
                                                                   @Param("taskIds") List<String> taskIds);

    int insertDelivery(ReminderDelivery delivery);

    /** 唯一键冲突时忽略，热路径不再先 EXISTS。 */
    int insertDeliveryIgnore(ReminderDelivery delivery);

    int countByUserAndTask(@Param("userId") String userId, @Param("taskId") String taskId);
}
