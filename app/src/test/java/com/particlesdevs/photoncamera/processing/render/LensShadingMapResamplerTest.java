package com.particlesdevs.photoncamera.processing.render;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class LensShadingMapResamplerTest {
    @Test
    public void fullRegionPreservesMapAndDoesNotMutateSource() {
        float[] source = ramp(5, 3);
        float[] original = source.clone();
        float[] output = LensShadingMapResampler.resample(source, 5, 3, 0, 0, 1, 1);
        assertArrayEquals(original, output, 0f);
        assertArrayEquals(original, source, 0f);
        assertNotSame(source, output);
    }

    @Test
    public void centeredZoomInterpolatesEveryChannelAtOriginalResolution() {
        float[] source = ramp(3, 3);
        float[] output = LensShadingMapResampler.resample(source, 3, 3, .25f, .25f, .75f, .75f);
        assertEquals(source.length, output.length);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                for (int c = 0; c < 4; c++) {
                    assertEquals(value(.5f + x * .5f, .5f + y * .5f, c),
                            output[(y * 3 + x) * 4 + c], 1e-5f);
                }
            }
        }
    }

    @Test
    public void offCenterCropUsesItsOwnRegion() {
        float[] output = LensShadingMapResampler.resample(ramp(5, 3), 5, 3, 0, .5f, .5f, 1);
        for (int c = 0; c < 4; c++) {
            assertEquals(value(0, 1, c), output[c], 0f);
            assertEquals(value(1, 1.5f, c), output[(1 * 5 + 2) * 4 + c], 1e-5f);
            assertEquals(value(2, 2, c), output[(2 * 5 + 4) * 4 + c], 0f);
        }
    }

    @Test
    public void zoomRemovesBrightOuterVignetteCorrection() {
        float[] source = new float[3 * 3 * 4];
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                for (int c = 0; c < 4; c++) {
                    source[(y * 3 + x) * 4 + c] = 1 + Math.abs(x - 1) + Math.abs(y - 1);
                }
            }
        }
        float[] output = LensShadingMapResampler.resample(source, 3, 3, .25f, .25f, .75f, .75f);
        assertEquals(2f, output[0], 0f);
        assertEquals(1f, output[16], 0f);
        assertEquals(2f, output[32], 0f);
    }

    @Test
    public void cropOutsideActiveArrayClampsToEdges() {
        float[] source = ramp(3, 3);
        float[] output = LensShadingMapResampler.resample(source, 3, 3, -.25f, -.5f, 1.25f, 1.5f);
        assertArrayEquals(source, output, 0f);
    }

    @Test
    public void identityAndSingleAxisMapsRemainValid() {
        float[] identity = {1, 1, 1, 1};
        assertArrayEquals(identity, LensShadingMapResampler.resample(identity, 1, 1,
                .2f, .3f, .4f, .5f), 0f);
        float[] row = LensShadingMapResampler.resample(ramp(3, 1), 3, 1, .25f, .3f, .75f, .8f);
        float[] column = LensShadingMapResampler.resample(ramp(1, 3), 1, 3, .3f, .25f, .8f, .75f);
        for (int i = 0; i < 3; i++) {
            for (int c = 0; c < 4; c++) {
                assertEquals(value(.5f + .5f * i, 0, c), row[i * 4 + c], 1e-5f);
                assertEquals(value(0, .5f + .5f * i, c), column[i * 4 + c], 1e-5f);
            }
        }
    }

    private static float[] ramp(int columns, int rows) {
        float[] source = new float[columns * rows * 4];
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < columns; x++) {
                for (int c = 0; c < 4; c++) source[(y * columns + x) * 4 + c] = value(x, y, c);
            }
        }
        return source;
    }

    private static float value(float x, float y, int channel) {
        return 1 + channel + (channel + 1) * x + (4 - channel) * y;
    }
}
