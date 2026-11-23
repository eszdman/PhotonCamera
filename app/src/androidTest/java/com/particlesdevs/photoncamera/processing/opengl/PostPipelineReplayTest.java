//
// Created by eszdman on 23.11.2026.
//

package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.particlesdevs.photoncamera.api.Settings;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.particlesdevs.photoncamera.processing.render.ColorCorrectionTransform;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.processing.render.SpecificSettingSensor;
import com.particlesdevs.photoncamera.util.Allocator;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class PostPipelineReplayTest {
    /** Render the same normalized FP16 Bayer file consumed by post_replay sdr. */
    @Test public void renderMergedBayer() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        Assume.assumeTrue(args.containsKey("postWidth"));
        int w = Integer.parseInt(args.getString("postWidth"));
        int h = Integer.parseInt(args.getString("postHeight"));
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = args.containsKey("postPrivateFiles") ? context.getFilesDir() : context.getExternalFilesDir(null);
        byte[] pixels = Files.readAllBytes(new File(root, "post-input.f16").toPath());
        assertEquals(w * h * 2, pixels.length);
        Parameters p = new Parameters();
        p.rawSize = new Point(w, h);
        p.sensorPix = new Rect(0, 0, w, h);
        p.mapSize = new Point(1, 1);
        p.gainMap = new float[]{1, 1, 1, 1};
        p.whiteLevel = 1;
        p.whitePoint = new float[]{.51f, 1, .72f};
        p.cfaPattern = 3;
        p.cameraRotation = Integer.parseInt(args.getString("postRotation", "0"));
        p.sensorToProPhoto = new float[]{1,0,0,0,1,0,0,0,1};
        p.CCT = new ColorCorrectionTransform();
        p.CCT.matrix = p.sensorToProPhoto.clone();
        if (args.containsKey("postMatrix")) {
            String[] values = args.getString("postMatrix").split(",");
            assertEquals(9, values.length);
            for (int i = 0; i < 9; i++) p.CCT.matrix[i] = Float.parseFloat(values[i]);
        }
        p.sensorSpecifics = new SpecificSettingSensor();
        Pair<Double,Double> noise = new Pair<>(Double.parseDouble(args.getString("postNoiseS", "0.0001")),
                Double.parseDouble(args.getString("postNoiseO", "0.000001")));
        p.noiseModeler = new NoiseModeler(new Pair[]{noise, noise, noise}, 800, 100, 3, null);
        p.noiseModeler.computeModel = new Pair[]{noise, noise, noise};
        Settings settings = PhotonCamera.getSettings();
        settings.hdrxNR = true;
        settings.alignAlgorithm = 1;
        settings.cfaPattern = p.cfaPattern;
        settings.noiseRstr = settings.contrastMpy = settings.shadows = 0;
        settings.saturation = 1;
        settings.ultraHdr = args.containsKey("postHdr");
        settings.aspect169 = false;

        String checkpoint = args.getString("postStage");
        boolean profile = args.containsKey("postProfile");
        Bundle timing = new Bundle();
        PostPipeline pipeline = checkpoint == null && !profile ? new PostPipeline() : new PostPipeline() {
            private long nodeStart;
            @Override public void startTimeMeasure() {
                super.startTimeMeasure();
                if (profile) {
                    android.opengl.GLES30.glFinish();
                    nodeStart = SystemClock.elapsedRealtimeNanos();
                }
            }

            @Override protected void postDrawOracle(Node node) {
                super.postDrawOracle(node);
                String name = node.getClass().getSimpleName();
                if (profile) {
                    android.opengl.GLES30.glFinish();
                    timing.putString("post_node_ms_" + name,
                            Double.toString((SystemClock.elapsedRealtimeNanos() - nodeStart) / 1_000_000.0));
                }
                if (!name.equals(checkpoint)) return;
                node.WorkingTexture.BufferLoad();
                ByteBuffer packed = node.WorkingTexture.textureBufferHalfFloat();
                if (packed == null) throw new AssertionError("FP16 readback failed");
                try (FileOutputStream stream = new FileOutputStream(new File(root, "post-stage.rgba16f"))) {
                    while (packed.hasRemaining()) stream.getChannel().write(packed);
                } catch (IOException ex) {
                    throw new AssertionError(ex);
                } finally {
                    Allocator.free(packed);
                }
            }
        };
        ByteBuffer input = Allocator.allocate(pixels.length);
        input.put(pixels).rewind();
        android.content.SharedPreferences prefs = PhotonCamera.getSettingsManagerStatic().getDefaultPreferences();
        String key = "pref_tunable_postpipeline_tonepipeline";
        String saved = prefs.getString(key, null);
        String filterKey = "pref_tunable_esd3d2_version";
        String savedFilter = prefs.getString(filterKey, null);
        try {
            android.content.SharedPreferences.Editor overrides = prefs.edit().putString(key, args.getString("postTone", "curve"));
            if (args.containsKey("postDenoiseVersion")) overrides.putString(filterKey, args.getString("postDenoiseVersion"));
            overrides.apply();
            long start = SystemClock.elapsedRealtimeNanos();
            Bitmap bitmap = pipeline.Run(input, p);
            Bitmap gain = settings.ultraHdr ? pipeline.RunHDRGainMap(p, bitmap, 6).bitmap : null;
            long elapsed = SystemClock.elapsedRealtimeNanos() - start;
            timing.putString("post_total_ms", Double.toString(elapsed / 1_000_000.0));
            InstrumentationRegistry.getInstrumentation().sendStatus(2, timing);
            try {
                if (!args.containsKey("postTimingOnly")) {
                    ByteBuffer rgba = ByteBuffer.allocate(bitmap.getByteCount());
                    bitmap.copyPixelsToBuffer(rgba);
                    Files.write(new File(root, "post-gles.rgba8").toPath(), rgba.array());
                    if (gain != null) {
                        rgba.clear();
                        gain.copyPixelsToBuffer(rgba);
                        Files.write(new File(root, "post-gles-gain.rgba8").toPath(), rgba.array());
                    }
                }
            } finally {
                if (gain != null) gain.recycle();
                bitmap.recycle();
            }
        } finally {
            android.content.SharedPreferences.Editor restore = prefs.edit().putString(key, saved);
            if (args.containsKey("postDenoiseVersion")) restore.putString(filterKey, savedFilter);
            restore.commit();
            if (!pipeline.isStackFrameReleased()) Allocator.free(input);
        }
    }
}
