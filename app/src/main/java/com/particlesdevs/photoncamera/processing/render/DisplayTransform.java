package com.particlesdevs.photoncamera.processing.render;

/**
 * The camera -> linear display transform used by the still render and by the
 * Ultra HDR scene-luma pass.
 *
 * <p>Keeping this in one place is important for P3 Ultra HDR: the renderer and
 * the gain-map's Yhdr measurement must apply exactly the same white balance,
 * color matrices and cube interpolation, otherwise the gain ratio is measured
 * in a different color space than the stored SDR base.
 *
 * <p>Pure Java so the matrix/cube selection is unit-testable without Android.
 */
public final class DisplayTransform {

    private final boolean cubeMode;
    private final float[] matrix;
    private final float[][] cubes;

    private DisplayTransform(boolean cubeMode, float[] matrix, float[][] cubes) {
        this.cubeMode = cubeMode;
        this.matrix = matrix;
        this.cubes = cubes;
    }

    /**
     * Builds the display transform for the current correction mode.
     *
     * @param cct          loaded color correction transform
     * @param whitePoint   camera neutral / white point
     * @param p3           true to convert the output to linear Display P3
     * @param allowCubes   true only for the "curve" pipeline, which actually
     *                     samples DCP cubes. ModernInitial/HeadroomRender use
     *                     the matrix path even when a cube mode is selected.
     */
    public static DisplayTransform from(ColorCorrectionTransform cct,
            float[] whitePoint, boolean p3, boolean allowCubes) {
        if (cct == null) {
            throw new IllegalArgumentException("Null color correction transform");
        }
        boolean cube = allowCubes
                && (cct.correctionMode == ColorCorrectionTransform.CorrectionMode.CUBE
                || cct.correctionMode == ColorCorrectionTransform.CorrectionMode.CUBES);
        float[] matrix = p3 ? Converter.toDisplayP3(cct.matrix) : copy(cct.matrix);
        if (cube) {
            float[][] raw;
            if (cct.correctionMode == ColorCorrectionTransform.CorrectionMode.CUBES) {
                // Combine mutates its white-point scratch vector; never let it
                // accumulate into the shot parameters.
                raw = cct.cubes[0].Combine(cct.cubes[1], whitePoint.clone());
            } else {
                raw = cct.cubes[0].cube;
            }
            float[][] converted = p3 ? Converter.toDisplayP3(raw) : copy(raw);
            // Keep matrix non-null for uniform binding; the cube shader
            // branch ignores it.
            return new DisplayTransform(true, matrix, converted);
        }
        if (cct.correctionMode == ColorCorrectionTransform.CorrectionMode.MATRIXES) {
            float[] combined = cct.combineMatrix(whitePoint);
            matrix = p3 ? Converter.toDisplayP3(combined) : copy(combined);
        }
        return new DisplayTransform(false, matrix, null);
    }

    public boolean isCubeMode() {
        return cubeMode;
    }

    /** Matrix path; also a valid (unused) fallback in cube mode. */
    public float[] matrix() {
        return matrix;
    }

    /**
     * Three 3x3 matrices for cube modes (low/mid/high brightness), already
     * interpolated when the profile defines two cubes.
     */
    public float[][] cubes() {
        return cubes;
    }

    private static float[] copy(float[] in) {
        if (in == null) {
            return null;
        }
        float[] out = new float[in.length];
        System.arraycopy(in, 0, out, 0, in.length);
        return out;
    }

    private static float[][] copy(float[][] in) {
        if (in == null) {
            return null;
        }
        float[][] out = new float[in.length][];
        for (int i = 0; i < in.length; i++) {
            out[i] = copy(in[i]);
        }
        return out;
    }
}
