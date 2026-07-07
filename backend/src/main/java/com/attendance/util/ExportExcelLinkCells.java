package com.attendance.util;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.List;

public final class ExportExcelLinkCells {

    private ExportExcelLinkCells() {}

    public static CellStyle createLinkStyle(Workbook workbook, CellStyle wrapStyle) {
        Font linkFont = workbook.createFont();
        linkFont.setUnderline(Font.U_SINGLE);
        linkFont.setColor(IndexedColors.BLUE.getIndex());
        CellStyle style = workbook.createCellStyle();
        if (wrapStyle != null) {
            style.cloneStyleFrom(wrapStyle);
        } else {
            style.setWrapText(true);
        }
        style.setFont(linkFont);
        return style;
    }

    public static void writeImageLinkCell(Workbook workbook, Cell cell, List<String> urls, CellStyle linkStyle) {
        if (urls == null || urls.isEmpty()) {
            cell.setCellValue("");
            return;
        }
        if (linkStyle != null) {
            cell.setCellStyle(linkStyle);
        }
        if (urls.size() == 1) {
            CreationHelper helper = workbook.getCreationHelper();
            Hyperlink hyperlink = helper.createHyperlink(HyperlinkType.URL);
            hyperlink.setAddress(urls.get(0));
            cell.setHyperlink(hyperlink);
            cell.setCellValue("查看原图");
            return;
        }
        cell.setCellFormula(buildMultiHyperlinkFormula(urls));
    }

    static String buildMultiHyperlinkFormula(List<String> urls) {
        StringBuilder formula = new StringBuilder();
        for (int i = 0; i < urls.size(); i++) {
            if (i > 0) {
                formula.append("&CHAR(10)&");
            }
            formula.append("HYPERLINK(\"")
                    .append(escapeFormulaString(urls.get(i)))
                    .append("\",\"图")
                    .append(i + 1)
                    .append("\")");
        }
        return formula.toString();
    }

    private static String escapeFormulaString(String value) {
        return value == null ? "" : value.replace("\"", "\"\"");
    }
}
