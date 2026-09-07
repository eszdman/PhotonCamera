package com.particlesdevs.photoncamera.processing.encoder;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class UltraHdrHeicContainerTest {

    private static byte[] singleImageHeic(byte[] mediaPayload, int w, int h, byte[] hvcC) {
        return multiImageHeic(new byte[][]{mediaPayload}, new int[]{w}, new int[]{h},
                new byte[][]{hvcC}, new String[]{"Primary"});
    }

    /**
     * Builds a synthetic HEIF with N hvc1 items (item ids 1..N, pitm=1) and
     * real absolute iloc offsets (two-pass layout).
     */
    private static byte[] multiImageHeic(byte[][] payloads, int[] ws, int[] hs,
            byte[][] hvcCs, String[] names) {
        byte[] ftypPayload = ByteBuffer.allocate(4 + 4 + 12).order(ByteOrder.BIG_ENDIAN)
                .put("heic".getBytes(StandardCharsets.US_ASCII))
                .putInt(0)
                .put("heic".getBytes(StandardCharsets.US_ASCII))
                .put("mif1".getBytes(StandardCharsets.US_ASCII))
                .put("hevc".getBytes(StandardCharsets.US_ASCII))
                .array();
        byte[] ftyp = IsoBmff.buildBox("ftyp", ftypPayload);

        List<byte[]> infes = new ArrayList<>();
        for (int i = 0; i < payloads.length; i++) {
            infes.add(UltraHdrHeicContainer.buildInfeV2(i + 1, "hvc1", names[i], null));
        }
        ByteBuffer iinfBody = ByteBuffer
                .allocate(2 + totalLen(infes)).order(ByteOrder.BIG_ENDIAN);
        iinfBody.putShort((short) payloads.length);
        for (byte[] e : infes) {
            iinfBody.put(e);
        }

        ByteBuffer hdlrBody = ByteBuffer.allocate(4 + 4 + 12 + 1).order(ByteOrder.BIG_ENDIAN);
        hdlrBody.putInt(0);
        hdlrBody.put("pict".getBytes(StandardCharsets.US_ASCII));
        hdlrBody.put(new byte[12]);
        hdlrBody.put((byte) 0);
        byte[] hdlr = IsoBmff.buildBox("hdlr",
                IsoBmff.fullBoxPayload(0, 0, hdlrBody.array()));
        ByteBuffer pitmBody = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN);
        pitmBody.putShort((short) 1);
        byte[] pitm = IsoBmff.buildBox("pitm",
                IsoBmff.fullBoxPayload(0, 0, pitmBody.array()));

        List<byte[]> props = new ArrayList<>();
        for (int i = 0; i < payloads.length; i++) {
            props.add(IsoBmff.buildBox("hvcC", hvcCs[i]));
            props.add(IsoBmff.buildBox("ispe",
                    stripFullHeader(UltraHdrHeicContainer.buildIspe(ws[i], hs[i]))));
        }
        byte[] ipco = IsoBmff.buildBox("ipco", IsoBmff.concat(props));
        // ipma v0: one entry per item, each -> its own hvcC+ispe pair.
        ByteBuffer ipmaBody = ByteBuffer
                .allocate(4 + payloads.length * (2 + 1 + 2)).order(ByteOrder.BIG_ENDIAN);
        ipmaBody.putInt(payloads.length);
        for (int i = 0; i < payloads.length; i++) {
            ipmaBody.putShort((short) (i + 1));
            ipmaBody.put((byte) 2);
            ipmaBody.put((byte) (0x80 | (2 * i + 1)));
            ipmaBody.put((byte) (2 * i + 2));
        }
        byte[] ipma = IsoBmff.buildBox("ipma",
                IsoBmff.fullBoxPayload(0, 0, ipmaBody.array()));
        byte[] iprp = IsoBmff.buildBox("iprp", IsoBmff.concat(listOf(ipco, ipma)));

        List<byte[]> mdatPayloads = new ArrayList<>();
        for (byte[] p : payloads) {
            mdatPayloads.add(p);
        }
        // Pass 1: dummy iloc (same extent count => same size) to measure meta.
        List<UltraHdrHeicContainer.Extent> dummy = new ArrayList<>();
        for (int i = 0; i < payloads.length; i++) {
            dummy.add(new UltraHdrHeicContainer.Extent(0, 0, 0));
        }
        byte[] metaPass1 = assembleMeta(hdlr, pitm, dummy, iinfBody.array(), iprp);
        long mdatStart = ftyp.length + metaPass1.length + 8;
        // Pass 2: real absolute offsets.
        List<UltraHdrHeicContainer.Extent> extents = new ArrayList<>();
        long cursor = mdatStart;
        for (int i = 0; i < payloads.length; i++) {
            extents.add(new UltraHdrHeicContainer.Extent(i + 1, cursor, payloads[i].length));
            cursor += payloads[i].length;
        }
        byte[] meta = assembleMeta(hdlr, pitm, extents, iinfBody.array(), iprp);
        byte[] mdat = IsoBmff.buildBox("mdat", IsoBmff.concat(mdatPayloads));
        return IsoBmff.concat(listOf(ftyp, meta, mdat));
    }

    private static byte[] assembleMeta(byte[] hdlr, byte[] pitm,
            List<UltraHdrHeicContainer.Extent> extents, byte[] iinfBody, byte[] iprp) {
        byte[] iloc = IsoBmff.buildBox("iloc",
                IsoBmff.fullBoxPayload(0, 0, UltraHdrHeicContainer.buildIlocBody(extents)));
        byte[] iinf = IsoBmff.buildBox("iinf",
                IsoBmff.fullBoxPayload(0, 0, iinfBody));
        List<byte[]> metaKids = new ArrayList<>();
        metaKids.add(hdlr);
        metaKids.add(pitm);
        metaKids.add(iloc);
        metaKids.add(iinf);
        metaKids.add(iprp);
        return IsoBmff.buildBox("meta", IsoBmff.concat(listOf(
                IsoBmff.fullBoxPayload(0, 0, null), IsoBmff.concat(metaKids))));
    }

    private static int totalLen(List<byte[]> parts) {
        int n = 0;
        for (byte[] p : parts) {
            n += p.length;
        }
        return n;
    }

    private static byte[] stripFullHeader(byte[] fullBoxPayload) {
        byte[] out = new byte[fullBoxPayload.length - 4];
        System.arraycopy(fullBoxPayload, 4, out, 0, out.length);
        return out;
    }

    private static List<byte[]> listOf(byte[]... parts) {
        List<byte[]> out = new ArrayList<>();
        for (byte[] p : parts) {
            out.add(p);
        }
        return out;
    }

    private UltraHdrHeicContainer.Inputs inputs(byte[] exif) {
        UltraHdrHeicContainer.Inputs in = new UltraHdrHeicContainer.Inputs();
        in.baseHeic = singleImageHeic(bytes(64, (byte) 0xA5), 64, 48, new byte[]{1, 2, 3});
        in.gainHeic = singleImageHeic(bytes(16, (byte) 0x5A), 16, 12, new byte[]{9, 9});
        in.baseW = 64;
        in.baseH = 48;
        in.gainW = 16;
        in.gainH = 12;
        in.gainMapMin = 0f;
        in.gainMapMax = 2.5f;
        in.hdrCapacityMax = 2.5f;
        in.exifPayload = exif;
        return in;
    }

    private static byte[] bytes(int n, byte fill) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = fill;
        }
        return b;
    }

    @Test
    public void mergeWithoutExifHasFourItems() {
        byte[] out = UltraHdrHeicContainer.merge(inputs(null));
        List<IsoBmff.Box> top = IsoBmff.parse(out);
        assertEquals(3, top.size());
        assertEquals("ftyp", top.get(0).type);
        assertEquals("meta", top.get(1).type);
        assertEquals("mdat", top.get(2).type);

        List<IsoBmff.Box> kids = IsoBmff.parse(top.get(1).payload, 4, top.get(1).payload.length - 4);
        IsoBmff.Box iinf = null;
        IsoBmff.Box iloc = null;
        IsoBmff.Box iref = null;
        for (IsoBmff.Box b : kids) {
            if (b.type.equals("iinf")) {
                iinf = b;
            } else if (b.type.equals("iloc")) {
                iloc = b;
            } else if (b.type.equals("iref")) {
                iref = b;
            }
        }
        assertTrue(iinf != null && iloc != null && iref != null);
        assertEquals(5, IsoBmff.u16(iinf.payload, 4));

        // iloc: 4 extents, first two lengths match the inputs, offsets absolute.
        byte[] body = iloc.payload;
        assertEquals(5, IsoBmff.u16(body, 6));
        long ftypLen = 8 + top.get(0).payload.length;
        long metaLen = 8 + top.get(1).payload.length;
        long mdatStart = ftypLen + metaLen + 8;
        int pos = 8;
        assertEquals(1, IsoBmff.u16(body, pos));
        assertEquals(mdatStart, IsoBmff.u32(body, pos + 6));
        assertEquals(64L, IsoBmff.u32(body, pos + 10));
        pos += 14;
        assertEquals(2, IsoBmff.u16(body, pos));
        assertEquals(mdatStart + 64, IsoBmff.u32(body, pos + 6));
        assertEquals(16L, IsoBmff.u32(body, pos + 10));

        // XMP + auxC signatures land in mdat; auxl link in iref.
        String mdatText = new String(top.get(2).payload, StandardCharsets.UTF_8);
        assertTrue(mdatText.contains("hdr-gain-map"));
        String metaText = new String(top.get(1).payload, StandardCharsets.ISO_8859_1);
        assertTrue(metaText.contains("urn:iso:std:iso:ts:21496:-1"));
        assertTrue(mdatText.contains("image/heic"));
        String irefText = new String(iref.payload, StandardCharsets.ISO_8859_1);
        assertTrue(irefText.contains("auxl"));
        assertTrue(irefText.contains("cdsc"));
    }

    @Test
    public void mergeWithExifHasFiveItems() {
        byte[] exif = {'E', 'x', 'i', 'f', 0, 0, 1, 2, 3};
        byte[] out = UltraHdrHeicContainer.merge(inputs(exif));
        List<IsoBmff.Box> top = IsoBmff.parse(out);
        List<IsoBmff.Box> kids = IsoBmff.parse(top.get(1).payload, 4, top.get(1).payload.length - 4);
        for (IsoBmff.Box b : kids) {
            if (b.type.equals("iinf")) {
                assertEquals(6, IsoBmff.u16(b.payload, 4));
            }
            if (b.type.equals("iloc")) {
                assertEquals(6, IsoBmff.u16(b.payload, 6));
            }
        }
    }

    @Test
    public void payloadBytesPreserved() {
        byte[] out = UltraHdrHeicContainer.merge(inputs(null));
        List<IsoBmff.Box> top = IsoBmff.parse(out);
        byte[] mdat = top.get(2).payload;
        for (int i = 0; i < 64; i++) {
            assertEquals((byte) 0xA5, mdat[i]);
        }
        for (int i = 0; i < 16; i++) {
            assertEquals((byte) 0x5A, mdat[64 + i]);
        }
    }

    @Test
    public void multiItemInputKeepsPrimaryOnly() {
        UltraHdrHeicContainer.Inputs in = inputs(null);
        // Base file with primary + thumbnail: merge must succeed, keep the
        // primary bytes, and drop the thumbnail from the output.
        in.baseHeic = multiImageHeic(
                new byte[][]{bytes(64, (byte) 0xA5), bytes(8, (byte) 0x11)},
                new int[]{64, 8}, new int[]{48, 6},
                new byte[][]{new byte[]{1, 2, 3}, new byte[]{7, 7}},
                new String[]{"Primary", "Thumb"});
        byte[] out = UltraHdrHeicContainer.merge(in);
        List<IsoBmff.Box> top = IsoBmff.parse(out);
        List<IsoBmff.Box> kids = IsoBmff.parse(top.get(1).payload, 4, top.get(1).payload.length - 4);
        for (IsoBmff.Box b : kids) {
            if (b.type.equals("iinf")) {
                // primary + gain + 2 xmp = 4 (thumbnail dropped).
                assertEquals(5, IsoBmff.u16(b.payload, 4));
            }
            if (b.type.equals("iloc")) {
                assertEquals(5, IsoBmff.u16(b.payload, 6));
            }
        }
        byte[] mdat = top.get(2).payload;
        for (int i = 0; i < 64; i++) {
            assertEquals((byte) 0xA5, mdat[i]);
        }
        for (int i = 0; i < 16; i++) {
            assertEquals((byte) 0x5A, mdat[64 + i]);
        }
        // No thumbnail bytes anywhere in the output (XMP/EXIF are ASCII).
        for (byte v : mdat) {
            assertTrue(v != (byte) 0x11);
        }
    }

    @Test
    public void ilocV1IsAccepted() {
        // Same single image but with a v1 iloc (mp4box.js layout): merge
        // must succeed exactly as with v0.
        UltraHdrHeicContainer.Inputs in = inputs(null);
        in.baseHeic = withIlocV1(in.baseHeic);
        byte[] out = UltraHdrHeicContainer.merge(in);
        List<IsoBmff.Box> top = IsoBmff.parse(out);
        // Output iloc is always v0 with absolute offsets into output mdat.
        for (IsoBmff.Box b : IsoBmff.parse(
                top.get(1).payload, 4, top.get(1).payload.length - 4)) {
            if (b.type.equals("iloc")) {
                assertEquals(0, b.payload[0] & 0xFF);
                assertEquals(5, IsoBmff.u16(b.payload, 6));
            }
        }
        byte[] mdat = top.get(2).payload;
        for (int i = 0; i < 64; i++) {
            assertEquals((byte) 0xA5, mdat[i]);
        }
    }

    /** Rebuilds the file's single-extent iloc as v1 (mp4box.js field order). */
    private static byte[] withIlocV1(byte[] file) {
        List<IsoBmff.Box> top = IsoBmff.parse(file);
        List<byte[]> newTop = new ArrayList<>();
        for (IsoBmff.Box b : top) {
            if (!b.type.equals("meta")) {
                newTop.add(IsoBmff.buildBox(b.type, b.payload));
                continue;
            }
            List<IsoBmff.Box> kids = IsoBmff.parse(b.payload, 4, b.payload.length - 4);
            List<byte[]> newKids = new ArrayList<>();
            for (IsoBmff.Box k : kids) {
                if (!k.type.equals("iloc")) {
                    newKids.add(IsoBmff.buildBox(k.type, k.payload));
                    continue;
                }
                List<UltraHdrHeicContainer.Extent> extents =
                        UltraHdrHeicContainer.ilocExtents(k.payload, -1);
                // Rebuilding v0->v1 grows meta by 2 bytes/entry; rebase the
                // absolute offsets so they still hit mdat.
                byte[] v1box = IsoBmff.buildBox("iloc", IsoBmff.fullBoxPayload(1, 0,
                        v1IlocBody(extents)));
                long shift = (long) v1box.length - (8 + k.payload.length);
                List<UltraHdrHeicContainer.Extent> rebased = new ArrayList<>();
                for (UltraHdrHeicContainer.Extent e : extents) {
                    rebased.add(new UltraHdrHeicContainer.Extent(
                            e.itemId, e.offset + shift, e.length));
                }
                newKids.add(IsoBmff.buildBox("iloc", IsoBmff.fullBoxPayload(1, 0,
                        v1IlocBody(rebased))));
            }
            byte[] header = new byte[4];
            System.arraycopy(b.payload, 0, header, 0, 4);
            List<byte[]> metaParts = new ArrayList<>();
            metaParts.add(header);
            metaParts.add(IsoBmff.concat(newKids));
            newTop.add(IsoBmff.buildBox("meta", IsoBmff.concat(metaParts)));
        }
        return IsoBmff.concat(newTop);
    }

    /**
     * v1 iloc body in the mp4box.js-verified layout: u16 count/ids, u16
     * construction (low 4 bits), u16 data-ref, u16 extent counts.
     */
    static byte[] v1IlocBody(List<UltraHdrHeicContainer.Extent> extents) {
        java.util.Map<Integer, List<UltraHdrHeicContainer.Extent>> byId =
                new java.util.LinkedHashMap<>();
        for (UltraHdrHeicContainer.Extent e : extents) {
            if (!byId.containsKey(e.itemId)) {
                byId.put(e.itemId, new ArrayList<UltraHdrHeicContainer.Extent>());
            }
            byId.get(e.itemId).add(e);
        }
        int size = 2 + 2;
        for (List<UltraHdrHeicContainer.Extent> l : byId.values()) {
            size += 2 + 2 + 2 + 2 + l.size() * (4 + 4);
        }
        ByteBuffer bb = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) 0x44);
        bb.put((byte) 0x00); // base_offset_size=0, index_size=0
        bb.putShort((short) byId.size());
        for (java.util.Map.Entry<Integer, List<UltraHdrHeicContainer.Extent>> en
                : byId.entrySet()) {
            bb.putShort((short) (en.getKey() & 0xFFFF));
            bb.putShort((short) (en.getValue().get(0).construction & 0xF));
            bb.putShort((short) 0); // data_reference_index
            bb.putShort((short) en.getValue().size());
            for (UltraHdrHeicContainer.Extent e : en.getValue()) {
                bb.putInt((int) e.offset);
                bb.putInt((int) e.length);
            }
        }
        return bb.array();
    }

    @Test
    public void describeIncludesDiagnostics() {
        UltraHdrHeicContainer.Inputs in = inputs(null);
        String desc = UltraHdrHeicContainer.describeHeic(in.baseHeic);
        assertTrue(desc.contains("ftyp("));
        assertTrue(desc.contains("iloc(v0 hex="));
        // Synthetic files have no idat/iref; device files do.
        assertTrue(UltraHdrHeicContainer.hex(new byte[]{0x27, 0x10}, 0, 2).equals("2710"));
        assertTrue(UltraHdrHeicContainer.hex(new byte[]{0x27, 0x10}, 0, 99).equals("2710"));
        assertTrue(UltraHdrHeicContainer.hex(new byte[]{0x27}, 5, 2).equals(""));
    }

    @Test
    public void gridGraphTransmux() {
        // Mirrors the real HeifWriter layout from the device log: two tiles +
        // grid primary linked by dimg, v1 iloc.
        UltraHdrHeicContainer.Inputs in = inputs(null);
        in.baseHeic = gridHeic(bytes(64, (byte) 0xA5), bytes(16, (byte) 0x11));
        byte[] out = UltraHdrHeicContainer.merge(in);
        List<IsoBmff.Box> top = IsoBmff.parse(out);
        List<IsoBmff.Box> kids = IsoBmff.parse(top.get(1).payload, 4, top.get(1).payload.length - 4);
        int iinfCount = -1;
        int ilocCount = -1;
        boolean hasDimg = false;
        boolean hasAuxl = false;
        int pitm = -1;
        for (IsoBmff.Box b : kids) {
            if (b.type.equals("iinf")) {
                iinfCount = IsoBmff.u16(b.payload, 4);
            } else if (b.type.equals("iloc")) {
                ilocCount = IsoBmff.u16(b.payload, 6);
            } else if (b.type.equals("pitm")) {
                pitm = IsoBmff.u16(b.payload, 4);
            } else if (b.type.equals("iref")) {
                String text = new String(b.payload, StandardCharsets.ISO_8859_1);
                hasDimg = text.contains("dimg");
                hasAuxl = text.contains("auxl");
            }
        }
        // 2 tiles + grid + remapped gain + 2 xmp = 6.
        assertEquals(7, iinfCount);
        assertEquals(7, ilocCount);
        assertEquals(3, pitm);
        assertTrue(hasDimg);
        assertTrue(hasAuxl);
        byte[] mdat = top.get(2).payload;
        for (int i = 0; i < 64; i++) {
            assertEquals((byte) 0xA5, mdat[i]);
        }
        for (int i = 0; i < 16; i++) {
            assertEquals((byte) 0x11, mdat[64 + i]);
        }
        // Gain bytes (0x5A) follow the grid params.
        boolean foundGain = false;
        for (int i = 0; i + 16 <= mdat.length; i++) {
            boolean all = true;
            for (int j = 0; j < 16; j++) {
                if (mdat[i + j] != (byte) 0x5A) {
                    all = false;
                    break;
                }
            }
            if (all) {
                foundGain = true;
                break;
            }
        }
        assertTrue(foundGain);
    }

    /**
     * Two tiles (ids 1-2) + grid primary (id 3) with dimg 3-&gt;[1,2] and a v1
     * iloc with real absolute offsets (two-pass layout).
     */
    private static byte[] gridHeic(byte[] tileA, byte[] tileB) {
        return gridHeicInternal(tileA, tileB, -1);
    }

    /**
     * Device layout: tiles method-0 in mdat, grid params method-1 in an
     * {@code idat} meta child. {@code gridOffset} is the idat-relative
     * offset (0 = correct payload-relative; 8 = box-relative misreading).
     */
    private static byte[] gridHeicIdat(byte[] tileA, byte[] tileB, int gridOffset) {
        return gridHeicInternal(tileA, tileB, gridOffset);
    }

    private static byte[] gridHeicInternal(byte[] tileA, byte[] tileB, int idatGridOffset) {
        // Device-like grid params: rows-1=7, cols-1=5 (8x6 covering 48
        // tiles), output 3072x4096.
        byte[] gridParams = new byte[]{0, 0, 7, 5, 0x0c, 0, 0x10, 0};
        boolean idatGrid = idatGridOffset >= 0;
        byte[] idat = idatGrid ? IsoBmff.buildBox("idat", gridParams) : null;
        byte[] ftyp = IsoBmff.buildBox("ftyp", ByteBuffer.allocate(20)
                .order(ByteOrder.BIG_ENDIAN)
                .put("heic".getBytes(StandardCharsets.US_ASCII)).putInt(0)
                .put("heic".getBytes(StandardCharsets.US_ASCII))
                .put("mif1".getBytes(StandardCharsets.US_ASCII))
                .put("hevc".getBytes(StandardCharsets.US_ASCII)).array());
        byte[] hdlr = IsoBmff.buildBox("hdlr", IsoBmff.fullBoxPayload(0, 0,
                ByteBuffer.allocate(4 + 4 + 12 + 1).order(ByteOrder.BIG_ENDIAN)
                        .putInt(0).put("pict".getBytes(StandardCharsets.US_ASCII))
                        .put(new byte[12]).put((byte) 0).array()));
        byte[] pitm = IsoBmff.buildBox("pitm", IsoBmff.fullBoxPayload(0, 0,
                ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN)
                        .putShort((short) 3).array()));
        List<byte[]> infes = new ArrayList<>();
        infes.add(UltraHdrHeicContainer.buildInfeV2(1, "hvc1", "TileA", null));
        infes.add(UltraHdrHeicContainer.buildInfeV2(2, "hvc1", "TileB", null));
        infes.add(UltraHdrHeicContainer.buildInfeV2(3, "grid", "Grid", null));
        ByteBuffer iinfBody = ByteBuffer.allocate(2 + totalLen(infes))
                .order(ByteOrder.BIG_ENDIAN);
        iinfBody.putShort((short) 3);
        for (byte[] e : infes) {
            iinfBody.put(e);
        }
        List<byte[]> props = new ArrayList<>();
        props.add(IsoBmff.buildBox("hvcC", new byte[]{1, 2, 3}));
        props.add(IsoBmff.buildBox("ispe",
                stripFullHeader(UltraHdrHeicContainer.buildIspe(64, 48))));
        props.add(IsoBmff.buildBox("ispe",
                stripFullHeader(UltraHdrHeicContainer.buildIspe(16, 12))));
        byte[] ipco = IsoBmff.buildBox("ipco", IsoBmff.concat(props));
        // ipma: tiles -> hvcC+ispe, grid -> none.
        ByteBuffer ipmaBody = ByteBuffer.allocate(4 + 2 * (2 + 1 + 2) + (2 + 1))
                .order(ByteOrder.BIG_ENDIAN);
        ipmaBody.putInt(3);
        ipmaBody.putShort((short) 1);
        ipmaBody.put((byte) 2);
        ipmaBody.put((byte) 0x81);
        ipmaBody.put((byte) 0x02);
        ipmaBody.putShort((short) 2);
        ipmaBody.put((byte) 2);
        ipmaBody.put((byte) 0x81);
        ipmaBody.put((byte) 0x03);
        ipmaBody.putShort((short) 3);
        ipmaBody.put((byte) 0);
        byte[] ipma = IsoBmff.buildBox("ipma",
                IsoBmff.fullBoxPayload(0, 0, ipmaBody.array()));
        byte[] iprp = IsoBmff.buildBox("iprp", IsoBmff.concat(listOf(ipco, ipma)));
        // iref v0: dimg 3->[1,2].
        ByteBuffer dimg = ByteBuffer.allocate(2 + 2 + 2 * 2).order(ByteOrder.BIG_ENDIAN);
        dimg.putShort((short) 3);
        dimg.putShort((short) 2);
        dimg.putShort((short) 1);
        dimg.putShort((short) 2);
        byte[] iref = IsoBmff.buildBox("iref", IsoBmff.fullBoxPayload(0, 0,
                IsoBmff.concat(listOf(IsoBmff.buildBox("dimg", dimg.array())))));
        // Two-pass iloc with real offsets.
        List<UltraHdrHeicContainer.Extent> dummy = new ArrayList<>();
        dummy.add(new UltraHdrHeicContainer.Extent(1, 0, 0));
        dummy.add(new UltraHdrHeicContainer.Extent(2, 0, 0));
        dummy.add(new UltraHdrHeicContainer.Extent(3, 0, 0, idatGrid ? 1 : 0));
        byte[] metaPass1 = assembleMeta(hdlr, pitm, dummy, iinfBody.array(), iprp, iref,
                idatGrid ? idat : null);
        long mdatStart = ftyp.length + metaPass1.length + 8;
        List<UltraHdrHeicContainer.Extent> extents = new ArrayList<>();
        extents.add(new UltraHdrHeicContainer.Extent(1, mdatStart, tileA.length));
        extents.add(new UltraHdrHeicContainer.Extent(2, mdatStart + tileA.length, tileB.length));
        if (idatGrid) {
            extents.add(new UltraHdrHeicContainer.Extent(3, idatGridOffset, gridParams.length, 1));
        } else {
            extents.add(new UltraHdrHeicContainer.Extent(3,
                    mdatStart + tileA.length + tileB.length, gridParams.length));
        }
        byte[] meta = assembleMeta(hdlr, pitm, extents, iinfBody.array(), iprp, iref,
                idatGrid ? idat : null);
        byte[] mdat = IsoBmff.buildBox("mdat", idatGrid
                ? IsoBmff.concat(listOf(tileA, tileB))
                : IsoBmff.concat(listOf(tileA, tileB, gridParams)));
        return IsoBmff.concat(listOf(ftyp, meta, mdat));
    }

    private static byte[] assembleMeta(byte[] hdlr, byte[] pitm,
            List<UltraHdrHeicContainer.Extent> extents, byte[] iinfBody, byte[] iprp,
            byte[] iref) {
        return assembleMeta(hdlr, pitm, extents, iinfBody, iprp, iref, null);
    }

    private static byte[] assembleMeta(byte[] hdlr, byte[] pitm,
            List<UltraHdrHeicContainer.Extent> extents, byte[] iinfBody, byte[] iprp,
            byte[] iref, byte[] idat) {
        byte[] iloc = IsoBmff.buildBox("iloc",
                IsoBmff.fullBoxPayload(1, 0, v1IlocBody(extents)));
        byte[] iinf = IsoBmff.buildBox("iinf",
                IsoBmff.fullBoxPayload(0, 0, iinfBody));
        List<byte[]> metaKids = new ArrayList<>();
        metaKids.add(hdlr);
        metaKids.add(pitm);
        metaKids.add(iloc);
        metaKids.add(iinf);
        metaKids.add(iprp);
        if (idat != null) {
            metaKids.add(idat);
        }
        if (iref != null) {
            metaKids.add(iref);
        }
        return IsoBmff.buildBox("meta", IsoBmff.concat(listOf(
                IsoBmff.fullBoxPayload(0, 0, null), IsoBmff.concat(metaKids))));
    }

    @Test
    public void idatGridTransmux() {
        // Exact device layout: tiles method-0 in mdat, grid method-1 in idat.
        UltraHdrHeicContainer.Inputs in = inputs(null);
        in.baseHeic = gridHeicIdat(bytes(64, (byte) 0xA5), bytes(16, (byte) 0x11), 0);
        byte[] out = UltraHdrHeicContainer.merge(in);
        List<IsoBmff.Box> top = IsoBmff.parse(out);
        // No idat in the output: everything is normalized into mdat.
        for (IsoBmff.Box b : top) {
            assertTrue(!b.type.equals("idat"));
        }
        List<IsoBmff.Box> kids = IsoBmff.parse(top.get(1).payload, 4, top.get(1).payload.length - 4);
        int iinfCount = -1;
        int pitm = -1;
        for (IsoBmff.Box b : kids) {
            if (b.type.equals("iinf")) {
                iinfCount = IsoBmff.u16(b.payload, 4);
            } else if (b.type.equals("pitm")) {
                pitm = IsoBmff.u16(b.payload, 4);
            }
        }
        assertEquals(7, iinfCount);
        assertEquals(3, pitm);
        byte[] mdat = top.get(2).payload;
        for (int i = 0; i < 64; i++) {
            assertEquals((byte) 0xA5, mdat[i]);
        }
        for (int i = 0; i < 16; i++) {
            assertEquals((byte) 0x11, mdat[64 + i]);
        }
        // Grid params relocated right after the tiles.
        assertEquals((byte) 7, mdat[80 + 2]);
        assertEquals((byte) 5, mdat[80 + 3]);
    }

    @Test
    public void gridBoundaryCoversExactTileCount() {
        // Device case: 8x6 grid params covering exactly 48 sibling tiles.
        // tileCount includes the grid itself, so the check must compare
        // against tileCount-1 (regression: rows*cols < tileCount misfired).
        UltraHdrHeicContainer.Meta m = new UltraHdrHeicContainer.Meta();
        m.infeTypes.put(10048, "grid");
        byte[] params = new byte[]{0, 0, 7, 5, 0x0c, 0, 0x10, 0};
        UltraHdrHeicContainer.checkPayloadPlausible(m, 10048, params, 49, "test");
        try {
            UltraHdrHeicContainer.checkPayloadPlausible(m, 10048, params, 50, "test");
            fail("expected rejection when grid covers fewer tiles");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("tiles=49"));
        }
    }

    @Test
    public void tmapItemDualEncoded() {
        // ISO 21496-1 tmap item alongside the hdrgm XMP, following the
        // reference recipe: GMap-named infe, dimg tmap->[primary, gain],
        // ispe/pixi/colr properties, altr group scoping, tmap ftyp brand.
        UltraHdrHeicContainer.Inputs in = inputs(null);
        in.gainMapMin = 0f;
        in.gainMapMax = 2.5f;
        in.hdrCapacityMax = 2.5f;
        byte[] out = UltraHdrHeicContainer.merge(in);
        List<IsoBmff.Box> top = IsoBmff.parse(out);
        // ftyp carries the tmap compatible brand.
        String ftypText = new String(top.get(0).payload, StandardCharsets.ISO_8859_1);
        assertTrue(ftypText.contains("tmap"));
        List<IsoBmff.Box> kids = IsoBmff.parse(top.get(1).payload, 4, top.get(1).payload.length - 4);
        int tmapId = -1;
        int gainId = -1;
        for (IsoBmff.Box b : kids) {
            if (!b.type.equals("iinf")) {
                continue;
            }
            int v = IsoBmff.fullVersion(b.payload);
            int cs = v == 0 ? 2 : 4;
            byte[] region = new byte[b.payload.length - 4 - cs];
            System.arraycopy(b.payload, 4 + cs, region, 0, region.length);
            for (IsoBmff.Box e : IsoBmff.parse(region)) {
                int ev = IsoBmff.fullVersion(e.payload);
                int id = ev >= 3 ? (int) IsoBmff.u32(e.payload, 4) : IsoBmff.u16(e.payload, 4);
                String t = IsoBmff.fourcc(e.payload, 8);
                if (t.equals("tmap")) {
                    tmapId = id;
                }
                if (t.equals("hvc1") && id != 1) {
                    gainId = id;
                }
            }
        }
        assertTrue(tmapId > 0);
        assertTrue(gainId > 0);
        // dimg tmap->[primary, gain] present (reference order).
        boolean dimgOk = false;
        for (IsoBmff.Box b : kids) {
            if (!b.type.equals("iref")) {
                continue;
            }
            String text = new String(b.payload, StandardCharsets.ISO_8859_1);
            assertTrue(text.contains("auxl"));
            List<UltraHdrHeicContainer.IrefEntry> entries =
                    UltraHdrHeicContainer.parseIrefEntries(b.payload,
                            IsoBmff.fullVersion(b.payload));
            for (UltraHdrHeicContainer.IrefEntry e : entries) {
                if (e.type.equals("dimg") && e.from == tmapId && e.to.size() == 2
                        && e.to.get(0) == 1 && e.to.get(1) == gainId) {
                    dimgOk = true;
                }
            }
        }
        assertTrue(dimgOk);
        // tmap ipma entry: ispe sized to the base photo, pixi 10-bit, colr.
        boolean tmapPropsOk = false;
        for (IsoBmff.Box b : kids) {
            if (!b.type.equals("iprp")) {
                continue;
            }
            List<IsoBmff.Box> iprpKids = IsoBmff.parse(b.payload);
            List<IsoBmff.Box> ipco = null;
            byte[] ipma = null;
            for (IsoBmff.Box k : iprpKids) {
                if (k.type.equals("ipco")) {
                    ipco = IsoBmff.parse(k.payload);
                } else if (k.type.equals("ipma")) {
                    ipma = k.payload;
                }
            }
            assertTrue(ipco != null && ipma != null);
            UltraHdrHeicContainer.Meta probe = new UltraHdrHeicContainer.Meta();
            probe.ipmaPayload = ipma;
            UltraHdrHeicContainer.parseIpmaAssoc(probe);
            List<UltraHdrHeicContainer.PropRef> refs = probe.ipmaAssoc.get(tmapId);
            assertTrue(refs != null && refs.size() == 3);
            boolean ispeOk = false;
            boolean pixiOk = false;
            boolean colrOk = false;
            for (UltraHdrHeicContainer.PropRef r : refs) {
                IsoBmff.Box prop = ipco.get(r.index - 1);
                if (prop.type.equals("ispe")) {
                    ispeOk = IsoBmff.u32(prop.payload, 4) == 64
                            && IsoBmff.u32(prop.payload, 8) == 48;
                } else if (prop.type.equals("pixi")) {
                    pixiOk = prop.payload.length == 8 && prop.payload[4] == 3
                            && prop.payload[5] == 10 && prop.payload[6] == 10
                            && prop.payload[7] == 10;
                } else if (prop.type.equals("colr")) {
                    colrOk = prop.payload.length == 15;
                }
            }
            tmapPropsOk = ispeOk && pixiOk && colrOk;
        }
        assertTrue(tmapPropsOk);
        // altr group scoping [tmap, primary].
        boolean altrOk = false;
        for (IsoBmff.Box b : kids) {
            if (!b.type.equals("grpl")) {
                continue;
            }
            for (IsoBmff.Box g : IsoBmff.parse(b.payload)) {
                if (g.type.equals("altr") && IsoBmff.u32(g.payload, 8) == 2
                        && IsoBmff.u32(g.payload, 12) == tmapId
                        && IsoBmff.u32(g.payload, 16) == 1) {
                    altrOk = true;
                }
            }
        }
        assertTrue(altrOk);
        // tmap payload: version 0 + ISO fields matching the inputs.
        byte[] tmapBytes = null;
        List<UltraHdrHeicContainer.Extent> extents =
                UltraHdrHeicContainer.ilocExtents(
                        findBox(kids, "iloc").payload, tmapId);
        assertEquals(1, extents.size());
        byte[] mdat = top.get(2).payload;
        long ftypLen = 8 + top.get(0).payload.length;
        long metaLen = 8 + top.get(1).payload.length;
        long mdatStart = ftypLen + metaLen + 8;
        tmapBytes = new byte[(int) extents.get(0).length];
        System.arraycopy(mdat, (int) (extents.get(0).offset - mdatStart), tmapBytes, 0,
                tmapBytes.length);
        assertEquals(62, tmapBytes.length);
        assertEquals(0, tmapBytes[0]); // tmap version
        assertEquals(0x40, tmapBytes[5] & 0xFF); // flags: base space, single
        ByteBuffer bb = ByteBuffer.wrap(tmapBytes).order(ByteOrder.BIG_ENDIAN);
        assertEquals(5, bb.getInt(14)); // altN
        assertEquals(2, bb.getInt(18)); // altD
        assertEquals(5, bb.getInt(30)); // maxN
        assertEquals(2, bb.getInt(34)); // maxD
        assertEquals(64, bb.getInt(50)); // baseOffD
    }

    private static IsoBmff.Box findBox(List<IsoBmff.Box> boxes, String type) {
        for (IsoBmff.Box b : boxes) {
            if (b.type.equals(type)) {
                return b;
            }
        }
        throw new IllegalArgumentException("missing " + type);
    }

    @Test
    public void boxRelativeIdatOffsetRejected() {
        // A box-relative reading of the grid offset (8 instead of 0) runs
        // past the 8-byte idat payload and must fail closed, not corrupt.
        UltraHdrHeicContainer.Inputs in = inputs(null);
        in.baseHeic = gridHeicIdat(bytes(64, (byte) 0xA5), bytes(16, (byte) 0x11), 8);
        try {
            UltraHdrHeicContainer.merge(in);
            fail("expected rejection of out-of-range idat extent");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("idat"));
        }
    }

    @Test
    public void exifBlobExtraction() {
        byte[] exif = {'E', 'x', 'i', 'f', 0, 0, 0x11, 0x22};
        ByteBuffer app1 = ByteBuffer.allocate(2 + exif.length).order(ByteOrder.BIG_ENDIAN);
        app1.putShort((short) (2 + exif.length));
        app1.put(exif);
        byte[] jpeg = IsoBmff.concat(listOf(
                new byte[]{(byte) 0xFF, (byte) 0xD8},
                new byte[]{(byte) 0xFF, (byte) 0xE1},
                app1.array(),
                new byte[]{(byte) 0xFF, (byte) 0xD9}));
        assertArrayEquals(exif, ExifBlob.extractExifPayload(jpeg));
        assertEquals(null, ExifBlob.extractExifPayload(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9}));
    }
}
