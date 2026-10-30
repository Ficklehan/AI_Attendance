package com.attendance.recognition;

import com.attendance.recognition.dto.GroundedCell;
import com.attendance.recognition.dto.PageLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * P3：把 vision_ground 的格框收成稳定行。别名归一、按 Y 聚类分行、同行同列去重。
 */
public final class LayoutNormalizer {

    private LayoutNormalizer() {
    }

    public static void normalize(PageLayout layout) {
        if (layout == null || layout.cells == null || layout.cells.isEmpty()) {
            return;
        }
        List<GroundedCell> cleaned = new ArrayList<GroundedCell>();
        for (int i = 0; i < layout.cells.size(); i++) {
            GroundedCell cell = layout.cells.get(i);
            if (cell == null || cell.bbox == null || cell.bbox.w < 2 || cell.bbox.h < 2) {
                continue;
            }
            cell.field = HarnessColumnCatalog.canonicalField(cell.field);
            if (cell.field == null || cell.field.isEmpty()) {
                continue;
            }
            cleaned.add(cell);
        }
        reindexRowsByY(cleaned);
        List<GroundedCell> unique = dedupe(cleaned);
        clipOverlaps(unique);
        layout.cells = unique;
    }

    /**
     * pad / 定位框过高时会吃进邻行邻列，OCR 会把两行姓名、两个 60 拼在一起。
     */
    static void clipOverlaps(List<GroundedCell> cells) {
        if (cells == null || cells.size() < 2) {
            return;
        }
        Map<String, List<GroundedCell>> byField = new LinkedHashMap<String, List<GroundedCell>>();
        Map<Integer, List<GroundedCell>> byRow = new LinkedHashMap<Integer, List<GroundedCell>>();
        for (int i = 0; i < cells.size(); i++) {
            GroundedCell cell = cells.get(i);
            if (cell == null || cell.bbox == null) {
                continue;
            }
            List<GroundedCell> fieldGroup = byField.get(cell.field);
            if (fieldGroup == null) {
                fieldGroup = new ArrayList<GroundedCell>();
                byField.put(cell.field, fieldGroup);
            }
            fieldGroup.add(cell);
            Integer rowKey = Integer.valueOf(cell.row);
            List<GroundedCell> rowGroup = byRow.get(rowKey);
            if (rowGroup == null) {
                rowGroup = new ArrayList<GroundedCell>();
                byRow.put(rowKey, rowGroup);
            }
            rowGroup.add(cell);
        }
        for (List<GroundedCell> group : byField.values()) {
            Collections.sort(group, new Comparator<GroundedCell>() {
                @Override
                public int compare(GroundedCell a, GroundedCell b) {
                    return Integer.compare(a.bbox.y, b.bbox.y);
                }
            });
            for (int i = 0; i < group.size() - 1; i++) {
                GroundedCell cur = group.get(i);
                GroundedCell next = group.get(i + 1);
                int maxH = next.bbox.y - cur.bbox.y;
                if (maxH >= 4 && cur.bbox.h > maxH) {
                    cur.bbox.h = maxH;
                }
            }
        }
        for (List<GroundedCell> group : byRow.values()) {
            Collections.sort(group, new Comparator<GroundedCell>() {
                @Override
                public int compare(GroundedCell a, GroundedCell b) {
                    return Integer.compare(a.bbox.x, b.bbox.x);
                }
            });
            for (int i = 0; i < group.size() - 1; i++) {
                GroundedCell cur = group.get(i);
                GroundedCell next = group.get(i + 1);
                int maxW = next.bbox.x - cur.bbox.x;
                if (maxW >= 4 && cur.bbox.w > maxW) {
                    cur.bbox.w = maxW;
                }
            }
        }
    }

    static void reindexRowsByY(List<GroundedCell> cells) {
        if (cells == null || cells.isEmpty()) {
            return;
        }
        if (cells.size() == 1) {
            if (cells.get(0).row <= 0) {
                cells.get(0).row = 1;
            }
            return;
        }
        List<Integer> heights = new ArrayList<Integer>();
        for (int i = 0; i < cells.size(); i++) {
            heights.add(Integer.valueOf(Math.max(1, cells.get(i).bbox.h)));
        }
        Collections.sort(heights);
        int medianH = heights.get(heights.size() / 2).intValue();
        int thresh = Math.max(8, (int) Math.round(medianH * 0.55d));
        List<GroundedCell> byY = new ArrayList<GroundedCell>(cells);
        Collections.sort(byY, new Comparator<GroundedCell>() {
            @Override
            public int compare(GroundedCell a, GroundedCell b) {
                return Integer.compare(centerY(a), centerY(b));
            }
        });
        int row = 1;
        int clusterStart = centerY(byY.get(0));
        byY.get(0).row = 1;
        for (int i = 1; i < byY.size(); i++) {
            int center = centerY(byY.get(i));
            if (center - clusterStart > thresh) {
                row++;
                clusterStart = center;
            }
            byY.get(i).row = row;
        }
    }

    static List<GroundedCell> dedupe(List<GroundedCell> cells) {
        Map<String, GroundedCell> best = new LinkedHashMap<String, GroundedCell>();
        for (int i = 0; i < cells.size(); i++) {
            GroundedCell cell = cells.get(i);
            String key = cell.row + "|" + cell.field;
            GroundedCell prev = best.get(key);
            if (prev == null || area(cell) > area(prev)) {
                best.put(key, cell);
            }
        }
        return new ArrayList<GroundedCell>(best.values());
    }

    private static int centerY(GroundedCell cell) {
        return cell.bbox.y + cell.bbox.h / 2;
    }

    private static int area(GroundedCell cell) {
        return cell.bbox.w * cell.bbox.h;
    }
}
