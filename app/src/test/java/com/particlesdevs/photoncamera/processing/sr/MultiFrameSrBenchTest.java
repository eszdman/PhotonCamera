package com.particlesdevs.photoncamera.processing.sr;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Host bench for the multi-frame SR algorithm: synthesizes handheld bursts
 * (a scene with genuine content above the raw Nyquist, a pixel-aperture +
 * lens PSF, per-frame sub-pixel shifts and exposure, noise), mirrors the
 * shader's scatter deposit and Gaussian-difference band recovery, and
 * measures how much of the above-Nyquist band survives.
 *
 * <p>This is the scoreboard for the restoration gain, the gates and the
 * sample weighting: an algorithm mirror, so changes can be compared without
 * a device round trip. It is deliberately analytic (sines + an exact PSF
 * attenuation) so the truth is known at every frequency.</p>
 */
public class MultiFrameSrBenchTest {

    /** Raw grid size (samples per axis). */
    private static final int RAW = 96;
    /** Output expansion (output px per raw px). */
    private static final int EXP = 3;
    private static final int OUT = RAW * EXP;
    /** Lens blur sigma in raw px (a decent phone lens, slightly over-sampled). */
    private static final double LENS_SIGMA_RAW = 0.30;
    /** Per-sample Gaussian noise std (normalized signal units). */
    private static final double NOISE = 0.010;

    /** A scene: sum of sinusoids up to a hard cutoff (cycles per output px). */
    private static final class Scene {
        final double[] fx, fy, ph, amp, mtf;
        final double fOpt;

        Scene(long seed, int count, double fOpt) {
            this.fOpt = fOpt;
            Random r = new Random(seed);
            fx = new double[count];
            fy = new double[count];
            ph = new double[count];
            amp = new double[count];
            mtf = new double[count];
            for (int k = 0; k < count; k++) {
                double f = fOpt * (0.15 + 0.85 * r.nextDouble());
                double a = r.nextDouble() * 2.0 * Math.PI;
                fx[k] = f * Math.cos(a);
                fy[k] = f * Math.sin(a);
                ph[k] = r.nextDouble() * 2.0 * Math.PI;
                // 1/f-ish spectrum so low frequencies carry the structure.
                amp[k] = 1.0 / (0.15 + f * 8.0);
                // Sensor PSF at this frequency, in cycles per raw px.
                double fRaw = Math.hypot(fx[k], fy[k]) * EXP;
                double box = fRaw < 1e-9 ? 1.0 : Math.sin(Math.PI * fRaw) / (Math.PI * fRaw);
                double lens = Math.exp(-2.0 * Math.PI * Math.PI
                        * LENS_SIGMA_RAW * LENS_SIGMA_RAW * fRaw * fRaw);
                mtf[k] = box * lens;
            }
        }

        /** Scene value at an output-grid position (continuous, out px). */
        double value(double x, double y) {
            double s = 0;
            for (int k = 0; k < fx.length; k++) {
                s += amp[k] * Math.cos(2 * Math.PI * (fx[k] * x + fy[k] * y) + ph[k]);
            }
            return s;
        }

        /** Sensor sample at a raw site: the scene through the PSF. */
        double sample(double x, double y) {
            double s = 0;
            for (int k = 0; k < fx.length; k++) {
                s += amp[k] * mtf[k] * Math.cos(2 * Math.PI * (fx[k] * x + fy[k] * y) + ph[k]);
            }
            return s;
        }
    }

    /** Result of one simulated burst. */
    private static final class Burst {
        double[] sumV = new double[OUT * OUT];
        double[] sumW = new double[OUT * OUT];
        int frames;
    }

    /**
     * Simulates {@code frames} handheld frames: random sub-pixel shifts in
     * output px and per-frame exposure, sampled through the PSF with noise,
     * bilinearly splatted into the accumulators (the shader's deposit).
     *
     * <p>{@code alignError} injects a per-frame registration error (output
     * px, random direction): the deposit lands at the wrong position, which
     * is exactly what the alignment workstream has to minimize.</p>
     */
    private static Burst simulate(Scene scene, int frames, double exposureSpread,
                                  double alignError, long seed) {
        Burst b = new Burst();
        Random r = new Random(seed);
        b.frames = frames;
        for (int f = 0; f < frames; f++) {
            double ox = r.nextDouble() * EXP;
            double oy = r.nextDouble() * EXP;
            double gain = 1.0 + exposureSpread * (r.nextDouble() * 2.0 - 1.0);
            double ex = 0.0, ey = 0.0;
            if (alignError > 0) {
                double a = r.nextDouble() * 2 * Math.PI;
                ex = alignError * Math.cos(a);
                ey = alignError * Math.sin(a);
            }
            for (int j = 0; j < RAW; j++) {
                for (int i = 0; i < RAW; i++) {
                    // Raw site center in output px, plus the frame's shift.
                    double x = i * EXP + EXP / 2.0 + ox;
                    double y = j * EXP + EXP / 2.0 + oy;
                    double v = scene.sample(x, y) * gain
                            + NOISE * r.nextGaussian();
                    // Bilinear splat at (x, y) in output texel-index space,
                    // displaced by this frame's registration error.
                    double cx = x - 0.5 + ex;
                    double cy = y - 0.5 + ey;
                    int x0 = (int) Math.floor(cx);
                    int y0 = (int) Math.floor(cy);
                    double fx = cx - x0;
                    double fy = cy - y0;
                    for (int dy = 0; dy <= 1; dy++) {
                        for (int dx = 0; dx <= 1; dx++) {
                            int tx = x0 + dx;
                            int ty = y0 + dy;
                            if (tx < 0 || ty < 0 || tx >= OUT || ty >= OUT) continue;
                            double w = (dx == 0 ? 1 - fx : fx) * (dy == 0 ? 1 - fy : fy);
                            b.sumV[ty * OUT + tx] += w * v;
                            b.sumW[ty * OUT + tx] += w;
                        }
                    }
                }
            }
        }
        return b;
    }

    /** Separable Gaussian blur, clamped edges (the shader's DoG halves). */
    private static double[] blur(double[] src, int w, int h, double sigma) {
        int r = Math.max(1, (int) Math.ceil(2.0 * sigma));
        double[] k = new double[2 * r + 1];
        double ks = 0;
        for (int i = -r; i <= r; i++) {
            k[i + r] = Math.exp(-0.5 * i * i / (sigma * sigma));
            ks += k[i + r];
        }
        for (int i = 0; i < k.length; i++) k[i] /= ks;
        double[] tmp = new double[w * h];
        double[] dst = new double[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double s = 0;
                for (int i = -r; i <= r; i++) {
                    int xx = Math.min(w - 1, Math.max(0, x + i));
                    s += k[i + r] * src[y * w + xx];
                }
                tmp[y * w + x] = s;
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double s = 0;
                for (int i = -r; i <= r; i++) {
                    int yy = Math.min(h - 1, Math.max(0, y + i));
                    s += k[i + r] * tmp[yy * w + x];
                }
                dst[y * w + x] = s;
            }
        }
        return dst;
    }

    /** The recovery's band-pass: G(sigmaN) - G(sigmaC) at [fN, 1.5 fN]. */
    private static double[] bandOf(double[] field, int w, int h, double perOut) {
        double sigN = clamp(0.187 / clamp(0.5 * perOut, 1e-4, 0.45), 0.35, 0.95);
        double sigC = clamp(0.187 / clamp(0.75 * perOut, 1e-4, 0.45), 0.35, 0.95);
        double[] a = blur(field, w, h, sigN);
        double[] b = blur(field, w, h, sigC);
        double[] out = new double[w * h];
        for (int i = 0; i < out.length; i++) out[i] = a[i] - b[i];
        return out;
    }

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double sinc(double f) {
        return f < 1e-6 ? 1.0 : Math.sin(Math.PI * f) / (Math.PI * f);
    }

    /**
     * Mirror of the shader's modeled restoration gain: the inverse of the
     * sampling MTF (pixel aperture x bilinear deposit x lens) at the band
     * center, capped. Kept in lockstep with merge/srrecover.glsl.
     */
    private static double srRestoreGain(double perOut) {
        double fRaw = 0.625;
        double fOut = fRaw * perOut;
        double box = sinc(fRaw);
        double splat = sinc(fOut);
        splat *= splat;
        double lens = Math.exp(-2 * Math.PI * Math.PI
                * LENS_SIGMA_RAW * LENS_SIGMA_RAW * fRaw * fRaw);
        double mtf = Math.max(box * splat * lens, 1e-4);
        return Math.min(Math.max(1.0 / mtf, 1.0), 2.2);
    }

    /** True scene band on the output grid (no PSF, the target signal). */
    private static double[] truthBand(Scene scene) {
        double[] t = new double[OUT * OUT];
        for (int y = 0; y < OUT; y++) {
            for (int x = 0; x < OUT; x++) {
                t[y * OUT + x] = scene.value(x + 0.5, y + 0.5);
            }
        }
        return bandOf(t, OUT, OUT, 1.0 / EXP);
    }

    /** Recovered band from a simulated burst, with the restore gain applied. */
    private static double[] recoveredBand(Burst b, double restore) {
        double[] f = new double[OUT * OUT];
        for (int i = 0; i < f.length; i++) {
            f[i] = b.sumW[i] > 1e-9 ? restore * b.sumV[i] / b.sumW[i] : 0.0;
        }
        return bandOf(f, OUT, OUT, 1.0 / EXP);
    }

    /** Normalized correlation between two fields (central region, valid only). */
    private static double correlation(double[] a, double[] b) {
        int n = 0;
        double ma = 0, mb = 0;
        int m = 8;
        for (int y = m; y < OUT - m; y++) {
            for (int x = m; x < OUT - m; x++) {
                ma += a[y * OUT + x];
                mb += b[y * OUT + x];
                n++;
            }
        }
        ma /= n;
        mb /= n;
        double sab = 0, sa = 0, sb = 0;
        for (int y = m; y < OUT - m; y++) {
            for (int x = m; x < OUT - m; x++) {
                double da = a[y * OUT + x] - ma;
                double db = b[y * OUT + x] - mb;
                sab += da * db;
                sa += da * da;
                sb += db * db;
            }
        }
        return sab / Math.sqrt(Math.max(sa * sb, 1e-30));
    }

    /** RMS amplitude of a field's central region. */
    private static double amplitude(double[] a) {
        int m = 8;
        double s = 0;
        int n = 0;
        for (int y = m; y < OUT - m; y++) {
            for (int x = m; x < OUT - m; x++) {
                s += a[y * OUT + x] * a[y * OUT + x];
                n++;
            }
        }
        return Math.sqrt(s / n);
    }

    @Test
    public void recoveredBandCarriesRealAboveNyquistContent() {
        Scene scene = new Scene(42, 48, 0.5);
        Burst b = simulate(scene, 24, 0.0, 0.0, 7);
        double[] truth = truthBand(scene);
        double[] rec = recoveredBand(b, 1.3);
        double corr = correlation(rec, truth);
        double ratio = amplitude(rec) / Math.max(amplitude(truth), 1e-12);
        System.out.printf("bench: above-Nyquist corr=%.3f amplitude ratio=%.3f%n", corr, ratio);
        assertTrue("recovered band must correlate with the true above-Nyquist content",
                corr > 0.5);
        assertTrue("restoration must recover a useful fraction of the band", ratio > 0.25);
    }

    @Test
    public void modeledGainRecoversMoreBandAtSafeCorrelation() {
        Scene scene = new Scene(42, 48, 0.5);
        Burst b = simulate(scene, 24, 0.0, 0.0, 7);
        double[] truth = truthBand(scene);
        double gain = srRestoreGain(1.0 / EXP);
        double[] rec = recoveredBand(b, gain);
        double corr = correlation(rec, truth);
        double ratio = amplitude(rec) / Math.max(amplitude(truth), 1e-12);
        System.out.printf("bench: modeled gain %.2f -> ratio=%.3f corr=%.3f%n",
                gain, ratio, corr);
        assertTrue("modeled gain must be above the flat baseline", gain > 1.5);
        assertTrue("modeled restoration must recover a majority of the band", ratio > 0.5);
        assertTrue("correlation must survive the modeled gain", corr > 0.5);
    }

    @Test
    public void higherRestoreRaisesAmplitudeWithoutBreakingCorrelation() {
        Scene scene = new Scene(42, 48, 0.5);
        Burst b = simulate(scene, 24, 0.0, 0.0, 7);
        double[] truth = truthBand(scene);
        double[] low = recoveredBand(b, 1.0);
        double[] high = recoveredBand(b, 2.2);
        double corrLow = correlation(low, truth);
        double corrHigh = correlation(high, truth);
        double ampLow = amplitude(low);
        double ampHigh = amplitude(high);
        System.out.printf("bench: gain 1.0 corr=%.3f amp=%.4f | gain 2.2 corr=%.3f amp=%.4f%n",
                corrLow, ampLow, corrHigh, ampHigh);
        assertTrue("restoration gain raises the recovered amplitude", ampHigh > ampLow * 1.5);
        assertTrue("correlation must survive the gain", corrHigh > 0.5);
    }

    // ---- reconstruction (upscale) bench: kernel width vs acutance --------

    /** Smooth 1D step of transition width w (px). */
    private static double[] stepCurve(int n, double w) {
        double[] s = new double[n];
        for (int i = 0; i < n; i++) {
            s[i] = 0.5 * (1.0 + Math.tanh((i - n / 2.0) / w));
        }
        return s;
    }

    /** Clamped-edge Gaussian blur of a 1D signal. */
    private static double[] blur1(double[] x, double sigma) {
        int r = Math.max(1, (int) Math.ceil(2.5 * sigma));
        double[] k = new double[2 * r + 1];
        double s = 0;
        for (int i = -r; i <= r; i++) {
            k[i + r] = Math.exp(-0.5 * i * i / (sigma * sigma));
            s += k[i + r];
        }
        for (int i = 0; i < k.length; i++) k[i] /= s;
        double[] out = new double[x.length];
        for (int i = 0; i < x.length; i++) {
            double a = 0;
            for (int j = -r; j <= r; j++) {
                int p = Math.min(x.length - 1, Math.max(0, i + j));
                a += k[j + r] * x[p];
            }
            out[i] = a;
        }
        return out;
    }

    /**
     * Edge-aligned unsharp with a Weber-like cap: x + a*(x - blur_wide(x)),
     * the added term limited to cap times the local step height (scaled by
     * the local slope's share of the edge) - the aniso shader's acutance
     * structure, reduced to 1D.
     */
    private static double[] acutance1(double[] x, double a, double wide, double cap, double stepHeight) {
        double[] w = blur1(x, wide);
        double[] out = x.clone();
        for (int i = 0; i < x.length; i++) {
            double add = a * (x[i] - w[i]);
            double lim = cap * stepHeight;
            out[i] = x[i] + Math.max(-lim, Math.min(lim, add));
        }
        return out;
    }

    /** Peak edge slope (max |first difference|). */
    private static double slope1(double[] x) {
        double m = 0;
        for (int i = 1; i < x.length; i++) m = Math.max(m, Math.abs(x[i] - x[i - 1]));
        return m;
    }

    /** Overshoot beyond the signal's own [min,max] at the edges. */
    private static double overshoot1(double[] x) {
        double lo = 0, hi = 1;
        double over = 0;
        for (int i = 2; i < x.length - 2; i++) {
            if (x[i] > hi) over = Math.max(over, x[i] - hi);
            if (x[i] < lo) over = Math.max(over, lo - x[i]);
        }
        return over;
    }

    @Test
    public void reconstructionSharpenTradeoff() {
        int n = 129;
        double[] nativeEdge = stepCurve(n, 1.0);
        double nativeSlope = slope1(nativeEdge);
        System.out.printf("bench recon: native slope=%.4f%n", nativeSlope);

        // The general reconstruction (deliberately soft): a wider Gaussian.
        double[] soft = blur1(nativeEdge, 1.1);
        System.out.printf("bench recon: soft kernel slope=%.4f (%.2fx native) overshoot=%.3f%n",
                slope1(soft), slope1(soft) / nativeSlope, overshoot1(soft));

        // Route 1: narrower kernel (the failed 0.68 trim direction).
        double[] narrow = blur1(nativeEdge, 0.8);
        System.out.printf("bench recon: narrow kernel slope=%.4f (%.2fx) overshoot=%.3f%n",
                slope1(narrow), slope1(narrow) / nativeSlope, overshoot1(narrow));

        // Route 2: the soft kernel plus bounded acutance (the SR proposal).
        double[] acut = acutance1(soft, 0.9, 3.0, 0.06, 1.0);
        System.out.printf("bench recon: soft + acutance slope=%.4f (%.2fx) overshoot=%.3f%n",
                slope1(acut), slope1(acut) / nativeSlope, overshoot1(acut));

        // The acutance route must recover most of the native edge slope while
        // keeping the overshoot smaller than a narrower kernel that reaches
        // the same slope (this is why the SR path sharpens via acutance, not
        // via a narrower reconstruction kernel).
        assertTrue("soft reconstruction is genuinely softer", slope1(soft) < 0.9 * nativeSlope);
        assertTrue("bounded acutance must recover most of the native slope",
                slope1(acut) > 0.85 * nativeSlope);
        assertTrue("acutance overshoot must stay bounded", overshoot1(acut) < 0.07);
        assertTrue("narrow kernel must not beat acutance at equal or lower overshoot",
                !(slope1(narrow) > slope1(acut) && overshoot1(narrow) <= overshoot1(acut)));
    }

    @Test
    public void registrationErrorDegradesRecovery() {
        Scene scene = new Scene(42, 48, 0.5);
        double[] truth = truthBand(scene);
        double gain = srRestoreGain(1.0 / EXP);
        double clean = correlation(recoveredBand(
                simulate(scene, 24, 0.0, 0.0, 7), gain), truth);
        double err05 = correlation(recoveredBand(
                simulate(scene, 24, 0.0, 0.5, 7), gain), truth);
        double err10 = correlation(recoveredBand(
                simulate(scene, 24, 0.0, 1.0, 7), gain), truth);
        System.out.printf("bench: alignment corr err=0 -> %.3f, 0.5px -> %.3f, 1.0px -> %.3f%n",
                clean, err05, err10);
        assertTrue("clean bursts must recover", clean > 0.5);
        assertTrue("registration error must degrade the recovery", err05 < clean && err10 < err05);
    }
}
