package com.attendance.util;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
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

    /**
     * 每张图片写入一个独立单元格（各自一个真实超链接）。
     *
     * <p>Excel/WPS 不支持在单个单元格内放置多个 HYPERLINK（拼接会退化为不可用的文本地址），
     * 因此多图时按 startCol 起向右铺开：单图显示“查看原图”，多图显示“图1、图2…”。</p>
     *
     * @return 实际写入的链接单元格数量（即占用的列数）。
     */
    public static int writeImageLinkCells(Workbook workbook, Row row, int startCol, List<String> urls,
                                          CellStyle linkStyle) {
        if (row == null || urls == null || urls.isEmpty()) {
            return 0;
        }
        CreationHelper helper = workbook.getCreationHelper();
        boolean single = urls.size() == 1;
        int written = 0;
        for (int i = 0; i < urls.size(); i++) {
            Cell cell = row.createCell(startCol + i);
            if (linkStyle != null) {
                cell.setCellStyle(linkStyle);
            }
            String url = urls.get(i) != null ? urls.get(i).trim() : "";
            if (url.isEmpty()) {
                cell.setCellValue("");
                continue;
            }
            Hyperlink hyperlink = helper.createHyperlink(HyperlinkType.URL);
            hyperlink.setAddress(url);
            cell.setHyperlink(hyperlink);
            cell.setCellValue(single ? "查看原图" : ("图" + (i + 1)));
            written++;
        }
        return Math.max(written, urls.size());
    }
}
