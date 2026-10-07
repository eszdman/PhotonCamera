package com.particlesdevs.photoncamera.util;

import android.content.Context;
import android.opengl.GLES30;
import android.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.GLTexture;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Debug dump of the SR post inputs: the real KernelNet map, the fused lattice
 * and the recovered band. Reads the GL textures directly with a fresh FBO and
 * explicit pixel-pack state (the app's own {@code textureBufferHalfFloatNative}
 * reads a stale framebuffer in the SR path - the normal path is a GPU handoff
 * with no readback - so it returned empty data). Files land in the app's own
 * external dir:  Android/data/&lt;pkg&gt;/files/srdump/
 *
 * Enable with {@link #FORCE} = true (also forces the CPU-ferry path in
 * ESD4D.probeGpuHandoff, harmless). Written per texture with a params.json and
 * a self-check log so an empty/again-broken dump is obvious.
 */
public final class SrDump {

    private static final String TAG = "SrDump";
    /** Enable the dump (and the CPU-ferry path). */
    public static boolean FORCE = false;

    private static final Map<String, String> META = new LinkedHashMap<>();
    private static String baseParams = "{}";
    private static File dir;

    private SrDump() {
    }

    /** Start a new dump set (clears prior meta). */
    public static void reset(String paramsJson) {
        META.clear();
        baseParams = paramsJson == null ? "{}" : paramsJson;
    }

    /** Replace the base params without clearing the accumulated textures. */
    public static void setBase(String paramsJson) {
        baseParams = paramsJson == null ? "{}" : paramsJson;
    }

    /**
     * Read one texture via a fresh FBO and write it (top-down) to
     * {@code <name>.raw}. {@code comp} = channels per texel, {@code glExt} /
     * {@code glType} = the readback format (e.g. GL_RGBA + GL_HALF_FLOAT for
     * the fp16 grids, GL_RED_INTEGER + GL_UNSIGNED_INT for the packed band).
     */
    public static void dumpTexture(Context ctx, GLTexture tex, String name,
                                   int comp, int glExt, int glType) {
        if (!FORCE || ctx == null || tex == null) return;
        try {
            if (dir == null) dir = new File(ctx.getExternalFilesDir(null), "srdump");
            if (!dir.exists() && !dir.mkdirs()) {
                Log.e(TAG, "cannot create " + dir);
                return;
            }
            int w = tex.mSize.x, h = tex.mSize.y;
            // readback bytes per texel: 1 uint32 for the packed band, else
            // comp * 2 (fp16). The file is written in those same bytes; the
            // replay interprets it with `comp` channels (band = 2 halves).
            int bytesPerTexel = (glType == GLES30.GL_UNSIGNED_INT) ? 4 : comp * 2;
            int rowBytes = w * bytesPerTexel;
            ByteBuffer buf = ByteBuffer.allocate(rowBytes * h).order(ByteOrder.nativeOrder());

            int[] fbo = new int[1];
            GLES30.glGenFramebuffers(1, fbo, 0);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0]);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
                    GLES30.GL_TEXTURE_2D, tex.mTextureID, 0);
            GLES30.glPixelStorei(GLES30.GL_PACK_ALIGNMENT, 1);
            GLES30.glPixelStorei(GLES30.GL_PACK_ROW_LENGTH, 0);
            GLES30.glReadPixels(0, 0, w, h, glExt, glType, buf);
            int err = GLES30.glGetError();
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0);
            GLES30.glDeleteFramebuffers(1, fbo, 0);
            if (err != GLES30.GL_NO_ERROR) {
                Log.e(TAG, name + " readback failed 0x" + Integer.toHexString(err));
                return;
            }
            // glReadPixels is bottom-up: flip rows to top-down.
            byte[] src = buf.array();
            byte[] out = new byte[src.length];
            for (int y = 0; y < h; y++) {
                System.arraycopy(src, (h - 1 - y) * rowBytes, out, y * rowBytes, rowBytes);
            }
            try (FileOutputStream o = new FileOutputStream(new File(dir, name + ".raw"))) {
                o.write(out);
            }
            META.put(name, "{\"w\": " + w + ", \"h\": " + h + ", \"comp\": " + comp
                    + ", \"x0\": 0, \"y0\": 0, \"rw\": " + w + ", \"rh\": " + h + "}");
            Log.i(TAG, name + " dumped " + w + "x" + h + "x" + comp + " -> " + dir);
        } catch (Throwable t) {
            Log.e(TAG, name + " dump failed", t);
        }
    }

    /** Write params.json from the accumulated textures + base params. */
    public static void flush(Context ctx) {
        if (!FORCE || ctx == null) return;
        try {
            if (dir == null) dir = new File(ctx.getExternalFilesDir(null), "srdump");
            StringBuilder b = new StringBuilder(baseParams.trim());
            if (b.length() > 0 && b.charAt(b.length() - 1) == '}') {
                b.setLength(b.length() - 1);
                b.append(',');
            } else {
                b.setLength(0);
                b.append('{');
            }
            b.append('\n');
            for (Map.Entry<String, String> e : META.entrySet()) {
                b.append("  \"").append(e.getKey()).append("\": ").append(e.getValue()).append(",\n");
            }
            b.append("  \"empty\": false\n}\n");
            try (FileOutputStream o = new FileOutputStream(new File(dir, "params.json"))) {
                o.write(b.toString().getBytes());
            }
        } catch (Throwable t) {
            Log.e(TAG, "flush failed", t);
        }
    }
}
