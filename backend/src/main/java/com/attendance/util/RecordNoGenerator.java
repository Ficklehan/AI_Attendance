package com.attendance.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class RecordNoGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    public synchronized String generate(String lastTaskId) {
        String dateStr = LocalDate.now().format(DATE_FORMATTER);
        int sequence = 1;

        if (lastTaskId != null && lastTaskId.startsWith(dateStr)) {
            int lastSeq = parseSequence(lastTaskId);
            if (lastSeq > 0) {
                sequence = lastSeq + 1;
            }
        }

        return formatTaskId(dateStr, sequence);
    }

    /** 在已有任务号基础上流水号 +1（主键冲突重试时使用） */
    public synchronized String nextAfter(String taskId) {
        String dateStr = LocalDate.now().format(DATE_FORMATTER);
        if (taskId == null || !taskId.startsWith(dateStr)) {
            return generate(null);
        }
        int seq = parseSequence(taskId);
        return formatTaskId(dateStr, seq > 0 ? seq + 1 : 1);
    }

    private static int parseSequence(String taskId) {
        String[] parts = taskId.split("_");
        if (parts.length != 2) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String formatTaskId(String dateStr, int sequence) {
        return String.format("%s_%03d", dateStr, sequence);
    }
}
