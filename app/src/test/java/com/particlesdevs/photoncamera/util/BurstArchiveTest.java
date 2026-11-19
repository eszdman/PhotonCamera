package com.particlesdevs.photoncamera.util;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;

public class BurstArchiveTest {
    @Test
    public void streamsAllFramesWithTheirOriginalBytes() throws Exception {
        byte[][] frames = {new byte[700000], new byte[12345], new byte[]{73, 73, 42, 0}};
        for (int i = 0; i < frames[0].length; i++) frames[0][i] = (byte) (i * 31);
        Arrays.fill(frames[1], (byte) 173);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BurstArchive.write(output, "IMG_test", frames.length, (index, entry) -> entry.write(frames[index]));
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(output.toByteArray()))) {
            for (int i = 0; i < frames.length; i++) {
                ZipEntry entry = zip.getNextEntry();
                assertNotNull(entry);
                assertEquals(String.format(java.util.Locale.ROOT, "IMG_test_f%02d.dng", i), entry.getName());
                assertArrayEquals(frames[i], zip.readAllBytes());
            }
            assertNull(zip.getNextEntry());
        }
    }

    @Test
    public void stopsAndClosesOutputWhenAFrameFails() throws Exception {
        boolean[] closed = {false};
        ByteArrayOutputStream output = new ByteArrayOutputStream() {
            @Override public void close() { closed[0] = true; }
        };
        int[] calls = {0};
        IOException failure = new IOException("Disk full");
        try {
            BurstArchive.write(output, "IMG_test", 3, (index, entry) -> {
                calls[0]++;
                if (index == 1) throw failure;
                entry.write(42);
            });
            fail("Export must report a failed frame");
        } catch (IOException e) {
            assertSame(failure, e);
        }
        assertEquals(2, calls[0]);
        assertTrue(closed[0]);
    }

    @Test(expected = IOException.class)
    public void rejectsEmptyBursts() throws Exception {
        BurstArchive.write(new ByteArrayOutputStream(), "IMG_empty", 0, (index, output) -> fail());
    }
}
