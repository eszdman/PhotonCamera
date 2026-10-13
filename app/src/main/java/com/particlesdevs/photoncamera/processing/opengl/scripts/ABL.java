package com.particlesdevs.photoncamera.processing.opengl.scripts;

import android.annotation.SuppressLint;

import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.GLBuffer;
import com.particlesdevs.photoncamera.processing.opengl.GLContext;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLImage;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;


public class ABL implements AutoCloseable{
    private static final String TAG = "ABL";
    /**
     * Sigma multiplier of the black-point noise blob that one histogram bin
     * step covers: 16 sigma spans the full bin range, keeping the blob
     * resolved and unclamped while real scene content clamps into the top
     * bins, out of the search region. The old amplification (8/full-scale
     * noise) mapped the blob itself past the clamp, so the percentile
     * readout rode into whatever the scene contained - the over-correction.
     */
    private static final double BLOB_SPAN_SIGMAS = 16.0;
    /**
     * Sub-black headroom kept in positive bins (histogram binOffset). Without
     * it the blob's lower tail clamps at bin 0 and the per-channel
     * differences below zero - exactly the tint signal - are destroyed.
     */
    private static final double OFFSET_SIGMAS = 4.0;
    /** Normal z-quantile of the readout percentile (1% -> -2.326 sigma). */
    private static final double PERCENTILE_Z = 2.326;
    GLContext context;
    int histSize;
    public int resize = 3;
    public float[] exposure = new float[4];
    public ABL(GLContext context) {
        this(context,256);
    }
    public ABL(GLContext context, int size) {
        histSize = size;
        this.context = context;
        Log.d(TAG, "Creating ABL with size: " + size);
        for (int i = 0; i < 4; i++) {
            exposure[i] = 1.0f;
        }
    }
    @SuppressLint("DefaultLocale")
    public float[] Compute(double minExposureMpy, double maxEV, double noise, GLTexture input) {
        GLHistogram histogram = new GLHistogram(context, histSize);
        histogram.Rc = true;
        histogram.Gc = true;
        histogram.Bc = true;
        histogram.Ac = false;
        double noiseFloor = Math.pow(2.0, -maxEV);
        double expoSearch = 1.0 / (BLOB_SPAN_SIGMAS * noise + noiseFloor);
        Log.d(TAG, "Exposure Search Value: " + expoSearch);
        histogram.exposure[0] = (float) (expoSearch);
        histogram.exposure[1] = (float) (expoSearch);
        histogram.exposure[2] = (float) (expoSearch);
        // Bin bias in bin units: the shader adds it after the exposure
        // multiply, so it must carry the (histSize-1) bin scaling itself.
        histogram.binOffset = (float) (OFFSET_SIGMAS * noise * expoSearch * (histSize - 1));

        int[][] hist = histogram.Compute(input);

        histogram.close();

        // The 1% percentile lands PERCENTILE_Z sigma below the blob center;
        // the bin offset lifted every bin by OFFSET_SIGMAS sigma. Net
        // correction from the readout bin back to the blob center (the
        // black level), in amplified units.
        double sigmaLift = (OFFSET_SIGMAS - PERCENTILE_Z) * noise * expoSearch;
        // Search upper bound: a scene with too few black pixels rides the
        // percentile into content, and every channel caps out at the same
        // value -> zero tint deviation, brightness untouched. Fail-safe
        // instead of over-correction.
        double cap = minExposureMpy * (noise + noiseFloor);

        float[] blackLevels = new float[3];
        blackLevels[0] = calculateBlackLevel(hist[0], sigmaLift, cap * expoSearch);
        blackLevels[1] = calculateBlackLevel(hist[1], sigmaLift, cap * expoSearch);
        blackLevels[2] = calculateBlackLevel(hist[2], sigmaLift, cap * expoSearch);

        for (int i = 0; i < blackLevels.length; i++) {
            blackLevels[i] /= (float) (expoSearch);
        }

        Log.d(TAG, String.format("Black Levels - R: %.4f, G: %.4f, B: %.4f",
                blackLevels[0], blackLevels[1], blackLevels[2]));

        return blackLevels;
    }

    @Override
    public void close() {
    }

    /**
     * Calculate black level from histogram data
     * Percentile readout lifted back to the noise-blob center and capped;
     * sigmaLift and cap are in amplified (bin-normalized) units.
     */
    private float calculateBlackLevel(int[] histogram, double sigmaLift, double capAmp) {
        if (histogram == null || histogram.length == 0) {
            return 0.0f;
        }

        // Calculate total pixel count
        long totalPixels = 0;
        for (int count : histogram) {
            totalPixels += count;
        }

        if (totalPixels == 0) {
            return 0.0f;
        }


        long targetPixels = totalPixels / 100; // 1% of pixels
        long currentPixels = 0;

        for (int i = 0; i < histogram.length; i++) {
            currentPixels += histogram[i];
            if (currentPixels >= targetPixels) {
                // Convert bin index to amplified value (0.0 - 1.0)
                float blackLevel = (float) i / (histogram.length - 1);

                // Apply some constraints to avoid extreme values
                blackLevel = (float) Math.min(blackLevel - sigmaLift, capAmp);
                blackLevel = Math.max(blackLevel, 0.0f); // Min 0% black level
                Log.d(TAG, "Calculated black level for channel: " + blackLevel);
                return blackLevel;
            }
        }

        return 0.0f;
    }
}
