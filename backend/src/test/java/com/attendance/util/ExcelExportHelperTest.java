package com.attendance.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelExportHelperTest {

    @Test
    void cell_formatsLocalDateWithoutTimeFields() {
        assertEquals("2026-07-09", ExcelExportHelper.cell(LocalDate.of(2026, 7, 9)));
    }

    @Test
    void cell_formatsLocalDateTimeWithTime() {
        assertEquals(
                "2026-07-09 10:30:45",
                ExcelExportHelper.cell(LocalDateTime.of(2026, 7, 9, 10, 30, 45)));
    }
}
