package com.attendance.recognition.dto;

/**
 * 原图像素矩形。
 */
public class BBox {
    public int x;
    public int y;
    public int w;
    public int h;

    public BBox() {
    }

    public BBox(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public BBox inset(int pad) {
        if (pad <= 0) {
            return new BBox(x, y, w, h);
        }
        int nw = w - 2 * pad;
        int nh = h - 2 * pad;
        if (nw < 4 || nh < 4) {
            return new BBox(x, y, w, h);
        }
        return new BBox(x + pad, y + pad, nw, nh);
    }

    public BBox padded(int pad, int imageW, int imageH) {
        int nx = Math.max(0, x - pad);
        int ny = Math.max(0, y - pad);
        int x1 = Math.min(imageW, x + w + pad);
        int y1 = Math.min(imageH, y + h + pad);
        return new BBox(nx, ny, Math.max(1, x1 - nx), Math.max(1, y1 - ny));
    }

    public BBox remapFromPreview(int previewWidth, int sourceWidth) {
        if (previewWidth <= 0 || sourceWidth <= 0 || previewWidth == sourceWidth) {
            return new BBox(x, y, w, h);
        }
        double scale = sourceWidth / (double) previewWidth;
        return new BBox(
                (int) Math.round(x * scale),
                (int) Math.round(y * scale),
                Math.max(1, (int) Math.round(w * scale)),
                Math.max(1, (int) Math.round(h * scale)));
    }
}
