package com.particlesdevs.photoncamera.util;

import java.util.Arrays;
import java.util.BitSet;

/** Sparse sensor-coordinate verification; never resamples the whole image. */
public final class BurstHotPixelFilter {
    private BurstHotPixelFilter() {}

    public interface Frame {
        float get(int index);
        void set(int index, float value);
    }

    public static final class Result {
        public final int[] sites;
        public final int[] correctedPerFrame;
        public final int candidates;
        public final boolean rejectedOverfull;
        private Result(int[] sites, int[] corrected, int candidates, boolean overfull) {
            this.sites = sites;
            this.correctedPerFrame = corrected;
            this.candidates = candidates;
            this.rejectedOverfull = overfull;
        }
    }

    // Strong temporal evidence, using single-frame, unscaled sensor noise.
    public static float sigmaThreshold(double tunable) {
        return (float) Math.max(6.0, 4.0 * tunable);
    }

    private static float threshold(float value, float s, float o, float sigma) {
        return Math.max(.02f, noiseThreshold(value, s, o, sigma));
    }

    private static float noiseThreshold(float value, float s, float o, float sigma) {
        return sigma * (float) Math.sqrt(Math.max(0f, s * value + o));
    }

    private static float sample(Frame frame, int index) {
        return Math.max(0f, Math.min(1f, frame.get(index)));
    }

    private static float neighborMedian(Frame frame, int index, int width, float[] neighbors) {
        int k = 0;
        for (int dy = -2; dy <= 2; dy += 2)
            for (int dx = -2; dx <= 2; dx += 2)
                if (dx != 0 || dy != 0) neighbors[k++] = sample(frame, index + dy * width + dx);
        Arrays.sort(neighbors);
        return .5f * (neighbors[3] + neighbors[4]);
    }

    private static boolean isolated(Frame frame, int index, int width, float s, float o,
                                    float sigma, float[] neighbors) {
        float background = neighborMedian(frame, index, width, neighbors);
        float center = sample(frame, index);
        // The site already passed burst verification. The proposal's absolute
        // 0.02 floor would leave its dimmer frames uncorrected; retain the noise
        // and strict-maximum guards here to protect overlaid scene highlights.
        return center > neighbors[7] && center - background > noiseThreshold(background, s, o, sigma);
    }

    private static float medianDeviation(float[] patch, int index, float[] scratch) {
        int k = 0;
        for (int dy = -2; dy <= 2; dy += 2)
            for (int dx = -2; dx <= 2; dx += 2)
                scratch[k++] = patch[index + dy * 9 + dx];
        Arrays.sort(scratch);
        return patch[index] - scratch[4];
    }

    private static boolean textureOutlier(Frame[] frames, int index, int width,
                                          float[] patch, float[] scratch, float[] temporal,
                                          float[] residuals) {
        // RawTherapee 5.13 findHotDeadPixels compares the center's same-CFA
        // median residual with the surrounding 24 raw-site residuals. Its
        // default threshold is 21 times their mean magnitude.
        // https://github.com/RawTherapee/RawTherapee/blob/1924179107995b01b73cdc7f4fe91e44fc8227a7/rtengine/badpixels.cc
        // Use a sensor-coordinate temporal median: single-frame read noise can
        // hide warm pixels from this ratio test. Only sparse verified candidates
        // reach this stage; the GPU supplies a deliberately permissive proposal.
        for (int dy = -4; dy <= 4; ++dy)
            for (int dx = -4; dx <= 4; ++dx) {
                for (int j = 0; j < temporal.length; ++j)
                    temporal[j] = sample(frames[j * (frames.length - 1) / (temporal.length - 1)],
                            index + dy * width + dx);
                Arrays.sort(temporal);
                patch[(dy + 4) * 9 + dx + 4] =
                        .5f * (temporal[(temporal.length - 1) / 2] + temporal[temporal.length / 2]);
            }
        float center = medianDeviation(patch, 40, scratch), surround = 0f, largest = 0f;
        for (int dy = -2; dy <= 2; ++dy)
            for (int dx = -2; dx <= 2; ++dx)
                if (dx != 0 || dy != 0) {
                    float residual = Math.abs(medianDeviation(patch, 40 + dy * 9 + dx, scratch));
                    surround += residual;
                    largest = Math.max(largest, residual);
                }
        // One neighboring defect must not veto another. Trim only one of 24
        // residuals; optical spots/edges still have multiple supporting sites.
        if (center > 21f * Math.max(0f, surround - largest) / 23f) return true;

        // Temporal aggregation and spatial background subtraction do not commute.
        // The estimate above suppresses read noise well on a stationary scene,
        // but a moving edge can imprint structure on the temporal median image.
        // Also test persistent local residuals: subtract each frame's same-CFA
        // background first, then take the temporal median. Keep the same spatial
        // ratio and neighbor trim; only candidates rejected above pay this cost.
        for (int j = 0; j < temporal.length; ++j) {
            Frame frame = frames[j * (frames.length - 1) / (temporal.length - 1)];
            for (int dy = -4; dy <= 4; ++dy)
                for (int dx = -4; dx <= 4; ++dx)
                    patch[(dy + 4) * 9 + dx + 4] = sample(frame, index + dy * width + dx);
            for (int dy = -2; dy <= 2; ++dy)
                for (int dx = -2; dx <= 2; ++dx)
                    residuals[((dy + 2) * 5 + dx + 2) * temporal.length + j] =
                            medianDeviation(patch, 40 + dy * 9 + dx, scratch);
        }
        center = 0f; surround = 0f; largest = 0f;
        for (int k = 0; k < 25; ++k) {
            System.arraycopy(residuals, k * temporal.length, temporal, 0, temporal.length);
            Arrays.sort(temporal);
            float residual = .5f * (temporal[(temporal.length - 1) / 2] + temporal[temporal.length / 2]);
            if (k == 12) center = residual;
            else {
                surround += Math.abs(residual);
                largest = Math.max(largest, Math.abs(residual));
            }
        }
        return center > 21f * Math.max(0f, surround - largest) / 23f;
    }

    public static Result correct(Frame[] frames, int width, int height, int cfa,
                                 int[] proposals, float s, float o, float sigma, int maximumSites) {
        int[] corrected = new int[frames.length];
        if (frames.length < 3 || cfa < 0 || cfa > 3 || !Float.isFinite(s) || !Float.isFinite(o)
                || s < 0f || o < 0f || !Float.isFinite(sigma) || sigma < 6f)
            return new Result(new int[0], corrected, proposals.length, false);
        int samples = Math.min(10, frames.length);
        float[] values = new float[samples], scratch = new float[9], patch = new float[81];
        float[] excesses = new float[samples], residuals = new float[25 * samples];
        float[] neighbors = new float[8];
        float[] temporal = new float[samples];
        int allowedMisses = samples / 5, trim = samples >= 5 ? 1 : 0;
        float persistenceSigma = Math.max(3f, sigma * .5f);
        BitSet defects = new BitSet(width * height);
        for (int index : proposals) {
            if (index < 0 || index >= width * height) continue;
            int x = index % width, y = index / width;
            // The texture check reaches four raw pixels in each direction.
            if (x < 4 || y < 4 || x >= width - 4 || y >= height - 4) continue;
            int misses = 0, strong = 0;
            for (int j = 0; j < samples; ++j) {
                Frame frame = frames[j * (frames.length - 1) / (samples - 1)];
                values[j] = sample(frame, index);
                float background = neighborMedian(frame, index, width, neighbors);
                float excess = excesses[j] = values[j] - background;
                // Compare persistent excess against the local same-CFA median.
                // A changing scene changes the signal even at a defective site;
                // the maximum neighbor also fluctuates with noise and edges.
                // A passing edge or one low noise sample must not veto an
                // otherwise persistent defect. At least 80% must pass both.
                if (values[j] <= neighbors[7] || excess <= threshold(background, s, o, persistenceSigma)) {
                    if (++misses > allowedMisses) break;
                }
                if (excess > threshold(background, s, o, sigma)) strong++;
            }
            // Noise may move a persistent warm pixel below the strong threshold
            // occasionally. Strong evidence still needs a strict majority.
            if (misses > allowedMisses || strong <= samples / 2) continue;
            Arrays.sort(values);
            Arrays.sort(excesses);
            // Reject changing impulses, without mistaking background motion for
            // variation in the defect. Trim only one extreme at each end for
            // bursts of five or more samples, and include center shot noise.
            if (excesses[samples - 1 - trim] - excesses[trim]
                    > threshold(values[samples / 2], s, o, sigma)) continue;
            if (!textureOutlier(frames, index, width, patch, scratch, temporal, residuals)) continue;
            defects.set(index);
        }
        if (defects.cardinality() > maximumSites)
            // Never choose an arbitrary strongest subset of a textured scene.
            return new Result(new int[0], corrected, proposals.length, true);
        int[] sites = defects.stream().toArray();
        float[] replacements = new float[sites.length];
        for (int f = 0; f < frames.length; ++f) {
            Frame frame = frames[f];
            Arrays.fill(replacements, Float.NaN);
            for (int k = 0; k < sites.length; ++k) {
                int index = sites[k];
                // Unsampled frames may put genuine highlights over this site.
                if (!isolated(frame, index, width, s, o, persistenceSigma, neighbors)) continue;
                replacements[k] = interpolate(frame, index, width, cfa, defects, s, o);
            }
            // Gather before any writes: adjacent repairs cannot read each other
            // or depend on candidate-list order. Known bad neighbors are excluded.
            for (int k = 0; k < sites.length; ++k) {
                if (Float.isFinite(replacements[k])) {
                    frame.set(sites[k], replacements[k]);
                    corrected[f]++;
                }
            }
        }
        return new Result(sites, corrected, proposals.length, false);
    }

    private static float interpolate(Frame frame, int index, int width, int cfa,
                                     BitSet defects, float s, float o) {
        int parity = ((index % width) & 1) ^ ((index / width) & 1);
        boolean green = parity == ((cfa == 0 || cfa == 3) ? 1 : 0);
        int diagonal = green ? 1 : 2;
        int[] offsets = {2, 2 * width, diagonal * (width + 1), diagonal * (width - 1)};
        double total = 0, norm = 0;
        for (int d = 0; d < offsets.length; ++d) {
            int offset = offsets[d];
            if (defects.get(index - offset) || defects.get(index + offset)) continue;
            float a = sample(frame, index - offset), b = sample(frame, index + offset);
            double epsilon = Math.max(1.0 / 65535, Math.sqrt(Math.max(0, s * (a + b) * .5 + o)));
            double distance = d < 2 ? 2.0 : Math.sqrt(2.0) * diagonal;
            double weight = 1.0 / (distance * (Math.abs(a - b) + epsilon));
            total += weight * (a + b) * .5;
            norm += weight;
        }
        return norm > 0 ? (float) (total / norm) : Float.NaN;
    }
}
