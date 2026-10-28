package com.particlesdevs.photoncamera.processing.encoder;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JpegColorProfileTest {

    private static final byte[] ICC_SIG =
            {'I', 'C', 'C', '_', 'P', 'R', 'O', 'F', 'I', 'L', 'E', 0};

    private static byte[] segment(int marker, byte[] payload) {
        int len = payload.length + 2;
        byte[] out = new byte[4 + payload.length];
        out[0] = (byte) 0xFF;
        out[1] = (byte) marker;
        out[2] = (byte) (len >> 8);
        out[3] = (byte) len;
        System.arraycopy(payload, 0, out, 4, payload.length);
        return out;
    }

    private static byte[] iccSegment(byte[] icc, int seq, int count) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(ICC_SIG, 0, ICC_SIG.length);
        payload.write(seq);
        payload.write(count);
        payload.write(icc, 0, icc.length);
        return segment(0xE2, payload.toByteArray());
    }

    private static byte[] jpeg(boolean withApp0, byte[]... extraSegments) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        if (withApp0) {
            try {
                out.write(segment(0xE0, "JFIF\0".getBytes(StandardCharsets.US_ASCII)));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        for (byte[] segment : extraSegments) {
            out.write(segment, 0, segment.length);
        }
        byte[] sos = segment(0xDA, new byte[0]);
        out.write(sos, 0, sos.length);
        byte[] scan = {1, 2, 3, 4, (byte) 0xFF, 0};
        out.write(scan, 0, scan.length);
        out.write(0xFF);
        out.write(0xD9);
        return out.toByteArray();
    }

    private static int countIcc(byte[] jpeg) {
        int count = 0;
        int pos = 2;
        while (pos + 3 < jpeg.length) {
            if ((jpeg[pos] & 0xFF) != 0xFF) {
                break;
            }
            int marker = jpeg[pos + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0x01
                    || (marker >= 0xD0 && marker <= 0xD7)) {
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
            if (marker == 0xE2 && startsWith(jpeg, pos + 4, pos + 2 + len, ICC_SIG)) {
                count++;
            }
            pos += 2 + len;
        }
        return count;
    }

    private static boolean startsWith(byte[] data, int start, int end, byte[] prefix) {
        if (end - start < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[start + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void insertsIccAfterApp0AndPreservesTheRest() {
        byte[] icc = new byte[64];
        for (int i = 0; i < icc.length; i++) {
            icc[i] = (byte) (i * 3 + 1);
        }
        byte[] app1 = segment(0xE1, "Exif\0\0payload".getBytes(StandardCharsets.US_ASCII));
        byte[] in = jpeg(true, app1);
        byte[] out = JpegColorProfile.ensureIcc(in, icc);
        assertEquals(1, countIcc(out));
        assertTrue(indexOf(out, app1) >= 0);
        // ICC follows APP0 and precedes APP1.
        int app0End = 4 + "JFIF\0".getBytes(StandardCharsets.US_ASCII).length;
        int iccStart = indexOf(out, ICC_SIG);
        assertTrue(iccStart > app0End);
        assertTrue(iccStart < indexOf(out, app1));
        // SOS and entropy data survive.
        byte[] sos = segment(0xDA, new byte[0]);
        assertTrue(indexOf(out, sos) >= 0);
    }

    @Test
    public void replacesExistingIccAndIsIdempotent() {
        byte[] oldIcc = new byte[32];
        byte[] newIcc = new byte[48];
        for (int i = 0; i < newIcc.length; i++) {
            newIcc[i] = (byte) (0x80 + i);
        }
        byte[] in = jpeg(true, iccSegment(oldIcc, 1, 1));
        byte[] once = JpegColorProfile.ensureIcc(in, newIcc);
        assertEquals(1, countIcc(once));
        assertTrue(indexOf(once, newIcc) >= 0);
        assertTrue(indexOf(once, oldIcc) < 0);
        byte[] twice = JpegColorProfile.ensureIcc(once, newIcc);
        assertArrayEquals(once, twice);
    }

    @Test
    public void insertsWithoutApp0() {
        byte[] icc = new byte[16];
        byte[] app1 = segment(0xE1, "Exif\0\0x".getBytes(StandardCharsets.US_ASCII));
        byte[] out = JpegColorProfile.ensureIcc(jpeg(false, app1), icc);
        assertEquals(1, countIcc(out));
        int iccStart = indexOf(out, ICC_SIG);
        assertTrue(iccStart > 2);
        assertTrue(iccStart < indexOf(out, app1));
    }

    @Test
    public void leavesNonJpegUntouched() {
        byte[] data = {1, 2, 3, 4};
        assertArrayEquals(data, JpegColorProfile.ensureIcc(data, new byte[8]));
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
