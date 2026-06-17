package com.attendance;

import com.attendance.util.RecordNoGenerator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecordNoGeneratorTest {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final RecordNoGenerator generator = new RecordNoGenerator();

    @Test
    void generateFromNullStartsAtOne() {
        String today = LocalDate.now().format(DATE);
        assertEquals(today + "_001", generator.generate(null));
    }

    @Test
    void generateIncrementsSameDay() {
        String today = LocalDate.now().format(DATE);
        assertEquals(today + "_008", generator.generate(today + "_007"));
    }

    @Test
    void nextAfterIncrementsByOne() {
        String today = LocalDate.now().format(DATE);
        assertEquals(today + "_008", generator.nextAfter(today + "_007"));
        assertEquals(today + "_009", generator.nextAfter(today + "_008"));
    }
}
