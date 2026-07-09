package com.attendance.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class ExcelExportHelper {

    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final float DEFAULT_ROW_HEIGHT_POINTS = 18f;

    private ExcelExportHelper() {}

    public static ExcelSheetWriter open(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        return new ExcelSheetWriter(path);
    }

    public static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDate) {
            return DATE.format((LocalDate) value);
        }
        if (value instanceof LocalDateTime) {
            return DT.format((LocalDateTime) value);
        }
        if (value instanceof LocalTime) {
            return TIME.format((LocalTime) value);
        }
        if (value instanceof ZonedDateTime) {
            return DT.format((ZonedDateTime) value);
        }
        if (value instanceof OffsetDateTime) {
            return DT.format((OffsetDateTime) value);
        }
        return String.valueOf(value);
    }

    public static final class ExcelSheetWriter implements AutoCloseable {

        private final SXSSFWorkbook workbook;
        private final Sheet sheet;
        private final Path path;
        private final CellStyle headerStyle;
        private final CellStyle bodyStyle;
        private final CellStyle totalRowStyle;
        private int rowNum;
        private int columnCount;
        private long dataRows;

        private ExcelSheetWriter(Path path) {
            this.path = path;
            this.workbook = new SXSSFWorkbook(200);
            this.sheet = workbook.createSheet("Sheet1");
            this.headerStyle = ExcelExportStyles.createHeaderStyle(workbook);
            this.bodyStyle = ExcelExportStyles.createBodyStyle(workbook);
            this.totalRowStyle = ExcelExportStyles.createTotalRowStyle(workbook);
            this.rowNum = 0;
            this.columnCount = 0;
            this.dataRows = 0;
        }

        public void writeHeader(String... headers) {
            writeCells(headerStyle, false, headers);
            if (headers != null && headers.length > 0) {
                columnCount = Math.max(columnCount, headers.length);
                sheet.createFreezePane(0, rowNum - 1);
            }
        }

        public void writeRow(String... cells) {
            writeCells(bodyStyle, true, cells);
        }

        public void writeTotalRow(String... cells) {
            writeCells(totalRowStyle, false, cells);
        }

        public void writeBlankRow() {
            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(10f);
        }

        private void writeCells(CellStyle style, boolean countAsData, String... cells) {
            if (cells == null || cells.length == 0) {
                return;
            }
            columnCount = Math.max(columnCount, cells.length);
            Row row = sheet.createRow(rowNum++);
            row.setHeightInPoints(style == headerStyle ? 26f : DEFAULT_ROW_HEIGHT_POINTS);
            for (int i = 0; i < cells.length; i++) {
                Cell cell = row.createCell(i);
                String value = cells[i] != null ? cells[i] : "";
                cell.setCellValue(value);
                cell.setCellStyle(style);
            }
            if (countAsData) {
                dataRows++;
            }
        }

        public long getDataRowCount() {
            return dataRows;
        }

        @Override
        public void close() throws IOException {
            try {
                if (columnCount > 0) {
                    ExcelExportStyles.fitColumns(sheet, columnCount, 6, 48);
                }
                try (OutputStream out = Files.newOutputStream(path)) {
                    workbook.write(out);
                }
            } finally {
                workbook.dispose();
                workbook.close();
            }
        }
    }
}
