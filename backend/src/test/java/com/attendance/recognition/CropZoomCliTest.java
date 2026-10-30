package com.attendance.recognition;

import com.attendance.config.RecognitionProperties;
import com.attendance.recognition.dto.BBox;
import com.attendance.recognition.harness.CropZoomCli;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CropZoomCliTest {

    @Test
    void parseFilePathsReadsJsonAfterHumanSummary() {
        String out = "1 file written from /tmp/page.png (200x100), kernel nearest.\n\n"
                + "#1  source 10,10 20x10  x16  -> 320x160\n\n"
                + "File: /tmp/page-r1.png\n\n"
                + "Now read that file with read_image. Do not answer from this geometry summary alone.\n\n"
                + "{\n  \"files\": [\"/tmp/page-r1.png\"],\n"
                + "  \"regions\": [{\"outPath\":\"/tmp/page-r1.png\"}]\n}\n";
        List<Path> files = CropZoomCli.parseFilePaths(out);
        assertEquals(1, files.size());
        assertEquals("/tmp/page-r1.png", files.get(0).toString());
    }

    @Test
    void clampRejectsBelowMinSideAndFitsImage() {
        assertNull(CropZoomCli.clampForCli(new BBox(0, 0, 3, 10), 100, 100));
        BBox ok = CropZoomCli.clampForCli(new BBox(-2, 0, 20, 10), 100, 100);
        assertNotNull(ok);
        assertEquals(0, ok.x);
        assertEquals(20, ok.w);
        BBox clipped = CropZoomCli.clampForCli(new BBox(90, 0, 20, 10), 100, 100);
        assertNotNull(clipped);
        assertEquals(10, clipped.w);
        assertNull(CropZoomCli.clampForCli(new BBox(100, 0, 20, 10), 100, 100));
        assertNull(CropZoomCli.clampForCli(new BBox(97, 0, 20, 10), 100, 100));
    }

    @Test
    void liveCliCropsNearestToMaxScale() throws Exception {
        CropZoomCli cli = new CropZoomCli(new RecognitionProperties());
        Assumptions.assumeTrue(cli.available(), "dsh-crop-zoom not installed");
        Path dir = Files.createTempDirectory("crop-zoom-cli-");
        try {
            BufferedImage src = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = src.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 80, 40);
            g.setColor(Color.BLACK);
            g.fillRect(8, 8, 16, 8);
            g.dispose();
            Path page = dir.resolve("page.png");
            ImageIO.write(src, "png", page.toFile());
            List<Path> out = cli.crop(page,
                    Collections.singletonList(new BBox(8, 8, 16, 8)),
                    dir.resolve("out.png"), 1600, "nearest");
            assertEquals(1, out.size());
            assertTrue(Files.isRegularFile(out.get(0)));
            BufferedImage img = ImageIO.read(out.get(0).toFile());
            assertNotNull(img);
            assertEquals(16 * 16, img.getWidth());
            assertEquals(8 * 16, img.getHeight());
        } finally {
            FileTree.deleteQuietly(dir);
        }
    }

    private static final class FileTree {
        static void deleteQuietly(Path dir) {
            if (dir == null || !Files.exists(dir)) {
                return;
            }
            try {
                java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(dir);
                try {
                    for (Path p : stream) {
                        Files.deleteIfExists(p);
                    }
                } finally {
                    stream.close();
                }
                Files.deleteIfExists(dir);
            } catch (Exception ignored) {
                // test temp
            }
        }
    }
}
