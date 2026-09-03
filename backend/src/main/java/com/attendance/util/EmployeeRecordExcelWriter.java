package com.attendance.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.util.Units;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 考勤记录导出专用 Excel 写入器。
 *
 * <p>采用 SXSSF 流式写入（行数据边写边刷盘、临时文件压缩），配合右尺寸预览嵌图，
 * 保证大批量考勤照也能可靠嵌入到下载文件中；原图全分辨率由链接列另行保留。</p>
 *
 * <p>每张图片单独 try/catch 并在失败时以标准 RGB PNG 重编码后重试一次，
 * 修复 CMYK/异常色彩模型等 POI 无法直接嵌入的图片；仅当重试仍失败才在单元格标注，
 * 绝不让单张坏图拖垮整份导出。</p>
 */
public final class EmployeeRecordExcelWriter implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EmployeeRecordExcelWriter.class);

    private static final int IMAGE_PADDING_PX = 6;
    private static final int IMAGE_GAP_PX = 6;
    private static final float DEFAULT_ROW_HEIGHT_POINTS = 20f;
    private static final int STREAM_WINDOW_ROWS = 100;

    private final SXSSFWorkbook workbook;
    private final Sheet sheet;
    private final Path path;
    private final CellStyle headerStyle;
    private final CellStyle bodyStyle;
    private final CellStyle linkStyle;
    private final boolean embedImages;
    private final int baseColumnCount;
    private final int thumbColIndex;
    private final int linkColIndex;
    private Drawing<?> drawing;
    private int rowNum;
    private long dataRows;
    private long embedFailures;
    private int maxImageStripWidthPx;
    private int maxLinkColumns = 1;

    private EmployeeRecordExcelWriter(Path path, boolean embedImages, int baseColumnCount, String sheetName)
            throws IOException {
        this.path = path;
        this.embedImages = embedImages;
        this.baseColumnCount = baseColumnCount;
        this.workbook = new SXSSFWorkbook(STREAM_WINDOW_ROWS);
        this.workbook.setCompressTempFiles(true);
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
        this.embedFailures = 0;
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
        int textLines = 1;
        for (String cellVal : baseCells) {
            if (cellVal == null || cellVal.isEmpty()) continue;
            int lines = 1;
            for (int i = 0; i < cellVal.length(); i++) {
                if (cellVal.charAt(i) == '\n') lines++;
            }
            if (lines > textLines) textLines = lines;
        }
        float textHeight = Math.min(DEFAULT_ROW_HEIGHT_POINTS * textLines, 120f);
        row.setHeightInPoints(hasImages && maxDisplayHeight > 0
                ? Math.max(pixelsToPoints(maxDisplayHeight + IMAGE_PADDING_PX * 2), textHeight)
                : textHeight);

        for (int i = 0; i < baseCells.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(baseCells[i] != null ? baseCells[i] : "");
            cell.setCellStyle(bodyStyle);
        }

        Cell thumbCell = row.createCell(thumbColIndex);
        thumbCell.setCellStyle(bodyStyle);

        if (imageUrls != null && !imageUrls.isEmpty()) {
            int used = ExportExcelLinkCells.writeImageLinkCells(workbook, row, linkColIndex, imageUrls, linkStyle);
            maxLinkColumns = Math.max(maxLinkColumns, used);
        } else {
            Cell linkCell = row.createCell(linkColIndex);
            linkCell.setCellStyle(bodyStyle);
        }

        if (hasImages) {
            List<Integer> failedOrdinals = embedRowImages(row, exportImages);
            if (!failedOrdinals.isEmpty()) {
                thumbCell.setCellValue(buildEmbedFailureNote(failedOrdinals));
            }
        }

        dataRows++;
    }

    /** 逐张嵌入本行图片；返回嵌入失败（含重试）的图片序号（从 1 开始）。 */
    private List<Integer> embedRowImages(Row row, List<EmployeeRecordExportImages.ExportImage> exportImages) {
        List<Integer> failedOrdinals = new ArrayList<>();
        int offsetPx = IMAGE_PADDING_PX;
        int topPx = IMAGE_PADDING_PX;
        int ordinal = 0;
        for (EmployeeRecordExportImages.ExportImage image : exportImages) {
            ordinal++;
            if (image == null || image.getData() == null || image.getData().length == 0) {
                failedOrdinals.add(ordinal);
                continue;
            }
            boolean embedded = tryEmbedPicture(row, image, offsetPx, topPx);
            if (!embedded) {
                EmployeeRecordExportImages.ExportImage normalized =
                        EmployeeRecordExportImages.reencodeForReliableEmbed(image);
                embedded = normalized != null && tryEmbedPicture(row, normalized, offsetPx, topPx);
            }
            if (embedded) {
                offsetPx += image.getDisplayWidthPx() + IMAGE_GAP_PX;
            } else {
                embedFailures++;
                failedOrdinals.add(ordinal);
                log.warn("考勤记录导出：第 {} 行第 {} 张图片嵌入失败（已重试重编码）", row.getRowNum() + 1, ordinal);
            }
        }
        if (offsetPx > IMAGE_PADDING_PX) {
            int stripWidth = offsetPx - IMAGE_GAP_PX + IMAGE_PADDING_PX;
            maxImageStripWidthPx = Math.max(maxImageStripWidthPx, stripWidth);
        }
        return failedOrdinals;
    }

    private boolean tryEmbedPicture(Row row, EmployeeRecordExportImages.ExportImage image, int offsetPx, int topPx) {
        try {
            int displayW = image.getDisplayWidthPx();
            int displayH = image.getDisplayHeightPx();
            int pictureIdx = workbook.addPicture(image.getData(), image.getPictureType());
            if (drawing == null) {
                drawing = sheet.createDrawingPatriarch();
            }
            ClientAnchor anchor = workbook.getCreationHelper().createClientAnchor();
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
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildEmbedFailureNote(List<Integer> failedOrdinals) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < failedOrdinals.size(); i++) {
            if (i > 0) {
                sb.append('、');
            }
            sb.append('图').append(failedOrdinals.get(i));
        }
        sb.append(" 嵌入失败，请用链接查看原图");
        return sb.toString();
    }

    public long getDataRowCount() {
        return dataRows;
    }

    public long getEmbedFailureCount() {
        return embedFailures;
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

    private void applyLinkColumnWidths() {
        int columns = Math.max(1, maxLinkColumns);
        for (int i = 0; i < columns; i++) {
            sheet.setColumnWidth(linkColIndex + i, (columns == 1 ? 14 : 8) * 256);
        }
    }

    @Override
    public void close() throws IOException {
        try {
            applyThumbColumnWidth();
            applyLinkColumnWidths();
            try (OutputStream out = Files.newOutputStream(path)) {
                workbook.write(out);
            }
        } finally {
            workbook.dispose();
            workbook.close();
        }
    }
}
