package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

/**
 * Scene-adaptive LLF detail: the per-shot scene metrics and the mapping from
 * them to the effective {@code detail} of the local Laplacian remap curve.
 *
 * <p>The metrics are measured once per shot by {@link AutoExposureCurve}
 * (histogram spread, clipped fraction, local-detail density) and reset in
 * {@code PostPipeline.bindShot}. {@link LocalLaplacian2} turns them into the
 * detail value baked into its remap LUT. Everything here is pure math with no
 * Android dependencies, so the policy is unit-testable on the JVM.</p>
 *
 * <p>Policy shape: scenes with the widest histogram spread get the {@code floor}
 * (the safe baseline for high dynamic range); flat scenes get up to {@code max}
 * (where local contrast is what the image is missing). Three attenuators
 * protect the boost: sensor noise (the bump would amplify noise), highlight
 * clipping (the bump would push already-clipped detail further), and measured
 * local-detail density (already-textured flat scenes must not gain more
 * microcontrast). Boost-only, bounded, monotone, and it falls back to the
 * floor whenever the scene metrics are unavailable.</p>
 */
final class LlfAdaptiveDetail {
    private LlfAdaptiveDetail() {
    }

    /** Per-shot scene measurements; {@code -1} marks an unmeasured metric. */
    static final class Scene {
        /** p2..p98 display-domain crossing spread; -1 = unmeasured. */
        float spread = -1f;
        /** p5..p98 crossing spread (noise-robust variant); -1 = unmeasured. */
        float spreadRobust = -1f;
        /** Scene noise from the pre-denoise-slider model, sqrt(noiseS0/2 + noiseO0). */
        float noise = 0f;
        /** Fraction of the AE response above display white; 0 = neutral. */
        float clipped = 0f;
        /** Fraction of samples carrying local detail; -1 = unmeasured. */
        float density = -1f;
    }

    /** Policy tuning; {@link LocalLaplacian2} injects its tunables over these. */
    static final class Tuning {
        float floor = 0.30f;
        float max = 0.50f;
        float spreadFlat = 0.30f;
        float spreadFull = 0.60f;
        float noiseClean = 0.008f;
        float noiseNoisy = 0.030f;
        float noiseBoostMin = 0.30f;
        float clipBoostAtten = 0.5f;
        float clipRef = 0.05f;
        float densityLow = 0.02f;
        float densityHigh = 0.15f;
        float densityBoostMin = 0.5f;
    }

    /**
     * Effective detail for the remap LUT.
     *
     * <p>{@code flatness} rises as the spread narrows; the noise blend moves the
     * low percentile from p2 toward p5 (which sits above the noise floor) and
     * scales the boost down as noise rises; clipping and detail density scale it
     * down further. The result is clamped to [{@code floor}, {@code max}], with
     * {@code max} lifted to {@code floor} if the tunables are inverted. An
     * unmeasured spread returns the floor: no measurement, no adaptation.</p>
     */
    static float effectiveDetail(Tuning t, Scene s) {
        Tuning tt = t == null ? DEFAULTS : t;
        float floor = clamp(safe(tt.floor, DEFAULTS.floor), 0f, 4f);
        float max = Math.max(floor, clamp(safe(tt.max, DEFAULTS.max), 0f, 4f));
        if (s == null || !Float.isFinite(s.spread) || s.spread < 0f) return floor;

        float robust = Float.isFinite(s.spreadRobust) && s.spreadRobust >= 0f
                ? s.spreadRobust : s.spread;
        float noise = Float.isFinite(s.noise) ? Math.max(0f, s.noise) : 0f;
        float clipped = Float.isFinite(s.clipped) ? clamp(s.clipped, 0f, 1f) : 0f;
        float density = Float.isFinite(s.density) && s.density >= 0f
                ? clamp(s.density, 0f, 1f) : -1f;

        float noiseClean = safe(tt.noiseClean, DEFAULTS.noiseClean);
        float noiseNoisy = safe(tt.noiseNoisy, DEFAULTS.noiseNoisy);
        float noiseBoostMin = clamp(safe(tt.noiseBoostMin, DEFAULTS.noiseBoostMin), 0f, 1f);
        float clipBoostAtten = clamp(safe(tt.clipBoostAtten, DEFAULTS.clipBoostAtten), 0f, 1f);
        float clipRef = Math.max(safe(tt.clipRef, DEFAULTS.clipRef), 1e-4f);
        float densityLow = safe(tt.densityLow, DEFAULTS.densityLow);
        float densityHigh = safe(tt.densityHigh, DEFAULTS.densityHigh);
        float densityBoostMin = clamp(safe(tt.densityBoostMin, DEFAULTS.densityBoostMin), 0f, 1f);

        float noiseSmooth = smoothstep(noiseClean, noiseNoisy, noise);
        float spread = clamp(s.spread, 0f, 1f);
        // Noise robustness: on noisier scenes the low edge moves from p2 to p5,
        // which sits above the noise floor, so the measured spread does not
        // inflate from sensor noise alone.
        spread = mix(spread, clamp(robust, 0f, 1f), noiseSmooth);
        float flatness = 1f - smoothstep(safe(tt.spreadFlat, DEFAULTS.spreadFlat),
                safe(tt.spreadFull, DEFAULTS.spreadFull), spread);
        float noiseAtt = mix(1f, noiseBoostMin, noiseSmooth);
        float clipAtt = 1f - clipBoostAtten * clamp(clipped / clipRef, 0f, 1f);
        float densityAtt = density < 0f ? 1f : mix(1f, densityBoostMin,
                smoothstep(densityLow, densityHigh, density));

        float scale = flatness * noiseAtt * clipAtt * densityAtt;
        return clamp(floor + (max - floor) * scale, floor, max);
    }

    /** Non-finite tunable values (corrupted prefs) fall back to the default. */
    private static float safe(float v, float fallback) {
        return Float.isFinite(v) ? v : fallback;
    }

    private static final Tuning DEFAULTS = new Tuning();

    /**
     * Channel-crossing display-domain spread: per channel the p2 (or p2..p5
     * blended) and p98 positions of the weighted bin CDF, then the highest low
     * against the lowest high - white-balance agnostic, the same crossing logic
     * the white-point search uses. {@code robustBlend} 0 = p2, 1 = p5.
     * Returns -1 when no channel has mass.
     */
    static float spread(int[][] result, float[][] mapped, int bins, int[] norms, float robustBlend) {
        if (result == null || mapped == null || norms == null || bins < 2) return -1f;
        float blend = clamp(robustBlend, 0f, 1f);
        float low = 0f;
        float high = 1f;
        boolean any = false;
        for (int c = 0; c < 3 && c < result.length && c < mapped.length; c++) {
            if (norms.length <= c || norms[c] <= 0) continue;
            float p2 = percentile(result[c], mapped[c], norms[c], 0.02f);
            float p5 = percentile(result[c], mapped[c], norms[c], 0.05f);
            float p98 = percentile(result[c], mapped[c], norms[c], 0.98f);
            float lowC = p2 + (p5 - p2) * blend;
            if (!any) {
                low = lowC;
                high = p98;
                any = true;
            } else {
                low = Math.max(low, lowC);
                high = Math.min(high, p98);
            }
        }
        if (!any) return -1f;
        return clamp(high - low, 0f, 1f);
    }

    /**
     * Weighted percentile (fractional bin position, 0..1) of one channel's
     * counts over its display-domain bin positions. Returns 0 for an empty or
     * invalid input; the caller checks the channel normalizer first.
     */
    static float percentile(int[] counts, float[] mapped, int norm, float pct) {
        if (counts == null || mapped == null || counts.length == 0 || mapped.length == 0 || norm <= 0) {
            return 0f;
        }
        int last = counts.length - 1;
        if (last <= 0) return 0f;
        float target = clamp(pct, 0f, 1f) * norm;
        float acc = 0f;
        for (int i = 0; i < counts.length; i++) {
            acc += counts[i];
            if (acc >= target) {
                return mapped[Math.min(i, mapped.length - 1)] / last;
            }
        }
        return mapped[mapped.length - 1] / last;
    }

    /**
     * Fraction of samples whose bin index is at or above {@code thresholdBin}.
     * Returns -1 when there are no samples.
     */
    static float density(int[] counts, int thresholdBin) {
        if (counts == null || counts.length == 0) return -1f;
        int t = Math.max(0, Math.min(thresholdBin, counts.length - 1));
        long total = 0;
        long above = 0;
        for (int i = 0; i < counts.length; i++) {
            total += counts[i];
            if (i >= t) above += counts[i];
        }
        return total > 0 ? above / (float) total : -1f;
    }

    /** Smooth Hermite ramp; a hard step when {@code b <= a} (degenerate tuning). */
    static float smoothstep(float a, float b, float x) {
        if (!(b > a)) return x >= b ? 1f : 0f;
        float t = (x - a) / (b - a);
        if (t <= 0f) return 0f;
        if (t >= 1f) return 1f;
        return t * t * (3f - 2f * t);
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
