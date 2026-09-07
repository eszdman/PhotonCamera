package com.particlesdevs.photoncamera.processing.encoder;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles an Ultra HDR (ISO 21496-1) HEIF file from two single-image HEIC
 * payloads — the SDR base and the normalized gain map — plus XMP/EXIF items.
 *
 * <p>Manual BMFF mux in the spirit of {@code UltraHdrContainer} (no native
 * dependency): {@code HeifWriter} supplies the hardware HEVC encoding, this
 * class supplies the multi-item {@code meta} box (primary + auxiliary
 * gain-map item with {@code auxC urn:iso:std:iso:ts:21496:-1}, {@code auxl} /
 * {@code cdsc} references, XMP and EXIF items) with rebased {@code iloc}
 * extents. Only single-item inputs are accepted; anything unexpected throws
 * so callers fall back to SDR HEIC.
 *
 * <p>API 34+ only (see {@link HeicSupport#isUltraHdrHeicSupported}).
 */
public final class UltraHdrHeicContainer {

    private static final String AUX_TYPE_21496 = "urn:iso:std:iso:ts:21496:-1";
    private static final String XMP_MIME = "application/rdf+xml";

    private UltraHdrHeicContainer() {}

    public static final class Inputs {
        public byte[] baseHeic;
        public byte[] gainHeic;
        public int baseW;
        public int baseH;
        public int gainW;
        public int gainH;
        public float gainMapMin;
        public float gainMapMax;
        public float hdrCapacityMax;
        /** Raw {@code Exif\0\0 + TIFF} payload, may be null. */
        public byte[] exifPayload;
    }

    public static byte[] merge(Inputs in) {
        if (in == null || in.baseHeic == null || in.gainHeic == null) {
            throw new IllegalArgumentException("Null HEIC input");
        }
        List<IsoBmff.Box> baseTop = IsoBmff.parse(in.baseHeic);
        List<IsoBmff.Box> gainTop = IsoBmff.parse(in.gainHeic);
        IsoBmff.Box baseFtyp = findRequired(baseTop, "ftyp");
        IsoBmff.Box baseMeta = findRequired(baseTop, "meta");
        findRequired(baseTop, "mdat");
        IsoBmff.Box gainMeta = findRequired(gainTop, "meta");
        findRequired(gainTop, "mdat");

        Meta base;
        Meta gain;
        try {
            base = Meta.parse(baseMeta.payload);
            gain = Meta.parse(gainMeta.payload);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unparseable HEIF meta: " + e.getMessage()
                    + " base=" + describeHeic(in.baseHeic)
                    + " gain=" + describeHeic(in.gainHeic), e);
        }
        // Keep sets: primaries plus everything derived via dimg (grid
        // tiles). Real encoders may add thumbnails etc.; those are dropped.
        java.util.LinkedHashSet<Integer> baseKeep;
        java.util.LinkedHashSet<Integer> gainKeep;
        java.util.Map<Integer, byte[]> baseKept;
        java.util.Map<Integer, byte[]> gainKept;
        try {
            baseKeep = computeKeepSet(base);
            gainKeep = computeKeepSet(gain);
            baseKept = sliceKeptPayloads(in.baseHeic, base, baseKeep, "base");
            gainKept = sliceKeptPayloads(in.gainHeic, gain, gainKeep, "gain");
        } catch (Exception e) {
            throw new IllegalArgumentException("Keep-set slice failed: " + e.getMessage()
                    + " base=" + describeHeic(in.baseHeic)
                    + " gain=" + describeHeic(in.gainHeic), e);
        }

        int primaryId = base.primaryItemId;
        int gainGridOld = gain.primaryItemId;
        int freshStart = Math.max(base.maxItemId, gain.maxItemId) + 1;

        // Property indexes in the merged ipco: base children keep their order
        // (kept ipma indexes stay valid), gain extras are appended.
        List<IsoBmff.Box> baseIpco = base.ipcoChildren;
        int basePropCount = baseIpco.size();
        GainGraph gg;
        try {
            gg = remapGainGraph(gain, gainKeep, freshStart, basePropCount);
        } catch (Exception e) {
            throw new IllegalArgumentException("Gain remap failed: " + e.getMessage()
                    + " gain=" + describeHeic(in.gainHeic), e);
        }
        byte[] auxC = buildAuxC(AUX_TYPE_21496);
        int auxCIndex = basePropCount + gg.propBoxes.size() + 1;
        // The aux (gain grid) entry: original remapped associations + auxC,
        // plus an ispe when the grid carries none.
        List<PropRef> gridAssoc = new ArrayList<>();
        List<PropRef> gridOrig = gain.ipmaAssoc.get(gainGridOld);
        Integer auxIspeIndex = null;
        if (gridOrig != null) {
            for (PropRef r : gridOrig) {
                Integer mapped = gg.propRemap.get(r.index);
                if (mapped == null) {
                    throw new IllegalArgumentException("grid prop not staged");
                }
                gridAssoc.add(new PropRef(r.essential, mapped));
                if (auxIspeIndex == null
                        && gain.ipcoChildren.get(r.index - 1).type.equals("ispe")) {
                    auxIspeIndex = mapped;
                }
            }
        }
        byte[] auxIspe = null;
        if (auxIspeIndex == null) {
            auxIspe = buildIspe(in.gainW, in.gainH);
            auxIspeIndex = auxCIndex + 1;
            gridAssoc.add(new PropRef(false, auxIspeIndex));
        }
        gridAssoc.add(new PropRef(true, auxCIndex)); // auxC is essential
        int nextFresh = freshStart + gg.freshOrder.size();
        int gainGridNew = gg.gridNewId;
        int xmpPrimaryId = nextFresh++;
        int xmpGainId = nextFresh++;
        int exifId = -1;
        if (in.exifPayload != null) {
            exifId = nextFresh++;
        }
        // ISO 21496-1 binary metadata as a tmap derived item (dual-encoded
        // with the hdrgm XMP, like iOS 18 files): version byte + binary blob.
        byte[] tmapPayload = Iso21496Writer.tmapPayload(
                in.gainMapMin, in.gainMapMax, in.hdrCapacityMax);
        int tmapId = nextFresh++;
        int altrGroupId = nextFresh++; // unused id scoping tmap<->base
        // Output iloc/ipma (v0) and rebuilt refs need u16 ids; real encoder
        // ids are ~10000 so this only trips on pathological inputs.
        java.util.List<Integer> allNewIds = new ArrayList<>(gg.freshOrder);
        allNewIds.add(xmpPrimaryId);
        allNewIds.add(xmpGainId);
        allNewIds.add(tmapId);
        if (exifId >= 0) {
            allNewIds.add(exifId);
        }
        for (int id : allNewIds) {
            if (id > 0xFFFF) {
                throw new IllegalArgumentException("fresh item id too large: " + id);
            }
        }
        for (int id : baseKeep) {
            if (id > 0xFFFF) {
                throw new IllegalArgumentException("base item id too large for v0: " + id);
            }
        }

        byte[] xmpPrimary = buildPrimaryXmp(xmpGainPayloadLength(in));
        byte[] xmpGain = buildGainMapXmp(in.gainMapMin, in.gainMapMax, in.hdrCapacityMax);

        // mdat layout: kept base payloads, kept gain payloads, XMP/EXIF.
        // Base keeps original IDs; gain keeps fresh IDs.
        List<byte[]> mdatParts = new ArrayList<>();
        List<Integer> baseOrder = new ArrayList<>(baseKeep);
        java.util.Collections.sort(baseOrder);
        for (int id : baseOrder) {
            mdatParts.add(baseKept.get(id));
        }
        for (int fresh : gg.freshOrder) {
            mdatParts.add(gainKept.get(reverseLookup(gg.idRemap, fresh)));
        }
        mdatParts.add(xmpPrimary);
        mdatParts.add(xmpGain);
        mdatParts.add(tmapPayload);
        byte[] exifItemPayload = null;
        if (in.exifPayload != null) {
            ByteBuffer eb = ByteBuffer.allocate(4 + in.exifPayload.length).order(ByteOrder.BIG_ENDIAN);
            eb.putInt(0);
            eb.put(in.exifPayload);
            exifItemPayload = eb.array();
            mdatParts.add(exifItemPayload);
        }

        // New ipco = base children + gain referenced props + auxC [+ aux ispe].
        List<byte[]> ipcoBoxes = new ArrayList<>();
        for (IsoBmff.Box b : baseIpco) {
            ipcoBoxes.add(IsoBmff.buildBox(b.type, b.payload));
        }
        ipcoBoxes.addAll(gg.propBoxes);
        ipcoBoxes.add(IsoBmff.buildBox("auxC", auxC));
        if (auxIspe != null) {
            ipcoBoxes.add(IsoBmff.buildBox("ispe", auxIspe));
        }
        // tmap properties per the reference recipe: ispe sized to the BASE
        // photo, pixi 10-bit, and the derived-HDR nclx colr. Readers check
        // the tmap ispe; the colr describes the derived rendition
        // (linear-light BT.709, full range — matching our gain math space).
        int propCursor = basePropCount + (ipcoBoxes.size() - baseIpco.size());
        byte[] tmapIspe = buildIspe(in.baseW, in.baseH);
        int tmapIspeIndex = propCursor + 1;
        byte[] tmapPixi = IsoBmff.fullBoxPayload(0, 0, buildPixiBody(10, 10, 10));
        int tmapPixiIndex = propCursor + 2;
        byte[] tmapColr = IsoBmff.fullBoxPayload(0, 0, buildNclxBody(1, 8, 1, true));
        int tmapColrIndex = propCursor + 3;
        ipcoBoxes.add(IsoBmff.buildBox("ispe", tmapIspe));
        ipcoBoxes.add(IsoBmff.buildBox("pixi", tmapPixi));
        ipcoBoxes.add(IsoBmff.buildBox("colr", tmapColr));
        byte[] newIpco = IsoBmff.buildBox("ipco", IsoBmff.concat(ipcoBoxes));
        List<Integer> tmapPropIdx = new ArrayList<>();
        tmapPropIdx.add(tmapIspeIndex);
        tmapPropIdx.add(tmapPixiIndex);
        tmapPropIdx.add(tmapColrIndex);

        // New ipma = kept base entries + kept gain entries (remapped) + aux grid
        // entry + tmap entry (descriptive props) + (XMP/EXIF need none).
        byte[] newIpma = extendIpma(base, baseKeep, gg, gridAssoc, tmapId, tmapPropIdx);

        // New iinf = kept base entries + remapped gain entries + new.
        List<byte[]> keptInfe = new ArrayList<>();
        for (int id : baseOrder) {
            byte[] infe = infeBoxFor(base, id);
            if (infe == null) {
                throw new IllegalArgumentException("base infe missing for kept item " + id);
            }
            keptInfe.add(infe);
        }
        for (int fresh : gg.freshOrder) {
            keptInfe.add(gg.infeBoxes.get(fresh));
        }
        List<byte[]> newInfe = new ArrayList<>();
        newInfe.add(buildInfeV2(xmpPrimaryId, "mime", "", XMP_MIME));
        newInfe.add(buildInfeV2(xmpGainId, "mime", "", XMP_MIME));
        newInfe.add(buildInfeV2(tmapId, "tmap", "GMap", null));
        if (in.exifPayload != null) {
            newInfe.add(buildInfeV2(exifId, "Exif", "", null));
        }
        byte[] newIinf = buildIinf(base.iinfPayload, keptInfe, newInfe);

        // New iref = kept base refs + remapped gain dimg + auxl/cdsc links,
        // all rebuilt at one version (v1 when base used v1; ids fit u16).
        int outRefVersion = base.irefVersion;
        List<byte[]> extraRefs = new ArrayList<>();
        for (IrefEntry e : gg.dimg) {
            extraRefs.add(buildSingleRef(e.type, e.from,
                    toIntArray(e.to), outRefVersion));
        }
        extraRefs.add(buildSingleRef("auxl", gainGridNew, new int[]{primaryId}, outRefVersion));
        extraRefs.add(buildSingleRef("cdsc", xmpPrimaryId, new int[]{primaryId}, outRefVersion));
        extraRefs.add(buildSingleRef("cdsc", xmpGainId, new int[]{gainGridNew}, outRefVersion));
        // tmap derivation inputs: primary first, gain second (reference order).
        extraRefs.add(buildSingleRef("dimg", tmapId,
                new int[]{primaryId, gainGridNew}, outRefVersion));
        if (in.exifPayload != null) {
            extraRefs.add(buildSingleRef("cdsc", exifId, new int[]{primaryId}, outRefVersion));
        }
        byte[] newIref = extendIref(filterIref(base, baseKeep, outRefVersion), extraRefs,
                outRefVersion);

        // Placeholder meta to measure, then real iloc with absolute offsets.
        // Order: hdlr, pitm, iloc, iinf, iref, iprp.
        // The iloc body size depends only on the extent count (14 bytes per
        // v0 entry), so a zeroed body of the final count measures exactly.
        byte[] metaHeader = base.metaFullHeader;
        byte[] ftypBox = withTmapBrand(baseFtyp);
        long ftypSize = ftypBox.length;
        int extentCount = baseOrder.size() + gg.freshOrder.size() + 3
                + (exifItemPayload != null ? 1 : 0);
        List<Extent> zeroExtents = new ArrayList<>();
        for (int i = 0; i < extentCount; i++) {
            zeroExtents.add(new Extent(0, 0, 0));
        }
        byte[] zeroIloc = IsoBmff.buildBox("iloc",
                IsoBmff.fullBoxPayload(0, 0, buildIlocBody(zeroExtents)));
        // iprp wrapper: 'iprp' box containing ipco + ipma.
        byte[] iprpBox = IsoBmff.buildBox("iprp", IsoBmff.concat(listOf(newIpco, newIpma)));
        // altr group scoping tmap<->base (readers may ignore tmap outside it).
        byte[] grplBox = buildAltrGroup(altrGroupId, new int[]{tmapId, primaryId});
        long metaBoxSize = 8 + 4 + base.hdlr.length + base.pitm.length
                + zeroIloc.length + newIinf.length + newIref.length + iprpBox.length
                + grplBox.length;
        long mdatDataStart = ftypSize + metaBoxSize + 8;
        long cursor = mdatDataStart;
        List<Extent> extents = new ArrayList<>();
        for (int id : baseOrder) {
            byte[] payload = baseKept.get(id);
            extents.add(new Extent(id, cursor, payload.length));
            cursor += payload.length;
        }
        for (int fresh : gg.freshOrder) {
            byte[] payload = gainKept.get(reverseLookup(gg.idRemap, fresh));
            extents.add(new Extent(fresh, cursor, payload.length));
            cursor += payload.length;
        }
        extents.add(new Extent(xmpPrimaryId, cursor, xmpPrimary.length));
        cursor += xmpPrimary.length;
        extents.add(new Extent(xmpGainId, cursor, xmpGain.length));
        cursor += xmpGain.length;
        extents.add(new Extent(tmapId, cursor, tmapPayload.length));
        cursor += tmapPayload.length;
        if (exifItemPayload != null) {
            extents.add(new Extent(exifId, cursor, exifItemPayload.length));
        }
        byte[] ilocBox = IsoBmff.buildBox("iloc",
                IsoBmff.fullBoxPayload(0, 0, buildIlocBody(extents)));

        List<byte[]> metaChildren = new ArrayList<>();
        metaChildren.add(base.hdlr);
        metaChildren.add(base.pitm);
        metaChildren.add(ilocBox);
        metaChildren.add(newIinf);
        metaChildren.add(newIref);
        metaChildren.add(iprpBox);
        // altr group scoping tmap<->base (readers may ignore tmap outside it).
        metaChildren.add(grplBox);
        byte[] metaBox = IsoBmff.buildBox("meta", IsoBmff.concat(
                listOf(metaHeader, IsoBmff.concat(metaChildren))));
        byte[] mdatBox = IsoBmff.buildBox("mdat", IsoBmff.concat(mdatParts));

        ByteArrayOutputStream out = new ByteArrayOutputStream(
                (int) (ftypSize + metaBox.length + mdatBox.length));
        try {
            out.write(ftypBox, 0, ftypBox.length);
            out.write(metaBox, 0, metaBox.length);
            out.write(mdatBox, 0, mdatBox.length);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------
    // Parsed meta helper
    // ------------------------------------------------------------------

    /** One ipma association: property index (1-based into ipco) + essential. */
    static final class PropRef {
        final boolean essential;
        final int index;

        PropRef(boolean essential, int index) {
            this.essential = essential;
            this.index = index;
        }
    }

    static final class Meta {
        byte[] metaFullHeader; // 4-byte version/flags
        byte[] hdlr; // full box bytes
        byte[] pitm; // full box bytes
        byte[] iinfPayload; // FullBox payload (version/flags + entries)
        byte[] irefPayload; // FullBox payload, may be null
        int irefVersion;
        int irefFlags;
        byte[] ipmaPayload; // FullBox payload (version/flags + entries)
        int ipmaVersion;
        int ipmaFlags;
        /** itemId -> associations in entry order. */
        java.util.Map<Integer, java.util.List<PropRef>> ipmaAssoc =
                new java.util.HashMap<>();
        byte[] ilocPayload; // FullBox payload
        /** itemId -> infe item_type (hvc1/grid/mime/...). */
        java.util.Map<Integer, String> infeTypes = new java.util.HashMap<>();
        List<IsoBmff.Box> ipcoChildren;
        int primaryItemId;
        int maxItemId;
        int itemCount;

        static Meta parse(byte[] metaPayload) {
            if (metaPayload == null || metaPayload.length < 4) {
                throw new IllegalArgumentException("Truncated meta box");
            }
            Meta m = new Meta();
            m.metaFullHeader = new byte[4];
            System.arraycopy(metaPayload, 0, m.metaFullHeader, 0, 4);
            List<IsoBmff.Box> kids = IsoBmff.parse(metaPayload, 4, metaPayload.length - 4);
            IsoBmff.Box hdlr = required(kids, "hdlr");
            IsoBmff.Box pitm = required(kids, "pitm");
            IsoBmff.Box iloc = required(kids, "iloc");
            IsoBmff.Box iinf = required(kids, "iinf");
            IsoBmff.Box iprp = required(kids, "iprp");
            m.hdlr = IsoBmff.buildBox(hdlr.type, hdlr.payload);
            m.pitm = IsoBmff.buildBox(pitm.type, pitm.payload);
            m.iinfPayload = iinf.payload;
            List<IsoBmff.Box> iprpKids = IsoBmff.parse(iprp.payload);
            IsoBmff.Box ipco = required(iprpKids, "ipco");
            IsoBmff.Box ipma = required(iprpKids, "ipma");
            m.ipcoChildren = IsoBmff.parse(ipco.payload);
            m.ipmaPayload = ipma.payload;
            m.ilocPayload = iloc.payload;
            parseIpmaAssoc(m);
            int irefIdx = IsoBmff.indexOfType(kids, "iref");
            if (irefIdx >= 0) {
                IsoBmff.Box irefBox = kids.get(irefIdx);
                // extendIref expects the FullBox *payload*.
                m.irefPayload = irefBox.payload;
                m.irefVersion = IsoBmff.fullVersion(irefBox.payload);
                m.irefFlags = fullFlags(irefBox.payload);
            } else {
                m.irefPayload = null;
                m.irefVersion = 0;
                m.irefFlags = 0;
            }
            int pitmVersion = IsoBmff.fullVersion(pitm.payload);
            if (pitmVersion == 0) {
                m.primaryItemId = IsoBmff.u16(pitm.payload, 4);
            } else {
                m.primaryItemId = (int) IsoBmff.u32(pitm.payload, 4);
            }
            int iinfVersion = IsoBmff.fullVersion(iinf.payload);
            int countSize = iinfVersion == 0 ? 2 : 4;
            int count = iinfVersion == 0
                    ? IsoBmff.u16(iinf.payload, 4)
                    : (int) IsoBmff.u32(iinf.payload, 4);
            m.itemCount = count;
            byte[] entries = new byte[iinf.payload.length - 4 - countSize];
            System.arraycopy(iinf.payload, 4 + countSize, entries, 0, entries.length);
            int maxId = m.primaryItemId;
            for (IsoBmff.Box e : IsoBmff.parse(entries)) {
                if (!e.type.equals("infe")) {
                    continue;
                }
                int v = IsoBmff.fullVersion(e.payload);
                int id = (v >= 3) ? (int) IsoBmff.u32(e.payload, 4) : IsoBmff.u16(e.payload, 4);
                if (id > maxId) {
                    maxId = id;
                }
                if (e.payload.length >= 12) {
                    m.infeTypes.put(id, IsoBmff.fourcc(e.payload, 8));
                }
            }
            m.maxItemId = maxId;
            return m;
        }

        private static IsoBmff.Box required(List<IsoBmff.Box> kids, String type) {
            for (IsoBmff.Box b : kids) {
                if (b.type.equals(type)) {
                    return b;
                }
            }
            throw new IllegalArgumentException("Missing meta child: " + type);
        }
    }

    static Meta parseMeta(byte[] metaPayload) {
        return Meta.parse(metaPayload);
    }

    static final class Extent {
        final int itemId;
        final long offset;
        final long length;
        /** iloc construction_method: 0 = file offsets, 1 = idat-relative. */
        final int construction;

        Extent(int itemId, long offset, long length) {
            this(itemId, offset, length, 0);
        }

        Extent(int itemId, long offset, long length, int construction) {
            this.itemId = itemId;
            this.offset = offset;
            this.length = length;
            this.construction = construction;
        }
    }

    /** v0 iloc body (offset_size=4, length_size=4, base_offset_size=0). */
    static byte[] buildIlocBody(List<Extent> extents) {
        int size = 2 + 2;
        for (Extent e : extents) {
            size += 2 + 2 + 2 + 4 + 4;
        }
        ByteBuffer bb = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) 0x44); // offset_size=4, length_size=4
        bb.put((byte) 0x00); // base_offset_size=0
        IsoBmff.putU16(bb, extents.size());
        for (Extent e : extents) {
            IsoBmff.putU16(bb, e.itemId);
            IsoBmff.putU16(bb, 0); // data_reference_index
            IsoBmff.putU16(bb, 1); // extent_count
            IsoBmff.putU32(bb, e.offset);
            IsoBmff.putU32(bb, e.length);
        }
        return bb.array();
    }

    static byte[] buildInfeV2(int itemId, String type, String name, String contentType) {
        byte[] nameBytes = (name + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] tail = nameBytes;
        if ("mime".equals(type) && contentType != null) {
            byte[] ct = (contentType + "\0").getBytes(StandardCharsets.UTF_8);
            byte[] enc = new byte[]{0};
            ByteBuffer tb = ByteBuffer.allocate(nameBytes.length + ct.length + enc.length);
            tb.put(nameBytes);
            tb.put(ct);
            tb.put(enc);
            tail = tb.array();
        }
        ByteBuffer bb = ByteBuffer.allocate(2 + 2 + 4 + tail.length).order(ByteOrder.BIG_ENDIAN);
        IsoBmff.putU16(bb, itemId);
        IsoBmff.putU16(bb, 0); // protection index
        bb.put(IsoBmff.fourccBytes(type));
        bb.put(tail);
        return IsoBmff.buildBox("infe", IsoBmff.fullBoxPayload(2, 0, bb.array()));
    }

    static byte[] buildIspe(int w, int h) {
        ByteBuffer bb = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN);
        IsoBmff.putU32(bb, w);
        IsoBmff.putU32(bb, h);
        return IsoBmff.fullBoxPayload(0, 0, bb.array());
    }

    static byte[] buildAuxC(String auxType) {
        byte[] s = (auxType + "\0").getBytes(StandardCharsets.US_ASCII);
        return IsoBmff.fullBoxPayload(0, 0, s);
    }

    /** pixi property body (callers wrap in FullBox): channel count + bits. */
    static byte[] buildPixiBody(int... bits) {
        ByteBuffer bb = ByteBuffer.allocate(1 + bits.length).order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) (bits.length & 0xFF));
        for (int b : bits) {
            bb.put((byte) (b & 0xFF));
        }
        return bb.array();
    }

    /**
     * nclx colr property body (callers wrap in FullBox): colour_type 'nclx'
     * + primaries/transfer/matrix u16 + full_range flag byte.
     */
    static byte[] buildNclxBody(int primaries, int transfer, int matrix, boolean fullRange) {
        ByteBuffer bb = ByteBuffer.allocate(4 + 2 + 2 + 2 + 1).order(ByteOrder.BIG_ENDIAN);
        bb.put(IsoBmff.fourccBytes("nclx"));
        IsoBmff.putU16(bb, primaries);
        IsoBmff.putU16(bb, transfer);
        IsoBmff.putU16(bb, matrix);
        bb.put((byte) (fullRange ? 0x80 : 0));
        return bb.array();
    }

    /**
     * Builds a grpl box holding one altr entity group (used to scope the
     * tmap item to the base image; some readers ignore tmap outside altr).
     * Note: grpl itself is a plain container (no FullBox header); only the
     * inner altr entity-to-group box is a FullBox.
     */
    static byte[] buildAltrGroup(int groupId, int[] entityIds) {
        ByteBuffer bb = ByteBuffer.allocate(4 + 4 + 4 * entityIds.length)
                .order(ByteOrder.BIG_ENDIAN);
        IsoBmff.putU32(bb, groupId);
        IsoBmff.putU32(bb, entityIds.length);
        for (int id : entityIds) {
            IsoBmff.putU32(bb, id);
        }
        byte[] altr = IsoBmff.buildBox("altr",
                IsoBmff.fullBoxPayload(0, 0, bb.array()));
        return IsoBmff.buildBox("grpl", altr);
    }

    /**
     * Returns an ftyp box with the tmap compatible brand appended (parsed
     * from the base file's brands; no-op when already present). Readers gate
     * tmap handling on this brand.
     */
    static byte[] withTmapBrand(IsoBmff.Box baseFtyp) {
        byte[] p = baseFtyp.payload;
        if (p.length < 8 || ((p.length - 8) % 4) != 0) {
            throw new IllegalArgumentException("malformed ftyp");
        }
        for (int i = 8; i + 4 <= p.length; i += 4) {
            if (p[i] == 't' && p[i + 1] == 'm' && p[i + 2] == 'a' && p[i + 3] == 'p') {
                return IsoBmff.buildBox(baseFtyp.type, p);
            }
        }
        byte[] out = new byte[p.length + 4];
        System.arraycopy(p, 0, out, 0, p.length);
        out[p.length] = 't';
        out[p.length + 1] = 'm';
        out[p.length + 2] = 'a';
        out[p.length + 3] = 'p';
        return IsoBmff.buildBox(baseFtyp.type, out);
    }

    static byte[] buildSingleRef(String refType, int fromId, int[] toIds, int version) {
        int idSize = version == 0 ? 2 : 4;
        ByteBuffer bb = ByteBuffer.allocate(idSize + 2 + idSize * toIds.length)
                .order(ByteOrder.BIG_ENDIAN);
        if (version == 0) {
            IsoBmff.putU16(bb, fromId);
        } else {
            IsoBmff.putU32(bb, fromId);
        }
        IsoBmff.putU16(bb, toIds.length);
        for (int id : toIds) {
            if (version == 0) {
                IsoBmff.putU16(bb, id);
            } else {
                IsoBmff.putU32(bb, id);
            }
        }
        return IsoBmff.buildBox(refType, bb.array());
    }

    static int fullFlags(byte[] fullBoxPayload) {
        return ((fullBoxPayload[1] & 0xFF) << 16)
                | ((fullBoxPayload[2] & 0xFF) << 8)
                | (fullBoxPayload[3] & 0xFF);
    }

    static int[] toIntArray(List<Integer> list) {
        int[] out = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    static int reverseLookup(java.util.Map<Integer, Integer> map, int value) {
        for (java.util.Map.Entry<Integer, Integer> e : map.entrySet()) {
            if (e.getValue() == value) {
                return e.getKey();
            }
        }
        throw new IllegalArgumentException("no reverse mapping for " + value);
    }

    /**
     * Builds the output iinf: kept base entries + remapped gain entries +
     * fresh entries, preserving the base iinf version.
     */
    static byte[] buildIinf(byte[] baseIinf, List<byte[]> keptInfe, List<byte[]> newEntries) {
        int version = IsoBmff.fullVersion(baseIinf);
        int countSize = version == 0 ? 2 : 4;
        int total = totalLen(keptInfe) + totalLen(newEntries);
        ByteBuffer bb = ByteBuffer.allocate(4 + countSize + total).order(ByteOrder.BIG_ENDIAN);
        bb.put(baseIinf, 0, 4);
        int count = keptInfe.size() + newEntries.size();
        if (version == 0) {
            IsoBmff.putU16(bb, count);
        } else {
            IsoBmff.putU32(bb, count);
        }
        for (byte[] e : keptInfe) {
            bb.put(e);
        }
        for (byte[] e : newEntries) {
            bb.put(e);
        }
        return IsoBmff.buildBox("iinf", bb.array());
    }

    static byte[] buildIinf(byte[] baseIinf, byte[] primaryInfeBox, List<byte[]> newEntries) {
        List<byte[]> kept = new ArrayList<>();
        kept.add(primaryInfeBox);
        return buildIinf(baseIinf, kept, newEntries);
    }

    /**
     * Keeps only reference entries fully inside {@code keep} (source and every
     * target); references to dropped items (thumbnails etc.) would otherwise
     * dangle. Returns a FullBox payload, or null when empty/absent.
     */
    static byte[] filterIref(Meta m, int keepId) {
        java.util.Set<Integer> keep = new java.util.HashSet<>();
        keep.add(keepId);
        return filterIref(m, keep, m.irefVersion);
    }

    /**
     * Keeps entries fully inside {@code keep}, rebuilt at
     * {@code outVersion} so kept and fresh refs share one layout.
     */
    static byte[] filterIref(Meta m, java.util.Set<Integer> keep, int outVersion) {
        List<IrefEntry> entries = parseIrefEntries(m.irefPayload, m.irefVersion);
        List<byte[]> kept = new ArrayList<>();
        for (IrefEntry e : entries) {
            if (keep.contains(e.from) && keep.containsAll(e.to)) {
                kept.add(buildSingleRef(e.type, e.from, toIntArray(e.to), outVersion));
            }
        }
        if (kept.isEmpty()) {
            return null;
        }
        return IsoBmff.fullBoxPayload(outVersion, m.irefFlags, IsoBmff.concat(kept));
    }

    static byte[] extendIref(byte[] filteredBaseIref, List<byte[]> extra, int version) {
        byte[] body;
        if (filteredBaseIref != null) {
            body = new byte[filteredBaseIref.length - 4];
            System.arraycopy(filteredBaseIref, 4, body, 0, body.length);
        } else {
            body = new byte[0];
        }
        List<byte[]> parts = new ArrayList<>();
        if (body.length > 0) {
            parts.add(body);
        }
        parts.addAll(extra);
        return IsoBmff.buildBox("iref",
                IsoBmff.fullBoxPayload(version, 0, IsoBmff.concat(parts)));
    }

    /** Parses ipma associations into {@link Meta#ipmaAssoc}; v0/v1 only. */
    static void parseIpmaAssoc(Meta m) {
        byte[] p = m.ipmaPayload;
        int version = IsoBmff.fullVersion(p);
        if (version > 1) {
            throw new IllegalArgumentException("unsupported ipma v" + version);
        }
        m.ipmaVersion = version;
        m.ipmaFlags = fullFlags(p);
        boolean wide = version >= 1 && (m.ipmaFlags & 1) != 0;
        long count = IsoBmff.u32(p, 4);
        int pos = 8;
        for (long i = 0; i < count; i++) {
            int itemId;
            if (version == 0) {
                if (pos + 3 > p.length) {
                    throw new IllegalArgumentException("truncated ipma");
                }
                itemId = IsoBmff.u16(p, pos);
                pos += 2;
            } else {
                if (pos + 5 > p.length) {
                    throw new IllegalArgumentException("truncated ipma");
                }
                itemId = (int) IsoBmff.u32(p, pos);
                pos += 4;
            }
            int assocCount = p[pos++] & 0xFF;
            List<PropRef> refs = new ArrayList<>();
            for (int a = 0; a < assocCount; a++) {
                if (wide) {
                    if (pos + 2 > p.length) {
                        throw new IllegalArgumentException("truncated ipma assoc");
                    }
                    int v = IsoBmff.u16(p, pos);
                    pos += 2;
                    refs.add(new PropRef((v & 0x8000) != 0, v & 0x7FFF));
                } else {
                    if (pos + 1 > p.length) {
                        throw new IllegalArgumentException("truncated ipma assoc");
                    }
                    int v = p[pos++] & 0xFF;
                    refs.add(new PropRef((v & 0x80) != 0, v & 0x7F));
                }
            }
            m.ipmaAssoc.put(itemId, refs);
        }
    }

    static byte[] serializeIpma(int version, int flags, java.util.Map<Integer,
            java.util.List<PropRef>> entries, java.util.List<Integer> order) {
        boolean wide = version >= 1 && (flags & 1) != 0;
        int size = 4 + 4;
        for (int id : order) {
            List<PropRef> refs = entries.get(id);
            int n = refs == null ? 0 : refs.size();
            size += (version == 0 ? 2 : 4) + 1 + n * (wide ? 2 : 1);
        }
        ByteBuffer bb = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) version);
        bb.put((byte) ((flags >> 16) & 0xFF));
        bb.put((byte) ((flags >> 8) & 0xFF));
        bb.put((byte) (flags & 0xFF));
        IsoBmff.putU32(bb, order.size());
        for (int id : order) {
            List<PropRef> refs = entries.get(id);
            if (version == 0) {
                IsoBmff.putU16(bb, id);
            } else {
                IsoBmff.putU32(bb, id);
            }
            int n = refs == null ? 0 : refs.size();
            bb.put((byte) (n & 0xFF));
            if (refs != null) {
                for (PropRef r : refs) {
                    if (wide) {
                        IsoBmff.putU16(bb, (r.essential ? 0x8000 : 0) | (r.index & 0x7FFF));
                    } else {
                        bb.put((byte) ((r.essential ? 0x80 : 0) | (r.index & 0x7F)));
                    }
                }
            }
        }
        return IsoBmff.buildBox("ipma", bb.array());
    }

    /**
     * Rebuilds ipma: kept base entries verbatim, kept gain entries with
     * remapped property indexes, and the aux grid entry (remapped original
     * associations plus auxC/ispe handling from the caller).
     */
    static byte[] extendIpma(Meta base, java.util.Set<Integer> baseKeep, GainGraph gg,
            List<PropRef> gridAssoc, int tmapId, List<Integer> tmapPropIdx) {
        int version = IsoBmff.fullVersion(base.ipmaPayload);
        Meta tmp = new Meta();
        tmp.ipmaPayload = base.ipmaPayload;
        parseIpmaAssoc(tmp);
        boolean wide = tmp.ipmaVersion >= 1 && (tmp.ipmaFlags & 1) != 0;
        java.util.Map<Integer, java.util.List<PropRef>> entries = new java.util.HashMap<>();
        java.util.List<Integer> order = new ArrayList<>();
        List<Integer> baseOrder = new ArrayList<>(baseKeep);
        java.util.Collections.sort(baseOrder);
        for (int id : baseOrder) {
            if (tmp.ipmaAssoc.containsKey(id)) {
                entries.put(id, tmp.ipmaAssoc.get(id));
                order.add(id);
            }
        }
        for (int fresh : gg.freshOrder) {
            int old = reverseLookup(gg.idRemap, fresh);
            List<PropRef> out = new ArrayList<>();
            if (fresh == gg.gridNewId) {
                out.addAll(gridAssoc);
            } else {
                List<PropRef> refs = gg.gainAssoc.get(old);
                if (refs == null) {
                    throw new IllegalArgumentException(
                            "gain ipma entry missing for item " + old);
                }
                for (PropRef r : refs) {
                    Integer mapped = gg.propRemap.get(r.index);
                    if (mapped == null) {
                        throw new IllegalArgumentException(
                                "gain prop not staged for item " + old);
                    }
                    out.add(new PropRef(r.essential, mapped));
                }
            }
            entries.put(fresh, out);
            order.add(fresh);
        }
        List<PropRef> tmapAssoc = new ArrayList<>();
        for (int idx : tmapPropIdx) {
            tmapAssoc.add(new PropRef(false, idx));
        }
        entries.put(tmapId, tmapAssoc);
        order.add(tmapId);
        for (java.util.List<PropRef> refs : entries.values()) {
            for (PropRef r : refs) {
                if (!wide && r.index > 0x7F) {
                    throw new IllegalArgumentException("property index too large for ipma v0");
                }
                if (wide && r.index > 0x7FFF) {
                    throw new IllegalArgumentException("property index too large for ipma");
                }
            }
        }
        if (version == 0) {
            for (int id : order) {
                if (id > 0xFFFF) {
                    throw new IllegalArgumentException("item id too large for ipma v0");
                }
            }
        }
        return serializeIpma(tmp.ipmaVersion, tmp.ipmaFlags, entries, order);
    }

    static long readSized(byte[] d, int offset, int size) {
        if (size == 0) {
            return 0;
        } else if (size == 4) {
            return IsoBmff.u32(d, offset);
        } else if (size == 8) {
            return IsoBmff.u64(d, offset);
        }
        throw new IllegalArgumentException("bad iloc field size " + size);
    }

    /**
     * Walks a v0/v1 iloc for one item's extents (absolute file offsets).
     * Layout verified against mp4box.js: v1 keeps u16 count/ids and adds a
     * u16 construction field (low 4 bits) plus optional extent indexes.
     * Only construction_method 0 (file offsets) is supported; anything else
     * throws so exotic layouts degrade to SDR HEIC instead of a corrupt mux.
     */
    static List<Extent> ilocExtentsForItem(byte[] ilocPayload, int itemId) {
        List<Extent> all = ilocExtents(ilocPayload, itemId);
        if (all.isEmpty()) {
            throw new IllegalArgumentException("no iloc extents for item " + itemId);
        }
        return all;
    }

    /** Parses all items' extents; when {@code onlyId >= 0} filters to it. */
    static List<Extent> ilocExtents(byte[] ilocPayload, int onlyId) {
        int version = IsoBmff.fullVersion(ilocPayload);
        if (version != 0 && version != 1) {
            throw new IllegalArgumentException(
                    "unsupported iloc v" + version + " (SDR fallback)");
        }
        int offSize = (ilocPayload[4] >> 4) & 0xF;
        int lenSize = ilocPayload[4] & 0xF;
        int baseSize = (ilocPayload[5] >> 4) & 0xF;
        int indexSize = version >= 1 ? ilocPayload[5] & 0xF : 0;
        if (indexSize != 0 && indexSize != 4 && indexSize != 8) {
            throw new IllegalArgumentException("bad iloc index_size " + indexSize);
        }
        int count = IsoBmff.u16(ilocPayload, 6);
        int pos = 8;
        List<Extent> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int id = IsoBmff.u16(ilocPayload, pos);
            pos += 2;
            int construction = 0;
            if (version >= 1) {
                int raw = IsoBmff.u16(ilocPayload, pos);
                pos += 2;
                construction = raw & 0xF;
                if (construction != 0 && construction != 1) {
                    throw new IllegalArgumentException(
                            "unsupported iloc construction " + construction);
                }
            }
            pos += 2; // data_reference_index
            long base = readSized(ilocPayload, pos, baseSize);
            pos += baseSize;
            int extCount = IsoBmff.u16(ilocPayload, pos);
            pos += 2;
            for (int e = 0; e < extCount; e++) {
                pos += indexSize; // extent_index (ignored)
                long off = readSized(ilocPayload, pos, offSize);
                pos += offSize;
                long len = readSized(ilocPayload, pos, lenSize);
                pos += lenSize;
                if (onlyId < 0 || id == onlyId) {
                    out.add(new Extent(id, base + off, len, construction));
                }
            }
        }
        return out;
    }

    /** One parsed iref child entry. */
    static final class IrefEntry {
        final String type;
        final int from;
        final List<Integer> to;

        IrefEntry(String type, int from, List<Integer> to) {
            this.type = type;
            this.from = from;
            this.to = to;
        }
    }

    static List<IrefEntry> parseIrefEntries(byte[] irefPayloadOrNull, int version) {
        List<IrefEntry> out = new ArrayList<>();
        if (irefPayloadOrNull == null) {
            return out;
        }
        byte[] p = irefPayloadOrNull;
        int idSize = version == 0 ? 2 : 4;
        int pos = 4;
        while (pos + 8 <= p.length) {
            long size = IsoBmff.u32(p, pos);
            if (size < 8 + idSize + 2 || pos + size > p.length) {
                throw new IllegalArgumentException("truncated iref entry");
            }
            String type = IsoBmff.fourcc(p, pos + 4);
            int from = version == 0 ? IsoBmff.u16(p, pos + 8) : (int) IsoBmff.u32(p, pos + 8);
            int n = IsoBmff.u16(p, pos + 8 + idSize);
            List<Integer> to = new ArrayList<>();
            int q = pos + 8 + idSize + 2;
            for (int i = 0; i < n; i++) {
                if (q + idSize > pos + size) {
                    throw new IllegalArgumentException("truncated iref targets");
                }
                to.add(version == 0 ? IsoBmff.u16(p, q) : (int) IsoBmff.u32(p, q));
                q += idSize;
            }
            out.add(new IrefEntry(type, from, to));
            pos += (int) size;
        }
        return out;
    }

    /**
     * Keep set: the primary plus everything it derives from via {@code dimg}
     * (grid tiles). Other reference types are not followed, so thumbnails and
     * unrelated items are dropped. IDs ascending, primary first.
     */
    static java.util.LinkedHashSet<Integer> computeKeepSet(Meta m) {
        java.util.LinkedHashSet<Integer> keep = new java.util.LinkedHashSet<>();
        keep.add(m.primaryItemId);
        List<IrefEntry> refs = parseIrefEntries(m.irefPayload, m.irefVersion);
        boolean grown = true;
        while (grown) {
            grown = false;
            for (IrefEntry e : refs) {
                if (!e.type.equals("dimg") || !keep.contains(e.from)) {
                    continue;
                }
                for (int id : e.to) {
                    if (!keep.contains(id)) {
                        keep.add(id);
                        grown = true;
                    }
                }
            }
        }
        return keep;
    }

    /**
     * Copies every kept item's bytes, keyed by item id (ascending). Method 0
     * extents are absolute file offsets into {@code mdat}; method 1 extents
     * are payload-relative into the {@code idat} box (meta child first,
     * top-level fallback). The source mdat may sit anywhere (a {@code free}
     * box may follow meta), so ranges are located, not assumed.
     */
    static java.util.Map<Integer, byte[]> sliceKeptPayloads(byte[] file, Meta m,
            java.util.Set<Integer> keep, String tag) {
        long[] mdat = IsoBmff.boxDataRange(file, "mdat");
        if (mdat == null) {
            throw new IllegalArgumentException(tag + " has no mdat");
        }
        long[] idat = idatPayloadRange(file);
        List<Extent> all = ilocExtents(m.ilocPayload, -1);
        java.util.Map<Integer, java.util.List<Extent>> byId = new java.util.HashMap<>();
        for (Extent e : all) {
            if (!keep.contains(e.itemId)) {
                continue;
            }
            if (e.construction != 0 && e.construction != 1) {
                throw new IllegalArgumentException(
                        tag + " unsupported iloc construction " + e.construction);
            }
            if (e.construction == 1 && idat == null) {
                throw new IllegalArgumentException(tag + " idat extent without idat box");
            }
            if (!byId.containsKey(e.itemId)) {
                byId.put(e.itemId, new ArrayList<Extent>());
            }
            byId.get(e.itemId).add(e);
        }
        java.util.Map<Integer, byte[]> out = new java.util.TreeMap<>();
        for (int id : keep) {
            List<Extent> extents = byId.get(id);
            if (extents == null || extents.isEmpty()) {
                throw new IllegalArgumentException(tag + " item " + id + " has no extents");
            }
            long total = 0;
            for (Extent e : extents) {
                long[] src = e.construction == 0 ? mdat : idat;
                long base = e.construction == 0 ? 0 : src[0];
                long abs = base + e.offset;
                if (e.offset < 0 || e.length <= 0 || abs < src[0]
                        || abs + e.length > src[0] + src[1]) {
                    throw new IllegalArgumentException(tag + " item " + id
                            + " extent outside " + (e.construction == 0 ? "mdat" : "idat"));
                }
                total += e.length;
                if (total > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException(tag + " payload too large");
                }
            }
            byte[] buf = new byte[(int) total];
            int pos = 0;
            for (Extent e : extents) {
                long[] src = e.construction == 0 ? mdat : idat;
                long abs = (e.construction == 0 ? 0 : src[0]) + e.offset;
                System.arraycopy(file, (int) abs, buf, pos, (int) e.length);
                pos += (int) e.length;
            }
            checkPayloadPlausible(m, id, buf, byId.size(), tag);
            out.put(id, buf);
        }
        return out;
    }

    /** Locates the idat payload: meta child first, top-level fallback. */
    static long[] idatPayloadRange(byte[] file) {
        long[] meta = IsoBmff.boxDataRange(file, "meta");
        if (meta != null && meta[1] > 4) {
            // Meta children start after its 4-byte FullBox header.
            long[] child = scanChildren(file, meta[0] + 4, meta[1] - 4, "idat");
            if (child != null) {
                return child;
            }
        }
        return IsoBmff.boxDataRange(file, "idat");
    }

    /** Walks child boxes in [start, start+len), returns {dataStart, dataLen}. */
    static long[] scanChildren(byte[] file, long start, long len, String type) {
        long pos = start;
        long end = start + len;
        while (pos + 8 <= end && pos + 8 <= file.length) {
            int p = (int) pos;
            long size = IsoBmff.u32(file, p);
            String t = IsoBmff.fourcc(file, p + 4);
            int header = 8;
            if (size == 1) {
                if (pos + 16 > end) {
                    return null;
                }
                size = IsoBmff.u64(file, p + 8);
                header = 16;
            } else if (size == 0) {
                size = end - pos;
            }
            if (size < header || pos + size > end) {
                return null;
            }
            if (t.equals(type)) {
                return new long[]{pos + header, size - header};
            }
            pos += size;
        }
        return null;
    }

    /**
     * Content sanity over sliced payloads. Real tiles are length-prefixed
     * HEVC; the grid params encode rows x cols covering the tiles. Payloads
     * at or under {@link #PLAINTEXT_PROBE_LIMIT} (synthetic fixtures) skip
     * the HEVC check. Anything suspicious throws -> SDR fallback.
     */
    static final int PLAINTEXT_PROBE_LIMIT = 1024;

    static void checkPayloadPlausible(Meta m, int id, byte[] buf, int tileCount, String tag) {
        String type = m.infeTypes.get(id);
        if ("grid".equals(type)) {
            // Canonical 8-byte grid params: version, spare, rows-1, cols-1,
            // output width/height u16 BE. Observed on-device as
            // 00 00 07 05 0c00 1000 (8x6 grid for 48 tiles).
            // tileCount includes the grid itself, so it covers tileCount-1.
            if (buf.length == 8) {
                int rows = (buf[2] & 0xFF) + 1;
                int cols = (buf[3] & 0xFF) + 1;
                long w = IsoBmff.u16(buf, 4);
                long h = IsoBmff.u16(buf, 6);
                if (rows < 1 || rows > 64 || cols < 1 || cols > 64
                        || (long) rows * cols < tileCount - 1 || w <= 0 || h <= 0) {
                    throw new IllegalArgumentException(tag + " implausible grid params rows="
                            + rows + " cols=" + cols + " tiles=" + (tileCount - 1));
                }
            }
            return;
        }
        if (!"hvc1".equals(type) || buf.length <= PLAINTEXT_PROBE_LIMIT) {
            return;
        }
        long nalLen = IsoBmff.u32(buf, 0);
        if (nalLen <= 0 || nalLen + 4 > buf.length || (buf[4] & 0x80) != 0) {
            throw new IllegalArgumentException(tag + " item " + id + " not HEVC-like");
        }
    }

    /** Remapped gain graph: fresh IDs, patched entries, appended props. */
    static final class GainGraph {
        /** old gain id -> fresh id, ascending old order. */
        final java.util.Map<Integer, Integer> idRemap = new java.util.HashMap<>();
        final List<Integer> freshOrder = new ArrayList<>();
        /** fresh id -> rebuilt infe full box. */
        final java.util.Map<Integer, byte[]> infeBoxes = new java.util.HashMap<>();
        /** old gain ipco index -> merged ipco index. */
        final java.util.Map<Integer, Integer> propRemap = new java.util.HashMap<>();
        final List<byte[]> propBoxes = new ArrayList<>();
        /** remapped dimg entries (fresh ids). */
        final List<IrefEntry> dimg = new ArrayList<>();
        /** kept gain id -> original associations. */
        final java.util.Map<Integer, List<PropRef>> gainAssoc = new java.util.HashMap<>();
        int gridNewId;
    }

    /** Full infe box bytes for one item id, or null when absent. */
    static byte[] infeBoxFor(Meta m, int itemId) {
        byte[] p = m.iinfPayload;
        int version = IsoBmff.fullVersion(p);
        int countSize = version == 0 ? 2 : 4;
        byte[] region = new byte[p.length - 4 - countSize];
        System.arraycopy(p, 4 + countSize, region, 0, region.length);
        for (IsoBmff.Box e : IsoBmff.parse(region)) {
            if (!e.type.equals("infe")) {
                continue;
            }
            int v = IsoBmff.fullVersion(e.payload);
            int id = (v >= 3) ? (int) IsoBmff.u32(e.payload, 4) : IsoBmff.u16(e.payload, 4);
            if (id == itemId) {
                return IsoBmff.buildBox("infe", e.payload);
            }
        }
        return null;
    }

    /** Returns a copy of an infe full box with the item id replaced. */
    static byte[] patchInfeId(byte[] infeBox, int newId) {
        byte[] out = infeBox.clone();
        int v = out[8] & 0xFF; // FullBox version inside the box bytes
        if (v >= 3) {
            ByteBuffer bb = ByteBuffer.wrap(out, 8 + 4, 4).order(ByteOrder.BIG_ENDIAN);
            bb.putInt(newId);
        } else {
            ByteBuffer bb = ByteBuffer.wrap(out, 8 + 4, 2).order(ByteOrder.BIG_ENDIAN);
            bb.putShort((short) (newId & 0xFFFF));
        }
        return out;
    }

    /**
     * Remaps the gain keep-set to fresh IDs and stages everything the merge
     * needs: patched infe boxes, referenced property boxes (deduped by old
     * index, first-seen order over ascending items), and remapped dimg refs.
     *
     * @param firstFresh   first free id (above every id in both inputs)
     * @param propBase     merged-ipco size before gain props (1-based base)
     */
    static GainGraph remapGainGraph(Meta gain, java.util.Set<Integer> gainKeep,
            int firstFresh, int propBase) {
        GainGraph g = new GainGraph();
        List<Integer> ordered = new ArrayList<>(gainKeep);
        java.util.Collections.sort(ordered);
        int next = firstFresh;
        for (int old : ordered) {
            g.idRemap.put(old, next);
            g.freshOrder.add(next);
            byte[] infe = infeBoxFor(gain, old);
            if (infe == null) {
                throw new IllegalArgumentException("gain infe missing for item " + old);
            }
            g.infeBoxes.put(next, patchInfeId(infe, next));
            next++;
        }
        g.gridNewId = g.idRemap.get(gain.primaryItemId);
        for (int old : ordered) {
            List<PropRef> refs = gain.ipmaAssoc.get(old);
            if (refs != null) {
                g.gainAssoc.put(old, refs);
            }
        }
        for (int old : ordered) {
            List<PropRef> refs = gain.ipmaAssoc.get(old);
            if (refs == null) {
                continue;
            }
            for (PropRef r : refs) {
                if (r.index < 1 || r.index > gain.ipcoChildren.size()) {
                    throw new IllegalArgumentException(
                            "gain ipma index out of range for item " + old);
                }
                if (!g.propRemap.containsKey(r.index)) {
                    IsoBmff.Box b = gain.ipcoChildren.get(r.index - 1);
                    g.propBoxes.add(IsoBmff.buildBox(b.type, b.payload));
                    g.propRemap.put(r.index, propBase + g.propBoxes.size());
                }
            }
        }
        for (IrefEntry e : parseIrefEntries(gain.irefPayload, gain.irefVersion)) {
            if (!e.type.equals("dimg") || !gainKeep.contains(e.from)) {
                continue;
            }
            List<Integer> to = new ArrayList<>();
            for (int id : e.to) {
                if (!gainKeep.contains(id)) {
                    throw new IllegalArgumentException("gain dimg escapes keep-set");
                }
                to.add(g.idRemap.get(id));
            }
            g.dimg.add(new IrefEntry(e.type, g.idRemap.get(e.from), to));
        }
        return g;
    }

    /** One-line box inventory for mux failure messages / device logs. */
    static String describeHeic(byte[] file) {
        try {
            StringBuilder sb = new StringBuilder();
            List<IsoBmff.Box> top = IsoBmff.parse(file);
            for (IsoBmff.Box b : top) {
                sb.append(b.type).append('(').append(b.payload.length).append(')');
            }
            for (IsoBmff.Box b : top) {
                if (!b.type.equals("meta") || b.payload.length < 4) {
                    continue;
                }
                List<IsoBmff.Box> kids = IsoBmff.parse(b.payload, 4, b.payload.length - 4);
                sb.append(" meta[");
                for (IsoBmff.Box k : kids) {
                    sb.append(k.type);
                    if (k.type.equals("iinf")) {
                        int v = IsoBmff.fullVersion(k.payload);
                        int cs = v == 0 ? 2 : 4;
                        int n = v == 0 ? IsoBmff.u16(k.payload, 4) : (int) IsoBmff.u32(k.payload, 4);
                        sb.append("(v").append(v).append(" n").append(n).append(':');
                        byte[] region = new byte[k.payload.length - 4 - cs];
                        System.arraycopy(k.payload, 4 + cs, region, 0, region.length);
                        for (IsoBmff.Box e : IsoBmff.parse(region)) {
                            int ev = IsoBmff.fullVersion(e.payload);
                            int id = ev >= 3 ? (int) IsoBmff.u32(e.payload, 4)
                                    : IsoBmff.u16(e.payload, 4);
                            String t = e.payload.length >= 12
                                    ? IsoBmff.fourcc(e.payload, 8) : "?";
                            sb.append(id).append('=').append(t).append(',');
                        }
                        sb.append(')');
                    } else if (k.type.equals("pitm")) {
                        int pv = IsoBmff.fullVersion(k.payload);
                        sb.append("(primary=")
                                .append(pv == 0 ? IsoBmff.u16(k.payload, 4)
                                        : (int) IsoBmff.u32(k.payload, 4))
                                .append(')');
                    } else if (k.type.equals("iloc")) {
                        sb.append("(v").append(IsoBmff.fullVersion(k.payload)).append(' ');
                        sb.append("hex=").append(hex(k.payload, 0,
                                Math.min(k.payload.length, 96))).append(')');
                    } else if (k.type.equals("idat")) {
                        sb.append("(len=").append(k.payload.length).append(" head=")
                                .append(hex(k.payload, 0,
                                        Math.min(k.payload.length, 48))).append(')');
                    } else if (k.type.equals("iref")) {
                        sb.append('(');
                        try {
                            for (IrefEntry e : parseIrefEntries(k.payload,
                                    IsoBmff.fullVersion(k.payload))) {
                                sb.append(e.type).append(e.from).append("->").append(e.to)
                                        .append(';');
                            }
                        } catch (Exception dumpFailed) {
                            sb.append("unparseable");
                        }
                        sb.append(')');
                    } else if (k.type.equals("grpl")) {
                        sb.append('(');
                        try {
                            // grpl is a plain container (no FullBox header).
                            for (IsoBmff.Box g : IsoBmff.parse(k.payload)) {
                                sb.append(g.type);
                                if (g.type.equals("altr") && g.payload.length >= 12) {
                                    sb.append("[gid=")
                                            .append(IsoBmff.u32(g.payload, 4)).append(':');
                                    int n = (int) IsoBmff.u32(g.payload, 8);
                                    for (int i = 0; i < n && 12 + 4 * i + 4 <= g.payload.length;
                                            i++) {
                                        sb.append(IsoBmff.u32(g.payload, 12 + 4 * i))
                                                .append(',');
                                    }
                                    sb.append(']');
                                }
                            }
                        } catch (Exception dumpFailed) {
                            sb.append("unparseable");
                        }
                        sb.append(')');
                    }
                }
                sb.append(']');
            }
            return sb.toString();
        } catch (Exception e) {
            return "undescribable:" + e.getMessage();
        }
    }

    /** Lowercase hex of a slice, for device-log forensics. */
    static String hex(byte[] data, int offset, int length) {
        StringBuilder sb = new StringBuilder(Math.max(0, length) * 2);
        int end = Math.min(data.length, offset + Math.max(0, length));
        for (int i = Math.max(0, offset); i < end; i++) {
            sb.append(Character.forDigit((data[i] >> 4) & 0xF, 16));
            sb.append(Character.forDigit(data[i] & 0xF, 16));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // XMP packets (same hdrgm vocabulary as the JPEG container, HEIC mime)
    // ------------------------------------------------------------------

    static byte[] buildPrimaryXmp(int gainMapLen) {
        String xml = "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n"
                + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"PhotonCamera\">\n"
                + " <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
                + "  <rdf:Description rdf:about=\"\"\n"
                + "    xmlns:Container=\"http://ns.google.com/photos/1.0/container/\"\n"
                + "    xmlns:Item=\"http://ns.google.com/photos/1.0/container/item/\"\n"
                + "    xmlns:hdrgm=\"http://ns.adobe.com/hdr-gain-map/1.0/\"\n"
                + "    hdrgm:Version=\"1.0\">\n"
                + "   <Container:Directory>\n"
                + "    <rdf:Seq>\n"
                + "     <rdf:li rdf:parseType=\"Resource\">\n"
                + "      <Container:Item Item:Semantic=\"Primary\" Item:Mime=\"image/heic\"/>\n"
                + "     </rdf:li>\n"
                + "     <rdf:li rdf:parseType=\"Resource\">\n"
                + "      <Container:Item Item:Semantic=\"GainMap\" Item:Mime=\"image/heic\" Item:Length=\""
                + gainMapLen + "\"/>\n"
                + "     </rdf:li>\n"
                + "    </rdf:Seq>\n"
                + "   </Container:Directory>\n"
                + "  </rdf:Description>\n"
                + " </rdf:RDF>\n"
                + "</x:xmpmeta>\n"
                + "<?xpacket end=\"w\"?>\n";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] buildGainMapXmp(float gMin, float gMax, float hdrCap) {
        String xml = "<?xpacket begin=\"\uFEFF\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n"
                + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"PhotonCamera\">\n"
                + " <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
                + "  <rdf:Description rdf:about=\"\"\n"
                + "    xmlns:hdrgm=\"http://ns.adobe.com/hdr-gain-map/1.0/\"\n"
                + "    hdrgm:Version=\"1.0\"\n"
                + "    hdrgm:GainMapMin=\"" + fmt(gMin) + "\"\n"
                + "    hdrgm:GainMapMax=\"" + fmt(gMax) + "\"\n"
                + "    hdrgm:Gamma=\"1\"\n"
                + "    hdrgm:OffsetSDR=\"" + fmt(1.0f / 64.0f) + "\"\n"
                + "    hdrgm:OffsetHDR=\"" + fmt(1.0f / 64.0f) + "\"\n"
                + "    hdrgm:HDRCapacityMin=\"0\"\n"
                + "    hdrgm:HDRCapacityMax=\"" + fmt(hdrCap) + "\"\n"
                + "    hdrgm:BaseRenditionIsHDR=\"False\"/>\n"
                + " </rdf:RDF>\n"
                + "</x:xmpmeta>\n"
                + "<?xpacket end=\"w\"?>\n";
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    static String fmt(float v) {
        if (!Float.isFinite(v)) {
            v = 0f;
        }
        return String.format(java.util.Locale.US, "%.6f", v);
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private static int xmpGainPayloadLength(Inputs in) {
        return buildGainMapXmp(in.gainMapMin, in.gainMapMax, in.hdrCapacityMax).length;
    }

    private static List<byte[]> listOf(byte[]... parts) {
        List<byte[]> out = new ArrayList<>();
        for (byte[] p : parts) {
            out.add(p);
        }
        return out;
    }

    private static int totalLen(List<byte[]> parts) {
        int n = 0;
        for (byte[] p : parts) {
            n += p.length;
        }
        return n;
    }

    private static IsoBmff.Box findRequired(List<IsoBmff.Box> boxes, String type) {
        for (IsoBmff.Box b : boxes) {
            if (b.type.equals(type)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Missing box: " + type);
    }
}
