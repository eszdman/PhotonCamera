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

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Random;

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
 * The initial baseModel is deliberately 3x the injected noise so a silently
 * skipped/failed fit cannot pass the assertions.
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

    @Test
    public void flatScene_recoversInjectedModel() throws Exception {
        assertRecoversModel(scene(false, 0), null, "flat");
    }

    @Test
    public void brightScene_withoutBlacks_recoversInjectedModel() throws Exception {
        // Regression for the old minBr rescale: scenes without near-black
        // content used to get S inflated by 1/(1-minBr). Bright-only scene,
        // looser upper bound because O is extrapolated without dark data.
        float[][] scene = new float[H][W];
        for (int y = 0; y < H; y++)
            for (int x = 0; x < W; x++)
                scene[y][x] = 0.35f + 0.5f * x / W + 0.05f * y / H;
        Pair<Double, Double> fitted = fit(scene, null, FRAMES);
        for (double b : new double[]{0.4, 0.55, 0.75}) {
            double ratio = sigmaAt(fitted, b) / sigmaTrue(b);
            android.util.Log.d("ESD4DNoiseBlend", "bright/no-black: sigma ratio @" + b + " = " + ratio);
            assertTrue("bright no-black: fitted sigma ratio @" + b + " = " + ratio,
                    ratio >= FIT_LO && ratio <= 1.6);
        }
    }

    @Test
    public void texturedScene_recoversInjectedModel_andBeatsSingleFrame() throws Exception {
        float[][] scene = scene(true, 5);
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

    private void assertRecoversModel(float[][] scene, float[] fpn, String name) throws Exception {
        Pair<Double, Double> fitted = fit(scene, fpn, FRAMES);
        assertSigmaRatio(fitted, name);
    }

    /** Runs the full ESD4D pipeline on a synthetic burst and returns the
     * fitted (S, O) the estimator wrote into baseModel. */
    private Pair<Double, Double> fit(float[][] scene, float[] fpn, int blendFrames) throws Exception {
        setTunables(blendFrames);
        ArrayList<ImageFrame> images = new ArrayList<>();
        Random rng = new Random(4242);
        for (int f = 0; f < FRAMES; f++) {
            int hx = rng.nextInt(5) - 2; // handshake +-2 raw px
            int hy = rng.nextInt(5) - 2;
            ImageFrame frame = new ImageFrame(makeFrame(scene, hx, hy, rng, fpn));
            frame.pair = makePair();
            images.add(frame);
            openFrames.add(frame);
        }
        ESD4D esd4d = new ESD4D(new Point(W, H), images);
        esd4d.parameters = makeParameters();
        try {
            esd4d.Run();
            Pair<Double, Double> model = esd4d.parameters.noiseModeler.baseModel[0];
            assertNotNull(model);
            android.util.Log.d("ESD4DNoiseBlend", "blendFrames=" + blendFrames
                    + " fitted S=" + model.first + " O=" + model.second
                    + " (injected S=" + S + " O=" + O + ")");
            return model;
        } finally {
            esd4d.close();
        }
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
        SettingsManagerExtensions.setInt(sm, SettingsManager.SCOPE_GLOBAL,
                "pref_tunable_esd4d_noisefitvarbins", 45);
        SettingsManagerExtensions.setFloat(sm, SettingsManager.SCOPE_GLOBAL,
                "pref_tunable_esd4d_noisefitgatempy", 2.0f);
        SettingsManagerExtensions.setInt(sm, SettingsManager.SCOPE_GLOBAL,
                "pref_tunable_esd4d_noisescansubsample", 3);
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
        ByteBuffer buf = ByteBuffer.allocateDirect(W * H * 2).order(ByteOrder.nativeOrder());
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int sy = Math.max(0, Math.min(H - 1, y - shiftY));
                int sx = Math.max(0, Math.min(W - 1, x - shiftX));
                float I = scene[sy][sx];
                double sigma = Math.sqrt(S * I + O) * (WHITE - BLACK);
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
        params.iso = 100;
        params.exposureTime = 1000000L;
        // 3x the injected model: a skipped or failed fit keeps these values
        // and must fail the ratio assertions.
        Pair<Double, Double>[] model = new Pair[]{new Pair<>(S * 3.0, O * 3.0),
                new Pair<>(S * 3.0, O * 3.0), new Pair<>(S * 3.0, O * 3.0)};
        params.noiseModeler = new NoiseModeler(model, 100, 100, 0, null);
        return params;
    }

    private static IsoExpoSelector.ExpoPair makePair() {
        IsoExpoSelector.ExpoPair pair = new IsoExpoSelector.ExpoPair(33333333L, 1000000L, 1000000000L, 100, 100, 6400, 100);
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
