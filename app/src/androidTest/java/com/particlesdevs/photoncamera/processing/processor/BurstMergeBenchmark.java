//
// Created by eszdman on 19.11.2026.
//

package com.particlesdevs.photoncamera.processing.processor;

import static org.junit.Assert.*;

import android.content.Context;
import android.os.PowerManager;
import android.os.SystemClock;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.particlesdevs.photoncamera.processing.opengl.scripts.ESD4D;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.util.Allocator;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.ArrayList;
import org.json.JSONObject;

public final class BurstMergeBenchmark {
    public static JSONObject measure(String backend, ArrayList<ImageFrame> frames, Parameters p)
        throws Exception {
        PowerManager power =
            (PowerManager) PhotonCamera.getAppContext().getSystemService(Context.POWER_SERVICE);
        JSONObject result = new JSONObject().put("packed_bits", frames.get(0).packedBits);
        if (android.os.Build.VERSION.SDK_INT >= 29)
            result.put("thermal_start", power.getCurrentThermalStatus());
        ByteBuffer output = null;
        KomputeBurst nativeShot = null;
        ESD4D gl = null;
        try {
            long cpu = android.os.Process.getElapsedCpuTime();
            long start = SystemClock.elapsedRealtimeNanos();
            if ("kompute".equals(backend)) {
                nativeShot = KomputeBurst.tryMerge(frames, p);
                assertNotNull("Kompute fallback invalidates timing", nativeShot);
                output = nativeShot.bayer;
            } else {
                assertEquals("gles", backend);
                gl = new ESD4D(p.rawSize, frames);
                gl.parameters = p;
                try {
                    gl.Run();
                    output = gl.Output;
                } finally {
                    gl.close();
                }
            }
            for (ImageFrame frame : frames)
                if (frame.buffer != null)
                    frame.close();
            result.put("wall_ms", (SystemClock.elapsedRealtimeNanos() - start) / 1e6);
            result.put("process_cpu_ms", android.os.Process.getElapsedCpuTime() - cpu);
            assertEquals(p.rawSize.x * p.rawSize.y * 2, output.capacity());
            result.put("output_sha256", sha256(output));
            if (nativeShot != null) {
                PostPipeline post = new PostPipeline();
                nativeShot.handOffKernels(post);
                try {
                    result.put("kernel_sha256", sha256(post.kernelParamsBase));
                } finally {
                    Allocator.free(post.kernelParamsBase);
                }
            } else {
                result.put("kernel_sha256", sha256(gl.kernelsMapBase));
            }
            if (android.os.Build.VERSION.SDK_INT >= 29)
                result.put("thermal_end", power.getCurrentThermalStatus());
            return result.put("backend", backend)
                .put("frames", frames.size())
                .put("width", p.rawSize.x)
                .put("height", p.rawSize.y);
        } finally {
            if (output != null)
                Allocator.free(output);
            if (nativeShot != null)
                nativeShot.releaseKernels();
            if (gl != null && gl.kernelsMapBase != null)
                Allocator.free(gl.kernelsMapBase);
        }
    }

    private static String sha256(ByteBuffer buffer) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        ByteBuffer bytes = buffer.duplicate();
        bytes.clear();
        hash.update(bytes);
        StringBuilder hex = new StringBuilder();
        for (byte b : hash.digest()) hex.append(String.format(java.util.Locale.ROOT, "%02x", b));
        return hex.toString();
    }
}
