package com.particlesdevs.photoncamera.processing.encoder;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ImageFormatConfigTest {

    @Test
    public void legacyValuesAreStable() {
        assertEquals(0, ImageFormatConfig.SAVE_JPEG);
        assertEquals(1, ImageFormatConfig.SAVE_RAW_JPEG);
        assertEquals(2, ImageFormatConfig.SAVE_RAW_ONLY);
    }

    @Test
    public void heicModes() {
        assertTrue(ImageFormatConfig.usesHeic(ImageFormatConfig.SAVE_HEIC));
        assertTrue(ImageFormatConfig.usesHeic(ImageFormatConfig.SAVE_HEIC_RAW));
        assertFalse(ImageFormatConfig.usesHeic(ImageFormatConfig.SAVE_JPEG));
        assertFalse(ImageFormatConfig.usesHeic(ImageFormatConfig.SAVE_RAW_JPEG));
        assertFalse(ImageFormatConfig.usesHeic(ImageFormatConfig.SAVE_RAW_ONLY));
        assertEquals("heic", ImageFormatConfig.stillExtension(ImageFormatConfig.SAVE_HEIC));
        assertEquals("heic", ImageFormatConfig.stillExtension(ImageFormatConfig.SAVE_HEIC_RAW));
        assertEquals("jpg", ImageFormatConfig.stillExtension(ImageFormatConfig.SAVE_JPEG));
        assertEquals("image/heic", ImageFormatConfig.stillMimeType(ImageFormatConfig.SAVE_HEIC));
        assertEquals("image/jpeg", ImageFormatConfig.stillMimeType(ImageFormatConfig.SAVE_JPEG));
    }

    @Test
    public void rawGates() {
        assertTrue(ImageFormatConfig.savesRaw(ImageFormatConfig.SAVE_RAW_JPEG));
        assertTrue(ImageFormatConfig.savesRaw(ImageFormatConfig.SAVE_RAW_ONLY));
        assertTrue(ImageFormatConfig.savesRaw(ImageFormatConfig.SAVE_HEIC_RAW));
        assertFalse(ImageFormatConfig.savesRaw(ImageFormatConfig.SAVE_JPEG));
        assertFalse(ImageFormatConfig.savesRaw(ImageFormatConfig.SAVE_HEIC));
        assertTrue(ImageFormatConfig.isRawOnly(ImageFormatConfig.SAVE_RAW_ONLY));
        assertFalse(ImageFormatConfig.isRawOnly(ImageFormatConfig.SAVE_HEIC_RAW));
    }

    @Test
    public void normalizeAndFallback() {
        assertEquals(ImageFormatConfig.SAVE_JPEG, ImageFormatConfig.normalize(99));
        assertEquals(ImageFormatConfig.SAVE_JPEG,
                ImageFormatConfig.withoutHeic(ImageFormatConfig.SAVE_HEIC));
        assertEquals(ImageFormatConfig.SAVE_RAW_JPEG,
                ImageFormatConfig.withoutHeic(ImageFormatConfig.SAVE_HEIC_RAW));
        assertEquals(ImageFormatConfig.SAVE_RAW_ONLY,
                ImageFormatConfig.withoutHeic(ImageFormatConfig.SAVE_RAW_ONLY));
    }

    @Test
    public void heicSaveModeHelper() {
        HeicSupport.setHeicEncodeSupportedForTest(null);
        assertTrue(HeicSupport.isHeicSaveMode(ImageFormatConfig.SAVE_HEIC));
        assertTrue(HeicSupport.isHeicSaveMode(ImageFormatConfig.SAVE_HEIC_RAW));
        assertFalse(HeicSupport.isHeicSaveMode(ImageFormatConfig.SAVE_JPEG));
    }
}
