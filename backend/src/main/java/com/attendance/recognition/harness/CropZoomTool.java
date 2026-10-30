package com.attendance.recognition.harness;

import com.attendance.recognition.dto.BBox;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * dsh-crop-zoom 契约的进程内回退：原图按 bbox 裁切，长边放到 target，倍率 ≤ maxScale。
 * 生产由 {@link CropZoomCli} 调 crop-zoom；CLI 缺失或单格过小才走这里。
 */
public final class CropZoomTool {

    private CropZoomTool() {
    }

    public static BufferedImage cropZoom(BufferedImage source, BBox box, int target, int maxScale, String mode) {
        if (source == null || box == null) {
            throw new IllegalArgumentException("source/box required");
        }
        int x = clamp(box.x, 0, Math.max(0, source.getWidth() - 1));
        int y = clamp(box.y, 0, Math.max(0, source.getHeight() - 1));
        int w = Math.max(1, Math.min(box.w, source.getWidth() - x));
        int h = Math.max(1, Math.min(box.h, source.getHeight() - y));
        BufferedImage crop = source.getSubimage(x, y, w, h);
        int longEdge = Math.max(w, h);
        int cap = Math.max(1, maxScale);
        int scale;
        if (target > 0 && longEdge > 0) {
            scale = (int) Math.ceil(target / (double) longEdge);
        } else {
            scale = 1;
        }
        if (scale < 1) {
            scale = 1;
        }
        if (scale > cap) {
            scale = cap;
        }
        if (scale == 1) {
            return copy(crop);
        }
        int nw = w * scale;
        int nh = h * scale;
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        if ("nearest".equalsIgnoreCase(mode)) {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        } else {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        }
        g.drawImage(crop, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
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
