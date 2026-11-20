package com.attendance.util;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfToImageConverterTest {

    @Test
    void jbig2ImageIoReaderIsRegistered() {
        ImageIO.scanForPlugins();
        assertTrue(PdfToImageConverter.hasJbig2Reader(), "jbig2-imageio must be on the classpath");
    }

    @Test
    void jbig2EmptySheetIsNotRenderedBlank() throws Exception {
        byte[] pdf;
        try (InputStream in = getClass().getResourceAsStream("/jbig2-empty-sheet.pdf")) {
            assertTrue(in != null, "missing test resource jbig2-empty-sheet.pdf");
            pdf = readAll(in);
        }
        List<byte[]> pages = PdfToImageConverter.toJpegPages(pdf, 5, 72f, null);
        assertEquals(1, pages.size());
        assertFalse(RecognitionEmptyPageSupport.isNearlyBlank(pages.get(0)),
                "JBIG2 empty table still has grid ink and must not render as a white page");
    }

    private static byte[] readAll(InputStream in) throws Exception {
        byte[] buf = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
