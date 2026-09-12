package com.particlesdevs.photoncamera.processing.encoder;

import android.graphics.Bitmap;

import androidx.exifinterface.media.ExifInterface;

import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * Bridges {@link ParseExif.ExifData} to HEIF.
 *
 * <p>{@code ExifInterface.saveAttributes()} only writes JPEG/PNG/WebP, so for
 * HEIC the EXIF payload must be supplied separately via
 * {@code HeifWriter.addExifData()} (or the manual HEIF mux). This helper
 * reuses the existing JPEG EXIF path: compress a tiny proxy JPEG, stamp it
 * with {@link ParseExif#setAllAttributes}, then slice the EXIF APP1 payload
 * back out.
 */
public final class ExifBlob {

    private static final byte[] EXIF_HEADER = {'E', 'x', 'i', 'f', 0, 0};

    private static volatile byte[] sProxyJpeg;

    private ExifBlob() {}

    /**
     * The 8x8 gray proxy JPEG is constant content: compress once per process
     * instead of paying a Bitmap alloc + codec init on every HEIC shot.
     */
    private static byte[] proxyJpeg() {
        byte[] cached = sProxyJpeg;
        if (cached == null) {
            synchronized (ExifBlob.class) {
                cached = sProxyJpeg;
                if (cached == null) {
                    Bitmap proxy = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888);
                    proxy.eraseColor(0xFF808080);
                    ByteArrayOutputStream os = new ByteArrayOutputStream();
                    // noinspection deprecation — Bitmap.compress path is fine for a proxy.
                    proxy.compress(Bitmap.CompressFormat.JPEG, 90, os);
                    proxy.recycle();
                    cached = os.toByteArray();
                    sProxyJpeg = cached;
                }
            }
        }
        return cached;
    }

    /**
     * @return raw EXIF payload ({@code Exif\0\0 + TIFF}) for
     * {@code HeifWriter.addExifData} (which requires the header), or null.
     */
    public static byte[] fromExifData(ParseExif.ExifData exif) {
        if (exif == null) {
            return null;
        }
        try {
            byte[] jpeg = proxyJpeg();
            File tmp = File.createTempFile("heic_exif_", ".jpg");
            try {
                Files.write(tmp.toPath(), jpeg);
                ExifInterface inter = ParseExif.setAllAttributes(tmp, exif);
                if (inter != null) {
                    inter.saveAttributes();
                }
                byte[] stamped = Files.readAllBytes(tmp.toPath());
                byte[] payload = extractExifPayload(stamped);
                if (payload == null) {
                    Log.e("ExifBlob", "no EXIF APP1 found after stamping");
                }
                return payload;
            } finally {
                // noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Exception e) {
            Log.e("ExifBlob", "fromExifData failed, no EXIF will be written", e);
            return null;
        }
    }

    /**
     * @return true when a non-empty TIFF payload can be sliced (cheap
     * pre-flight for callers that want to log the outcome).
     */
    public static boolean hasTiffPayload(byte[] exifPayload) {
        return tiffPayload(exifPayload) != null;
    }

    /**
     * Strips the 6-byte {@code Exif\0\0} header, returning the raw TIFF
     * payload. Kept for validation/testing; the merge path uses
     * {@link #heifExifItemBody} (header-preserving OEM convention).
     *
     * @return TIFF bytes or null when the input is not an EXIF payload.
     */
    public static byte[] tiffPayload(byte[] exifPayload) {
        if (exifPayload == null || exifPayload.length <= EXIF_HEADER.length + 8) {
            return null;
        }
        for (int i = 0; i < EXIF_HEADER.length; i++) {
            if (exifPayload[i] != EXIF_HEADER[i]) {
                return null;
            }
        }
        return Arrays.copyOfRange(exifPayload, EXIF_HEADER.length, exifPayload.length);
    }

    /**
     * Builds a HEIF {@code Exif} item body matching the de-facto OEM
     * convention (verified against a working SDR-HEIC inventory dump):
     * {@code u32 offset + "Exif\0\0" + TIFF} with offset == 6. Readers key
     * off this form; a bare TIFF with offset 0 is invisible to them.
     *
     * @return item body or null when the input is not an EXIF payload.
     */
    public static byte[] heifExifItemBody(byte[] exifPayload) {
        if (exifPayload == null || exifPayload.length <= EXIF_HEADER.length + 8) {
            return null;
        }
        for (int i = 0; i < EXIF_HEADER.length; i++) {
            if (exifPayload[i] != EXIF_HEADER[i]) {
                return null;
            }
        }
        java.nio.ByteBuffer eb = java.nio.ByteBuffer
                .allocate(4 + exifPayload.length).order(java.nio.ByteOrder.BIG_ENDIAN);
        eb.putInt(EXIF_HEADER.length);
        eb.put(exifPayload);
        return eb.array();
    }

    /**
     * Slices the first APP1 {@code Exif\0\0} payload out of a JPEG.
     *
     * @return payload bytes or null if none found.
     */
    public static byte[] extractExifPayload(byte[] jpeg) {
        if (jpeg == null || jpeg.length < 8) {
            return null;
        }
        int pos = 2; // skip SOI
        while (pos + 4 <= jpeg.length) {
            if ((jpeg[pos] & 0xFF) != 0xFF) {
                break;
            }
            int marker = jpeg[pos + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0xD9) {
                pos += 2;
                continue;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                pos += 2;
                continue;
            }
            int len = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
            if (len < 2 || pos + 2 + len > jpeg.length) {
                break;
            }
            if (marker == 0xE1 && len > EXIF_HEADER.length + 2
                    && startsWith(jpeg, pos + 4, EXIF_HEADER)) {
                int start = pos + 4;
                return Arrays.copyOfRange(jpeg, start, pos + 2 + len);
            }
            pos += 2 + len;
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int offset, byte[] prefix) {
        if (offset < 0 || offset + prefix.length > data.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[offset + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
