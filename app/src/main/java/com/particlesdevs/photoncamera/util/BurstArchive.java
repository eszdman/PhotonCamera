package com.particlesdevs.photoncamera.util;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Streams one DNG at a time, without keeping the entire burst archive in memory. */
public final class BurstArchive {
    private BurstArchive() {}

    public interface FrameWriter {
        void write(int index, OutputStream output) throws IOException;
    }

    /** Owns and closes output. Frame writers must leave their entry stream open. */
    public static void write(OutputStream output, String stem, int frameCount,
                             FrameWriter writer) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            if (frameCount <= 0) throw new IOException("Burst has no RAW frames");
            zip.setLevel(Deflater.BEST_SPEED);
            for (int i = 0; i < frameCount; i++) {
                zip.putNextEntry(new ZipEntry(String.format(Locale.ROOT, "%s_f%02d.dng", stem, i)));
                writer.write(i, zip);
                zip.closeEntry();
            }
        }
    }
}
