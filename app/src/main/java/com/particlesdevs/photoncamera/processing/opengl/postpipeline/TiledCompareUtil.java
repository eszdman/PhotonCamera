package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;

/**
 * Pixel-exact comparison helpers for the tiled-rendering validation harness
 * (see {@code debugTiledCompare}): tiled output must match full-frame output
 * with {@code maxAbsDiff == 0} on every deterministic node. Pure Java, fully
 * unit-tested; callers extract pixel ints on-device.
 */
public final class TiledCompareUtil {

    private TiledCompareUtil() {}

    /** SHA-256 hex over ARGB pixel ints (native order). */
    public static String sha256Hex(int[] pixels) {
        try {
            ByteBuffer buf = ByteBuffer.allocate(pixels.length * 4)
                    .order(ByteOrder.nativeOrder());
            buf.asIntBuffer().put(pixels);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(buf.array());
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Maximum per-channel absolute difference between equal-length pixel
     * arrays. 0 means bit-exact. Throws on length mismatch.
     */
    public static int maxAbsDiff(int[] a, int[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "length mismatch: " + a.length + " vs " + b.length);
        }
        int max = 0;
        for (int i = 0; i < a.length; i++) {
            int d = a[i] ^ b[i];
            if (d != 0) {
                int da = Math.abs(((a[i] >>> 24) & 0xFF) - ((b[i] >>> 24) & 0xFF));
                int dr = Math.abs(((a[i] >>> 16) & 0xFF) - ((b[i] >>> 16) & 0xFF));
                int dg = Math.abs(((a[i] >>> 8) & 0xFF) - ((b[i] >>> 8) & 0xFF));
                int db = Math.abs((a[i] & 0xFF) - (b[i] & 0xFF));
                int v = Math.max(Math.max(da, dr), Math.max(dg, db));
                if (v > max) {
                    max = v;
                }
            }
        }
        return max;
    }

    /** PSNR in dB over 8-bit channels; infinite when identical. */
    public static double psnr(int[] a, int[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "length mismatch: " + a.length + " vs " + b.length);
        }
        if (a.length == 0) {
            return Double.POSITIVE_INFINITY;
        }
        long se = 0;
        for (int i = 0; i < a.length; i++) {
            int d = a[i] ^ b[i];
            if (d != 0) {
                int da = ((a[i] >>> 24) & 0xFF) - ((b[i] >>> 24) & 0xFF);
                int dr = ((a[i] >>> 16) & 0xFF) - ((b[i] >>> 16) & 0xFF);
                int dg = ((a[i] >>> 8) & 0xFF) - ((b[i] >>> 8) & 0xFF);
                int db = (a[i] & 0xFF) - (b[i] & 0xFF);
                se += (long) da * da + (long) dr * dr + (long) dg * dg + (long) db * db;
            }
        }
        if (se == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double mse = (double) se / ((double) a.length * 4.0);
        return 10.0 * Math.log10(255.0 * 255.0 / mse);
    }

    /**
     * Exact equality for float buffers (GL float readbacks): equal values or
     * both NaN at every position. Length mismatch returns false.
     */
    public static boolean floatEqualsExact(float[] a, float[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            float x = a[i], y = b[i];
            if (x != y && !(Float.isNaN(x) && Float.isNaN(y))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Maximum absolute difference over float buffers (NaN anywhere yields
     * positive infinity). Length mismatch yields infinity.
     */
    public static float floatMaxAbsDiff(float[] a, float[] b) {
        if (a.length != b.length) {
            return Float.POSITIVE_INFINITY;
        }
        float max = 0f;
        for (int i = 0; i < a.length; i++) {
            float x = a[i], y = b[i];
            if (x != y && !(Float.isNaN(x) && Float.isNaN(y))) {
                float d = Math.abs(x - y);
                if (d > max || Float.isNaN(d)) {
                    max = d;
                    if (Float.isNaN(d)) {
                        return Float.POSITIVE_INFINITY;
                    }
                }
            }
        }
        return max;
    }

    /**
     * Maximum absolute float difference over two byte buffers holding native-
     * order RGBA floats, compared over {@code min(remaining)} bytes from each
     * buffer's current position (positions untouched). Compares RGB only (see
     * above); NaN anywhere compared yields positive infinity.
     */
    public static float byteBufferFloatMaxAbsDiff(java.nio.ByteBuffer a, java.nio.ByteBuffer b) {
        java.nio.FloatBuffer fa = a.duplicate().order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        java.nio.FloatBuffer fb = b.duplicate().order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer();
        int n = Math.min(fa.remaining(), fb.remaining());
        float max = 0f;
        int baseA = fa.position();
        int baseB = fb.position();
        for (int i = 0; i < n; i++) {
            // RGB only: fresh tile textures carry alpha 0 while recycled
            // mains carry stale 1.0, and no post shader reads main-chain
            // alpha (audited) — alpha differences are allocation noise.
            if ((i & 3) == 3) {
                continue;
            }
            float x = fa.get(baseA + i), y = fb.get(baseB + i);
            if (x != y && !(Float.isNaN(x) && Float.isNaN(y))) {
                float d = Math.abs(x - y);
                if (d > max || Float.isNaN(d)) {
                    max = d;
                    if (Float.isNaN(d)) {
                        return Float.POSITIVE_INFINITY;
                    }
                }
            }
        }
        return max;
    }

    /**
     * Grayscale heatmap (opaque where different, black where equal), same
     * dimensions as the inputs, for saving next to a failing compare.
     */
    public static int[] diffHeatmap(int[] a, int[] b, int w, int h) {
        if (a.length != w * h || b.length != w * h) {
            throw new IllegalArgumentException("pixels don't match " + w + "x" + h);
        }
        int[] out = new int[w * h];
        for (int i = 0; i < out.length; i++) {
            int d = a[i] ^ b[i];
            if (d == 0) {
                out[i] = 0xFF000000;
                continue;
            }
            int da = Math.abs(((a[i] >>> 24) & 0xFF) - ((b[i] >>> 24) & 0xFF));
            int dr = Math.abs(((a[i] >>> 16) & 0xFF) - ((b[i] >>> 16) & 0xFF));
            int dg = Math.abs(((a[i] >>> 8) & 0xFF) - ((b[i] >>> 8) & 0xFF));
            int db = Math.abs((a[i] & 0xFF) - (b[i] & 0xFF));
            int v = Math.min(255, (Math.max(Math.max(da, dr), Math.max(dg, db))) * 16);
            out[i] = 0xFF000000 | (v << 16) | (v << 8) | v;
        }
        return out;
    }
}
