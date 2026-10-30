package com.attendance.recognition;

import com.attendance.recognition.dto.BBox;
import com.attendance.recognition.dto.GroundedCell;
import com.attendance.recognition.dto.PageLayout;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RowGridRebuilderTest {

    @Test
    void uniformGridFromNoCentersDoesNotOverlapNames() {
        PageLayout layout = tallLayout();
        Map<String, int[]> cols = RowGridRebuilder.medianColumns(layout.cells);
        int[] centers = new int[] {918, 975, 1031, 1088, 1145, 1201, 1258, 1314, 1371, 1428, 1484, 1541, 1597};
        int[] tops = new int[centers.length];
        for (int i = 0; i < centers.length; i++) {
            tops[i] = centers[i] - 28;
        }
        RowGridRebuilder.applyUniformGrid(layout, cols, tops, 57, 3508, 2479);
        assertEquals(13 * 3, layout.cells.size());
        int prevBottom = -1;
        int names = 0;
        for (int i = 0; i < layout.cells.size(); i++) {
            GroundedCell cell = layout.cells.get(i);
            if (!"NAME".equals(cell.field)) {
                continue;
            }
            names++;
            if (prevBottom >= 0) {
                assertTrue(cell.bbox.y >= prevBottom, "row " + cell.row + " overlaps previous NAME");
            }
            prevBottom = cell.bbox.y + cell.bbox.h;
        }
        assertEquals(13, names);
        assertEquals("no_strip", layout.gridSource.isEmpty() ? "no_strip" : layout.gridSource);
    }

    @Test
    void snapCollapsesTallGroundBoxes() {
        PageLayout layout = new PageLayout();
        layout.sourceWidth = 3508;
        layout.sourceHeight = 2479;
        layout.cells.add(new GroundedCell(1, "NAME", new BBox(412, 1156, 695, 77)));
        layout.cells.add(new GroundedCell(1, "NO", new BBox(307, 1156, 105, 77)));
        layout.cells.add(new GroundedCell(2, "NAME", new BBox(412, 1233, 695, 77)));
        layout.cells.add(new GroundedCell(2, "NO", new BBox(307, 1233, 105, 77)));
        RowGridRebuilder.snapRowHeights(layout, 77, 3508, 2479);
        int h1 = 0;
        for (int i = 0; i < layout.cells.size(); i++) {
            if ("NAME".equals(layout.cells.get(i).field) && layout.cells.get(i).row == 1) {
                h1 = layout.cells.get(i).bbox.h;
            }
        }
        assertTrue(h1 <= 77);
        assertTrue(h1 >= 8);
    }

    @Test
    void missingNumbersAreInterpolated() {
        List<int[]> hits = new ArrayList<int[]>();
        hits.add(new int[] {1, 946});
        hits.add(new int[] {2, 1003});
        hits.add(new int[] {3, 1060});
        hits.add(new int[] {5, 1174});
        hits.add(new int[] {6, 1231});
        int[] tops = RowGridRebuilder.rowTopsFromHits(hits, 57, 2479);
        assertEquals(6, tops.length);
        assertTrue(tops[3] > tops[2]);
        assertTrue(tops[3] < tops[4]);
    }

    @Test
    void clockCellsAreInset() {
        PageLayout layout = tallLayout();
        Map<String, int[]> cols = RowGridRebuilder.medianColumns(layout.cells);
        int[] tops = new int[] {890, 947};
        RowGridRebuilder.applyUniformGrid(layout, cols, tops, 57, 3508, 2479);
        int clockW = 0;
        int nameW = 0;
        for (int i = 0; i < layout.cells.size(); i++) {
            GroundedCell cell = layout.cells.get(i);
            if ("ARRIVEE".equals(cell.field) && cell.row == 1) {
                clockW = cell.bbox.w;
            }
            if ("NAME".equals(cell.field) && cell.row == 1) {
                nameW = cell.bbox.w;
            }
        }
        assertEquals(cols.get("NAME")[1], nameW);
        assertEquals(cols.get("ARRIVEE")[1] - 8, clockW);
    }

    private static PageLayout tallLayout() {
        PageLayout layout = new PageLayout();
        layout.sourceWidth = 3508;
        layout.sourceHeight = 2479;
        int y = 890;
        for (int r = 1; r <= 13; r++) {
            layout.cells.add(new GroundedCell(r, "NO", new BBox(307, y, 105, 77)));
            layout.cells.add(new GroundedCell(r, "NAME", new BBox(412, y, 695, 77)));
            layout.cells.add(new GroundedCell(r, "ARRIVEE", new BBox(1620, y, 268, 77)));
            y += 77;
        }
        return layout;
    }
}
