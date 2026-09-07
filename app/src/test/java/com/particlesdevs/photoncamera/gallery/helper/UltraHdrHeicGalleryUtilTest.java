package com.particlesdevs.photoncamera.gallery.helper;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UltraHdrHeicGalleryUtilTest {

    private static byte[] heifHeader(byte[] metaPayload) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] ftyp = "heic".getBytes(StandardCharsets.US_ASCII);
        int ftypSize = 8 + 4 + 4 + 4;
        out.write((ftypSize >> 24) & 0xFF);
        out.write((ftypSize >> 16) & 0xFF);
        out.write((ftypSize >> 8) & 0xFF);
        out.write(ftypSize & 0xFF);
        out.write("ftyp".getBytes(StandardCharsets.US_ASCII));
        out.write(ftyp);
        out.write(0);
        out.write(0);
        out.write(0);
        out.write(0);
        out.write("mif1".getBytes(StandardCharsets.US_ASCII));
        out.write(metaPayload, 0, metaPayload.length);
        return out.toByteArray();
    }

    @Test
    public void detectsHeifGainMapUrn() throws IOException {
        byte[] data = heifHeader("xxxurn:iso:std:iso:ts:21496:-1yyy"
                .getBytes(StandardCharsets.US_ASCII));
        assertTrue(UltraHdrGalleryUtil.containsHeifGainMapMarkers(data, data.length));
    }

    @Test
    public void detectsHeifHdrGainMapXmp() throws IOException {
        byte[] data = heifHeader("<rdf:Description xmlns:hdrgm=\"http://ns.adobe.com/hdr-gain-map/1.0/\">"
                .getBytes(StandardCharsets.US_ASCII));
        assertTrue(UltraHdrGalleryUtil.containsHeifGainMapMarkers(data, data.length));
    }

    @Test
    public void plainHeifIsNotUltraHdr() throws IOException {
        byte[] data = heifHeader("plain sdr heif content".getBytes(StandardCharsets.US_ASCII));
        assertFalse(UltraHdrGalleryUtil.containsHeifGainMapMarkers(data, data.length));
    }

    @Test
    public void jpegBytesAreNotHeif() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        assertFalse(UltraHdrGalleryUtil.containsHeifGainMapMarkers(jpeg, jpeg.length));
        assertFalse(UltraHdrGalleryUtil.containsHeifGainMapMarkers(null, 0));
    }
}
