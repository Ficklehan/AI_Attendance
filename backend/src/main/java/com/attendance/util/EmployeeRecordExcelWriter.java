package com.attendance.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 考勤记录导出专用 Excel 写入器，支持嵌入原图（适中展示尺寸）与多行原图链接。
 */
public final class EmployeeRecordExcelWriter implements AutoCloseable {

    private static final int IMAGE_PADDING_PX = 6;
    private static final int IMAGE_GAP_PX = 6;
    private static final float DEFAULT_ROW_HEIGHT_POINTS = 20f;

    private final XSSFWorkbook workbook;
    private final XSSFSheet sheet;
    private final Path path;
    private final CellStyle headerStyle;
    private final CellStyle bodyStyle;
    private final CellStyle linkStyle;
    private final boolean embedImages;
    private final int baseColumnCount;
    private final int thumbColIndex;
    private final int linkColIndex;
    private int rowNum;
    private long dataRows;
    private int maxImageStripWidthPx;

    private EmployeeRecordExcelWriter(Path path, boolean embedImages, int baseColumnCount, String sheetName)
            throws IOException {
        this.path = path;
        this.embedImages = embedImages;
        this.baseColumnCount = baseColumnCount;
        this.workbook = new XSSFWorkbook();
        this.sheet = workbook.createSheet(sheetName != null && !sheetName.trim().isEmpty()
                ? sheetName.trim() : ExportLocaleSupport.text(null, "sheet.attendanceRecords"));
        Files.createDirectories(path.getParent());
        this.headerStyle = ExcelExportStyles.createHeaderStyle(workbook);
        this.bodyStyle = ExcelExportStyles.createBodyStyle(workbook);
        this.linkStyle = ExportExcelLinkCells.createLinkStyle(workbook, bodyStyle);
        this.thumbColIndex = baseColumnCount;
        this.linkColIndex = baseColumnCount + 1;
        this.rowNum = 0;
        this.dataRows = 0;
        sheet.createFreezePane(0, 1);
        applyPresetColumnWidths();
    }

    public static EmployeeRecordExcelWriter open(Path path, boolean embedImages, int baseColumnCount, String sheetName)
            throws IOException {
        return new EmployeeRecordExcelWriter(path, embedImages, baseColumnCount, sheetName);
    }

    public static EmployeeRecordExcelWriter open(Path path, boolean embedImages, int baseColumnCount)
            throws IOException {
        return open(path, embedImages, baseColumnCount, null);
    }

    public void writeHeader(String... headers) {
        if (headers == null || headers.length == 0) {
            return;
        }
        Row row = sheet.createRow(rowNum++);
        row.setHeightInPoints(28f);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i] != null ? headers[i] : "");
            cell.setCellStyle(headerStyle);
        }
    }

    public void writeRecordRow(String[] baseCells, List<String> imageUrls,
                               List<EmployeeRecordExportImages.ExportImage> exportImages) {
        if (baseCells == null) {
            return;
        }
        boolean hasImages = embedImages && exportImages != null && !exportImages.isEmpty();

        int maxDisplayHeight = 0;
        if (hasImages) {
            for (EmployeeRecordExportImages.ExportImage image : exportImages) {
                if (image != null) {
                    maxDisplayHeight = Math.max(maxDisplayHeight, image.getDisplayHeightPx());
                }
            }
        }

        Row row = sheet.createRow(rowNum++);
        row.setHeightInPoints(hasImages && maxDisplayHeight > 0
                ? pixelsToPoints(maxDisplayHeight + IMAGE_PADDING_PX * 2)
                : DEFAULT_ROW_HEIGHT_POINTS);

        for (int i = 0; i < baseCells.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(baseCells[i] != null ? baseCells[i] : "");
            cell.setCellStyle(bodyStyle);
        }

        Cell thumbCell = row.createCell(thumbColIndex);
        thumbCell.setCellStyle(bodyStyle);

        if (imageUrls != null && !imageUrls.isEmpty()) {
            Cell linkCell = row.createCell(linkColIndex);
            ExportExcelLinkCells.writeImageLinkCell(workbook, linkCell, imageUrls, linkStyle);
        } else {
            Cell linkCell = row.createCell(linkColIndex);
            linkCell.setCellStyle(bodyStyle);
        }

        if (hasImages) {
            XSSFDrawing drawing = sheet.createDrawingPatriarch();
            int offsetPx = IMAGE_PADDING_PX;
            int topPx = IMAGE_PADDING_PX;
            for (EmployeeRecordExportImages.ExportImage image : exportImages) {
                if (image == null || image.getData() == null || image.getData().length == 0) {
                    continue;
                }
                int displayW = image.getDisplayWidthPx();
                int displayH = image.getDisplayHeightPx();
                int pictureIdx = workbook.addPicture(image.getData(), image.getPictureType());
                XSSFClientAnchor anchor = new XSSFClientAnchor();
                anchor.setCol1(thumbColIndex);
                anchor.setCol2(thumbColIndex);
                anchor.setRow1(row.getRowNum());
                anchor.setRow2(row.getRowNum());
                anchor.setDx1(Units.pixelToEMU(offsetPx));
                anchor.setDy1(Units.pixelToEMU(topPx));
                anchor.setDx2(Units.pixelToEMU(offsetPx + displayW));
                anchor.setDy2(Units.pixelToEMU(topPx + displayH));
                anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_DONT_RESIZE);
                drawing.createPicture(anchor, pictureIdx);
                offsetPx += displayW + IMAGE_GAP_PX;
            }
            if (offsetPx > IMAGE_PADDING_PX) {
                int stripWidth = offsetPx - IMAGE_GAP_PX + IMAGE_PADDING_PX;
                maxImageStripWidthPx = Math.max(maxImageStripWidthPx, stripWidth);
            }
        }

        dataRows++;
    }

    public long getDataRowCount() {
        return dataRows;
    }

    private static float pixelsToPoints(int pixels) {
        return pixels * 72f / 96f;
    }

    private void applyPresetColumnWidths() {
        int[] baseWidths = {
                14, 10, 10, 18, 16,
                6, 8, 12, 6, 8, 11, 14, 10,
                8, 8, 8, 10, 10, 22, 24, 12
        };
        int count = Math.min(baseColumnCount, baseWidths.length);
        for (int i = 0; i < count; i++) {
            sheet.setColumnWidth(i, baseWidths[i] * 256);
        }
        sheet.setColumnWidth(thumbColIndex, columnWidthForPixels(
                EmployeeRecordExportImages.THUMB_COLUMN_DEFAULT_WIDTH_PX));
        sheet.setColumnWidth(linkColIndex, 14 * 256);
    }

    private static int columnWidthForPixels(int pixels) {
        int chars = (int) Math.ceil(pixels / 7.0) + 1;
        chars = Math.max(14, Math.min(44, chars));
        return chars * 256;
    }

    private void applyThumbColumnWidth() {
        int widthPx = maxImageStripWidthPx > 0
                ? maxImageStripWidthPx + 4
                : EmployeeRecordExportImages.THUMB_COLUMN_DEFAULT_WIDTH_PX;
        sheet.setColumnWidth(thumbColIndex, columnWidthForPixels(widthPx));
    }

    @Override
    public void close() throws IOException {
        try {
            ExcelExportStyles.fitColumns(sheet, baseColumnCount, 6, 52);
            applyThumbColumnWidth();
            sheet.setColumnWidth(linkColIndex, 14 * 256);
            try (OutputStream out = Files.newOutputStream(path)) {
                workbook.write(out);
            }
        } finally {
            workbook.close();
        }
    }
}
