package com.particlesdevs.photoncamera.processing.encoder;

import org.junit.Test;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link Mp4ColorRange}. Framework-free: builds a minimal
 * moov/hvcC/SPS structure in memory and verifies the metadata rewrite.
 */
public class Mp4ColorRangeTest {

    @Test
    public void fullRangeFlagFlippedAndColrInserted() throws Exception {
        byte[] original = buildMp4(false, false, false);
        File file = writeTemp(original);
        try {
            Mp4ColorRange.Result result = Mp4ColorRange.applyFullRange(file, false);
            assertTrue(result.modified);
            assertTrue(result.vuiPatched);
            assertTrue(result.colrInserted);

            byte[] patched = Files.readAllBytes(file.toPath());
            assertTrue(contains(patched, buildSps(true, false)));
            assertFalse(contains(patched, buildSps(false, false)));
            assertTrue(contains(patched, IsoBmff.buildBox("colr", colrPayload(9, 18, 9, 0x80))));
            // colr box is 8 + 11 bytes appended to a moov that sits last.
            assertTrue(patched.length == original.length + 19);
        } finally {
            file.delete();
        }
    }

    @Test
    public void colrReplacedWhenPresent() throws Exception {
        byte[] original = buildMp4(false, true, true);
        File file = writeTemp(original);
        try {
            Mp4ColorRange.Result result = Mp4ColorRange.applyFullRange(file, true);
            assertTrue(result.modified);
            assertTrue(result.vuiPatched);
            assertTrue(result.colrPatched);
            assertFalse(result.colrInserted);

            byte[] patched = Files.readAllBytes(file.toPath());
            assertTrue(contains(patched, buildSps(true, true)));
            assertTrue(contains(patched, IsoBmff.buildBox("colr", colrPayload(9, 16, 9, 0x80))));
            assertFalse(contains(patched, IsoBmff.buildBox("colr", colrPayload(1, 1, 1, 0x00))));
            assertTrue(patched.length == original.length);
        } finally {
            file.delete();
        }
    }

    @Test
    public void malformedFileUntouched() throws Exception {
        byte[] junk = "this is not an mp4 file".getBytes("US-ASCII");
        File file = writeTemp(junk);
        try {
            Mp4ColorRange.Result result = Mp4ColorRange.applyFullRange(file, false);
            assertFalse(result.modified);
            assertArrayEquals(junk, Files.readAllBytes(file.toPath()));
        } finally {
            file.delete();
        }
    }

    private static File writeTemp(byte[] bytes) throws Exception {
        File file = File.createTempFile("colorrange", ".mp4");
        Files.write(file.toPath(), bytes);
        return file;
    }

    private static final class BitWriter {
        private final List<Integer> bits = new ArrayList<>();

        void write(int value, int count) {
            for (int i = count - 1; i >= 0; i--) {
                bits.add((value >> i) & 1);
            }
        }

        byte[] toBytes(int padBit) {
            while (bits.size() % 8 != 0) {
                bits.add(padBit);
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

    /**
     * SPS NAL whose RBSP ends in a VUI-like color signature:
     * video_full_range_flag, colour_description_present, BT.2020 primaries,
     * HLG/PQ transfer and BT.2020 matrix.
     */
    private static byte[] buildSps(boolean full, boolean pq) {
        BitWriter writer = new BitWriter();
        writer.write(0xFF, 8);
        writer.write(0xFF, 8);
        writer.write(0xFF, 8);
        writer.write(full ? 1 : 0, 1);
        writer.write(1, 1);
        writer.write(9, 8);
        writer.write(pq ? 16 : 18, 8);
        writer.write(9, 8);
        writer.write(0xFF, 24);
        byte[] rbsp = writer.toBytes(1);
        byte[] nal = new byte[2 + rbsp.length];
        nal[0] = 0x42;
        nal[1] = 0x01;
        System.arraycopy(rbsp, 0, nal, 2, rbsp.length);
        return nal;
    }

    private static byte[] buildHvcC(byte[] spsNal) {
        ByteBuffer bb = ByteBuffer.allocate(23 + 3 + 2 + spsNal.length).order(ByteOrder.BIG_ENDIAN);
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

    private static byte[] colrPayload(int primaries, int transfer, int matrix, int rangeByte) {
        ByteBuffer bb = ByteBuffer.allocate(11).order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) 'n');
        bb.put((byte) 'c');
        bb.put((byte) 'l');
        bb.put((byte) 'x');
        bb.putShort((short) primaries);
        bb.putShort((short) transfer);
        bb.putShort((short) matrix);
        bb.put((byte) rangeByte);
        return bb.array();
    }

    private static byte[] buildMp4(boolean fullRangeInVui, boolean pq, boolean withColr) {
        byte[] hvcC = buildHvcC(buildSps(fullRangeInVui, pq));
        List<byte[]> children = new ArrayList<>();
        children.add(IsoBmff.buildBox("hvcC", hvcC));
        if (withColr) {
            children.add(IsoBmff.buildBox("colr", colrPayload(1, 1, 1, 0x00)));
        }
        byte[] entryPayload = concat(new byte[78], concatAll(children));
        byte[] entry = IsoBmff.buildBox("hvc1", entryPayload);
        byte[] stsd = IsoBmff.buildBox("stsd", concat(new byte[4], u32(1), entry));
        byte[] stbl = IsoBmff.buildBox("stbl", stsd);
        byte[] minf = IsoBmff.buildBox("minf", stbl);
        byte[] mdia = IsoBmff.buildBox("mdia", minf);
        byte[] trak = IsoBmff.buildBox("trak", mdia);
        byte[] moov = IsoBmff.buildBox("moov", trak);
        byte[] ftyp = IsoBmff.buildBox("ftyp", new byte[]{'i', 's', 'o', 'm', 0, 0, 0, 1});
        byte[] mdat = IsoBmff.buildBox("mdat", new byte[64]);
        return concatAll(Arrays.asList(ftyp, mdat, moov));
    }

    private static byte[] u32(int value) {
        return new byte[]{
                (byte) (value >>> 24), (byte) (value >>> 16),
                (byte) (value >>> 8), (byte) value};
    }

    private static byte[] concat(byte[]... parts) {
        return concatAll(Arrays.asList(parts));
    }

    private static byte[] concatAll(List<byte[]> parts) {
        int size = 0;
        for (byte[] part : parts) {
            size += part.length;
        }
        byte[] out = new byte[size];
        int pos = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, pos, part.length);
            pos += part.length;
        }
        return out;
    }

    private static boolean contains(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }
}
