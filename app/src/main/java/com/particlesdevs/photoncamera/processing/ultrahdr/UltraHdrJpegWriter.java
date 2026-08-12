package com.particlesdevs.photoncamera.processing.ultrahdr;

import com.particlesdevs.photoncamera.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Writes an Ultra HDR (ISO 21496-1) JPEG by embedding the gain map into an
 * already EXIF-tagged base JPEG. The container layout mirrors the reference
 * implementation (Google libultrahdr) byte-for-byte:
 * <pre>
 *   SOI | APP0 JFIF | APP1 EXIF | APP1 XMP (GContainer) | APP2 ISO 21496-1 (version only)
 *       | DQT/SOF/DHT... | APP2 MPF | SOS..EOI
 *   SOI | APP1 XMP (gain map metadata) | APP2 ISO 21496-1 (gain map metadata)
 *       | gain map JPEG data (without SOI)
 * </pre>
 * MPF is placed immediately before SOS per CIPA DC-007; all offsets are
 * big-endian and relative to the MPF offset base (the byte after the "MPF\0"
 * signature). The format is self-contained and does not require libultrahdr.
 *
 * <p>Structure mirrored from libultrahdr (Apache-2.0 / MIT), Copyright
 * 2022-2024 The Android Open Source Project.</p>
 */
public final class UltraHdrJpegWriter {

    private static final String TAG = "UltraHdrJpegWriter";

    private static final byte[] XMP_NS = "http://ns.adobe.com/xap/1.0/\0"
            .getBytes(StandardCharsets.US_ASCII);
    private static final byte[] ISO_NS = "urn:iso:std:iso:ts:21496:-1\0"
            .getBytes(StandardCharsets.US_ASCII);
    private static final String HDRGM_NS = "http://ns.adobe.com/hdr-gain-map/1.0/";
    private static final String CONTAINER_NS = "http://ns.google.com/photos/1.0/container/";
    private static final String ITEM_NS = "http://ns.google.com/photos/1.0/container/item/";
    private static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final long ISO_DENOM = 1_000_000L;
    private static final double LOG2 = Math.log(2.0);
    private static final double GAIN_OFFSET = UltraHdrGainMapStats.GAIN_OFFSET;

    private UltraHdrJpegWriter() {
    }

    /**
     * Embeds the gain map JPEG into the base JPEG at {@code file}.
     *
     * @param file            the base JPEG file (EXIF must already be written)
     * @param gainMapJpeg     a complete JPEG encoding of the single-channel gain map
     * @param maxContentBoost the max HDR/SDR content boost (>= 1.0, capped at 16.0)
     * @param minContentBoost the min HDR/SDR content boost (1.0 in production);
     *                        the production gain map uses 1.0
     * @return true if the Ultra HDR container was written successfully
     */
    public static boolean write(Path file, byte[] gainMapJpeg, float maxContentBoost,
                                float minContentBoost) throws IOException {
        if (file == null || !isJpeg(gainMapJpeg)) {
            Log.e(TAG, "Invalid gain map JPEG, cannot create Ultra HDR container");
            return false;
        }
        double boost = Math.max(1.05,
                Math.min(UltraHdrGainMapStats.MAX_CONTENT_BOOST, maxContentBoost));
        double log2MaxBoost = Math.log(boost) / LOG2;
        double minBoost;
        if (!Float.isFinite(minContentBoost) || minContentBoost <= 0.0f) {
            minBoost = 1.0;
        } else {
            minBoost = Math.min(1.0, Math.max(UltraHdrGainMapStats.MIN_CONTENT_BOOST,
                    minContentBoost));
        }
        double log2MinBoost = Math.log(minBoost) / LOG2;

        byte[] xmpSecondary = buildSecondaryXmp(log2MaxBoost, log2MinBoost);
        byte[] isoSecondary = buildIsoSecondaryPayload(log2MaxBoost, log2MinBoost);
        byte[] xmpSecondarySeg = appSegment(0xE1, concat(XMP_NS, xmpSecondary));
        byte[] isoSecondarySeg = appSegment(0xE2, concat(ISO_NS, isoSecondary));
        // Reference layout: SOI (2) + [FF E1 + length field + ns + xmp]
        // + [FF E2 + length field + ns + iso] + (gain map data minus its SOI)
        long secondarySize = (long) gainMapJpeg.length + 4
                + (2 + XMP_NS.length + xmpSecondary.length)
                + (2 + ISO_NS.length + isoSecondary.length);

        byte[] xmpPrimary = buildPrimaryXmp(secondarySize);
        byte[] isoPrimary = new byte[4];
        byte[] xmpPrimarySeg = appSegment(0xE1, concat(XMP_NS, xmpPrimary));
        byte[] isoPrimarySeg = appSegment(0xE2, concat(ISO_NS, isoPrimary));

        byte[] base = Files.readAllBytes(file);
        if (!isJpeg(base)) {
            Log.e(TAG, "Base file is not a valid JPEG, cannot append gain map");
            return false;
        }
        List<byte[]> leadingSegments = new ArrayList<>();
        List<byte[]> tableSegments = new ArrayList<>();
        int sos = -1;
        int pos = 2;
        while (pos + 1 < base.length) {
            if ((base[pos] & 0xFF) != 0xFF) {
                break;
            }
            int marker = base[pos + 1] & 0xFF;
            if (marker == 0xDA) {
                sos = pos;
                break;
            }
            if (marker == 0xD9) {
                break;
            }
            if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                pos += 2;
                continue;
            }
            if (pos + 4 > base.length) {
                break;
            }
            int len = ((base[pos + 2] & 0xFF) << 8) | (base[pos + 3] & 0xFF);
            if (len < 2 || pos + 2 + len > base.length) {
                break;
            }
            byte[] seg = Arrays.copyOfRange(base, pos, pos + 2 + len);
            if (marker >= 0xE0 && marker <= 0xEF) {
                // Preserve color profiles and application metadata, but replace
                // old XMP/MPF containers with the Ultra HDR metadata below.
                if (!((marker == 0xE1 && !isExifSegment(seg))
                        || (marker == 0xE2 && isMpfSegment(seg)))) {
                    leadingSegments.add(seg);
                }
            } else {
                tableSegments.add(seg);
            }
            pos += 2 + len;
        }
        if (sos < 0) {
            Log.e(TAG, "Base JPEG has no SOS marker, cannot append gain map");
            return false;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(base.length + 4096);
        out.write(0xFF);
        out.write(0xD8);
        for (byte[] seg : leadingSegments) {
            out.write(seg, 0, seg.length);
        }
        out.write(xmpPrimarySeg);
        out.write(isoPrimarySeg);
        for (byte[] seg : tableSegments) {
            out.write(seg, 0, seg.length);
        }
        int mpfMarkerPos = out.size();
        long primarySize = mpfMarkerPos + 2L + 2L + 86L + (base.length - sos);
        long secondaryOffset = primarySize - mpfMarkerPos - 8L;
        byte[] mpfSeg = appSegment(0xE2, buildMpf(primarySize, secondarySize, secondaryOffset));
        out.write(mpfSeg);
        out.write(base, sos, base.length - sos);
        out.write(0xFF);
        out.write(0xD8);
        out.write(xmpSecondarySeg);
        out.write(isoSecondarySeg);
        out.write(gainMapJpeg, 2, gainMapJpeg.length - 2);
        Files.write(file, out.toByteArray());
        Log.d(TAG, "UltraHDR JPEG written, primary size: " + primarySize
                + ", gain map size: " + secondarySize + ", max content boost: " + boost
                + ", min content boost: " + minBoost);
        return true;
    }

    private static boolean isExifSegment(byte[] seg) {
        return seg.length >= 10
                && seg[4] == 'E' && seg[5] == 'x' && seg[6] == 'i' && seg[7] == 'f'
                && seg[8] == 0 && seg[9] == 0;
    }

    private static boolean isMpfSegment(byte[] seg) {
        return seg.length >= 8 && seg[4] == 'M' && seg[5] == 'P'
                && seg[6] == 'F' && seg[7] == 0;
    }

    private static boolean isJpeg(byte[] data) {
        return data != null && data.length >= 4
                && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8
                && (data[data.length - 2] & 0xFF) == 0xFF
                && (data[data.length - 1] & 0xFF) == 0xD9;
    }

    static byte[] buildPrimaryXmp(long secondaryImageSize) {
        String xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Adobe XMP Core 5.1.2\">\n"
                + "<rdf:RDF xmlns:rdf=\"" + RDF_NS + "\">\n"
                + "<rdf:Description xmlns:Container=\"" + CONTAINER_NS + "\""
                + " xmlns:Item=\"" + ITEM_NS + "\""
                + " xmlns:hdrgm=\"" + HDRGM_NS + "\" hdrgm:Version=\"1.0\">\n"
                + "<Container:Directory>\n"
                + "<rdf:Seq>\n"
                + "<rdf:li rdf:parseType=\"Resource\">\n"
                + "<Container:Item Item:Semantic=\"Primary\" Item:Mime=\"image/jpeg\"/>\n"
                + "</rdf:li>\n"
                + "<rdf:li rdf:parseType=\"Resource\">\n"
                + "<Container:Item Item:Semantic=\"GainMap\" Item:Mime=\"image/jpeg\""
                + " Item:Length=\"" + secondaryImageSize + "\"/>\n"
                + "</rdf:li>\n"
                + "</rdf:Seq>\n"
                + "</Container:Directory>\n"
                + "</rdf:Description>\n"
                + "</rdf:RDF>\n"
                + "</x:xmpmeta>";
        return xmp.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] buildSecondaryXmp(double log2MaxBoost, double log2MinBoost) {
        String value = doubleToString(log2MaxBoost);
        String minValue = doubleToString(log2MinBoost);
        String offset = doubleToString(GAIN_OFFSET);
        String xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"Adobe XMP Core 5.1.2\">\n"
                + "<rdf:RDF xmlns:rdf=\"" + RDF_NS + "\">\n"
                + "<rdf:Description rdf:about=\"\""
                + " xmlns:hdrgm=\"" + HDRGM_NS + "\""
                + " hdrgm:Version=\"1.0\""
                + " hdrgm:GainMapMin=\"" + minValue + "\""
                + " hdrgm:GainMapMax=\"" + value + "\""
                + " hdrgm:Gamma=\"1\""
                + " hdrgm:OffsetSDR=\"" + offset + "\""
                + " hdrgm:OffsetHDR=\"" + offset + "\""
                + " hdrgm:HDRCapacityMin=\"" + minValue + "\""
                + " hdrgm:HDRCapacityMax=\"" + value + "\""
                + " hdrgm:BaseRenditionIsHDR=\"False\"/>\n"
                + "</rdf:RDF>\n"
                + "</x:xmpmeta>";
        return xmp.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] buildIsoSecondaryPayload(double log2MaxBoost, double log2MinBoost) {
        long gainMapMaxN = Math.round(log2MaxBoost * ISO_DENOM);
        long gainMapMinN = Math.round(log2MinBoost * ISO_DENOM);
        long offsetN = Math.round(GAIN_OFFSET * ISO_DENOM);
        ByteBuffer bb = ByteBuffer.allocate(37).order(ByteOrder.BIG_ENDIAN);
        bb.putShort((short) 0);          // minimum version
        bb.putShort((short) 0);          // writer version
        bb.put((byte) 0x48);             // useBaseColorSpace | commonDenominator (single channel)
        bb.putInt((int) ISO_DENOM);      // common denominator
        bb.putInt(0);                    // base HDR headroom numerator (log2(1.0) = 0)
        bb.putInt((int) gainMapMaxN);    // alternate HDR headroom numerator
        bb.putInt((int) gainMapMinN);    // gain map min numerator
        bb.putInt((int) gainMapMaxN);    // gain map max numerator
        bb.putInt((int) ISO_DENOM);      // gamma numerator (gamma = 1.0)
        bb.putInt((int) offsetN);        // base offset numerator (1/64)
        bb.putInt((int) offsetN);        // alternate offset numerator (1/64)
        return bb.array();
    }

    /**
     * Builds the 86-byte MPF APP2 payload (big-endian, CIPA DC-007).
     */
    static byte[] buildMpf(long primaryImageSize, long secondaryImageSize,
                           long secondaryImageOffset) {
        ByteBuffer bb = ByteBuffer.allocate(86).order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) 'M').put((byte) 'P').put((byte) 'F').put((byte) 0);
        bb.put((byte) 0x4D).put((byte) 0x4D).put((byte) 0x00).put((byte) 0x2A);
        bb.putInt(8);                    // index IFD offset
        bb.putShort((short) 3);          // tag count
        bb.putShort((short) 0xB000).putShort((short) 7).putInt(4)
                .put((byte) '0').put((byte) '1').put((byte) '0').put((byte) '0');
        bb.putShort((short) 0xB001).putShort((short) 4).putInt(1).putInt(2);
        bb.putShort((short) 0xB002).putShort((short) 7).putInt(32).putInt(42);
        bb.putInt(0);                    // next IFD offset
        bb.putInt(0x00030000);           // primary: JPEG | primary
        bb.putInt((int) primaryImageSize);
        bb.putInt(0);
        bb.putShort((short) 0).putShort((short) 0);
        bb.putInt(0x00000000);           // gain map: JPEG
        bb.putInt((int) secondaryImageSize);
        bb.putInt((int) secondaryImageOffset);
        bb.putShort((short) 0).putShort((short) 0);
        return bb.array();
    }

    private static byte[] appSegment(int marker, byte[] payload) {
        int len = 2 + payload.length;
        byte[] seg = new byte[2 + len];
        seg[0] = (byte) 0xFF;
        seg[1] = (byte) marker;
        seg[2] = (byte) (len >> 8);
        seg[3] = (byte) len;
        System.arraycopy(payload, 0, seg, 4, payload.length);
        return seg;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static String doubleToString(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }
}
