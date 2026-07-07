package com.attendance.util;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;

/**
 * Shared Excel export styling: header band, bordered body cells, shrink-to-fit, column sizing.
 */
public final class ExcelExportStyles {

    private static final short BORDER_COLOR = IndexedColors.GREY_40_PERCENT.getIndex();

    private ExcelExportStyles() {
    }

    public static CellStyle createHeaderStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        applyThinBorders(style);
        return style;
    }

    public static CellStyle createBodyStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setFontHeightInPoints((short) 10);

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setWrapText(true);
        style.setShrinkToFit(true);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.TOP);
        applyThinBorders(style);
        return style;
    }

    public static CellStyle createTotalRowStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 10);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());

        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setWrapText(true);
        style.setShrinkToFit(true);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        applyThinBorders(style);
        return style;
    }

    /** @deprecated use {@link #createBodyStyle(Workbook)} */
    public static CellStyle createBodyStyle(Workbook workbook, boolean zebra) {
        return createBodyStyle(workbook);
    }

    public static void applyThinBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setTopBorderColor(BORDER_COLOR);
        style.setBottomBorderColor(BORDER_COLOR);
        style.setLeftBorderColor(BORDER_COLOR);
        style.setRightBorderColor(BORDER_COLOR);
    }

    /**
     * Auto-size columns within [minChars, maxChars]; cells use shrink-to-fit when content still overflows.
     */
    public static void fitColumns(Sheet sheet, int columnCount, int minChars, int maxChars) {
        if (sheet == null || columnCount <= 0) {
            return;
        }
        int minWidth = Math.max(1, minChars) * 256;
        int maxWidth = Math.max(minChars, maxChars) * 256;
        for (int col = 0; col < columnCount; col++) {
            try {
                sheet.autoSizeColumn(col);
            } catch (Exception ignored) {
                // SXSSF may not autosize flushed rows; keep preset width
            }
            int width = sheet.getColumnWidth(col);
            if (width < minWidth) {
                width = minWidth;
            }
            if (width > maxWidth) {
                width = maxWidth;
            }
            sheet.setColumnWidth(col, width);
        }
    }
}
