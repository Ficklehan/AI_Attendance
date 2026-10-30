package com.attendance.recognition.harness;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeepSeekHarnessClientBandTest {

    @Test
    void threeBandsCoverFullHeightWithOverlap() {
        List<DeepSeekHarnessClient.BandSpec> bands = DeepSeekHarnessClient.splitBands(2479, 3);
        assertEquals(3, bands.size());
        assertEquals(0, bands.get(0).y0);
        int last = bands.get(2).y0 + bands.get(2).h;
        assertEquals(2479, last);
        assertTrue(bands.get(0).y0 + bands.get(0).h > bands.get(1).y0);
        assertTrue(bands.get(1).y0 + bands.get(1).h > bands.get(2).y0);
    }
}
