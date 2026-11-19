package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Point;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.scripts.ESD4D;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.settings.SettingsManager;
import com.particlesdevs.photoncamera.settings.SettingsManagerExtensions;
import com.particlesdevs.photoncamera.util.AdaptiveNoiseModel;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Device-side validation of the ESD4D noise estimation on the progressive
 * misaligned Gaussian blend. Runs the real pipeline headlessly on synthetic
 * bursts with a known injected noise model and asserts the fitted model
 * recovers it, including on lens-limited-textured and pixel-FPN scenes that
 * the old single-frame estimator overestimated (see
 * tools/noise-blend-calibration/mc.py for the matching CPU Monte-Carlo).
 *
 * Recovery tests use an initial baseModel 3x the injected noise so a silently
 * skipped/failed fit cannot pass. Low-ISO regressions use a calibrated prior
 * to verify that scene texture cannot inflate it into excessive denoising.
 */
@RunWith(AndroidJUnit4.class)
public class ESD4DNoiseBlendTest {
    private static final int W = 256;
    private static final int H = 256;
    private static final int BLACK = 64;
    private static final int WHITE = 1023;
    private static final int FRAMES = 9;
    // Injected noise: sized so the variance axis spans many bins and the
    // SPlace/OPlace(iso=100) floors (4.9e-5 / 2.2e-7) cannot bind.
    private static final float S = 5e-3f;
    private static final float O = 1e-5f;
    private static final double FIT_LO = 0.6, FIT_HI = 1.5;

    private final ArrayList<ImageFrame> openFrames = new ArrayList<>();
    private AdaptiveNoiseModel.Model lastMeasuredNoiseModel;

    @Test
    public void flatScene_recoversInjectedModel() throws Exception {
        assertRecoversModel(scene(false, 0), null, "flat");
    }

    @Test
    public void brightScene_withoutLowerRawCoverage_retainsCalibratedPrior() throws Exception {
        // The fitting region deliberately covers lower RAW values. A bright
        // scene entirely above that region cannot identify S/O; its calibrated
        // prior must survive instead of promoting scene residuals to noise.
        float[][] scene = new float[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                scene[y][x] = 0.35f + 0.5f * x / W + 0.05f * y / H;
        Pair<Double, Double> fitted = fit(scene, null, FRAMES, S, O, 1.0, 1600);
        assertEquals((double) S, fitted.first, 1e-9);
        assertEquals((double) O, fitted.second, 1e-12);
    }

    @Test
    public void texturedScene_recoversInjectedModel_andBeatsSingleFrame() throws Exception {
        // A spatial estimator needs some smooth regions to identify the
        // sensor floor. About a third of these 64x64 tiles retain texture;
        // the remaining tiles preserve the same smooth brightness gradient.
        // The low-ISO guard test below keeps the adversarial all-texture scene.
        float[][] scene = scene(true, 5);
        float[][] smooth = scene(false, 0);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                if ((x / 64 + y / 64) % 3 != 0) scene[y][x] = smooth[y][x];
        Pair<Double, Double> blend = fit(scene, null, FRAMES);
        assertSigmaRatio(blend, "textured/blend");
        Pair<Double, Double> single = fit(scene, null, 1);
        double sigBlend = sigmaAt(blend, 0.3);
        double sigSingle = sigmaAt(single, 0.3);
        android.util.Log.d("ESD4DNoiseBlend", "textured: blend sigma=" + sigBlend
                + " single-frame sigma=" + sigSingle);
        assertTrue("blend estimation should beat the single-frame path on texture ("
                + sigBlend + " vs " + sigSingle + ")", sigBlend < sigSingle);
    }

    @Test
    public void pixelFpnScene_recoversInjectedModel() throws Exception {
        // Fixed per-sensor pattern, unshifted across frames, 0.5*sigma strong.
        Random fpnRng = new Random(1234);
        float[] fpn = new float[W * H];
        double fpnStd = 0.5 * Math.sqrt(S * 0.4 + O);
        for (int i = 0; i < fpn.length; i++) fpn[i] = (float) (fpnRng.nextGaussian() * fpnStd);
        assertRecoversModel(scene(false, 0), fpn, "pixel-fpn");
    }

    @Test
    public void lowIsoTexturedScene_cannotPromoteTextureToNoise() throws Exception {
        double s = 5e-5, o = 1e-7;
        Pair<Double, Double> fitted = fit(scene(true, 5), null, FRAMES, s, o, 1.0, 100);
        assertLowIsoBound(fitted, s, o, "low-ISO texture");
    }

    @Test
    public void lowIsoBrightScene_withoutBlacks_cannotInflateReadNoise() throws Exception {
        float[][] scene = new float[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                scene[y][x] = 0.35f + 0.5f * x / W + 0.05f * y / H;
        double s = 5e-5, o = 1e-7;
        Pair<Double, Double> fitted = fit(scene, null, FRAMES, s, o, 1.0, 100);
        assertLowIsoBound(fitted, s, o, "low-ISO bright/no-black");
    }

    @Test
    public void rawLowIsoGradient_recoversNoiseBeforeStorageAndSafetyLimits() throws Exception {
        double s = 5e-5, o = 1e-7;
        for (int frames : new int[]{1, 3, 9}) {
            fit(lowerRawScene(false), null, frames, s, o, 3.0, 100);
            assertRawNoiseRecovery(lastMeasuredNoiseModel, s, o, "low-ISO gradient/f=" + frames);
        }
    }

    @Test
    public void rawLowIsoBrightnessLocalizedTexture_usesCleanNeighborRows() throws Exception {
        double s = 5e-5, o = 1e-7;
        fit(lowerRawScene(true), null, FRAMES, s, o, 3.0, 100);
        assertRawNoiseRecovery(lastMeasuredNoiseModel, s, o, "low-ISO localized texture");
        assertTrue("the elevated brightness region must be identified as texture",
                lastMeasuredNoiseModel.textureRows > 0);
    }

    @Test
    public void rawLowIsoNativeBinning_recoversQuarterVarianceAcrossGreenLayouts() throws Exception {
        double sourceS = 5e-5, sourceO = 1e-7;
        // RGGB/BGGR and GRBG/GBRG use different green tap layouts. Actual
        // native binning supplies their spatial covariance to the estimator.
        for (int cfa : new int[]{0, 1}) {
            fit(lowerRawScene(false), null, FRAMES, sourceS, sourceO, 3.0, 100, true, cfa);
            assertRawNoiseRecovery(lastMeasuredNoiseModel, sourceS / 4, sourceO / 4,
                    "low-ISO native binning/cfa=" + cfa);
        }
    }

    private static void assertRawNoiseRecovery(AdaptiveNoiseModel.Model measured,
                                               double s, double o, String name) {
        // This diagnostic is captured before the prior-based cap, persistent
        // store, and merge-strength scaling. Returning the 3x prior cannot pass.
        assertNotNull(name + ": spatial fit must provide a measurement", measured);
        for (double b : new double[]{0.03, 0.10, 0.20}) {
            double ratio = Math.sqrt((measured.s * b + measured.o) / (s * b + o));
            android.util.Log.d("ESD4DNoiseBlend", name + ": raw sigma ratio @" + b + " = " + ratio);
            assertTrue(name + ": raw fitted sigma ratio @" + b + " = " + ratio,
                    Double.isFinite(ratio) && ratio >= 0.7 && ratio <= 1.4);
        }
    }

    private static void assertLowIsoBound(Pair<Double, Double> fitted, double s, double o,
                                         String name) {
        for (double b : new double[]{0.0, 0.02, 0.2, 0.5, 0.85}) {
            double ratio = sigmaAt(fitted, b) / Math.sqrt(s * b + o);
            android.util.Log.d("ESD4DNoiseBlend", name + ": sigma ratio @" + b + " = " + ratio);
            assertTrue(name + ": fitted sigma ratio @" + b + " = " + ratio,
                    Double.isFinite(ratio) && ratio <= Math.sqrt(2.0) + 1e-5);
        }
        assertTrue(name + ": shot noise must survive rejection of a bad read offset",
                fitted.first >= 0.25 * s);
    }

    private void assertRecoversModel(float[][] scene, float[] fpn, String name) throws Exception {
        Pair<Double, Double> fitted = fit(scene, fpn, FRAMES);
        assertSigmaRatio(fitted, name);
    }

    /** Runs the full ESD4D pipeline on a synthetic burst and returns the
     * fitted (S, O) the estimator wrote into baseModel. */
    private Pair<Double, Double> fit(float[][] scene, float[] fpn, int blendFrames) throws Exception {
        // These fixtures inject high-ISO-sized noise. Keep their recovery
        // checks independent of the separate low-ISO protection tests.
        return fit(scene, fpn, blendFrames, S, O, 3.0, 1600);
    }

    private Pair<Double, Double> fit(float[][] scene, float[] fpn, int blendFrames,
                                    double injectedS, double injectedO, double priorScale,
                                    int iso) throws Exception {
        return fit(scene, fpn, blendFrames, injectedS, injectedO, priorScale, iso, false, 0);
    }

    private Pair<Double, Double> fit(float[][] scene, float[] fpn, int blendFrames,
                                    double injectedS, double injectedO, double priorScale,
                                    int iso, boolean binned, int cfa) throws Exception {
        setTunables(blendFrames);
        lastMeasuredNoiseModel = null;
        ArrayList<ImageFrame> images = new ArrayList<>();
        Random rng = new Random(4242);
        for (int f = 0; f < FRAMES; f++) {
            int hx = rng.nextInt(5) - 2; // handshake +-2 raw px
            int hy = rng.nextInt(5) - 2;
            ImageFrame frame;
            if (binned) {
                ByteBuffer sensor = makeBinningSource(scene, hx, hy, rng, injectedS, injectedO);
                frame = new ImageFrame(sensor, android.graphics.ImageFormat.RAW_SENSOR,
                        W * 2, W * 4, 0, sensor.capacity(), cfa, true);
                frame.width = W;
                frame.height = H;
            } else {
                frame = new ImageFrame(makeFrame(scene, hx, hy, rng, fpn,
                        injectedS, injectedO));
            }
            frame.pair = makePair(iso);
            images.add(frame);
            openFrames.add(frame);
        }
        ESD4D esd4d = new ESD4D(new Point(W, H), images);
        esd4d.parameters = makeParameters(injectedS, injectedO, priorScale, iso);
        esd4d.parameters.cfaPattern = (byte) cfa;
        if (binned) {
            esd4d.parameters.binned = true;
            esd4d.parameters.whiteLevel = WHITE * 4;
            esd4d.parameters.blackLevel = new float[]{BLACK * 4, BLACK * 4, BLACK * 4, BLACK * 4};
            esd4d.parameters.noiseModeler = new NoiseModeler(
                    new Pair[]{new Pair<>(injectedS * priorScale, injectedO * priorScale)},
                    3200, iso, cfa, null, true);
        }
        try {
            esd4d.Run();
            lastMeasuredNoiseModel = esd4d.measuredNoiseModel;
            Pair<Double, Double> model = esd4d.parameters.noiseModeler.baseModel[0];
            assertNotNull(model);
            android.util.Log.d("ESD4DNoiseBlend", "blendFrames=" + blendFrames
                    + " fitted S=" + model.first + " O=" + model.second
                    + " (injected S=" + injectedS + " O=" + injectedO + ")");
            return model;
        } finally {
            esd4d.close();
        }
    }

    /** A lower-RAW plane with texture restricted to a band of brightnesses. */
    private static float[][] lowerRawScene(boolean textured) {
        float[][] field = new float[H][W];
        Random rng = new Random(7391);
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++) field[y][x] = (float) rng.nextGaussian();
        blurInPlace(field, 1.2f);
        double energy = 0;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                field[y][x] = 0.7f * field[y][x] + 0.3f * (float) rng.nextGaussian();
                energy += field[y][x] * field[y][x];
            }
        }
        float textureScale = (float) (0.02 / Math.sqrt(energy / (W * H)));
        float[][] result = new float[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float b = 0.025f + 0.24f * x / (W - 1) + 0.01f * y / (H - 1);
                if (textured && b >= 0.08f && b <= 0.15f) b += field[y][x] * textureScale;
                result[y][x] = Math.max(0.005f, Math.min(0.31f, b));
            }
        }
        return result;
    }

    /** Independent full-resolution sensor noise, before the real four-tap sum. */
    private static ByteBuffer makeBinningSource(float[][] scene, int shiftX, int shiftY,
                                                Random rng, double s, double o) {
        int width = W * 2, height = H * 2;
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 2).order(ByteOrder.nativeOrder());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sx = Math.max(0, Math.min(width - 1, x - shiftX * 2));
                int sy = Math.max(0, Math.min(height - 1, y - shiftY * 2));
                double b = scene[sy / 2][sx / 2];
                double value = BLACK + (b + rng.nextGaussian() * Math.sqrt(s * b + o)) * (WHITE - BLACK);
                buffer.putShort((short) Math.round(Math.max(0.0, Math.min(WHITE, value))));
            }
        }
        buffer.rewind();
        return buffer;
    }

    private static void assertSigmaRatio(Pair<Double, Double> fitted, String name) {
        for (double b : new double[]{0.15, 0.3, 0.5}) {
            double ratio = sigmaAt(fitted, b) / sigmaTrue(b);
            android.util.Log.d("ESD4DNoiseBlend", name + ": sigma ratio @" + b + " = " + ratio);
            assertTrue(name + ": fitted sigma ratio @" + b + " = " + ratio
                    + " outside [" + FIT_LO + "," + FIT_HI + "]",
                    ratio >= FIT_LO && ratio <= FIT_HI);
        }
    }

    private static double sigmaAt(Pair<Double, Double> model, double brightness) {
        return Math.sqrt(Math.max(model.first, 0) * brightness + Math.max(model.second, 0));
    }

    private static double sigmaTrue(double brightness) {
        return Math.sqrt(S * brightness + O);
    }

    private static void setTunables(int blendFrames) {
        SettingsManager sm = PhotonCamera.getSettingsManagerStatic();
        SettingsManagerExtensions.setInt(sm, SettingsManager.SCOPE_GLOBAL,
                "pref_tunable_esd4d_enablehotpixelcorrection", 0);
        SettingsManagerExtensions.setInt(sm, SettingsManager.SCOPE_GLOBAL,
                "pref_tunable_esd4d_enableadaptivenoise", 1);
        SettingsManagerExtensions.setInt(sm, SettingsManager.SCOPE_GLOBAL,
                "pref_tunable_esd4d_enablenoisestore", 0);
        SettingsManagerExtensions.setInt(sm, SettingsManager.SCOPE_GLOBAL,
                "pref_tunable_esd4d_noiseblendmaxframes", blendFrames);
        SettingsManagerExtensions.setFloat(sm, SettingsManager.SCOPE_GLOBAL,
                "pref_tunable_esd4d_noisehistogramrange", 4.0f);
        SettingsManagerExtensions.setFloat(sm, SettingsManager.SCOPE_GLOBAL,
                "pref_tunable_esd4d_noisebrightnesstexturegate", 1.8f);
        SettingsManagerExtensions.setInt(sm, SettingsManager.SCOPE_GLOBAL,
                "pref_tunable_esd4d_noisescansubsample", 3);
        SettingsManagerExtensions.setFloat(sm, SettingsManager.SCOPE_GLOBAL,
                "pref_tunable_esd4d_noiseblendcalmpy", 1.0f);
        SettingsManagerExtensions.setFloat(sm, SettingsManager.SCOPE_GLOBAL,
                "pref_tunable_esd4d_lowisonoisevariancelimit", 2.0f);
        SettingsManagerExtensions.setInt(sm, SettingsManager.SCOPE_GLOBAL,
                "pref_tunable_esd4d_enablefitocorrection", 0);
    }

    /** Gradient with a black strip (occupies brightness bin 0) plus optional
     * lens-limited texture (gaussian-blurred noise + 30% white residue). */
    private static float[][] scene(boolean textured, long seed) {
        Random rng = new Random(seed);
        float[][] tex = null;
        if (textured) {
            tex = new float[H][W];
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                    tex[y][x] = (float) rng.nextGaussian();
            blurInPlace(tex, 1.2f);
            float std = 0, mean = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) mean += tex[y][x];
            mean /= W * H;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    tex[y][x] = 0.7f * tex[y][x] + 0.3f * (float) rng.nextGaussian();
                    std += tex[y][x] * tex[y][x];
                }
            std = (float) Math.sqrt(std / (W * H) - mean * mean);
            float scale = 0.05f / std;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) tex[y][x] *= scale;
        }
        float[][] scene = new float[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double v = 0.9 * ((double) x / W) * (0.55 + 0.45 * (double) y / H);
                if (x < 16) v = 0.0;
                if (tex != null) v += tex[y][x];
                scene[y][x] = (float) Math.max(0.0, Math.min(0.98, v));
            }
        }
        return scene;
    }

    /** Separable gaussian blur of an in-place float field. */
    private static void blurInPlace(float[][] img, float sigma) {
        int r = (int) Math.ceil(3 * sigma);
        float[] g = new float[2 * r + 1];
        float sum = 0;
        for (int i = -r; i <= r; i++) {
            g[i + r] = (float) Math.exp(-i * i / (2f * sigma * sigma));
            sum += g[i + r];
        }
        for (int i = 0; i < g.length; i++) g[i] /= sum;
        float[] tmp = new float[W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float acc = 0;
                for (int k = -r; k <= r; k++)
                    acc += g[k + r] * img[y][Math.max(0, Math.min(W - 1, x + k))];
                tmp[x] = acc;
            }
            System.arraycopy(tmp, 0, img[y], 0, W);
        }
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                float acc = 0;
                for (int k = -r; k <= r; k++)
                    acc += g[k + r] * img[Math.max(0, Math.min(H - 1, y + k))][x];
                tmp[y] = acc;
            }
            for (int y = 0; y < H; y++) img[y][x] = tmp[y];
        }
    }

    private static ByteBuffer makeFrame(float[][] scene, int shiftX, int shiftY,
                                        Random rng, float[] fpn) {
        return makeFrame(scene, shiftX, shiftY, rng, fpn, S, O);
    }

    private static ByteBuffer makeFrame(float[][] scene, int shiftX, int shiftY,
                                        Random rng, float[] fpn, double injectedS, double injectedO) {
        ByteBuffer buf = ByteBuffer.allocateDirect(W * H * 2).order(ByteOrder.nativeOrder());
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int sy = Math.max(0, Math.min(H - 1, y - shiftY));
                int sx = Math.max(0, Math.min(W - 1, x - shiftX));
                float I = scene[sy][sx];
                double sigma = Math.sqrt(injectedS * I + injectedO) * (WHITE - BLACK);
                double raw = BLACK + I * (WHITE - BLACK) + rng.nextGaussian() * sigma;
                if (fpn != null) raw += fpn[y * W + x] * (WHITE - BLACK);
                raw = Math.max(0, Math.min(65535, raw));
                buf.putShort((short) raw);
            }
        }
        buf.position(0);
        return buf;
    }

    private static Parameters makeParameters() {
        return makeParameters(S, O, 3.0, 1600);
    }

    private static Parameters makeParameters(double injectedS, double injectedO, double priorScale,
                                             int iso) {
        Parameters params = new Parameters();
        params.rawSize = new Point(W, H);
        params.whiteLevel = WHITE;
        params.blackLevel = new float[]{BLACK, BLACK, BLACK, BLACK};
        params.whitePoint = new float[]{1, 1, 1};
        params.cfaPattern = 0;
        params.tile = 16;
        params.tilesX = params.rawSize.x / 800 + 1;
        params.alignmentSize = new Point(params.rawSize.x / params.tile + 1,
                params.rawSize.y / params.tile + 1);
        params.gainMap = new float[]{1, 1, 1, 1};
        params.mapSize = new Point(1, 1);
        params.physicalID = 1;
        params.iso = iso;
        params.exposureTime = 1000000L;
        Pair<Double, Double>[] model = new Pair[]{
                new Pair<>(injectedS * priorScale, injectedO * priorScale),
                new Pair<>(injectedS * priorScale, injectedO * priorScale),
                new Pair<>(injectedS * priorScale, injectedO * priorScale)};
        params.noiseModeler = new NoiseModeler(model, 3200, iso, 0, null);
        return params;
    }

    private static IsoExpoSelector.ExpoPair makePair() {
        return makePair(1600);
    }

    private static IsoExpoSelector.ExpoPair makePair(int iso) {
        IsoExpoSelector.ExpoPair pair = new IsoExpoSelector.ExpoPair(33333333L, 1000000L, 1000000000L, iso, 100, 6400, 100);
        pair.layerMpy = 1.0f;
        pair.curlayer = IsoExpoSelector.ExpoPair.exposureLayer.Normal;
        return pair;
    }

    @After
    public void tearDown() {
        for (ImageFrame f : openFrames) {
            try { f.close(); } catch (Throwable ignored) { }
        }
        openFrames.clear();
    }
}
