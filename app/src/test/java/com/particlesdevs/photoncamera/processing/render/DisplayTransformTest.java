package com.particlesdevs.photoncamera.processing.render;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class DisplayTransformTest {

    private static final float[] WHITE = {1f, 1f, 1f};

    private static ColorCorrectionTransform matrixCct() {
        ColorCorrectionTransform cct = new ColorCorrectionTransform();
        cct.correctionMode = ColorCorrectionTransform.CorrectionMode.MATRIX;
        cct.matrix = new float[]{0.9f, 0.05f, 0.05f,
                0.02f, 0.96f, 0.02f,
                0.01f, 0.04f, 0.95f};
        return cct;
    }

    @Test
    public void srgbLeavesMatrixUnchangedButCopiesIt() {
        ColorCorrectionTransform cct = matrixCct();
        DisplayTransform transform = DisplayTransform.from(cct, WHITE, false, true);
        assertFalse(transform.isCubeMode());
        assertArrayEquals(cct.matrix, transform.matrix(), 0f);
        assertNotSame(cct.matrix, transform.matrix());
    }

    @Test
    public void p3PreMultipliesMatrix() {
        ColorCorrectionTransform cct = matrixCct();
        DisplayTransform transform = DisplayTransform.from(cct, WHITE, true, true);
        float[] expected = new float[9];
        Converter.multiply(Converter.sRGBtoDisplayP3, cct.matrix, expected);
        assertArrayEquals(expected, transform.matrix(), 1e-6f);
    }

    @Test
    public void matrixesModeCombinesThenConverts() {
        ColorCorrectionTransform cct = matrixCct();
        cct.correctionMode = ColorCorrectionTransform.CorrectionMode.MATRIXES;
        cct.point1 = new float[]{1f, 1f, 1f};
        cct.point2 = new float[]{0.5f, 0.5f, 1f};
        cct.matrix2 = new float[]{0.8f, 0.1f, 0.1f,
                0.1f, 0.8f, 0.1f,
                0.1f, 0.1f, 0.8f};
        DisplayTransform transform = DisplayTransform.from(cct, WHITE, true, true);
        float[] combined = cct.combineMatrix(WHITE);
        float[] expected = new float[9];
        Converter.multiply(Converter.sRGBtoDisplayP3, combined, expected);
        assertArrayEquals(expected, transform.matrix(), 1e-6f);
    }

    @Test
    public void cubeModeUsesCubesWhenAllowed() {
        ColorCorrectionTransform cct = matrixCct();
        cct.correctionMode = ColorCorrectionTransform.CorrectionMode.CUBE;
        cct.cubes[0].cube = new float[][]{
                {1, 0, 0, 0, 1, 0, 0, 0, 1},
                {0.9f, 0.05f, 0.05f, 0.02f, 0.96f, 0.02f, 0.01f, 0.04f, 0.95f},
                {0.8f, 0.1f, 0.1f, 0.1f, 0.8f, 0.1f, 0.1f, 0.1f, 0.8f}};
        DisplayTransform transform = DisplayTransform.from(cct, WHITE, true, true);
        assertTrue(transform.isCubeMode());
        assertNotNull(transform.matrix());
        assertNotNull(transform.cubes());
        float[] expected0 = Converter.toDisplayP3(cct.cubes[0].cube[0]);
        assertArrayEquals(expected0, transform.cubes()[0], 1e-6f);
    }

    @Test
    public void cubeModeFallsBackToMatrixWhenNotAllowed() {
        ColorCorrectionTransform cct = matrixCct();
        cct.correctionMode = ColorCorrectionTransform.CorrectionMode.CUBE;
        cct.cubes[0].cube = new float[][]{
                {1, 0, 0, 0, 1, 0, 0, 0, 1},
                {1, 0, 0, 0, 1, 0, 0, 0, 1},
                {1, 0, 0, 0, 1, 0, 0, 0, 1}};
        DisplayTransform transform = DisplayTransform.from(cct, WHITE, true, false);
        assertFalse(transform.isCubeMode());
        float[] expected = Converter.toDisplayP3(cct.matrix);
        assertArrayEquals(expected, transform.matrix(), 1e-6f);
    }
}
