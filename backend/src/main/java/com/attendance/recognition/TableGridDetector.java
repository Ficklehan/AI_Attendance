package com.attendance.recognition;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 确定性表格检测：竖线投影定位姓名列 + 时钟列（ARRIVEE/DEPAR），时钟列范围内分段贯穿度定位行。
 *
 * <p>列：表体范围内统计每列 x 的暗像素贯通强度。最强竖边是 SHIFT/ARRIVEE 边界（印刷班次列的子线
 * 在右边界汇聚）。以它为锚，向右找“宽列（到达）+ 窄列（离开）”，向左找第一个宽列（姓名）。
 * 相对阈值过滤手写数字竖笔画产生的伪峰值。不依赖 NO 列、不写死列宽。
 */
public final class TableGridDetector {

    private static final int DARK_LUM = 145;
    private static final int SEGMENTS = 24;

    public static final class Layout {
        public final int nameX0;
        public final int nameX1;
        public final int arriveX0;
        public final int arriveX1;
        public final int departX0;
        public final int departX1;

        Layout(int nameX0, int nameX1, int arriveX0, int arriveX1, int departX0, int departX1) {
            this.nameX0 = nameX0;
            this.nameX1 = nameX1;
            this.arriveX0 = arriveX0;
            this.arriveX1 = arriveX1;
            this.departX0 = departX0;
            this.departX1 = departX1;
        }

        public boolean ok() {
            return arriveX0 >= 0 && arriveX1 > arriveX0 && departX0 >= arriveX1 && departX1 > departX0;
        }
    }

    private TableGridDetector() {
    }

    /** 返回表体范围内全部竖边（x 升序，已去掉过近重复）。 */
    public static List<Integer> detectColumnEdges(BufferedImage image) {
        if (image == null || image.getWidth() < 80 || image.getHeight() < 80) {
            return new ArrayList<Integer>();
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean[][] ink = inkMask(image);
        int y0 = h * 20 / 100;
        int y1 = h * 85 / 100;
        int[] span = columnSpan(ink, w, h, y0, y1);
        int maxSpan = 0;
        for (int i = 0; i < span.length; i++) {
            if (span[i] > maxSpan) {
                maxSpan = span[i];
            }
        }
        int thresh = Math.max(350, maxSpan * 80 / 100);
        List<int[]> edges = dedupEdges(columnPeaks(span, thresh), 24);
        List<Integer> out = new ArrayList<Integer>();
        for (int i = 0; i < edges.size(); i++) {
            out.add(Integer.valueOf(edges.get(i)[0]));
        }
        return out;
    }

    /**
     * 按表头列数（cols）从全部竖边中选出 cols+1 条列边界。
     * 先剔除“列内窄分隔线”（间距远小于中位数），再按强度补足或裁掉。
     */
    public static int[] pickColumnEdges(List<Integer> allEdges, int cols) {
        if (allEdges == null || allEdges.size() < 2 || cols <= 0) {
            return null;
        }
        List<Integer> xs = new ArrayList<Integer>(allEdges);
        Collections.sort(xs);
        int need = cols + 1;
        // 中位数间距
        List<Integer> gaps = new ArrayList<Integer>();
        for (int i = 1; i < xs.size(); i++) {
            gaps.add(Integer.valueOf(xs.get(i) - xs.get(i - 1)));
        }
        Collections.sort(gaps);
        int median = gaps.get(gaps.size() / 2).intValue();
        // 剔除间距 < 中位数 35% 的“内部子线”（如 时:分 分隔线），保留靠右那条。
        int minGap = Math.max(8, median * 35 / 100);
        List<Integer> kept = new ArrayList<Integer>();
        kept.add(xs.get(0));
        for (int i = 1; i < xs.size(); i++) {
            if (xs.get(i) - kept.get(kept.size() - 1) >= minGap) {
                kept.add(xs.get(i));
            }
        }
        if (kept.size() < need) {
            return null;
        }
        // 多于需求：按等间距抽样，保住首尾。
        if (kept.size() > need) {
            List<Integer> sampled = new ArrayList<Integer>();
            for (int i = 0; i < need; i++) {
                int idx = (int) Math.round(i * (kept.size() - 1) / (double) (need - 1));
                int v = kept.get(Math.min(kept.size() - 1, Math.max(0, idx))).intValue();
                if (sampled.isEmpty() || v > sampled.get(sampled.size() - 1).intValue()) {
                    sampled.add(Integer.valueOf(v));
                }
            }
            if (sampled.size() == need) {
                kept = sampled;
            } else if (kept.size() > need) {
                // 兜底：保留首尾，中间按最强优先。
                List<Integer> fallback = new ArrayList<Integer>();
                fallback.add(kept.get(0));
                fallback.addAll(kept.subList(kept.size() - (need - 1), kept.size()));
                kept = fallback;
            }
        }
        return toArray(kept.subList(0, Math.min(need, kept.size())));
    }

    /** 定位姓名列 + 时钟列，返回 Layout 或 null。 */
    public static Layout detectLayout(BufferedImage image) {
        if (image == null || image.getWidth() < 80 || image.getHeight() < 80) {
            return null;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean[][] ink = inkMask(image);
        int y0 = h * 20 / 100;
        int y1 = h * 85 / 100;
        int[] span = columnSpan(ink, w, h, y0, y1);

        List<int[]> edges = dedupEdges(columnPeaks(span, 300), 24);
        if (edges.size() < 3) {
            return null;
        }
        int[] strongest = null;
        for (int i = 0; i < edges.size(); i++) {
            if (strongest == null || edges.get(i)[1] > strongest[1]) {
                strongest = edges.get(i);
            }
        }
        int thr = Math.max(180, strongest[1] * 40 / 100);

        // 时钟列：从最强竖边向右，找“宽列（到达）+ 窄列（离开）”。
        int wide = Math.max(40, w * 10 / 100);
        int[] clock = clockColumnsRight(edges, strongest[0], thr, wide);
        if (clock == null) {
            return null;
        }
        // 姓名列：从最强竖边向左，跳过窄列（班次内部子线），找第一个宽列。
        int[] name = nameColumnLeft(edges, strongest[0], thr, wide);
        if (name == null) {
            name = new int[] {Math.max(0, strongest[0] - wide * 3), strongest[0] - wide};
        }
        return new Layout(name[0], name[1], clock[0], clock[1], clock[1], clock[2]);
    }

    static int[] clockColumnsRight(List<int[]> edges, int anchorX, int thr, int wide) {
        List<Integer> xs = new ArrayList<Integer>();
        for (int i = 0; i < edges.size(); i++) {
            if (edges.get(i)[0] >= anchorX && edges.get(i)[1] > thr) {
                xs.add(Integer.valueOf(edges.get(i)[0]));
            }
        }
        Collections.sort(xs);
        // 到达列 = anchorX ~ 下一个宽列边界。到达/离开同等书写，离开列宽取与到达列相同。
        int arriveX1 = -1;
        for (int i = 0; i < xs.size(); i++) {
            int x = xs.get(i).intValue();
            if (x - anchorX >= wide) {
                arriveX1 = x;
                break;
            }
        }
        if (arriveX1 <= 0) {
            return null;
        }
        int departX1 = arriveX1 + (arriveX1 - anchorX);
        return new int[] {anchorX, arriveX1, departX1};
    }

    static int[] nameColumnLeft(List<int[]> edges, int anchorX, int thr, int wide) {
        List<Integer> xs = new ArrayList<Integer>();
        for (int i = 0; i < edges.size(); i++) {
            if (edges.get(i)[0] <= anchorX && edges.get(i)[1] > thr) {
                xs.add(Integer.valueOf(edges.get(i)[0]));
            }
        }
        Collections.sort(xs);
        // 姓名列是锚点左边最宽的列（宽于班次/中介等）。
        int bestLeft = -1;
        int bestRight = -1;
        int bestW = 0;
        for (int i = 0; i + 1 < xs.size(); i++) {
            int left = xs.get(i).intValue();
            int right = xs.get(i + 1).intValue();
            if (right <= anchorX && right - left >= wide && right - left > bestW) {
                bestLeft = left;
                bestRight = right;
                bestW = right - left;
            }
        }
        if (bestLeft >= 0) {
            return new int[] {bestLeft, bestRight};
        }
        return null;
    }

    /** 在 [x0, x1] 范围内检测行边界（含表头行），返回 int[] 或 null。 */
    public static int[] detectRowEdges(BufferedImage image, int x0, int x1) {
        if (image == null || x1 <= x0) {
            return null;
        }
        boolean[][] ink = inkMask(image);
        List<Integer> rows = detectRows(ink, image.getWidth(), image.getHeight(), x0, x1);
        return rows.size() >= 3 ? toArray(rows) : null;
    }

    static List<Integer> detectRows(boolean[][] ink, int w, int h, int x0, int x1) {
        x0 = Math.max(0, x0);
        x1 = Math.min(w, x1);
        int width = Math.max(8, x1 - x0);
        int segW = Math.max(1, width / SEGMENTS);
        int segCount = Math.max(2, width / segW);
        int[] span = new int[h];
        for (int y = 0; y < h; y++) {
            int covered = 0;
            for (int s = 0; s < segCount; s++) {
                int sx0 = s * segW;
                int sx1 = Math.min(width, sx0 + segW);
                boolean has = false;
                for (int x = sx0; x < sx1 && !has; x++) {
                    if (ink[y][x0 + x]) {
                        has = true;
                    }
                }
                if (has) {
                    covered++;
                }
            }
            span[y] = covered;
        }
        int maxSpan = 0;
        for (int i = 0; i < span.length; i++) {
            if (span[i] > maxSpan) {
                maxSpan = span[i];
            }
        }
        if (maxSpan < segCount / 2) {
            return new ArrayList<Integer>();
        }
        int thresh = Math.max(segCount * 6 / 10, maxSpan * 55 / 100);
        List<Integer> peaks = spansToLines(span, thresh);
        if (peaks.size() < 4) {
            return peaks;
        }
        // 合并行横线上下边缘（横线有厚度，被检测成两条），再按等间距补全。
        List<Integer> merged = mergeCloseLines(peaks);
        if (merged.size() < 4) {
            return peaks;
        }
        return keepRegularGaps(merged);
    }

    /** 合并间距过近的行线（行横线上下边缘），阈值取间距中位数的 1.4 倍。 */
    static List<Integer> mergeCloseLines(List<Integer> lines) {
        if (lines.size() < 2) {
            return new ArrayList<Integer>(lines);
        }
        List<Integer> gaps = new ArrayList<Integer>();
        for (int i = 1; i < lines.size(); i++) {
            gaps.add(Integer.valueOf(lines.get(i) - lines.get(i - 1)));
        }
        Collections.sort(gaps);
        int median = gaps.get(gaps.size() / 2).intValue();
        int mergeTh = Math.max(12, median * 14 / 10);
        List<Integer> merged = new ArrayList<Integer>();
        merged.add(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            int gap = lines.get(i).intValue() - merged.get(merged.size() - 1).intValue();
            if (gap < mergeTh) {
                merged.set(merged.size() - 1,
                        Integer.valueOf((merged.get(merged.size() - 1).intValue() + lines.get(i).intValue()) / 2));
            } else {
                merged.add(lines.get(i));
            }
        }
        return merged;
    }

    /** 找最长的“间距在 [minGap, maxGap] 内”的连续段。 */
    static List<Integer> densestEvenRun(List<Integer> lines, int minGap, int maxGap) {
        if (lines.size() < 2) {
            return new ArrayList<Integer>(lines);
        }
        int bestStart = 0;
        int bestLen = 1;
        int start = 0;
        for (int i = 1; i < lines.size(); i++) {
            int gap = lines.get(i) - lines.get(i - 1);
            if (gap < minGap || gap > maxGap) {
                if (i - start > bestLen) {
                    bestLen = i - start;
                    bestStart = start;
                }
                start = i;
            }
        }
        if (lines.size() - start > bestLen) {
            bestLen = lines.size() - start;
            bestStart = start;
        }
        return new ArrayList<Integer>(lines.subList(bestStart, bestStart + bestLen));
    }

    static List<int[]> dedupEdges(List<int[]> peaks, int minGap) {
        List<int[]> sorted = new ArrayList<int[]>(peaks);
        Collections.sort(sorted, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.compare(a[0], b[0]);
            }
        });
        List<int[]> out = new ArrayList<int[]>();
        for (int i = 0; i < sorted.size(); i++) {
            int[] cur = sorted.get(i);
            if (out.isEmpty() || cur[0] - out.get(out.size() - 1)[0] >= minGap) {
                out.add(cur);
            } else if (cur[1] > out.get(out.size() - 1)[1]) {
                out.set(out.size() - 1, cur);
            }
        }
        return out;
    }

    static int[] columnSpan(boolean[][] ink, int w, int h, int y0, int y1) {
        y0 = Math.max(0, y0);
        y1 = Math.min(h, Math.max(y0 + 8, y1));
        int[] span = new int[w];
        int stepY = Math.max(1, (y1 - y0) / 500);
        for (int x = 0; x < w; x++) {
            int dark = 0;
            int n = 0;
            for (int y = y0; y < y1; y += stepY) {
                n++;
                if (ink[y][x]) {
                    dark++;
                }
            }
            span[x] = n == 0 ? 0 : dark * 1000 / n;
        }
        return span;
    }

    static List<int[]> columnPeaks(int[] span, int thresh) {
        List<int[]> out = new ArrayList<int[]>();
        int n = span.length;
        int i = 0;
        while (i < n) {
            if (span[i] < thresh) {
                i++;
                continue;
            }
            int best = i;
            int bestS = span[i];
            while (i < n && span[i] >= thresh) {
                if (span[i] > bestS) {
                    bestS = span[i];
                    best = i;
                }
                i++;
            }
            out.add(new int[] {best, bestS});
        }
        return out;
    }

    static List<Integer> spansToLines(int[] span, int thresh) {
        List<Integer> out = new ArrayList<Integer>();
        int n = span.length;
        int i = 0;
        while (i < n) {
            if (span[i] < thresh) {
                i++;
                continue;
            }
            int start = i;
            while (i < n && span[i] >= thresh) {
                i++;
            }
            out.add(Integer.valueOf(start + (i - start) / 2));
        }
        return out;
    }

    static List<Integer> keepRegularGaps(List<Integer> lines) {
        if (lines.size() < 4) {
            return lines;
        }
        List<Integer> gaps = new ArrayList<Integer>();
        for (int i = 1; i < lines.size(); i++) {
            gaps.add(Integer.valueOf(lines.get(i) - lines.get(i - 1)));
        }
        List<Integer> sorted = new ArrayList<Integer>(gaps);
        Collections.sort(sorted);
        int median = sorted.get(sorted.size() / 2).intValue();
        if (median < 8) {
            return lines;
        }
        int lo = Math.max(8, median * 55 / 100);
        int hi = median * 175 / 100;
        List<Integer> out = new ArrayList<Integer>();
        out.add(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            int gap = lines.get(i) - out.get(out.size() - 1);
            if (gap < lo) {
                continue;
            }
            if (gap > hi) {
                int nGaps = (gap + median / 2) / Math.max(1, median);
                if (nGaps >= 2 && nGaps <= 4) {
                    int step = gap / nGaps;
                    int last = out.get(out.size() - 1);
                    for (int k = 1; k < nGaps; k++) {
                        out.add(Integer.valueOf(last + k * step));
                    }
                }
                out.add(lines.get(i));
                continue;
            }
            out.add(lines.get(i));
        }
        return out.size() >= 4 ? out : lines;
    }

    private static boolean[][] inkMask(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        boolean[][] ink = new boolean[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                ink[y][x] = luminance(image.getRGB(x, y)) < DARK_LUM;
            }
        }
        return ink;
    }

    private static int luminance(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    private static int[] toArray(List<Integer> list) {
        int[] a = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            a[i] = list.get(i).intValue();
        }
        return a;
    }
}
