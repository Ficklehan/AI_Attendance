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
            String[] parts = lastTaskId.split("_");
            if (parts.length == 2) {
                try {
                    sequence = Integer.parseInt(parts[1]) + 1;
                } catch (NumberFormatException ignored) {
                }
            }
        }

        return String.format("%s_%03d", dateStr, sequence);
    }
}
