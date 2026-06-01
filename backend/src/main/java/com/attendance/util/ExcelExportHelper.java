package com.attendance.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;

public final class ExcelExportHelper {

    public static final String CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private ExcelExportHelper() {}

    public static ExcelSheetWriter open(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        return new ExcelSheetWriter(path);
    }

    public static String cell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof TemporalAccessor) {
            return DT.format((TemporalAccessor) value);
        }
        return String.valueOf(value);
    }

    public static final class ExcelSheetWriter implements AutoCloseable {

        private final SXSSFWorkbook workbook;
        private final Sheet sheet;
        private final Path path;
        private final CellStyle headerStyle;
        private int rowNum;
        private long dataRows;

        private ExcelSheetWriter(Path path) {
            this.path = path;
            this.workbook = new SXSSFWorkbook(200);
            this.sheet = workbook.createSheet("Sheet1");
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            this.headerStyle = workbook.createCellStyle();
            this.headerStyle.setFont(headerFont);
            this.rowNum = 0;
            this.dataRows = 0;
        }

        public void writeHeader(String... headers) {
            if (headers == null || headers.length == 0) {
                return;
            }
            Row row = sheet.createRow(rowNum++);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = row.createCell(i);
                cell.setCellValue(headers[i] != null ? headers[i] : "");
                cell.setCellStyle(headerStyle);
            }
        }

        public void writeRow(String... cells) {
            if (cells == null || cells.length == 0) {
                return;
            }
            Row row = sheet.createRow(rowNum++);
            for (int i = 0; i < cells.length; i++) {
                Cell cell = row.createCell(i);
                String value = cells[i] != null ? cells[i] : "";
                cell.setCellValue(value);
            }
            dataRows++;
        }

        public long getDataRowCount() {
            return dataRows;
        }

        @Override
        public void close() throws IOException {
            try (OutputStream out = Files.newOutputStream(path)) {
                workbook.write(out);
            } finally {
                workbook.dispose();
                workbook.close();
            }
        }
    }
}
