package com.particlesdevs.photoncamera.processing.encoder;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class IsoBmffTest {

    @Test
    public void boxRoundTrip() {
        byte[] payload = {1, 2, 3, 4, 5};
        byte[] box = IsoBmff.buildBox("mdat", payload);
        List<IsoBmff.Box> boxes = IsoBmff.parse(box);
        assertEquals(1, boxes.size());
        assertEquals("mdat", boxes.get(0).type);
        assertArrayEquals(payload, boxes.get(0).payload);
    }

    @Test
    public void multiBoxSplit() {
        byte[] a = IsoBmff.buildBox("ftyp", new byte[]{'h', 'e', 'i', 'c'});
        byte[] b = IsoBmff.buildBox("mdat", new byte[10]);
        byte[] both = IsoBmff.concat(Arrays.asList(a, b));
        List<IsoBmff.Box> boxes = IsoBmff.parse(both);
        assertEquals(2, boxes.size());
        assertEquals("ftyp", boxes.get(0).type);
        assertEquals("mdat", boxes.get(1).type);
    }

    @Test(expected = IllegalArgumentException.class)
    public void truncatedBoxThrows() {
        IsoBmff.parse(new byte[]{(byte) 0xFF, (byte) 0xD8, 0x00});
    }

    @Test
    public void ilocBodyRoundTrip() {
        List<UltraHdrHeicContainer.Extent> extents = Arrays.asList(
                new UltraHdrHeicContainer.Extent(1, 1000L, 500L),
                new UltraHdrHeicContainer.Extent(2, 1500L, 250L));
        byte[] body = UltraHdrHeicContainer.buildIlocBody(extents);
        // 0x44 flags, 0x00, count=2, then 10 bytes per extent.
        assertEquals((byte) 0x44, body[0]);
        assertEquals((byte) 0x00, body[1]);
        assertEquals(2, IsoBmff.u16(body, 2));
        assertEquals(1, IsoBmff.u16(body, 4));
        assertEquals(1000L, IsoBmff.u32(body, 10));
        assertEquals(500L, IsoBmff.u32(body, 14));
        assertEquals(2, IsoBmff.u16(body, 18));
        assertEquals(1500L, IsoBmff.u32(body, 24));
    }

    @Test
    public void infeV2ParsesBack() {
        byte[] infe = UltraHdrHeicContainer.buildInfeV2(7, "hvc1", "GainMap", null);
        List<IsoBmff.Box> boxes = IsoBmff.parse(infe);
        assertEquals(1, boxes.size());
        assertEquals("infe", boxes.get(0).type);
        assertEquals(2, IsoBmff.fullVersion(boxes.get(0).payload));
        assertEquals(7, IsoBmff.u16(boxes.get(0).payload, 4));
    }
}
