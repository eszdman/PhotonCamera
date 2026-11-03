package com.particlesdevs.photoncamera.processing.encoder;

import org.junit.Test;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DisplayP3ProfileTest {

    @Test
    public void embeddedProfileIsAValidRgbDisplayProfile() throws Exception {
        File file = new File("src/main/res/raw/display_p3.icc");
        if (!file.exists()) {
            file = new File("app/src/main/res/raw/display_p3.icc");
        }
        assertTrue("missing Display P3 ICC asset: " + file.getAbsolutePath(), file.exists());
        byte[] icc = Files.readAllBytes(file.toPath());
        assertEquals(480, icc.length);
        ByteBuffer bb = ByteBuffer.wrap(icc).order(ByteOrder.BIG_ENDIAN);
        assertEquals(icc.length, bb.getInt(0));
        assertEquals("acsp", new String(icc, 36, 4, StandardCharsets.US_ASCII));
        assertEquals("RGB ", new String(icc, 16, 4, StandardCharsets.US_ASCII));
        assertEquals("XYZ ", new String(icc, 20, 4, StandardCharsets.US_ASCII));
        int tagCount = bb.getInt(128);
        assertTrue("profile has no tags", tagCount > 0);
        String text = new String(icc, StandardCharsets.ISO_8859_1).replace("\0", "");
        assertTrue(text.contains("sP3C"));
    }
}
