package com.attendance.recognition;

import com.attendance.recognition.dto.BBox;
import com.attendance.recognition.dto.GroundedCell;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 用印刷表横竖格线定格。列宽来自竖线位置，不写死比例、不按间距强行合并列。
 */
public final class TableGridLocator {

    /** 考勤签到表固定 9 列 → 10 条竖边。 */
    static final int EDGE_COUNT = 10;

    public static final class Grid {
        public final int[] xs;
        public final int[] ys;
        public final int headerRows;

        Grid(int[] xs, int[] ys, int headerRows) {
            this.xs = xs;
            this.ys = ys;
            this.headerRows = headerRows;
        }

        public boolean ok() {
            return xs != null && xs.length == EDGE_COUNT && ys != null && ys.length >= 4;
        }

        public int columnCount() {
            return ok() ? xs.length - 1 : 0;
        }

        public int bodyRowCount() {
            if (!ok()) {
                return 0;
            }
            return Math.max(0, ys.length - 1 - headerRows);
        }
    }

    static final class Scored {
        final int pos;
        final int score;

        Scored(int pos, int score) {
            this.pos = pos;
            this.score = score;
        }
    }

    private TableGridLocator() {
    }

    public static Grid locate(BufferedImage image) {
        if (image == null || image.getWidth() < 80 || image.getHeight() < 80) {
            return new Grid(null, null, 1);
        }
        int darkLum = 145;
        List<Integer> hLines = horizontalLines(image, darkLum);
        List<Integer> rows = densestEvenRun(hLines, 16, 160);
        rows = keepRegularGaps(rows);
        if (rows.size() < 4) {
            return new Grid(null, null, 1);
        }
        int y0 = rows.get(0).intValue();
        int y1 = rows.get(rows.size() - 1).intValue();
        int[] span = verticalSpanScores(image, darkLum, y0, y1);
        int maxSpan = 0;
        for (int i = 0; i < span.length; i++) {
            if (span[i] > maxSpan) {
                maxSpan = span[i];
            }
        }
        if (maxSpan < 200) {
            return new Grid(null, null, 1);
        }
        int thresh = Math.max(350, maxSpan * 55 / 100);
        List<Scored> peaks = peaksWithScore(span, thresh);
        peaks = mergeCloseScored(peaks, Math.max(3, image.getWidth() / 500));
        int minGap = Math.max(40, image.getWidth() / 90);
        List<Integer> xs = selectStrongest(peaks, EDGE_COUNT, minGap);
        if (xs.size() != EDGE_COUNT) {
            return new Grid(null, null, 1);
        }
        return new Grid(toArray(xs), toArray(rows), 1);
    }

    public static List<GroundedCell> toCells(Grid grid) {
        List<GroundedCell> cells = new ArrayList<GroundedCell>();
        if (grid == null || !grid.ok()) {
            return cells;
        }
        List<String> fields = HarnessColumnCatalog.orderedFields();
        int cols = Math.min(fields.size(), grid.xs.length - 1);
        int body = 0;
        for (int r = grid.headerRows; r < grid.ys.length - 1; r++) {
            int y = grid.ys[r];
            int h = grid.ys[r + 1] - y;
            if (h < 8) {
                continue;
            }
            body++;
            for (int c = 0; c < cols; c++) {
                int x = grid.xs[c];
                int w = grid.xs[c + 1] - x;
                if (w < 8) {
                    continue;
                }
                int inset = Math.max(2, Math.min(6, Math.min(w, h) / 18));
                BBox box = new BBox(x, y, w, h).inset(inset);
                cells.add(new GroundedCell(body, fields.get(c), box));
            }
        }
        return cells;
    }

    /**
     * 按竖线贯通强度选边：强度高的印刷格线优先，间距过近时丢掉弱的，禁止把两列并成一列。
     */
    static List<Integer> selectStrongest(List<Scored> peaks, int need, int minGap) {
        List<Integer> out = new ArrayList<Integer>();
        if (peaks == null || peaks.isEmpty() || need <= 0) {
            return out;
        }
        List<Scored> ranked = new ArrayList<Scored>(peaks);
        Collections.sort(ranked, new Comparator<Scored>() {
            @Override
            public int compare(Scored a, Scored b) {
                int byScore = Integer.compare(b.score, a.score);
                if (byScore != 0) {
                    return byScore;
                }
                return Integer.compare(a.pos, b.pos);
            }
        });
        List<Scored> chosen = new ArrayList<Scored>();
        for (int i = 0; i < ranked.size(); i++) {
            Scored cand = ranked.get(i);
            boolean far = true;
            for (int j = 0; j < chosen.size(); j++) {
                if (Math.abs(cand.pos - chosen.get(j).pos) < minGap) {
                    far = false;
                    break;
                }
            }
            if (!far) {
                continue;
            }
            chosen.add(cand);
            if (chosen.size() == need) {
                break;
            }
        }
        Collections.sort(chosen, new Comparator<Scored>() {
            @Override
            public int compare(Scored a, Scored b) {
                return Integer.compare(a.pos, b.pos);
            }
        });
        for (int i = 0; i < chosen.size(); i++) {
            out.add(Integer.valueOf(chosen.get(i).pos));
        }
        return out;
    }

    static int[] verticalSpanScores(BufferedImage image, int darkLum, int y0, int y1) {
        int w = image.getWidth();
        int h = image.getHeight();
        y0 = Math.max(0, y0);
        y1 = Math.min(h, Math.max(y0 + 8, y1));
        int[] score = new int[w];
        int stepY = Math.max(1, (y1 - y0) / 500);
        for (int x = 0; x < w; x++) {
            int dark = 0;
            int n = 0;
            for (int y = y0; y < y1; y += stepY) {
                n++;
                if (luminance(image.getRGB(x, y)) < darkLum) {
                    dark++;
                }
            }
            score[x] = n == 0 ? 0 : dark * 1000 / n;
        }
        return score;
    }

    static List<Integer> horizontalLines(BufferedImage image, int darkLum) {
        int w = image.getWidth();
        int h = image.getHeight();
        int stepX = Math.max(1, w / 900);
        int[] score = new int[h];
        int max = 0;
        for (int y = 0; y < h; y++) {
            int dark = 0;
            int n = 0;
            for (int x = 0; x < w; x += stepX) {
                n++;
                if (luminance(image.getRGB(x, y)) < darkLum) {
                    dark++;
                }
            }
            int s = n == 0 ? 0 : dark * 1000 / n;
            score[y] = s;
            if (s > max) {
                max = s;
            }
        }
        int thresh = Math.max(170, max * 42 / 100);
        List<Scored> peaks = peaksWithScore(score, thresh);
        List<Integer> out = new ArrayList<Integer>();
        for (int i = 0; i < peaks.size(); i++) {
            out.add(Integer.valueOf(peaks.get(i).pos));
        }
        return out;
    }

    static List<Scored> peaksWithScore(int[] score, int thresh) {
        List<Scored> out = new ArrayList<Scored>();
        int n = score.length;
        int i = 0;
        while (i < n) {
            if (score[i] < thresh) {
                i++;
                continue;
            }
            int best = i;
            int bestS = score[i];
            while (i < n && score[i] >= thresh) {
                if (score[i] > bestS) {
                    bestS = score[i];
                    best = i;
                }
                i++;
            }
            out.add(new Scored(best, bestS));
        }
        return out;
    }

    static List<Scored> mergeCloseScored(List<Scored> lines, int minSep) {
        List<Scored> out = new ArrayList<Scored>();
        if (lines == null || lines.isEmpty()) {
            return out;
        }
        long acc = lines.get(0).pos;
        int n = 1;
        int bestScore = lines.get(0).score;
        int bestPos = lines.get(0).pos;
        for (int i = 1; i < lines.size(); i++) {
            Scored cur = lines.get(i);
            if (cur.pos - (int) (acc / n) < minSep) {
                acc += cur.pos;
                n++;
                if (cur.score > bestScore) {
                    bestScore = cur.score;
                    bestPos = cur.pos;
                }
            } else {
                out.add(new Scored(bestPos, bestScore));
                acc = cur.pos;
                n = 1;
                bestScore = cur.score;
                bestPos = cur.pos;
            }
        }
        out.add(new Scored(bestPos, bestScore));
        return out;
    }

    static List<Integer> densestEvenRun(List<Integer> lines, int minGap, int maxGap) {
        if (lines == null || lines.size() < 2) {
            return lines == null ? new ArrayList<Integer>() : new ArrayList<Integer>(lines);
        }
        int bestStart = 0;
        int bestLen = 1;
        int start = 0;
        for (int i = 1; i < lines.size(); i++) {
            int gap = lines.get(i).intValue() - lines.get(i - 1).intValue();
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

    /**
     * 丢掉破坏均匀行距的横线（手写斜线、污点），保住印刷行线。
     */
    static List<Integer> keepRegularGaps(List<Integer> lines) {
        if (lines == null || lines.size() < 4) {
            return lines == null ? new ArrayList<Integer>() : new ArrayList<Integer>(lines);
        }
        List<Integer> gaps = new ArrayList<Integer>();
        for (int i = 1; i < lines.size(); i++) {
            gaps.add(Integer.valueOf(lines.get(i).intValue() - lines.get(i - 1).intValue()));
        }
        List<Integer> sorted = new ArrayList<Integer>(gaps);
        Collections.sort(sorted);
        int median = sorted.get(sorted.size() / 2).intValue();
        if (median < 8) {
            return new ArrayList<Integer>(lines);
        }
        int lo = Math.max(8, median * 55 / 100);
        int hi = median * 175 / 100;
        List<Integer> out = new ArrayList<Integer>();
        out.add(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            int gap = lines.get(i).intValue() - out.get(out.size() - 1).intValue();
            if (gap < lo) {
                continue;
            }
            if (gap > hi) {
                if (out.size() == 1 && gap <= median * 250 / 100) {
                    out.add(lines.get(i));
                    continue;
                }
                int nGaps = (gap + median / 2) / Math.max(1, median);
                if (nGaps >= 2 && nGaps <= 3) {
                    int step = gap / nGaps;
                    if (step >= lo && step <= hi) {
                        int last = out.get(out.size() - 1).intValue();
                        for (int k = 1; k < nGaps; k++) {
                            out.add(Integer.valueOf(last + k * step));
                        }
                        out.add(lines.get(i));
                        continue;
                    }
                }
                continue;
            }
            out.add(lines.get(i));
        }
        return out.size() >= 4 ? out : new ArrayList<Integer>(lines);
    }

    private static int[] toArray(List<Integer> lines) {
        int[] a = new int[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            a[i] = lines.get(i).intValue();
        }
        return a;
    }

    private static int luminance(int rgb) {
        int r = (rgb >> 16) & 0xff;
        int g = (rgb >> 8) & 0xff;
        int b = rgb & 0xff;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }
}
