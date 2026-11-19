package com.particlesdevs.photoncamera.processing.render;

/** Bilinear resampling of row-major, interleaved [R, Geven, Godd, B] gain maps. */
final class LensShadingMapResampler {
    private LensShadingMapResampler() {}

    /**
     * Returns the selected region at the original grid resolution. Coordinates
     * are relative to the first/last source grid nodes (0..1), not texel edges.
     * Samples outside the active array are clamped to its nearest edge.
     */
    static float[] resample(float[] source, int columns, int rows,
                            float left, float top, float right, float bottom) {
        if (columns < 1 || rows < 1 || source.length != (long) columns * rows * 4) {
            throw new IllegalArgumentException("Invalid lens shading map dimensions");
        }
        float[] output = new float[source.length];
        for (int y = 0; y < rows; y++) {
            float v = rows == 1 ? 0f : y / (float) (rows - 1);
            float sy = clamp(top + (bottom - top) * v) * (rows - 1);
            int y0 = (int) sy;
            int y1 = Math.min(y0 + 1, rows - 1);
            float fy = sy - y0;
            for (int x = 0; x < columns; x++) {
                float u = columns == 1 ? 0f : x / (float) (columns - 1);
                float sx = clamp(left + (right - left) * u) * (columns - 1);
                int x0 = (int) sx;
                int x1 = Math.min(x0 + 1, columns - 1);
                float fx = sx - x0;
                for (int c = 0; c < 4; c++) {
                    float a = source[(y0 * columns + x0) * 4 + c];
                    float b = source[(y0 * columns + x1) * 4 + c];
                    float d = source[(y1 * columns + x0) * 4 + c];
                    float e = source[(y1 * columns + x1) * 4 + c];
                    float upper = a + (b - a) * fx;
                    float lower = d + (e - d) * fx;
                    output[(y * columns + x) * 4 + c] = upper + (lower - upper) * fy;
                }
            }
        }
        return output;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
