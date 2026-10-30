package com.attendance.recognition;

import com.attendance.recognition.ink.CellInkAnalyzer;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 到达/离开格固定四宫格：时十、时个、冒号、分十、分个。
 * 每位单独认 0–9，禁止整格当一行字。
 */
public final class ClockDigitGrid {

    public static final class Layout {
        public final int[][] boxes;
        public final String source;

        Layout(int[][] boxes, String source) {
            this.boxes = boxes;
            this.source = source;
        }
    }

    private ClockDigitGrid() {
    }

    public static Layout locate(BufferedImage cell, int darkLum) {
        if (cell == null || cell.getWidth() < 16 || cell.getHeight() < 8) {
            return template(cell);
        }
        int[] peaks = strongestDividers(cell, darkLum);
        if (peaks != null && peaks.length == 3) {
            return fromDividers(cell.getWidth(), cell.getHeight(), peaks, "ruling");
        }
        return template(cell);
    }

    static Layout template(BufferedImage cell) {
        int w = cell == null ? 0 : cell.getWidth();
        int h = cell == null ? 0 : cell.getHeight();
        if (w < 8 || h < 4) {
            return new Layout(new int[0][0], "too_small");
        }
        int insetX = Math.max(2, w / 30);
        int insetY = Math.max(2, h / 12);
        int colonL = (int) Math.floor(w * 0.42d);
        int colonR = (int) Math.floor(w * 0.58d);
        int hourMid = (insetX + colonL) / 2;
        int minMid = (colonR + (w - insetX)) / 2;
        int[][] boxes = new int[][] {
                box(insetX, insetY, hourMid, h - insetY),
                box(hourMid, insetY, colonL, h - insetY),
                box(colonR, insetY, minMid, h - insetY),
                box(minMid, insetY, w - insetX, h - insetY)
        };
        return new Layout(boxes, "template");
    }

    static Layout fromDividers(int w, int h, int[] peaks, String source) {
        int insetY = Math.max(2, h / 12);
        int insetX = Math.max(2, w / 40);
        int x0 = insetX;
        int x1 = peaks[0];
        int x2 = peaks[1];
        int x3 = peaks[2];
        int x4 = w - insetX;
        int[][] boxes = new int[][] {
                box(x0, insetY, x1, h - insetY),
                box(x1, insetY, x2, h - insetY),
                box(x2, insetY, x3, h - insetY),
                box(x3, insetY, x4, h - insetY)
        };
        return new Layout(boxes, source);
    }

    static int[] strongestDividers(BufferedImage cell, int darkLum) {
        int w = cell.getWidth();
        int h = cell.getHeight();
        int[] score = new int[w];
        for (int x = 0; x < w; x++) {
            int dark = 0;
            for (int y = 0; y < h; y++) {
                if (CellInkAnalyzer.luminance(cell.getRGB(x, y)) < darkLum) {
                    dark++;
                }
            }
            score[x] = dark;
        }
        int edge = Math.max(4, w / 12);
        int minSep = Math.max(8, w / 10);
        int minHits = Math.max(3, (int) Math.floor(h * 0.22d));
        List<int[]> peaks = new ArrayList<int[]>();
        for (int x = edge; x < w - edge; x++) {
            int s = score[x];
            if (s < minHits) {
                continue;
            }
            if (s >= score[x - 1] && s >= score[x + 1]) {
                peaks.add(new int[] {x, s});
            }
        }
        if (peaks.isEmpty()) {
            return null;
        }
        Collections.sort(peaks, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return Integer.compare(b[1], a[1]);
            }
        });
        List<Integer> picked = new ArrayList<Integer>();
        for (int i = 0; i < peaks.size(); i++) {
            int x = peaks.get(i)[0];
            boolean far = true;
            for (int j = 0; j < picked.size(); j++) {
                if (Math.abs(x - picked.get(j).intValue()) < minSep) {
                    far = false;
                    break;
                }
            }
            if (far) {
                picked.add(Integer.valueOf(x));
            }
            if (picked.size() == 3) {
                break;
            }
        }
        if (picked.size() != 3) {
            return null;
        }
        Collections.sort(picked);
        return new int[] {picked.get(0).intValue(), picked.get(1).intValue(), picked.get(2).intValue()};
    }

    static int[] box(int x0, int y0, int x1, int y1) {
        int x = Math.min(x0, x1);
        int y = Math.min(y0, y1);
        int w = Math.max(1, Math.abs(x1 - x0));
        int h = Math.max(1, Math.abs(y1 - y0));
        return new int[] {x, y, w, h};
    }

    public static BufferedImage crop(BufferedImage cell, int[] box) {
        if (cell == null || box == null || box.length < 4) {
            return null;
        }
        int x = Math.max(0, box[0]);
        int y = Math.max(0, box[1]);
        int w = Math.min(box[2], cell.getWidth() - x);
        int h = Math.min(box[3], cell.getHeight() - y);
        if (w < 1 || h < 1) {
            return null;
        }
        int padX = Math.max(1, w / 10);
        int padY = Math.max(1, h / 10);
        int cx = Math.min(cell.getWidth() - 1, x + padX);
        int cy = Math.min(cell.getHeight() - 1, y + padY);
        int cw = Math.max(1, w - 2 * padX);
        int ch = Math.max(1, h - 2 * padY);
        if (cx + cw > cell.getWidth()) {
            cw = cell.getWidth() - cx;
        }
        if (cy + ch > cell.getHeight()) {
            ch = cell.getHeight() - cy;
        }
        if (cw < 1 || ch < 1) {
            return cell.getSubimage(x, y, Math.max(1, w), Math.max(1, h));
        }
        return cell.getSubimage(cx, cy, cw, ch);
    }

    /** 单格只接受一个 0–9；多位或整行时刻一律丢弃。 */
    public static Character parseDigit(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() == 1) {
            char c = t.charAt(0);
            if (c >= '0' && c <= '9') {
                return Character.valueOf(c);
            }
            return mapLetter(c);
        }
        String digits = digitsOnly(t);
        if (digits.length() == 1) {
            return Character.valueOf(digits.charAt(0));
        }
        return null;
    }

    static Character mapLetter(char c) {
        if (c == 'O' || c == 'o' || c == 'D' || c == 'Q') {
            return Character.valueOf('0');
        }
        if (c == 'I' || c == 'l' || c == '|' || c == 'i' || c == '/') {
            return Character.valueOf('1');
        }
        if (c == 'B') {
            return Character.valueOf('8');
        }
        if (c == 'S' || c == 's') {
            return Character.valueOf('5');
        }
        if (c == 'Z' || c == 'z') {
            return Character.valueOf('2');
        }
        if (c == 'G' || c == 'g') {
            return Character.valueOf('6');
        }
        return null;
    }

    public static String compose(Character[] digits) {
        if (digits == null || digits.length != 4) {
            return "";
        }
        for (int i = 0; i < 4; i++) {
            if (digits[i] == null) {
                return "";
            }
        }
        int hours = (digits[0].charValue() - '0') * 10 + (digits[1].charValue() - '0');
        int minutes = (digits[2].charValue() - '0') * 10 + (digits[3].charValue() - '0');
        if (hours > 23 || minutes > 59) {
            return "";
        }
        return String.format("%02d:%02d", Integer.valueOf(hours), Integer.valueOf(minutes));
    }

    public static String formatDigits(Character[] digits) {
        if (digits == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < digits.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(digits[i] == null ? "-" : digits[i].toString());
        }
        return sb.toString();
    }

    static String digitsOnly(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
