package com.attendance.config;

import com.attendance.service.ReminderScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时为已启用规则与在途任务回填提醒计划。
 */
@Component
@Order(17)
public class ReminderScheduleBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReminderScheduleBootstrap.class);

    private final ReminderScheduleService reminderScheduleService;

    public ReminderScheduleBootstrap(ReminderScheduleService reminderScheduleService) {
        this.reminderScheduleService = reminderScheduleService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            reminderScheduleService.reconcileAllEnabledRules();
            log.info("提醒计划启动回填完成");
        } catch (Exception e) {
            log.warn("提醒计划启动回填失败: {}", e.getMessage());
        }
    }
}
