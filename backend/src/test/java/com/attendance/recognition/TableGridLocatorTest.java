package com.attendance.recognition;

import com.attendance.recognition.dto.GroundedCell;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableGridLocatorTest {

    @Test
    void locateReadsNineColumnsAndSkipsHeaderRow() {
        int[] xs = new int[] {40, 90, 280, 360, 440, 520, 600, 680, 760, 820};
        int[] ys = new int[] {50, 100, 150, 200, 250, 300};
        BufferedImage img = grid(900, 360, xs, ys);
        TableGridLocator.Grid grid = TableGridLocator.locate(img);
        assertTrue(grid.ok());
        assertEquals(9, grid.columnCount());
        assertTrue(grid.bodyRowCount() >= 3);
        List<GroundedCell> cells = TableGridLocator.toCells(grid);
        int name = 0;
        int arrive = 0;
        for (int i = 0; i < cells.size(); i++) {
            if ("NAME".equals(cells.get(i).field)) {
                name++;
            }
            if ("ARRIVEE".equals(cells.get(i).field)) {
                arrive++;
            }
        }
        assertEquals(name, arrive);
        assertTrue(name >= 3);
        assertTrue(cells.get(0).bbox.h < 60);
    }

    @Test
    void selectStrongestKeepsNoNameBoundaryInsteadOfMerging() {
        List<TableGridLocator.Scored> peaks = new ArrayList<TableGridLocator.Scored>();
        peaks.add(new TableGridLocator.Scored(273, 989));
        peaks.add(new TableGridLocator.Scored(408, 851));
        peaks.add(new TableGridLocator.Scored(1089, 825));
        peaks.add(new TableGridLocator.Scored(1385, 913));
        peaks.add(new TableGridLocator.Scored(1581, 998));
        peaks.add(new TableGridLocator.Scored(1655, 686));
        peaks.add(new TableGridLocator.Scored(1724, 697));
        peaks.add(new TableGridLocator.Scored(1756, 722));
        peaks.add(new TableGridLocator.Scored(1890, 1000));
        peaks.add(new TableGridLocator.Scored(1963, 711));
        peaks.add(new TableGridLocator.Scored(2199, 1000));
        peaks.add(new TableGridLocator.Scored(2365, 1000));
        peaks.add(new TableGridLocator.Scored(2858, 963));
        peaks.add(new TableGridLocator.Scored(3147, 1000));
        List<Integer> xs = TableGridLocator.selectStrongest(peaks, 10, 40);
        assertEquals(10, xs.size());
        assertEquals(273, xs.get(0).intValue());
        assertEquals(408, xs.get(1).intValue());
        assertEquals(1089, xs.get(2).intValue());
        assertEquals(135, xs.get(1).intValue() - xs.get(0).intValue());
        assertEquals(681, xs.get(2).intValue() - xs.get(1).intValue());
    }

    @Test
    void liveSheetKeepsNarrowNoAndWideName() throws Exception {
        File file = new File("uploads/20261030_0006.jpg");
        Assumptions.assumeTrue(file.isFile(), "sample sheet missing");
        BufferedImage img = ImageIO.read(file);
        TableGridLocator.Grid grid = TableGridLocator.locate(img);
        assertTrue(grid.ok());
        assertEquals(9, grid.columnCount());
        int noW = grid.xs[1] - grid.xs[0];
        int nameW = grid.xs[2] - grid.xs[1];
        assertTrue(noW < 250, "NO width=" + noW);
        assertTrue(nameW > 400, "NAME width=" + nameW);
        assertTrue(nameW > noW * 2);
    }

    @Test
    void keepRegularGapsInsertsMissingBodyLine() {
        List<Integer> lines = new ArrayList<Integer>();
        int y = 100;
        for (int i = 0; i < 6; i++) {
            lines.add(Integer.valueOf(y));
            y += 58;
        }
        lines.remove(3);
        List<Integer> kept = TableGridLocator.keepRegularGaps(lines);
        assertTrue(kept.size() >= lines.size());
        int midGaps = 0;
        for (int i = 1; i < kept.size(); i++) {
            int gap = kept.get(i).intValue() - kept.get(i - 1).intValue();
            if (gap >= 50 && gap <= 70) {
                midGaps++;
            }
        }
        assertTrue(midGaps >= 4);
    }

    @Test
    void blankImageIsNotOk() {
        BufferedImage img = new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 200, 120);
        g.dispose();
        assertTrue(!TableGridLocator.locate(img).ok());
    }

    private static BufferedImage grid(int w, int h, int[] xs, int[] ys) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.BLACK);
        for (int i = 0; i < ys.length; i++) {
            g.fillRect(xs[0], ys[i], xs[xs.length - 1] - xs[0], 2);
        }
        for (int i = 0; i < xs.length; i++) {
            g.fillRect(xs[i], ys[0], 2, ys[ys.length - 1] - ys[0]);
        }
        g.dispose();
        return img;
    }
}
