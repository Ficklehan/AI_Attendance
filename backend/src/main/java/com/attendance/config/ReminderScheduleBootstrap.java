package com.attendance.config;

import com.attendance.service.ReminderScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时排队提醒欠账补发（异步单飞，不阻塞就绪）。
 */
@Component
@Order(100)
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
            log.info("提醒计划启动补发已排队（异步单飞）");
        } catch (Exception e) {
            log.warn("提醒计划启动补发排队失败: {}", e.getMessage());
        }
    }
}
