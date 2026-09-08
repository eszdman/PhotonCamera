package com.particlesdevs.photoncamera.processing.parameters;

import java.nio.FloatBuffer;

/** Validation for the compact cross-context map: (variance, relative variance, reserved, valid). */
public final class NightUncertaintyMap {
    private NightUncertaintyMap() {}

    public static boolean valid(FloatBuffer data, int width, int height, int rawWidth, int rawHeight) {
        if (data == null || rawWidth <= 0 || rawHeight <= 0 || width <= 0 || height <= 0
                || width != (rawWidth + 15L) / 16 || height != (rawHeight + 15L) / 16
                || data.remaining() != (long) width * height * 4) return false;
        for (int i = data.position(); i < data.limit(); i += 4) {
            float variance = data.get(i), ratio = data.get(i + 1);
            if (!Float.isFinite(variance) || variance <= 0 || !Float.isFinite(ratio)
                    || ratio <= 0 || ratio > 1 || data.get(i + 3) != 1) return false;
        }
        return true;
    }
}
