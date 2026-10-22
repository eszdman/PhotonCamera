package com.particlesdevs.photoncamera.gallery.helper;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class ExifDescriptionDecoderTest {

    @Test
    public void newlinesArePreserved() {
        // androidx ExifInterface.getAttribute() would turn every \n into '?'.
        byte[] raw = "parameters:\n\n hasGainMap=true\n FrameCount=13\0"
                .getBytes(StandardCharsets.US_ASCII);
        assertEquals("parameters:\n\n hasGainMap=true\n FrameCount=13",
                ExifDescriptionDecoder.decode(raw));
    }

    @Test
    public void crlfAndCrAreNormalized() {
        byte[] raw = "parameters:\r\n a=1\r b=2\0".getBytes(StandardCharsets.US_ASCII);
        assertEquals("parameters:\n a=1\n b=2", ExifDescriptionDecoder.decode(raw));
    }

    @Test
    public void asciiPrefixAndNulTerminatorAreStripped() {
        byte[] raw = {'A', 'S', 'C', 'I', 'I', 0, 0, 0, 'h', 'i', '\n', 0, 'x'};
        assertEquals("hi", ExifDescriptionDecoder.decode(raw));
    }

    @Test
    public void nullEmptyAndTerminatorOnlyReturnEmpty() {
        assertEquals("", ExifDescriptionDecoder.decode(null));
        assertEquals("", ExifDescriptionDecoder.decode(new byte[0]));
        assertEquals("", ExifDescriptionDecoder.decode(new byte[]{0}));
    }
}
