package com.particlesdevs.photoncamera.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Reader for Adobe .cube 3D LUT files that converts the table into the tiled
 * square CLUT layout sampled by postlookup() in initial.glsl.
 * <p>
 * The produced image (see {@link #toClutPixels(int)}) is a
 * edge*sqrt(edge) sized square split into sqrt(edge) x sqrt(edge) tiles of
 * edge x edge pixels. The blue slice k occupies tile (k % tiles, k / tiles),
 * red indexes x inside the tile and green indexes y, matching the shader's
 * quad/texPos math exactly.
 * <p>
 * Pure Java (no Android dependencies) so the conversion can be tested on the JVM.
 */
public final class CubeLut {
    /** Largest accepted LUT_3D_SIZE; 128^3 float triplets already need ~25 MB. */
    public static final int MAX_SIZE = 128;

    /** Cube edges the postlut GL path uses (64 -> 512px, 100 -> 1000px, 144 -> 1728px). */
    public static final int[] SUPPORTED_EDGES = {64, 100, 144};

    public final String title;
    public final int size;
    /** size^3 * 3 floats, entry index (r + g*size + b*size*size), red varying fastest. */
    public final float[] rgb;
    /** Per-channel input domain, default [0,1]. */
    private final float[] domainMin = {0, 0, 0};
    private final float[] domainMax = {1, 1, 1};

    public static class FormatException extends Exception {
        FormatException(String message) {
            super(message);
        }
    }

    private CubeLut(String title, int size, float[] rgb) {
        this.title = title;
        this.size = size;
        this.rgb = rgb;
    }

    /**
     * Parse a .cube stream. Only the 3D table format is supported;
     * LUT_1D_SIZE files are rejected with a descriptive error.
     */
    public static CubeLut parse(InputStream in) throws IOException, FormatException {
        String title = null;
        int size = 0;
        float[] table = null;
        int count = 0;
        float[] domMin = {0, 0, 0};
        float[] domMax = {1, 1, 1};
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int hash = line.indexOf('#');
                if (hash >= 0) line = line.substring(0, hash);
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                if (Character.isLetter(trimmed.charAt(0))) {
                    String keyword = trimmed.toUpperCase(Locale.ROOT);
                    if (keyword.startsWith("LUT_1D_SIZE")) {
                        throw new FormatException("1D cube LUTs are not supported");
                    }
                    if (keyword.startsWith("LUT_3D_SIZE")) {
                        if (size != 0) throw new FormatException("Duplicate LUT_3D_SIZE");
                        size = parseIntArg(trimmed);
                        if (size < 2) throw new FormatException("LUT_3D_SIZE must be at least 2, got " + size);
                        if (size > MAX_SIZE) {
                            throw new FormatException("LUT_3D_SIZE " + size + " too large (max " + MAX_SIZE + ")");
                        }
                        table = new float[size * size * size * 3];
                        continue;
                    }
                    if (keyword.startsWith("TITLE")) {
                        if (size == 0) title = stringArg(trimmed);
                        continue;
                    }
                    if (keyword.startsWith("DOMAIN_MIN") || keyword.startsWith("DOMAIN_MAX")) {
                        float[] d = parseFloatArgs(trimmed);
                        float[] dst = keyword.startsWith("DOMAIN_MIN") ? domMin : domMax;
                        System.arraycopy(d, 0, dst, 0, 3);
                        continue;
                    }
                    // Unknown keyword: skip (the entry-count check below still catches real damage).
                    continue;
                }

                if (size == 0) throw new FormatException("Data found before LUT_3D_SIZE");
                count = parseFloats(trimmed, table, count);
            }
        }
        if (size == 0) throw new FormatException("Missing LUT_3D_SIZE");
        if (table == null || count != table.length) {
            throw new FormatException("Expected " + (size * size * size) + " RGB entries, found " + count / 3);
        }
        for (float v : table) {
            if (!Float.isFinite(v)) throw new FormatException("Non-finite LUT value");
        }
        for (int c = 0; c < 3; c++) {
            if (!(domMin[c] < domMax[c])) {
                throw new FormatException("DOMAIN_MIN must be below DOMAIN_MAX");
            }
        }
        CubeLut lut = new CubeLut(title, size, table);
        lut.setDomain(domMin, domMax);
        return lut;
    }

    /** Smallest supported pipeline cube edge that fits the parsed size. */
    public static int targetEdgeFor(int size) {
        for (int edge : SUPPORTED_EDGES) {
            if (size <= edge) return edge;
        }
        return SUPPORTED_EDGES[SUPPORTED_EDGES.length - 1];
    }

    /** Side length of the square CLUT image for a given cube edge. */
    public static int imageSideForEdge(int edge) {
        int tiles = (int) Math.round(Math.sqrt(edge));
        if (tiles * tiles != edge) {
            throw new IllegalArgumentException("Cube edge must be a perfect square: " + edge);
        }
        return tiles * edge;
    }

    /**
     * Convert to tiled square CLUT pixels (ARGB_8888-packed ints, row-major,
     * row 0 first). Values outside [0,1] are clamped - the postlut texture is 8-bit.
     */
    public int[] toClutPixels(int targetEdge) {
        int side = imageSideForEdge(targetEdge);
        int tiles = side / targetEdge;
        int[] pixels = new int[side * side];
        float invDst = 1f / (targetEdge - 1);
        for (int b = 0; b < targetEdge; b++) {
            int tileX = b % tiles;
            int tileY = b / tiles;
            float bz = domainPos(2, b * invDst);
            for (int g = 0; g < targetEdge; g++) {
                float gy = domainPos(1, g * invDst);
                int rowOff = (tileY * targetEdge + g) * side + tileX * targetEdge;
                for (int r = 0; r < targetEdge; r++) {
                    float rx = domainPos(0, r * invDst);
                    pixels[rowOff + r] = 0xFF000000 | sampleRgb(rx, gy, bz);
                }
            }
        }
        return pixels;
    }

    void setDomain(float[] min, float[] max) {
        System.arraycopy(min, 0, domainMin, 0, 3);
        System.arraycopy(max, 0, domainMax, 0, 3);
    }

    /**
     * Map a normalized output-grid position through the LUT input domain:
     * the shader feeds 0..1 colors, entry t receives the cube sample at
     * domainMin + t*(domainMax-domainMin).
     */
    private float domainPos(int channel, float t) {
        float pos = domainMin[channel] + t * (domainMax[channel] - domainMin[channel]);
        return clamp01(pos) * (size - 1);
    }

    /** Trilinear sample of the source table at grid coordinates, quantized to 0xRRGGBB. */
    private int sampleRgb(float x, float y, float z) {
        int n = size;
        int x0 = (int) Math.min(x, n - 2);
        int y0 = (int) Math.min(y, n - 2);
        int z0 = (int) Math.min(z, n - 2);
        float fx = clamp01(x - x0);
        float fy = clamp01(y - y0);
        float fz = clamp01(z - z0);
        int base = ((z0 * n + y0) * n + x0) * 3;
        int sx = 3, sy = 3 * n, sz = 3 * n * n;
        int c = 0xFF000000;
        for (int ch = 0; ch < 3; ch++) {
            float c00 = rgb[base + ch] + (rgb[base + sx + ch] - rgb[base + ch]) * fx;
            float c01 = rgb[base + sy + ch] + (rgb[base + sy + sx + ch] - rgb[base + sy + ch]) * fx;
            float c10 = rgb[base + sz + ch] + (rgb[base + sz + sx + ch] - rgb[base + sz + ch]) * fx;
            float c11 = rgb[base + sz + sy + ch] + (rgb[base + sz + sy + sx + ch] - rgb[base + sz + sy + ch]) * fx;
            float c0 = c00 + (c01 - c00) * fy;
            float c1 = c10 + (c11 - c10) * fy;
            float v = c0 + (c1 - c0) * fz;
            int q = Math.round(clamp01(v) * 255f);
            c |= q << (16 - 8 * ch);
        }
        return c;
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static int parseIntArg(String line) throws FormatException {
        try {
            return Integer.parseInt(line.substring(line.indexOf(' ') + 1).trim());
        } catch (NumberFormatException e) {
            throw new FormatException("Malformed integer in: " + line);
        }
    }

    private static String stringArg(String line) {
        String arg = line.substring(line.indexOf(' ') + 1).trim();
        if (arg.length() >= 2 && arg.startsWith("\"") && arg.endsWith("\"")) {
            arg = arg.substring(1, arg.length() - 1);
        }
        return arg;
    }

    /** Parse the three floats after a DOMAIN_* keyword. */
    private static float[] parseFloatArgs(String line) throws FormatException {
        String[] args = line.substring(line.indexOf(' ') + 1).trim().split("\\s+");
        if (args.length < 3) {
            throw new FormatException("DOMAIN line needs 3 values: " + line);
        }
        try {
            return new float[]{Float.parseFloat(args[0]), Float.parseFloat(args[1]), Float.parseFloat(args[2])};
        } catch (NumberFormatException e) {
            throw new FormatException("Malformed DOMAIN line: " + line);
        }
    }

    /** Append whitespace-separated floats of one line into the table; returns the new count. */
    private static int parseFloats(String line, float[] out, int pos) throws FormatException {
        int len = line.length();
        int i = 0;
        try {
            while (i < len) {
                while (i < len && Character.isWhitespace(line.charAt(i))) i++;
                if (i >= len) break;
                int start = i;
                while (i < len && !Character.isWhitespace(line.charAt(i))) i++;
                float f = Float.parseFloat(line.substring(start, i));
                if (pos < out.length) out[pos] = f;
                pos++;
            }
        } catch (NumberFormatException e) {
            throw new FormatException("Malformed value in: " + line);
        }
        if (pos > out.length) {
            throw new FormatException("Too many table values (expected " + out.length / 3 + " entries)");
        }
        return pos;
    }
}
