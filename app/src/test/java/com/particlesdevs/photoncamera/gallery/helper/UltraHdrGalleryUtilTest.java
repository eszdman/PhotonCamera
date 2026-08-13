package com.particlesdevs.photoncamera.gallery.helper;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UltraHdrGalleryUtilTest {

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

    private static byte[] minimalSdrJpeg() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        out.write(segment(0xE0, new byte[]{'J', 'F', 'I', 'F', 0, 1, 2, 0, 0, 1, 0, 1, 0, 0, 0, 0}));
        out.write(segment(0xDB, new byte[]{0, 1, 2, 3}));
        out.write(segment(0xC0, new byte[]{8, 0}));
        out.write(segment(0xDA, new byte[]{0, 0}));
        out.write(new byte[]{0x11, 0x22, 0x33});
        out.write(0xFF);
        out.write(0xD9);
        return out.toByteArray();
    }

    @Test
    public void detectsXmpHdrGainMapSegment() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        String xmp = "<x:xmpmeta xmlns:hdrgm=\"http://ns.adobe.com/hdr-gain-map/1.0/\">";
        out.write(segment(0xE1, ("http://ns.adobe.com/xap/1.0/\0" + xmp)
                .getBytes(StandardCharsets.US_ASCII)));
        byte[] data = out.toByteArray();
        assertTrue(UltraHdrGalleryUtil.containsUltraHdrMarkers(data, data.length));
    }

    @Test
    public void detectsMpfSegment() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        out.write(segment(0xE2, new byte[]{'M', 'P', 'F', 0, 0x4D, 0x4D, 0x00, 0x2A}));
        byte[] data = out.toByteArray();
        assertTrue(UltraHdrGalleryUtil.containsUltraHdrMarkers(data, data.length));
    }

    @Test
    public void detectsIso21496Segment() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        out.write(segment(0xE2, "urn:iso:std:iso:ts:21496:-1\0\0\0\0\0\0"
                .getBytes(StandardCharsets.US_ASCII)));
        byte[] data = out.toByteArray();
        assertTrue(UltraHdrGalleryUtil.containsUltraHdrMarkers(data, data.length));
    }

    @Test
    public void plainJpegIsNotDetected() throws IOException {
        byte[] data = minimalSdrJpeg();
        assertFalse(UltraHdrGalleryUtil.containsUltraHdrMarkers(data, data.length));
    }

    @Test
    public void truncatedAndEmptyDataAreNotDetected() {
        assertFalse(UltraHdrGalleryUtil.containsUltraHdrMarkers(null, 0));
        assertFalse(UltraHdrGalleryUtil.containsUltraHdrMarkers(
                new byte[]{(byte) 0xFF, (byte) 0xD8}, 2));
        assertFalse(UltraHdrGalleryUtil.containsUltraHdrMarkers(
                new byte[]{(byte) 0xFF, (byte) 0xE2, 0x00}, 3));
    }

    @Test
    public void genericApp1WithoutHdrGainMapIsNotDetected() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0xFF);
        out.write(0xD8);
        out.write(segment(0xE1, ("http://ns.adobe.com/xap/1.0/\0" + "<x:xmpmeta>plain</x:xmpmeta>")
                .getBytes(StandardCharsets.US_ASCII)));
        byte[] data = out.toByteArray();
        assertFalse(UltraHdrGalleryUtil.containsUltraHdrMarkers(data, data.length));
    }
}
