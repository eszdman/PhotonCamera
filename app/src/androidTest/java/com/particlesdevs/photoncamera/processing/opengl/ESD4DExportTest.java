package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Point;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.ml.KernelNetNcnnProcessor;import com.particlesdevs.photoncamera.processing.opengl.GLDrawParams;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
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
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Device-side test for the ESD4D brightMap CPU export. Runs the real merge
 * pipeline headlessly (own EGL pbuffer context), then verifies:
 *   - brightMapCPU is exported at rawHalf resolution with finite luma in [0,1]
 *   - the exported CPU buffer feeds KernelNetProcessor and yields half-res params
 */
@RunWith(AndroidJUnit4.class)
public class ESD4DExportTest {
    private static final int W = 256;
    private static final int H = 256;
    private static final int BLACK = 64;
    private static final int WHITE = 1023;

    private final ArrayList<ImageFrame> openFrames = new ArrayList<>();

    @Test
    public void exportBrightMap_andRunKernelNet() throws Exception {
        disableMergingTunables();

        int[][] shifts = {{0, 0}, {3, -2}};
        float[][] scene = sceneFlat();
        float S = 2.9e-6f, O = 3.6e-6f;

        ArrayList<ImageFrame> images = new ArrayList<>();
        Random rng = new Random(4242);
        for (int[] sh : shifts) {
            ImageFrame frame = new ImageFrame(makeFrame(scene, sh[0], sh[1], S, O, rng));
            frame.pair = makePair();
            images.add(frame);
            openFrames.add(frame);
        }

        ESD4D esd4d = new ESD4D(new Point(W, H), images);
        esd4d.parameters = makeParameters();
        try {
            esd4d.Run();

            FloatBuffer cpu = esd4d.brightMapCPU;
            assertNotNull("brightMap must be exported to CPU", cpu);
            int outW = esd4d.brightMapCPUSize.x;
            int outH = esd4d.brightMapCPUSize.y;
            assertEquals(outW * outH, cpu.remaining());
            int finite = 0;
            float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
            double mean = 0;
            int n = cpu.remaining();
            FloatBuffer copy = cpu.duplicate();
            while (copy.hasRemaining()) {
                float v = copy.get();
                mean += v / n;
                assertTrue("brightMap values must be finite, got " + v, Float.isFinite(v));
                min = Math.min(min, v);
                max = Math.max(max, v);
                if (v > 0.0f && v <= 1.0f) finite++;
            }
            FloatBuffer sample = cpu.duplicate();
            StringBuilder sb = new StringBuilder("first8=");
            for (int i = 0; i < 8 && sample.hasRemaining(); i++) sb.append(sample.get()).append(" ");
            android.util.Log.d("ESD4DExport", "size=" + outW + "x" + outH + " min=" + min
                    + " max=" + max + " mean=" + mean + " glErr="
                    + android.opengl.GLES30.glGetError() + " " + sb);
            assertTrue("brightMap min/max out of range: [" + min + "," + max + "]", min >= 0.0f && max <= 1.0f);
            assertTrue("brightMap should contain lit pixels, got only " + finite + "/" + (outW * outH),
                    finite > outW * outH / 2);

            assertNotNull("kernelsMap must be created from kernel net output", esd4d.kernelsMap);
            assertEquals((outW - 1) / 2 + 1, esd4d.kernelsMap.mSize.x);
            assertEquals((outH - 1) / 2 + 1, esd4d.kernelsMap.mSize.y);

            KernelNetNcnnProcessor processor = new KernelNetNcnnProcessor(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
            try {
                assertTrue("model should load from assets", processor.isReady());
                KernelNetNcnnProcessor.Result result = processor.runInference(cpu, outW, outH, 0.08f);
                assertNotNull("inference must produce a result from exported brightMap", result);
                assertEquals((outW - 1) / 2 + 1, result.width);
                assertEquals((outH - 1) / 2 + 1, result.height);
                FloatBuffer params = result.asFloatBuffer();
                while (params.hasRemaining()) {
                    assertTrue("kernel params must be finite", Float.isFinite(params.get()));
                }
            } finally {
                processor.close();
            }
        } finally {
            esd4d.close();
        }
    }

    @Test
    public void readbackIsolation() {
        GLCoreBlockProcessing gl = new GLCoreBlockProcessing(new Point(64, 64),
                new GLFormat(GLFormat.DataType.UNSIGNED_16), GLDrawParams.Allocate.Direct);
        GLTexture tex = new GLTexture(64, 64, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);
        tex.BufferLoad();
        android.opengl.GLES30.glClearColor(0.5f, 0.25f, 0.75f, 1.0f);
        android.opengl.GLES30.glClear(android.opengl.GLES30.GL_COLOR_BUFFER_BIT);
        ByteBuffer raw = tex.textureBuffer(new GLFormat(GLFormat.DataType.FLOAT_32, 4), true);
        raw.order(ByteOrder.nativeOrder());
        FloatBuffer fb = raw.asFloatBuffer();
        android.util.Log.d("ESD4DExport", "isolation first4=" + fb.get(0) + " " + fb.get(1) + " "
                + fb.get(2) + " " + fb.get(3) + " glErr=" + android.opengl.GLES30.glGetError());
        assertEquals(0.5f, fb.get(0), 0.05f);
        tex.close();
        gl.close();
    }

    @Test
    public void kernelsMapTextureAcceptsNegativeValues() {
        GLCoreBlockProcessing gl = new GLCoreBlockProcessing(new Point(4, 4),
                new GLFormat(GLFormat.DataType.UNSIGNED_16), GLDrawParams.Allocate.Direct);
        GLTexture tex = new GLTexture(4, 4, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);
        float[] data = new float[4 * 4 * 4];
        for (int i = 0; i < 4 * 4; i++) {
            data[i * 4] = 0.5f;      // s1
            data[i * 4 + 1] = 1.0f;  // s2
            data[i * 4 + 2] = -1.0f; // rho (net range [-1,1])
            data[i * 4 + 3] = 1.0f;  // alpha
        }
        tex.loadData(FloatBuffer.wrap(data));
        tex.BufferLoad();
        ByteBuffer raw = tex.textureBuffer(new GLFormat(GLFormat.DataType.FLOAT_32, 4), true);
        raw.order(ByteOrder.nativeOrder());
        FloatBuffer fb = raw.asFloatBuffer();
        assertEquals(4 * 4 * 4, fb.remaining());
        int texels = 0;
        while (fb.hasRemaining()) {
            assertEquals("s1", 0.5f, fb.get(), 0.001f);
            assertEquals("s2", 1.0f, fb.get(), 0.001f);
            assertEquals("rho must survive half-float upload", -1.0f, fb.get(), 0.001f);
            assertEquals("alpha", 1.0f, fb.get(), 0.001f);
            texels++;
        }
        android.util.Log.d("ESD4DExport", "rgba16f keeps -1 rho in " + texels + " texels, glErr="
                + android.opengl.GLES30.glGetError());
        tex.close();
        gl.close();
    }

    private void disableMergingTunables() {
        SettingsManager sm = PhotonCamera.getSettingsManagerStatic();
        if (sm == null) fail("SettingsManager not available");
        SettingsManagerExtensions.setInt(sm, SettingsManager.SCOPE_GLOBAL, "pref_tunable_esd4d_enablehotpixelcorrection", 0);
        SettingsManagerExtensions.setInt(sm, SettingsManager.SCOPE_GLOBAL, "pref_tunable_esd4d_enableadaptivenoise", 0);
        SettingsManagerExtensions.setInt(sm, SettingsManager.SCOPE_GLOBAL, "pref_tunable_esd4d_enablenoisestore", 0);
    }

    private static float[][] sceneFlat() {
        float[][] scene = new float[H][W];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                scene[y][x] = 0.3f + 0.3f * x / W + 0.1f * (y % 8 == 0 ? 1 : 0);
            }
        }
        return scene;
    }

    private static ByteBuffer makeFrame(float[][] scene, int shiftX, int shiftY,
                                        float S, float O, Random rng) {
        ByteBuffer buf = ByteBuffer.allocateDirect(W * H * 2).order(ByteOrder.nativeOrder());
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int sy = Math.max(0, Math.min(H - 1, y - shiftY));
                int sx = Math.max(0, Math.min(W - 1, x - shiftX));
                float I = scene[sy][sx];
                double sigma = Math.sqrt(S * I + O) * (WHITE - BLACK);
                double raw = BLACK + I * (WHITE - BLACK) + rng.nextGaussian() * sigma;
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
        Pair<Double, Double>[] model = new Pair[]{new Pair<>(2.9e-6, 3.6e-6),
                new Pair<>(2.9e-6, 3.6e-6), new Pair<>(2.9e-6, 3.6e-6)};
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
