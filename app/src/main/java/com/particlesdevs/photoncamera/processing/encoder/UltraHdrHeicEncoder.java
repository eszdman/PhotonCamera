package com.particlesdevs.photoncamera.processing.encoder;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.os.Build;

import androidx.heifwriter.HeifWriter;

import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.processing.ultrahdr.GainMapComputer;
import com.particlesdevs.photoncamera.util.Log;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ultra HDR HEIC encode (API 34+ only).
 *
 * <p>Takes the same {@link GainMapComputer.Result} as the JPEG path, HEVC
 * encodes the SDR base and the gain map via {@code HeifWriter} into temp
 * files, then muxes them with {@link UltraHdrHeicContainer} (manual BMFF,
 * no native dependency). Any failure throws so callers fall back to SDR
 * HEIC — a mux bug can never lose a shot.
 */
public final class UltraHdrHeicEncoder {

    private static final String TAG = "UltraHdrHeicEncoder";

    private UltraHdrHeicEncoder() {}

    /**
     * Encodes and writes {@code dest} (must end in {@code .heic}).
     * Both bitmaps are recycled <b>only on success</b> — on failure they are
     * left alive so {@link StillEncoder} fallbacks can still use them.
     */
    public static void encodeToFile(Path dest, Bitmap sdr,
            GainMapComputer.Result gain, ParseExif.ExifData exif) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            throw new UnsupportedOperationException("HEIC Ultra HDR needs API 34+");
        }
        if (!HeicSupport.isUltraHdrHeicSupported()) {
            throw new UnsupportedOperationException("HEIC Ultra HDR not supported on this device");
        }
        if (sdr == null || sdr.isRecycled() || gain == null || gain.gainMap == null) {
            throw new IllegalArgumentException("Null/recycled SDR or gain map");
        }
        if (exif != null) {
            exif.IMAGE_WIDTH = String.valueOf(sdr.getWidth());
            exif.IMAGE_LENGTH = String.valueOf(sdr.getHeight());
        }
        File baseTmp = File.createTempFile("uhdr_heic_base_", ".heic");
        File gainTmp = File.createTempFile("uhdr_heic_gain_", ".heic");
        boolean success = false;
        try {
            writeSingleHeic(baseTmp, sdr);
            writeSingleHeic(gainTmp, gain.gainMap);
            byte[] baseBytes = Files.readAllBytes(baseTmp.toPath());
            byte[] gainBytes = Files.readAllBytes(gainTmp.toPath());
            UltraHdrHeicContainer.Inputs in = new UltraHdrHeicContainer.Inputs();
            in.baseHeic = baseBytes;
            in.gainHeic = gainBytes;
            in.baseW = sdr.getWidth();
            in.baseH = sdr.getHeight();
            in.gainW = gain.gainW;
            in.gainH = gain.gainH;
            in.gainMapMin = gain.gainMapMin;
            in.gainMapMax = gain.gainMapMax;
            in.hdrCapacityMax = gain.hdrCapacityMax;
            in.exifPayload = ExifBlob.fromExifData(exif);
            if (in.exifPayload != null) {
                Log.d(TAG, "HEIC EXIF blob: " + in.exifPayload.length + " bytes"
                        + (ExifBlob.hasTiffPayload(in.exifPayload) ? " (TIFF ok)" : " (TIFF BAD)"));
            } else {
                Log.e(TAG, "EXIF blob null, HEIC will carry no EXIF");
            }
            byte[] merged = UltraHdrHeicContainer.merge(in);
            try {
                Files.write(dest, merged);
                verifyMergedHeic(dest, sdr.getWidth(), sdr.getHeight());
            } catch (Exception verifyFailed) {
                try {
                    Files.deleteIfExists(dest);
                } catch (Exception ignored) {
                }
                throw verifyFailed;
            }
            success = true;
        } finally {
            // noinspection ResultOfMethodCallIgnored
            baseTmp.delete();
            // noinspection ResultOfMethodCallIgnored
            gainTmp.delete();
            if (success) {
                try {
                    sdr.recycle();
                } catch (Exception ignored) {
                }
                try {
                    gain.gainMap.recycle();
                } catch (Exception ignored) {
                }
            }
        }
        Log.d(TAG, "HEIC Ultra HDR written: " + dest);
    }

    /**
     * Control flow to abort the full decode once the header is captured.
     * decodeBitmap always decodes the whole image (~244 MB bitmap plus
     * decoder working set at 64 MP); the merged file only needs its
     * dimensions asserted. Not an error: caught below and treated as
     * success when dims were captured. No stack trace (thrown per shot).
     */
    private static final class HeaderDecoded extends RuntimeException {
        HeaderDecoded() {
            super(null, null, false, false);
        }
    }

    /**
     * Permanent safety net for the manual mux: header-decodes the merged file
     * (no full decode) and asserts the base dimensions. Any mismatch means
     * our boxes misdescribe the payloads — discard and let the caller fall
     * back to SDR instead of shipping a corrupt file.
     */
    static void verifyMergedHeic(Path dest, int expectedW, int expectedH) throws Exception {
        final int[] size = new int[2];
        final boolean[] seen = new boolean[1];
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(dest.toFile());
            ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                size[0] = info.getSize().getWidth();
                size[1] = info.getSize().getHeight();
                seen[0] = true;
                throw new HeaderDecoded();
            });
        } catch (HeaderDecoded abort) {
            // Expected path: dims captured, full decode skipped.
        } catch (Exception e) {
            throw new IllegalStateException("merged HEIC undecodable: " + e.getMessage(), e);
        }
        if (!seen[0]) {
            throw new IllegalStateException("merged HEIC header not decoded");
        }
        if (size[0] != expectedW || size[1] != expectedH) {
            throw new IllegalStateException("merged HEIC dimensions " + size[0] + "x" + size[1]
                    + " != expected " + expectedW + "x" + expectedH);
        }
    }

    private static void writeSingleHeic(File dest, Bitmap bitmap) throws Exception {
        HeifWriter writer = null;
        try {
            writer = new HeifWriter.Builder(
                    dest.getAbsolutePath(),
                    bitmap.getWidth(), bitmap.getHeight(),
                    HeifWriter.INPUT_MODE_BITMAP)
                    .setQuality(SdrHeicEncoder.HEIC_QUALITY)
                    .build();
            writer.start();
            writer.addBitmap(bitmap);
            writer.stop(0);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
