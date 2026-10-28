package com.particlesdevs.photoncamera.processing.encoder;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HevcSpsColorTest {

    private static final class BitWriter {
        final List<Integer> bits = new ArrayList<>();

        void write(int value, int count) {
            for (int i = count - 1; i >= 0; i--) {
                bits.add((value >> i) & 1);
            }
        }

        byte[] toBytes(int pad) {
            while (bits.size() % 8 != 0) {
                bits.add(pad);
            }
            byte[] out = new byte[bits.size() / 8];
            for (int i = 0; i < bits.size(); i++) {
                if (bits.get(i) != 0) {
                    out[i >> 3] |= (byte) (1 << (7 - (i & 7)));
                }
            }
            return out;
        }
    }

    private static byte[] buildSps(int primaries, int transfer, int matrix, boolean full,
            boolean includeVui) {
        BitWriter w = new BitWriter();
        w.write(0xFF, 8);
        w.write(0xFF, 8);
        w.write(0xFF, 8);
        if (includeVui) {
            w.write(full ? 1 : 0, 1);
            w.write(1, 1);
            w.write(primaries, 8);
            w.write(transfer, 8);
            w.write(matrix, 8);
        }
        w.write(0xFF, 24);
        byte[] rbsp = w.toBytes(1);
        byte[] nal = new byte[2 + rbsp.length];
        nal[0] = 0x42;
        nal[1] = 0x01;
        System.arraycopy(rbsp, 0, nal, 2, rbsp.length);
        return nal;
    }

    private static byte[] buildHvcC(byte[] spsNal) {
        ByteBuffer bb = ByteBuffer.allocate(23 + 3 + 2 + spsNal.length)
                .order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) 1);
        bb.put((byte) 1);
        bb.putInt(0);
        bb.put(new byte[6]);
        bb.put((byte) 0);
        bb.putShort((short) 0xF000);
        bb.put((byte) 0xFC);
        bb.put((byte) 0xFD);
        bb.put((byte) 0xF8);
        bb.put((byte) 0xF8);
        bb.putShort((short) 0);
        bb.put((byte) 0x0F);
        bb.put((byte) 1);
        bb.put((byte) 0x21);
        bb.putShort((short) 1);
        bb.putShort((short) spsNal.length);
        bb.put(spsNal);
        return bb.array();
    }

    private static byte[] hvcCFromSps(byte[] sps) {
        return buildHvcC(sps);
    }

    private static byte[] spsFromHvcC(byte[] hvcC) {
        ByteBuffer bb = ByteBuffer.wrap(hvcC).order(ByteOrder.BIG_ENDIAN);
        bb.position(23 + 3);
        int len = bb.getShort() & 0xFFFF;
        byte[] sps = new byte[len];
        bb.get(sps);
        return sps;
    }

    private static byte[] deescape(byte[] src, int offset) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = offset; i < src.length; i++) {
            if (i >= offset + 2 && src[i - 2] == 0 && src[i - 1] == 0
                    && (src[i] & 0xFF) == 0x03
                    && i + 1 < src.length && (src[i + 1] & 0xFF) <= 0x03) {
                continue;
            }
            out.write(src[i]);
        }
        return out.toByteArray();
    }

    private static int readByte(byte[] rbsp, int bit) {
        int value = 0;
        for (int i = 0; i < 8; i++) {
            value = (value << 1) | ((rbsp[(bit + i) >> 3] >> (7 - ((bit + i) & 7))) & 1);
        }
        return value;
    }

    private static int findColor(byte[] rbsp, int primaries, int transfer, int matrix) {
        int[] sig = new int[25];
        int k = 0;
        sig[k++] = 1;
        for (int i = 7; i >= 0; i--) sig[k++] = (primaries >> i) & 1;
        for (int i = 7; i >= 0; i--) sig[k++] = (transfer >> i) & 1;
        for (int i = 7; i >= 0; i--) sig[k++] = (matrix >> i) & 1;
        int total = rbsp.length * 8;
        int match = -1;
        for (int i = 0; i + sig.length <= total; i++) {
            boolean ok = true;
            for (int j = 0; j < sig.length; j++) {
                int b = (rbsp[(i + j) >> 3] >> (7 - ((i + j) & 7))) & 1;
                if (b != sig[j]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                if (match >= 0) return -1;
                match = i;
            }
        }
        return match;
    }

    @Test
    public void patchesBt709VuiToDisplayP3() {
        byte[] hvcC = hvcCFromSps(buildSps(1, 1, 1, false, true));
        int originalLen = hvcC.length;
        assertTrue(HevcSpsColor.patchHvcCPayload(hvcC));
        assertEquals(originalLen, hvcC.length);

        byte[] rbsp = deescape(spsFromHvcC(hvcC), 2);
        int p3 = findColor(rbsp, 12, 13, 1);
        assertTrue(p3 >= 1);
        // video_full_range_flag is immediately before the present flag.
        assertEquals(1, (rbsp[(p3 - 1) >> 3] >> (7 - ((p3 - 1) & 7))) & 1);
        assertEquals(12, readByte(rbsp, p3 + 1));
        assertEquals(13, readByte(rbsp, p3 + 9));
        assertEquals(1, readByte(rbsp, p3 + 17));
    }

    @Test
    public void alreadyP3IsLeftCorrect() {
        byte[] hvcC = hvcCFromSps(buildSps(12, 13, 1, true, true));
        byte[] before = hvcC.clone();
        assertTrue(HevcSpsColor.patchHvcCPayload(hvcC));
        assertTrue(java.util.Arrays.equals(before, hvcC));
    }

    @Test
    public void noVuiIsLeftUntouched() {
        byte[] hvcC = hvcCFromSps(buildSps(1, 1, 1, false, false));
        byte[] before = hvcC.clone();
        assertFalse(HevcSpsColor.patchHvcCPayload(hvcC));
        assertTrue(java.util.Arrays.equals(before, hvcC));
    }

    @Test
    public void patchesHvcCInsideHeicMeta() {
        byte[] hvcC = hvcCFromSps(buildSps(1, 1, 1, false, true));
        byte[] ipco = IsoBmff.buildBox("ipco", IsoBmff.buildBox("hvcC", hvcC));
        byte[] iprp = IsoBmff.buildBox("iprp", ipco);
        byte[] meta = IsoBmff.buildBox("meta", IsoBmff.concat(listOf(
                IsoBmff.fullBoxPayload(0, 0, null), iprp)));
        byte[] ftyp = IsoBmff.buildBox("ftyp",
                "heic0000heicmif1hevc".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        byte[] mdat = IsoBmff.buildBox("mdat", new byte[]{1, 2, 3});
        byte[] heic = IsoBmff.concat(listOf(ftyp, meta, mdat));
        int originalLen = heic.length;

        assertTrue(HevcSpsColor.patchDisplayP3(heic));
        assertEquals(originalLen, heic.length);
        // Re-parse the hvcC payload and verify it was patched.
        IsoBmff.Box topMeta = IsoBmff.parse(heic).get(1);
        IsoBmff.Box iprpBox = IsoBmff.parse(topMeta.payload, 4,
                topMeta.payload.length - 4).get(0);
        IsoBmff.Box ipcoBox = IsoBmff.parse(iprpBox.payload).get(0);
        IsoBmff.Box hvcCBox = IsoBmff.parse(ipcoBox.payload).get(0);
        byte[] patched = hvcCBox.payload;
        byte[] rbsp = deescape(spsFromHvcC(patched), 2);
        assertTrue(findColor(rbsp, 12, 13, 1) >= 1);
    }

    private static List<byte[]> listOf(byte[]... items) {
        List<byte[]> out = new ArrayList<>();
        for (byte[] item : items) out.add(item);
        return out;
    }
}
