package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.opengl.scripts.GLHistogram;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.Log;

/**
 * Motion V2 exposure estimator, replacing the AutoExposure role.
 *
 * Computes one linear display gain from percentiles of the linear
 * (camera-space) histogram, mirroring motionv2's percentile scheme:
 * gain50 = midtone/p50, gain90 = highlight/p90,
 * gain = sqrt(max(1, gain50)*max(1, gain90)) clamped to [gainMin, gainMax].
 * The gain is consumed by {@link HeadroomRender} as the linear exposure
 * multiplier and as sceneWhite (clamped 0.90*gain).
 *
 * Renders nothing: passes the input texture through and marks the program
 * closed so the pipeline skips the draw for this node.
 */
public class LinearExposure extends Node {
    @Tunable(title = "Histogram size", category = "Sky Exposure", defaultValue = 1024, min = 256, max = 16384, step = 16, description = "Histogram bin count")
    int histSize;

    @Tunable(title = "Midtone Anchor", category = "Sky Exposure", min = 0.005f, max = 0.200f, defaultValue = 0.050f, step = 0.005f, description = "Linear luminance target for the 50th percentile")
    float midAnchor = 0.050f;

    @Tunable(title = "Highlight Anchor", category = "Sky Exposure", min = 0.020f, max = 0.500f, defaultValue = 0.180f, step = 0.005f, description = "Linear luminance target for the 90th percentile")
    float highAnchor = 0.180f;

    @Tunable(title = "Gain Min", category = "Sky Exposure", min = 0.25f, max = 4.0f, defaultValue = 1.0f, step = 0.25f, description = "Lower clamp of the estimated display gain")
    float gainMin = 1.0f;

    @Tunable(title = "Gain Max", category = "Sky Exposure", min = 1.0f, max = 16.0f, defaultValue = 16.0f, step = 1.0f, description = "Upper clamp of the estimated display gain")
    float gainMax = 16.0f;

    public LinearExposure() {
        super("", "LinearExposure");
    }

    @Override
    public void Compile() {}

    @Override
    public int halo() {
        return 0; // passthrough; histogram is a frozen full-frame pre-pass
    }

    public void Run() {
        PostPipeline pipeline = (PostPipeline) basePipeline;
        // Keep the linear scene snapshot for the Ultra HDR gain-map pass
        // (this buffer is the post-demosaic/ABLC input Initial used to see).
        if (pipeline.captureDemosaic) {
            pipeline.captureDemosaicLinear(previousNode.WorkingTexture);
        }
        int bins = histSize;
        if (bins < 16) bins = 1024; // guard against a failed tunable injection

        GLHistogram histogram = new GLHistogram(glProg, bins);
        histogram.Rc = true;
        histogram.Gc = true;
        histogram.Bc = true;
        histogram.Ac = false;
        int[][] result;
        try {
            result = histogram.Compute(previousNode.WorkingTexture);
        } finally {
            histogram.close();
        }

        // Combined RGB histogram over the linear [0,1] range.
        long total = 0L;
        long[] cumulative = new long[bins];
        for (int i = 0; i < bins; i++) {
            total += (long) result[0][i] + (long) result[1][i] + (long) result[2][i];
            cumulative[i] = total;
        }

        float gain = gainMax;
        if (total > 0L) {
            float p50 = percentile(cumulative, total, 0.50f);
            float p90 = percentile(cumulative, total, 0.90f);
            float gain50 = midAnchor / Math.max(p50, 1.0e-4f);
            float gain90 = highAnchor / Math.max(p90, 1.0e-4f);
            float sceneGain = (float) Math.sqrt(
                    Math.max(1.f, gain50) * Math.max(1.f, gain90));
            gain = Math.max(gainMin, Math.min(gainMax, sceneGain));
            Log.d(Name, "p50:" + p50 + " p90:" + p90
                    + " displayGain:" + gain);
        } else {
            Log.d(Name, "Empty histogram, displayGain:" + gain);
        }
        pipeline.linearDisplayGain = gain;

        WorkingTexture = previousNode.WorkingTexture;
        glProg.closed = true;
    }

    /**
     * Bin where the cumulative count from the bottom crosses the requested
     * fraction of all samples; returns the bin value normalized to [0,1].
     */
    private static float percentile(long[] cumulative, long total, float frac) {
        long threshold = (long) (total * frac);
        for (int i = 0; i < cumulative.length; i++) {
            if (cumulative[i] > threshold) {
                return (float) i / (float) (cumulative.length - 1);
            }
        }
        return 1.0f;
    }
}
