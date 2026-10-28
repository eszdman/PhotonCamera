package com.particlesdevs.photoncamera.processing.encoder;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Embeds a Display P3 ICC profile in a JPEG that was encoded from P3 pixels.
 *
 * <p>JPEG has no standard color-space field comparable to HEIF's {@code colr},
 * so the profile must travel as an APP2 {@code ICC_PROFILE} segment. Android's
 * {@code Bitmap.compress} does not reliably attach the bitmap's color space on
 * every platform version, therefore the profile is written explicitly and any
 * existing ICC segment is replaced.
 *
 * <p>The profile is the CC0 Display P3 compatibility profile from
 * <a href="https://github.com/saucecontrol/Compact-ICC-Profiles">Compact ICC
 * Profiles</a> (480 bytes, exact sRGB parametric TRC). Its red-XYZ Z value is
 * nudged to zero for compatibility with strict ICC readers.
 */
public final class JpegColorProfile {

    private static final String TAG = "JpegColorProfile";
    private static final byte[] ICC_SIGNATURE =
            {'I', 'C', 'C', '_', 'P', 'R', 'O', 'F', 'I', 'L', 'E', 0};
    private static volatile byte[] sDisplayP3Profile;

    private JpegColorProfile() {}

    /** The embedded Display P3 ICC profile bytes. */
    public static byte[] displayP3Profile() {
        byte[] cached = sDisplayP3Profile;
        if (cached == null) {
            synchronized (JpegColorProfile.class) {
                cached = sDisplayP3Profile;
                if (cached == null) {
                    try (InputStream in = PhotonCamera.getResourcesStatic()
                            .openRawResource(R.raw.display_p3)) {
                        cached = readFully(in);
                    } catch (Exception e) {
                        Log.e(TAG, "Unable to load Display P3 ICC profile", e);
                        cached = new byte[0];
                    }
                    sDisplayP3Profile = cached;
                }
            }
        }
        return cached;
    }

    /** Adds/replaces the Display P3 ICC segment in a baseline JPEG. */
    public static byte[] ensureDisplayP3(byte[] jpeg) {
        byte[] profile = displayP3Profile();
        if (profile.length == 0) {
            return jpeg;
        }
        return ensureIcc(jpeg, profile);
    }

    /** File variant used after EXIF stamping. */
    public static void ensureDisplayP3(Path file) throws IOException {
        byte[] jpeg = Files.readAllBytes(file);
        byte[] tagged = ensureDisplayP3(jpeg);
        if (tagged != jpeg) {
            Files.write(file, tagged);
        }
    }

    /**
     * Rebuilds the JPEG header, dropping every existing ICC APP2 segment and
     * inserting {@code icc} after the leading APP0/JFIF segment.
     *
     * <p>Everything from SOS (or a malformed/unknown marker) onward is copied
     * verbatim, so EXIF, XMP, MPF and entropy data are preserved.
     */
    static byte[] ensureIcc(byte[] jpeg, byte[] icc) {
        if (jpeg == null || jpeg.length < 4
                || jpeg[0] != (byte) 0xFF || jpeg[1] != (byte) 0xD8) {
            return jpeg;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(jpeg.length + icc.length + 24);
        out.write(0xFF);
        out.write(0xD8);
        boolean inserted = false;
        int pos = 2;
        while (pos + 3 < jpeg.length) {
            if ((jpeg[pos] & 0xFF) != 0xFF) {
                break;
            }
            int markerStart = pos;
            while (pos + 1 < jpeg.length && (jpeg[pos + 1] & 0xFF) == 0xFF) {
                pos++; // fill bytes
            }
            if (pos + 3 >= jpeg.length) {
                break;
            }
            int marker = jpeg[pos + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0x01
                    || (marker >= 0xD0 && marker <= 0xD7)) {
                out.write(jpeg, markerStart, pos + 2 - markerStart);
                pos += 2;
                continue;
            }
            if (marker == 0xDA || marker == 0xD9) {
                break;
            }
            int len = ((jpeg[pos + 2] & 0xFF) << 8) | (jpeg[pos + 3] & 0xFF);
            if (len < 2 || pos + 2 + len > jpeg.length) {
                break;
            }
            int segmentEnd = pos + 2 + len;
            boolean iccSegment = marker == 0xE2 && hasIccSignature(jpeg, pos + 4, segmentEnd);
            if (!inserted && marker != 0xE0) {
                writeIccSegment(out, icc);
                inserted = true;
            }
            if (!iccSegment) {
                out.write(jpeg, pos, segmentEnd - pos);
            }
            pos = segmentEnd;
        }
        if (!inserted) {
            writeIccSegment(out, icc);
        }
        if (pos < jpeg.length) {
            out.write(jpeg, pos, jpeg.length - pos);
        }
        return out.toByteArray();
    }

    private static boolean hasIccSignature(byte[] data, int start, int end) {
        if (end - start < ICC_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < ICC_SIGNATURE.length; i++) {
            if (data[start + i] != ICC_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private static void writeIccSegment(ByteArrayOutputStream out, byte[] icc) {
        int payloadLen = ICC_SIGNATURE.length + 2 + icc.length;
        int segmentLen = payloadLen + 2; // length field itself
        out.write(0xFF);
        out.write(0xE2);
        out.write((segmentLen >> 8) & 0xFF);
        out.write(segmentLen & 0xFF);
        out.write(ICC_SIGNATURE, 0, ICC_SIGNATURE.length);
        out.write(1); // sequence number
        out.write(1); // total segments
        out.write(icc, 0, icc.length);
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
