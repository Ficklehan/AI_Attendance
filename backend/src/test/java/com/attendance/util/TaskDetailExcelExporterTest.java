package com.attendance.util;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDetailExcelExporterTest {

    @Test
    void pairHeaderJoinsFieldAndSide() {
        assertEquals("离开时间（修改前）", TaskDetailExcelExporter.pairHeader("离开时间", "修改前"));
        assertEquals("Departure（After）", TaskDetailExcelExporter.pairHeader("Departure", "After"));
        assertEquals("修改后", TaskDetailExcelExporter.pairHeader(" ", "修改后"));
    }

    @Test
    void buildChangeHeadersKeepsIdColumnsAndSplitsFields() {
        String[] source = {"页码", "NO", "姓名", "离开时间"};
        String[] headers = TaskDetailExcelExporter.buildChangeHeaders(source, "修改前", "修改后");
        assertEquals(6, headers.length);
        assertEquals("页码", headers[0]);
        assertEquals("NO", headers[1]);
        assertEquals("姓名（修改前）", headers[2]);
        assertEquals("姓名（修改后）", headers[3]);
        assertEquals("离开时间（修改前）", headers[4]);
        assertEquals("离开时间（修改后）", headers[5]);
    }

    @Test
    void changesSheetWritesOneRowWithBeforeAfterColumns() throws Exception {
        JSONObject record = new JSONObject(true);
        record.put("PAGE_NUM", "1");
        record.put("NO", "7");
        record.put("NOM_PRENOM", "Ribiel");
        record.put("ARRIVEE", "16:28");
        record.put("DEPAR", "22:50");
        record.put("PAUSE", 0);
        JSONObject baseline = new JSONObject(true);
        baseline.put("NOM_PRENOM", "Ribiel");
        baseline.put("ARRIVEE", "16:28");
        baseline.put("DEPAR", "20:23");
        baseline.put("PAUSE", 0);
        record.put("_aiBaseline", baseline);

        JSONArray records = new JSONArray();
        records.add(record);
        Path path = TaskDetailExcelExporter.exportRecords(records, "zh-CN");
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(path))) {
            Sheet sheet = workbook.getSheet("修改前后");
            assertEquals(1, sheet.getLastRowNum());
            Row header = sheet.getRow(0);
            Row data = sheet.getRow(1);
            assertEquals("7", data.getCell(1).getStringCellValue());
            int beforeCol = -1;
            int afterCol = -1;
            for (int c = 0; c < header.getLastCellNum(); c++) {
                String title = header.getCell(c).getStringCellValue();
                if ("离开时间（修改前）".equals(title)) {
                    beforeCol = c;
                } else if ("离开时间（修改后）".equals(title)) {
                    afterCol = c;
                }
            }
            assertTrue(beforeCol >= 0);
            assertEquals(beforeCol + 1, afterCol);
            assertEquals("20:23", data.getCell(beforeCol).getStringCellValue());
            assertEquals("22:50", data.getCell(afterCol).getStringCellValue());
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
