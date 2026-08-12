package com.particlesdevs.photoncamera.processing.ultrahdr;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class UltraHdrJpegWriterTest {

    private static byte[] segment(int marker, byte[] payload) {
        int len = 2 + payload.length;
        byte[] seg = new byte[2 + len];
        seg[0] = (byte) 0xFF;
        seg[1] = (byte) marker;
        seg[2] = (byte) (len >> 8);
        seg[3] = (byte) len;
        System.arraycopy(payload, 0, seg, 4, payload.length);
        return seg;
    }

    private static byte[] minimalBaseJpeg() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8); // SOI
        out.write(segment(0xE0, new byte[]{'J', 'F', 'I', 'F', 0, 1, 2, 0, 0, 1, 0, 1, 0, 0, 0, 0}));
        out.write(segment(0xE1, new byte[]{'E', 'x', 'i', 'f', 0, 0, 1, 2}));
        out.write(segment(0xDB, new byte[]{0, 1, 2, 3}));  // DQT
        out.write(segment(0xC0, new byte[]{8, 0}));        // SOF0
        out.write(segment(0xC4, new byte[]{0, 0}));        // DHT
        out.write(segment(0xDA, new byte[]{0, 0}));        // SOS
        out.write(new byte[]{0x11, 0x22, 0x33, 0x44});     // entropy data
        out.write(0xFF);
        out.write(0xD9); // EOI
        return out.toByteArray();
    }

    private static byte[] minimalGainMapJpeg() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8); // SOI
        out.write(segment(0xDB, new byte[]{0, 1, 2, 3}));
        out.write(segment(0xC0, new byte[]{8, 0}));
        out.write(segment(0xDA, new byte[]{0, 0}));
        out.write(new byte[]{0x55, 0x66, 0x77});
        out.write(0xFF);
        out.write(0xD9); // EOI
        return out.toByteArray();
    }

    private static int findMarker(byte[] data, int start, int marker) {
        for (int i = start; i < data.length - 1; i++) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == marker) {
                return i;
            }
        }
        return -1;
    }

    private static int readSegmentLength(byte[] data, int markerPos) {
        return ((data[markerPos + 2] & 0xFF) << 8) | (data[markerPos + 3] & 0xFF);
    }

    @Test
    public void mpfPayloadMatchesReferenceLayout() {
        byte[] mpf = UltraHdrJpegWriter.buildMpf(0x005BEDA0L, 0x00061F08L, 0x005BE487L);
        assertEquals(86, mpf.length);
        ByteBuffer bb = ByteBuffer.wrap(mpf).order(ByteOrder.BIG_ENDIAN);
        byte[] sig = new byte[4];
        bb.get(sig);
        assertArrayEquals(new byte[]{'M', 'P', 'F', 0}, sig);
        byte[] endian = new byte[4];
        bb.get(endian);
        assertArrayEquals(new byte[]{0x4D, 0x4D, 0x00, 0x2A}, endian);
        assertEquals(8, bb.getInt());
        assertEquals(3, bb.getShort());
        // Version tag
        assertEquals((short) 0xB000, bb.getShort());
        assertEquals(7, bb.getShort());
        assertEquals(4, bb.getInt());
        byte[] version = new byte[4];
        bb.get(version);
        assertArrayEquals(new byte[]{'0', '1', '0', '0'}, version);
        // Number of images
        assertEquals((short) 0xB001, bb.getShort());
        assertEquals(4, bb.getShort());
        assertEquals(1, bb.getInt());
        assertEquals(2, bb.getInt());
        // MP entry tag
        assertEquals((short) 0xB002, bb.getShort());
        assertEquals(7, bb.getShort());
        assertEquals(32, bb.getInt());
        assertEquals(42, bb.getInt());
        // Next IFD
        assertEquals(0, bb.getInt());
        // Primary entry
        assertEquals(0x00030000, bb.getInt());
        assertEquals(0x005BEDA0, bb.getInt());
        assertEquals(0, bb.getInt());
        assertEquals(0, bb.getShort());
        assertEquals(0, bb.getShort());
        // Secondary entry
        assertEquals(0x00000000, bb.getInt());
        assertEquals(0x00061F08, bb.getInt());
        assertEquals(0x005BE487, bb.getInt());
        assertEquals(0, bb.getShort());
        assertEquals(0, bb.getShort());
        assertEquals(86, bb.position());
    }

    @Test
    public void xmpPacketsContainRequiredFields() {
        byte[] primary = UltraHdrJpegWriter.buildPrimaryXmp(401160);
        String xmpPrimary = new String(primary, StandardCharsets.UTF_8);
        assertTrue(xmpPrimary.contains("hdrgm:Version=\"1.0\""));
        assertTrue(xmpPrimary.contains("xmlns:hdrgm=\"http://ns.adobe.com/hdr-gain-map/1.0/\""));
        assertTrue(xmpPrimary.contains("Item:Semantic=\"Primary\""));
        assertTrue(xmpPrimary.contains("Item:Semantic=\"GainMap\""));
        assertTrue(xmpPrimary.contains("Item:Length=\"401160\""));

        byte[] secondary = UltraHdrJpegWriter.buildSecondaryXmp(3.0, -1.0);
        String xmpSecondary = new String(secondary, StandardCharsets.UTF_8);
        assertTrue(xmpSecondary.contains("hdrgm:GainMapMin=\"-1\""));
        assertTrue(xmpSecondary.contains("hdrgm:GainMapMax=\"3\""));
        assertTrue(xmpSecondary.contains("hdrgm:Gamma=\"1\""));
        assertTrue(xmpSecondary.contains("hdrgm:OffsetSDR=\"0.015625\""));
        assertTrue(xmpSecondary.contains("hdrgm:OffsetHDR=\"0.015625\""));
        assertTrue(xmpSecondary.contains("hdrgm:HDRCapacityMin=\"-1\""));
        assertTrue(xmpSecondary.contains("hdrgm:HDRCapacityMax=\"3\""));
        assertTrue(xmpSecondary.contains("hdrgm:BaseRenditionIsHDR=\"False\""));
    }

    @Test
    public void isoSecondaryPayloadMatchesReferenceLayout() {
        byte[] iso = UltraHdrJpegWriter.buildIsoSecondaryPayload(3.0, -1.0);
        assertEquals(37, iso.length);
        ByteBuffer bb = ByteBuffer.wrap(iso).order(ByteOrder.BIG_ENDIAN);
        assertEquals(0, bb.getShort());   // min version
        assertEquals(0, bb.getShort());   // writer version
        assertEquals(0x48, bb.get());     // flags: useBaseColorSpace | commonDenominator
        assertEquals(1_000_000, bb.getInt()); // denominator
        assertEquals(0, bb.getInt());     // base HDR headroom
        assertEquals(3_000_000, bb.getInt()); // alternate HDR headroom (log2(8) * denom)
        assertEquals(-1_000_000, bb.getInt()); // gain map min (log2(0.5) * denom)
        assertEquals(3_000_000, bb.getInt()); // gain map max
        assertEquals(1_000_000, bb.getInt()); // gamma = 1
        assertEquals(15625, bb.getInt()); // offset 1/64
        assertEquals(15625, bb.getInt()); // offset 1/64
        assertEquals(37, bb.position());
    }

    @Test
    public void fullContainerStructureIsCorrect() throws IOException {
        byte[] base = minimalBaseJpeg();
        byte[] gm = minimalGainMapJpeg();
        Path file = Files.createTempFile("ultrahdr_test", ".jpg");
        Files.write(file, base);
        try {
            boolean ok = UltraHdrJpegWriter.write(file, gm, 6.0f, 0.5f);
            assertTrue(ok);
            byte[] out = Files.readAllBytes(file);

            // Starts with SOI
            assertEquals(0xFF, out[0] & 0xFF);
            assertEquals(0xD8, out[1] & 0xFF);
            // JFIF preserved as first segment
            assertEquals(0xFF, out[2] & 0xFF);
            assertEquals(0xE0, out[3] & 0xFF);
            // EXIF preserved
            int exifPos = findMarker(out, 4, 0xE1);
            assertTrue(exifPos > 0);
            assertEquals('E', out[exifPos + 4]);
            assertEquals('x', out[exifPos + 5]);
            assertEquals('i', out[exifPos + 6]);
            assertEquals('f', out[exifPos + 7]);
            // Primary XMP APP1
            int xmpPrimaryPos = findMarker(out, exifPos + 4, 0xE1);
            assertTrue(xmpPrimaryPos > exifPos);
            assertTrue(new String(out, xmpPrimaryPos + 4,
                    readSegmentLength(out, xmpPrimaryPos) - 2, StandardCharsets.US_ASCII)
                    .contains("hdrgm:Version=\"1.0\""));
            // Primary ISO APP2
            int isoPrimaryPos = findMarker(out, xmpPrimaryPos + 4, 0xE2);
            assertTrue(isoPrimaryPos > xmpPrimaryPos);
            assertTrue(new String(out, isoPrimaryPos + 4, 27, StandardCharsets.US_ASCII)
                    .contains("urn:iso:std:iso:ts:21496:-1"));
            // MPF APP2 right before SOS
            int sosPos = findMarker(out, isoPrimaryPos + 4, 0xDA);
            assertTrue(sosPos > 0);
            int mpfPos = sosPos - 90;
            assertEquals(0xFF, out[mpfPos] & 0xFF);
            assertEquals(0xE2, out[mpfPos + 1] & 0xFF);
            assertEquals(88, readSegmentLength(out, mpfPos));

            // Verify MPF offsets resolve exactly to the secondary SOI
            // ByteBuffer.wrap(array, offset, len) starts at `offset`, so read
            // the 54-byte header sequentially before the MP entries.
            ByteBuffer mpf = ByteBuffer.wrap(out, mpfPos + 4, 86).order(ByteOrder.BIG_ENDIAN);
            mpf.position(mpf.position() + 54);
            assertEquals(0x00030000, mpf.getInt()); // primary attributes
            long primarySize = Integer.toUnsignedLong(mpf.getInt());
            assertEquals(0, mpf.getInt()); // primary offset
            assertEquals(0, mpf.getShort());
            assertEquals(0, mpf.getShort());
            assertEquals(0, mpf.getInt()); // gain map attributes
            long secondarySize = Integer.toUnsignedLong(mpf.getInt());
            long secondaryOffset = Integer.toUnsignedLong(mpf.getInt());
            long mpfBase = mpfPos + 8L;
            long secondarySoi = mpfBase + secondaryOffset;
            assertEquals(0xFF, out[(int) secondarySoi] & 0xFF);
            assertEquals(0xD8, out[(int) secondarySoi + 1] & 0xFF);
            assertEquals(primarySize, secondarySoi);
            long totalFileSize = primarySize + secondarySize;
            assertEquals(out.length, totalFileSize);

            // Secondary contains the gain map XMP + ISO metadata
            int xmpSecondaryPos = findMarker(out, (int) secondarySoi + 2, 0xE1);
            assertTrue(xmpSecondaryPos > secondarySoi);
            assertTrue(new String(out, xmpSecondaryPos + 4,
                    readSegmentLength(out, xmpSecondaryPos) - 2, StandardCharsets.US_ASCII)
                    .contains("hdrgm:BaseRenditionIsHDR=\"False\""));
            int isoSecondaryPos = findMarker(out, xmpSecondaryPos + 4, 0xE2);
            assertTrue(isoSecondaryPos > xmpSecondaryPos);

            // Gain map entropy data present at the end
            assertTrue(Arrays.equals(
                    new byte[]{0x55, 0x66, 0x77, (byte) 0xFF, (byte) 0xD9},
                    Arrays.copyOfRange(out, out.length - 5, out.length)));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void writeRejectsJpegWithoutSos() throws IOException {
        byte[] base = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, (byte) 0xFF, (byte) 0xD9};
        byte[] gm = minimalGainMapJpeg();
        Path file = Files.createTempFile("ultrahdr_test", ".jpg");
        Files.write(file, base);
        try {
            boolean ok = UltraHdrJpegWriter.write(file, gm, 6.0f, 0.5f);
            assertTrue(!ok);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    public void writeRejectsMalformedGainMapJpeg() throws IOException {
        Path file = Files.createTempFile("ultrahdr_test", ".jpg");
        Files.write(file, minimalBaseJpeg());
        try {
            assertTrue(!UltraHdrJpegWriter.write(file,
                    new byte[]{(byte) 0xFF, (byte) 0xD8}, 6.0f, 0.5f));
        } finally {
            Files.deleteIfExists(file);
        }
    }
}
