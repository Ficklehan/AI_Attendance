package com.attendance.recognition;

import com.attendance.recognition.dto.BBox;
import com.attendance.recognition.dto.CellOcrResult;
import com.attendance.recognition.dto.GroundedCell;
import com.attendance.recognition.dto.PageLayout;
import com.attendance.recognition.ocr.OcrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 用印刷 N. 列行心 + ground 列 x 重建均匀行网格，避免一格吃两行。
 */
@Component
public class RowGridRebuilder {

    private static final Logger log = LoggerFactory.getLogger(RowGridRebuilder.class);
    static final int CLOCK_INSET = 4;
    private static final int MIN_HITS = 5;
    private static final int MAX_NO = 40;

    private final OcrClient ocrClient;

    public RowGridRebuilder(OcrClient ocrClient) {
        this.ocrClient = ocrClient;
    }

    public void rebuild(BufferedImage source, PageLayout layout, Path workDir) {
        if (layout == null || layout.cells == null || layout.cells.isEmpty() || source == null) {
            return;
        }
        Map<String, int[]> cols = medianColumns(layout.cells);
        int medianH = medianHeight(layout.cells);
        if (medianH < 24) {
            medianH = 57;
        }
        List<int[]> hits = scanNoStrip(source, layout, cols.get("NO"), medianH, workDir);
        int[] tops = rowTopsFromHits(hits, medianH, source.getHeight());
        if (tops != null && tops.length >= MIN_HITS) {
            int rowH = medianGap(tops, medianH);
            applyUniformGrid(layout, cols, tops, rowH, source.getWidth(), source.getHeight());
            layout.gridSource = "no_strip";
            layout.rowH = rowH;
            layout.firstY = tops[0];
            log.info("row grid no_strip rows={} rowH={} firstY={}", tops.length, rowH, tops[0]);
            return;
        }
        snapRowHeights(layout, medianH, source.getWidth(), source.getHeight());
        layout.gridSource = "ground_snap";
        layout.rowH = medianH;
        layout.firstY = firstY(layout);
        log.info("row grid ground_snap rows={} rowH={} firstY={}", rowCount(layout), medianH, layout.firstY);
    }

    static void applyUniformGrid(PageLayout layout, Map<String, int[]> cols, int[] rowTops, int rowH,
                                 int imageW, int imageH) {
        if (layout == null || cols == null || cols.isEmpty() || rowTops == null || rowTops.length == 0) {
            return;
        }
        int hDefault = Math.max(16, rowH);
        List<GroundedCell> out = new ArrayList<GroundedCell>();
        int[] xs = new int[cols.size()];
        int xi = 0;
        for (Map.Entry<String, int[]> e : cols.entrySet()) {
            xs[xi++] = e.getValue()[0];
        }
        layout.gridXs = xs;
        layout.gridYs = rowTops;
        for (int r = 0; r < rowTops.length; r++) {
            int y = clamp(rowTops[r], 0, Math.max(0, imageH - 1));
            int h = hDefault;
            if (r + 1 < rowTops.length) {
                h = Math.max(8, Math.min(hDefault, rowTops[r + 1] - y));
            }
            h = Math.max(8, Math.min(h, imageH - y));
            for (Map.Entry<String, int[]> e : cols.entrySet()) {
                String field = e.getKey();
                int x = e.getValue()[0];
                int w = e.getValue()[1];
                x = clamp(x, 0, Math.max(0, imageW - 1));
                w = Math.max(8, Math.min(w, imageW - x));
                BBox box = new BBox(x, y, w, h);
                if ("ARRIVEE".equals(field) || "DEPAR".equals(field)) {
                    box = box.inset(CLOCK_INSET);
                }
                out.add(new GroundedCell(r + 1, field, box));
            }
        }
        layout.cells = out;
    }

    static void snapRowHeights(PageLayout layout, int medianH, int imageW, int imageH) {
        if (layout == null || layout.cells == null || layout.cells.isEmpty()) {
            return;
        }
        LayoutNormalizer.normalize(layout);
        Map<Integer, List<GroundedCell>> byRow = new LinkedHashMap<Integer, List<GroundedCell>>();
        for (int i = 0; i < layout.cells.size(); i++) {
            GroundedCell cell = layout.cells.get(i);
            Integer key = Integer.valueOf(cell.row);
            List<GroundedCell> group = byRow.get(key);
            if (group == null) {
                group = new ArrayList<GroundedCell>();
                byRow.put(key, group);
            }
            group.add(cell);
        }
        List<Integer> rows = new ArrayList<Integer>(byRow.keySet());
        Collections.sort(rows);
        int[] tops = new int[rows.size()];
        for (int r = 0; r < rows.size(); r++) {
            List<GroundedCell> group = byRow.get(rows.get(r));
            int minY = Integer.MAX_VALUE;
            for (int i = 0; i < group.size(); i++) {
                if (group.get(i).bbox != null) {
                    minY = Math.min(minY, group.get(i).bbox.y);
                }
            }
            tops[r] = minY == Integer.MAX_VALUE ? 0 : minY;
        }
        int rowH = medianH;
        if (tops.length >= 2) {
            rowH = medianGap(tops, medianH);
        }
        Map<String, int[]> cols = medianColumns(layout.cells);
        applyUniformGrid(layout, cols, tops, rowH, imageW, imageH);
    }

    static int[] rowTopsFromHits(List<int[]> hits, int medianH, int imageH) {
        if (hits == null || hits.size() < MIN_HITS) {
            return null;
        }
        TreeMap<Integer, Integer> numToY = new TreeMap<Integer, Integer>();
        for (int i = 0; i < hits.size(); i++) {
            int num = hits.get(i)[0];
            int y = hits.get(i)[1];
            if (num < 1 || num > MAX_NO) {
                continue;
            }
            Integer prev = numToY.get(Integer.valueOf(num));
            if (prev == null) {
                numToY.put(Integer.valueOf(num), Integer.valueOf(y));
            }
        }
        if (numToY.size() < MIN_HITS) {
            return null;
        }
        int first = numToY.firstKey().intValue();
        int last = numToY.lastKey().intValue();
        int gap = medianNeighborGap(numToY, medianH);
        if (first > 1) {
            int y1 = numToY.get(Integer.valueOf(first)).intValue() - (first - 1) * gap;
            for (int n = first - 1; n >= 1; n--) {
                y1 = numToY.get(Integer.valueOf(n + 1)).intValue() - gap;
                numToY.put(Integer.valueOf(n), Integer.valueOf(y1));
            }
            first = 1;
        }
        for (int n = first + 1; n <= last; n++) {
            if (!numToY.containsKey(Integer.valueOf(n))) {
                Integer lo = numToY.lowerKey(Integer.valueOf(n));
                Integer hi = numToY.higherKey(Integer.valueOf(n));
                int y;
                if (lo != null && hi != null) {
                    int yLo = numToY.get(lo).intValue();
                    int yHi = numToY.get(hi).intValue();
                    y = yLo + (int) Math.round((yHi - yLo) * (n - lo.intValue())
                            / (double) (hi.intValue() - lo.intValue()));
                } else if (lo != null) {
                    y = numToY.get(lo).intValue() + (n - lo.intValue()) * gap;
                } else {
                    y = numToY.get(hi).intValue() - (hi.intValue() - n) * gap;
                }
                numToY.put(Integer.valueOf(n), Integer.valueOf(y));
            }
        }
        int[] tops = new int[numToY.size()];
        int i = 0;
        int half = Math.max(8, medianH / 2);
        for (Integer y : numToY.values()) {
            tops[i++] = clamp(y.intValue() - half, 0, Math.max(0, imageH - 1));
        }
        return tops;
    }

    private List<int[]> scanNoStrip(BufferedImage source, PageLayout layout, int[] noCol,
                                    int medianH, Path workDir) {
        List<int[]> hits = new ArrayList<int[]>();
        if (source == null || ocrClient == null || noCol == null) {
            return hits;
        }
        int x = clamp(noCol[0], 0, Math.max(0, source.getWidth() - 1));
        int w = Math.max(16, Math.min(noCol[1], source.getWidth() - x));
        int minY = Integer.MAX_VALUE;
        int maxY = 0;
        for (int i = 0; i < layout.cells.size(); i++) {
            BBox box = layout.cells.get(i).bbox;
            if (box == null) {
                continue;
            }
            minY = Math.min(minY, box.y);
            maxY = Math.max(maxY, box.y + box.h);
        }
        if (minY == Integer.MAX_VALUE) {
            return hits;
        }
        int y0 = Math.max(0, minY - 8 * medianH);
        int y1 = Math.min(source.getHeight(), maxY + 4 * medianH);
        int windowH = Math.max(32, medianH);
        int step = Math.max(20, (int) Math.round(medianH * 0.85d));
        for (int y = y0; y + windowH / 2 < y1; y += step) {
            int h = Math.min(windowH, source.getHeight() - y);
            if (h < 16) {
                break;
            }
            try {
                BufferedImage crop = source.getSubimage(x, y, w, h);
                Path png = workDir == null
                        ? java.nio.file.Files.createTempFile("no-strip-", ".png")
                        : workDir.resolve("no-y" + y + ".png");
                ImageIO.write(crop, "png", png.toFile());
                CellOcrResult ocr = ocrClient.ocrCell(png);
                String num = OcrLinePicker.number(ocr);
                if (num.matches("\\d{1,2}")) {
                    int n = Integer.parseInt(num);
                    if (n >= 1 && n <= MAX_NO) {
                        hits.add(new int[] {n, y + h / 2});
                    }
                }
            } catch (Exception e) {
                log.debug("NO strip ocr y={}: {}", y, e.getMessage());
            }
        }
        return uniqueHits(hits);
    }

    static List<int[]> uniqueHits(List<int[]> hits) {
        Map<Integer, int[]> best = new LinkedHashMap<Integer, int[]>();
        for (int i = 0; i < hits.size(); i++) {
            int[] hit = hits.get(i);
            Integer key = Integer.valueOf(hit[0]);
            if (!best.containsKey(key)) {
                best.put(key, hit);
            }
        }
        List<int[]> out = new ArrayList<int[]>(best.values());
        Collections.sort(out, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.compare(a[0], b[0]);
            }
        });
        return out;
    }

    static Map<String, int[]> medianColumns(List<GroundedCell> cells) {
        Map<String, List<Integer>> xs = new LinkedHashMap<String, List<Integer>>();
        Map<String, List<Integer>> ws = new LinkedHashMap<String, List<Integer>>();
        for (int i = 0; i < cells.size(); i++) {
            GroundedCell cell = cells.get(i);
            if (cell == null || cell.bbox == null || cell.field == null || cell.field.isEmpty()) {
                continue;
            }
            List<Integer> xg = xs.get(cell.field);
            if (xg == null) {
                xg = new ArrayList<Integer>();
                xs.put(cell.field, xg);
                ws.put(cell.field, new ArrayList<Integer>());
            }
            xg.add(Integer.valueOf(cell.bbox.x));
            ws.get(cell.field).add(Integer.valueOf(cell.bbox.w));
        }
        Map<String, int[]> cols = new LinkedHashMap<String, int[]>();
        List<String> order = HarnessColumnCatalog.orderedFields();
        for (int i = 0; i < order.size(); i++) {
            String field = order.get(i);
            List<Integer> xg = xs.get(field);
            if (xg == null || xg.isEmpty()) {
                continue;
            }
            cols.put(field, new int[] {median(xg), median(ws.get(field))});
        }
        for (Map.Entry<String, List<Integer>> e : xs.entrySet()) {
            if (!cols.containsKey(e.getKey())) {
                cols.put(e.getKey(), new int[] {median(e.getValue()), median(ws.get(e.getKey()))});
            }
        }
        return cols;
    }

    static int medianHeight(List<GroundedCell> cells) {
        List<Integer> hs = new ArrayList<Integer>();
        for (int i = 0; i < cells.size(); i++) {
            if (cells.get(i) != null && cells.get(i).bbox != null) {
                hs.add(Integer.valueOf(Math.max(1, cells.get(i).bbox.h)));
            }
        }
        if (hs.isEmpty()) {
            return 57;
        }
        return median(hs);
    }

    private static int medianNeighborGap(TreeMap<Integer, Integer> numToY, int fallback) {
        List<Integer> gaps = new ArrayList<Integer>();
        Integer prevK = null;
        Integer prevY = null;
        for (Map.Entry<Integer, Integer> e : numToY.entrySet()) {
            if (prevK != null && e.getKey().intValue() == prevK.intValue() + 1) {
                int g = e.getValue().intValue() - prevY.intValue();
                if (g >= 24 && g <= 120) {
                    gaps.add(Integer.valueOf(g));
                }
            }
            prevK = e.getKey();
            prevY = e.getValue();
        }
        return gaps.isEmpty() ? fallback : median(gaps);
    }

    private static int medianGap(int[] tops, int fallback) {
        List<Integer> gaps = new ArrayList<Integer>();
        for (int i = 1; i < tops.length; i++) {
            int g = tops[i] - tops[i - 1];
            if (g >= 24 && g <= 120) {
                gaps.add(Integer.valueOf(g));
            }
        }
        return gaps.isEmpty() ? fallback : median(gaps);
    }

    private static int median(List<Integer> values) {
        List<Integer> copy = new ArrayList<Integer>(values);
        Collections.sort(copy);
        return copy.get(copy.size() / 2).intValue();
    }

    private static int firstY(PageLayout layout) {
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < layout.cells.size(); i++) {
            BBox box = layout.cells.get(i).bbox;
            if (box != null) {
                min = Math.min(min, box.y);
            }
        }
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private static int rowCount(PageLayout layout) {
        int max = 0;
        for (int i = 0; i < layout.cells.size(); i++) {
            max = Math.max(max, layout.cells.get(i).row);
        }
        return max;
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }
}
