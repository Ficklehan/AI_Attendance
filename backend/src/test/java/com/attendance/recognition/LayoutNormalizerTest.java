package com.attendance.recognition;

import com.attendance.recognition.dto.BBox;
import com.attendance.recognition.dto.GroundedCell;
import com.attendance.recognition.dto.PageLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LayoutNormalizerTest {

    @Test
    void canonicalizesItalianHeadersAndSplitsRowsByY() {
        PageLayout layout = new PageLayout();
        layout.cells.add(new GroundedCell(1, "Arrivo", new BBox(10, 10, 40, 20)));
        layout.cells.add(new GroundedCell(1, "Uscita", new BBox(60, 10, 40, 20)));
        layout.cells.add(new GroundedCell(1, "Arrivo", new BBox(10, 80, 40, 20)));
        layout.cells.add(new GroundedCell(1, "Uscita", new BBox(60, 80, 40, 20)));
        LayoutNormalizer.normalize(layout);
        assertEquals(4, layout.cells.size());
        int row1 = 0;
        int row2 = 0;
        for (GroundedCell cell : layout.cells) {
            if ("ARRIVEE".equals(cell.field) || "DEPAR".equals(cell.field)) {
                if (cell.row == 1) {
                    row1++;
                }
                if (cell.row == 2) {
                    row2++;
                }
            }
        }
        assertEquals(2, row1);
        assertEquals(2, row2);
    }

    @Test
    void dedupesSameRowFieldKeepingLargerBox() {
        PageLayout layout = new PageLayout();
        layout.cells.add(new GroundedCell(1, "NAME", new BBox(0, 0, 10, 10)));
        layout.cells.add(new GroundedCell(1, "NAME", new BBox(0, 0, 30, 20)));
        LayoutNormalizer.normalize(layout);
        assertEquals(1, layout.cells.size());
        assertEquals(30, layout.cells.get(0).bbox.w);
        assertEquals("NAME", layout.cells.get(0).field);
    }

    @Test
    void clipsVerticalOverlapSoOcrDoesNotReadNextRow() {
        PageLayout layout = new PageLayout();
        layout.cells.add(new GroundedCell(1, "NAME", new BBox(408, 1201, 707, 89)));
        layout.cells.add(new GroundedCell(2, "NAME", new BBox(408, 1278, 707, 89)));
        layout.cells.add(new GroundedCell(1, "PAUSE", new BBox(2085, 1201, 258, 89)));
        layout.cells.add(new GroundedCell(2, "PAUSE", new BBox(2085, 1278, 258, 89)));
        LayoutNormalizer.normalize(layout);
        int nameH = 0;
        for (GroundedCell cell : layout.cells) {
            if ("NAME".equals(cell.field) && cell.row == 1) {
                nameH = cell.bbox.h;
            }
        }
        assertEquals(77, nameH);
    }

    @Test
    void clipsHorizontalOverlapBetweenNameAndAgency() {
        PageLayout layout = new PageLayout();
        layout.cells.add(new GroundedCell(1, "NAME", new BBox(408, 100, 707, 70)));
        layout.cells.add(new GroundedCell(1, "AGENCY", new BBox(1103, 100, 258, 70)));
        LayoutNormalizer.normalize(layout);
        int nameW = 0;
        for (GroundedCell cell : layout.cells) {
            if ("NAME".equals(cell.field)) {
                nameW = cell.bbox.w;
            }
        }
        assertEquals(695, nameW);
    }

    @Test
    void overlappingBandSamePhysicalRowDedupesToOne() {
        PageLayout layout = new PageLayout();
        layout.cells.add(new GroundedCell(1, "NAME", new BBox(412, 1201, 695, 57)));
        layout.cells.add(new GroundedCell(1, "ARRIVEE", new BBox(1598, 1201, 245, 57)));
        layout.cells.add(new GroundedCell(8, "NAME", new BBox(412, 1210, 690, 60)));
        layout.cells.add(new GroundedCell(8, "ARRIVEE", new BBox(1598, 1210, 250, 60)));
        LayoutNormalizer.normalize(layout);
        int names = 0;
        int maxRow = 0;
        for (GroundedCell cell : layout.cells) {
            if ("NAME".equals(cell.field)) {
                names++;
            }
            maxRow = Math.max(maxRow, cell.row);
        }
        assertEquals(1, names);
        assertEquals(1, maxRow);
    }
}
