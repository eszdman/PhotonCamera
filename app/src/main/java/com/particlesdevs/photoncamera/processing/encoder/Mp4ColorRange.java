package com.particlesdevs.photoncamera.processing.encoder;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Rewrites the color-range metadata of an already recorded MP4 to full range.
 *
 * <p>MediaRecorder exposes no color range/standard/transfer control, so HDR
 * recordings come out tagged as limited even when the 10-bit stream is
 * effectively full range (the same way stock/PhotonVidCam-style recorders tag
 * it). This class fixes the finished file:
 * <ul>
 *   <li>the video sample entry's {@code colr} box is rewritten (or inserted)
 *       with BT.2020 primaries, HLG/PQ transfer, BT.2020 matrix and the
 *       full-range flag;</li>
 *   <li>the HEVC SPS VUI {@code video_full_range_flag} inside {@code hvcC} is
 *       set when present. The bit is located by its known color signature
 *       (colour_description_present + primaries + transfer + matrix), so no
 *       full SPS parser is needed; the flip cannot change the RBSP length or
 *       create new emulation-prevention patterns.</li>
 * </ul>
 * Any parse problem leaves the file untouched.
 */
public final class Mp4ColorRange {

    private static final int PRIMARIES_BT2020 = 9;
    private static final int MATRIX_BT2020 = 9;
    private static final int TRANSFER_HLG = 18;
    private static final int TRANSFER_PQ = 16;
    /** Fixed part of a VisualSampleEntry before its child boxes. */
    private static final int VISUAL_SAMPLE_ENTRY_FIXED = 78;
    private static final long MAX_MOOV_BYTES = 256L * 1024 * 1024;

    private Mp4ColorRange() {}

    public static final class Result {
        public final boolean modified;
        public final boolean vuiPatched;
        public final boolean colrPatched;
        public final boolean colrInserted;
        public final String note;

        Result(boolean modified, boolean vuiPatched, boolean colrPatched,
                boolean colrInserted, String note) {
            this.modified = modified;
            this.vuiPatched = vuiPatched;
            this.colrPatched = colrPatched;
            this.colrInserted = colrInserted;
            this.note = note;
        }

        @Override
        public String toString() {
            String colr = colrInserted ? "inserted" : colrPatched ? "patched" : "no";
            return "modified=" + modified + " vui=" + vuiPatched + " colr=" + colr
                    + (note == null || note.isEmpty() ? "" : " (" + note + ")");
        }
    }

    private static final class State {
        final boolean pq;
        boolean vuiPatched;
        boolean colrPatched;
        boolean colrInserted;

        State(boolean pq) {
            this.pq = pq;
        }
    }

    private interface BoxModifier {
        byte[] modify(byte[] payload, State state);
    }

    /**
     * Patches {@code file} so the video track advertises full-range BT.2020
     * with the given HDR transfer. Returns what was (not) changed.
     */
    public static Result applyFullRange(File file, boolean pq) {
        State state = new State(pq);
        if (file == null || !file.isFile() || file.length() < 16) {
            return new Result(false, false, false, false, "no file");
        }
        try {
            long fileLength = file.length();
            long[] moov;
            byte[] moovPayload;
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                moov = findTopLevelMoov(raf, fileLength);
                if (moov == null) {
                    return new Result(false, false, false, false, "no moov");
                }
                long moovSize = moov[1];
                long headerSize = moov[2];
                if (moovSize <= headerSize || moovSize > MAX_MOOV_BYTES) {
                    return new Result(false, false, false, false, "moov too large");
                }
                moovPayload = new byte[(int) (moovSize - headerSize)];
                raf.seek(moov[0] + headerSize);
                raf.readFully(moovPayload);
            }
            byte[] patchedPayload = modifyMoov(moovPayload, state);
            if (patchedPayload == null) {
                return new Result(false, state.vuiPatched, state.colrPatched,
                        state.colrInserted, "no video sample entry");
            }
            byte[] newMoov = IsoBmff.buildBox("moov", patchedPayload);
            writeMoov(file, moov[0], moov[1], newMoov, fileLength);
        } catch (Exception e) {
            return new Result(false, state.vuiPatched, state.colrPatched,
                    state.colrInserted, "failed: " + e.getMessage());
        }
        return new Result(true, state.vuiPatched, state.colrPatched, state.colrInserted, "");
    }

    /**
     * Finds the top-level {@code moov} box: {@code [offset, size, headerSize]}.
     * Supports 32-bit and largesize boxes and size 0 (to end of file).
     */
    private static long[] findTopLevelMoov(RandomAccessFile raf, long fileLength)
            throws IOException {
        long pos = 0;
        while (pos + 8 <= fileLength) {
            raf.seek(pos);
            long size = raf.readInt() & 0xFFFFFFFFL;
            byte[] typeBytes = new byte[4];
            raf.readFully(typeBytes);
            String type = new String(typeBytes, StandardCharsets.US_ASCII);
            long header = 8;
            if (size == 1) {
                if (pos + 16 > fileLength) return null;
                size = raf.readLong();
                header = 16;
            } else if (size == 0) {
                size = fileLength - pos;
            }
            if (size < header || pos + size > fileLength) return null;
            if ("moov".equals(type)) return new long[]{pos, size, header};
            pos += size;
        }
        return null;
    }

    private static byte[] modifyMoov(byte[] payload, State state) {
        return modifyContainer(payload, "trak", Mp4ColorRange::modifyTrak, state);
    }

    private static byte[] modifyTrak(byte[] payload, State state) {
        return modifyContainer(payload, "mdia", Mp4ColorRange::modifyMdia, state);
    }

    private static byte[] modifyMdia(byte[] payload, State state) {
        return modifyContainer(payload, "minf", Mp4ColorRange::modifyMinf, state);
    }

    private static byte[] modifyMinf(byte[] payload, State state) {
        return modifyContainer(payload, "stbl", Mp4ColorRange::modifyStbl, state);
    }

    private static byte[] modifyStbl(byte[] payload, State state) {
        return modifyContainer(payload, "stsd", Mp4ColorRange::modifyStsd, state);
    }

    /** Rebuilds a container when the named child changed, else null. */
    private static byte[] modifyContainer(byte[] payload, String childType,
            BoxModifier modifier, State state) {
        List<IsoBmff.Box> boxes;
        try {
            boxes = IsoBmff.parse(payload);
        } catch (Exception e) {
            return null;
        }
        List<byte[]> out = new ArrayList<>(boxes.size());
        boolean changed = false;
        for (IsoBmff.Box box : boxes) {
            byte[] childPayload = null;
            if (childType.equals(box.type)) {
                childPayload = modifier.modify(box.payload, state);
            }
            if (childPayload != null) {
                out.add(IsoBmff.buildBox(box.type, childPayload));
                changed = true;
            } else {
                out.add(IsoBmff.buildBox(box.type, box.payload));
            }
        }
        return changed ? concat(out) : null;
    }

    private static byte[] modifyStsd(byte[] payload, State state) {
        if (payload.length < 8) return null;
        List<IsoBmff.Box> entries;
        try {
            entries = IsoBmff.parse(payload, 8, payload.length - 8);
        } catch (Exception e) {
            return null;
        }
        List<byte[]> out = new ArrayList<>(entries.size() + 1);
        out.add(Arrays.copyOfRange(payload, 0, 8));
        boolean changed = false;
        for (IsoBmff.Box entry : entries) {
            byte[] childPayload = null;
            if (isVideoSampleEntry(entry.type)) {
                childPayload = modifySampleEntry(entry.payload, state);
            }
            if (childPayload != null) {
                out.add(IsoBmff.buildBox(entry.type, childPayload));
                changed = true;
            } else {
                out.add(IsoBmff.buildBox(entry.type, entry.payload));
            }
        }
        return changed ? concat(out) : null;
    }

    private static boolean isVideoSampleEntry(String type) {
        switch (type) {
            case "hvc1":
            case "hev1":
            case "avc1":
            case "avc3":
            case "dvh1":
            case "dvhe":
            case "vp09":
            case "av01":
                return true;
            default:
                return false;
        }
    }

    private static byte[] modifySampleEntry(byte[] payload, State state) {
        if (payload.length < VISUAL_SAMPLE_ENTRY_FIXED) return null;
        List<IsoBmff.Box> children;
        try {
            children = IsoBmff.parse(payload, VISUAL_SAMPLE_ENTRY_FIXED,
                    payload.length - VISUAL_SAMPLE_ENTRY_FIXED);
        } catch (Exception e) {
            return null;
        }
        List<byte[]> out = new ArrayList<>(children.size() + 1);
        out.add(Arrays.copyOfRange(payload, 0, VISUAL_SAMPLE_ENTRY_FIXED));
        boolean changed = false;
        boolean hasColr = false;
        int hvcCIndex = -1;
        for (IsoBmff.Box child : children) {
            if ("hvcC".equals(child.type)) {
                hvcCIndex = out.size();
                byte[] flipped = flipVuiFullRange(child.payload, state);
                if (flipped != null) {
                    out.add(IsoBmff.buildBox("hvcC", flipped));
                    changed = true;
                    continue;
                }
            }
            if ("colr".equals(child.type)) {
                hasColr = true;
                out.add(buildColrBox(state));
                state.colrPatched = true;
                changed = true;
                continue;
            }
            out.add(IsoBmff.buildBox(child.type, child.payload));
        }
        if (!hasColr) {
            byte[] colr = buildColrBox(state);
            if (hvcCIndex >= 0 && hvcCIndex + 1 <= out.size()) {
                out.add(hvcCIndex + 1, colr);
            } else {
                out.add(colr);
            }
            state.colrInserted = true;
            changed = true;
        }
        return changed ? concat(out) : null;
    }

    /** {@code colr}/{@code nclx}: BT.2020 + HLG/PQ + BT.2020 matrix, full range. */
    private static byte[] buildColrBox(State state) {
        ByteBuffer bb = ByteBuffer.allocate(11).order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) 'n');
        bb.put((byte) 'c');
        bb.put((byte) 'l');
        bb.put((byte) 'x');
        bb.putShort((short) PRIMARIES_BT2020);
        bb.putShort((short) (state.pq ? TRANSFER_PQ : TRANSFER_HLG));
        bb.putShort((short) MATRIX_BT2020);
        bb.put((byte) 0x80);
        return IsoBmff.buildBox("colr", bb.array());
    }

    /**
     * Sets the SPS VUI {@code video_full_range_flag} in an {@code hvcC}
     * payload. Returns the same array on success, null when nothing changed
     * (no SPS, no VUI color signature, ambiguous match or already full).
     */
    private static byte[] flipVuiFullRange(byte[] hvcC, State state) {
        try {
            if (hvcC.length < 23) return null;
            int numArrays = hvcC[22] & 0xFF;
            int off = 23;
            for (int a = 0; a < numArrays && off + 3 <= hvcC.length; a++) {
                int nalType = hvcC[off] & 0x3F;
                int numNalus = ((hvcC[off + 1] & 0xFF) << 8) | (hvcC[off + 2] & 0xFF);
                off += 3;
                for (int n = 0; n < numNalus && off + 2 <= hvcC.length; n++) {
                    int nalLen = ((hvcC[off] & 0xFF) << 8) | (hvcC[off + 1] & 0xFF);
                    off += 2;
                    if (nalLen < 3 || off + nalLen > hvcC.length) return null;
                    if (nalType == 33) {
                        Rbsp rbsp = deescape(hvcC, off + 2, nalLen - 2);
                        Integer rangeBit = findRangeBit(rbsp, state.pq);
                        if (rangeBit != null) {
                            int bitInRbspByte = 7 - (rangeBit & 7);
                            int rawByte = rbsp.rawIndex[rangeBit >> 3];
                            if ((hvcC[rawByte] & (1 << bitInRbspByte)) != 0) {
                                return null;
                            }
                            hvcC[rawByte] |= (byte) (1 << bitInRbspByte);
                            state.vuiPatched = true;
                            return hvcC;
                        }
                    }
                    off += nalLen;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private static final class Rbsp {
        final byte[] data;
        /** Raw NAL index of each RBSP byte (emulation-prevention mapped). */
        final int[] rawIndex;

        Rbsp(byte[] data, int[] rawIndex) {
            this.data = data;
            this.rawIndex = rawIndex;
        }
    }

    private static Rbsp deescape(byte[] src, int offset, int length) {
        byte[] out = new byte[length];
        int[] map = new int[length];
        int n = 0;
        for (int i = 0; i < length; i++) {
            if (i >= 2 && src[offset + i - 2] == 0 && src[offset + i - 1] == 0
                    && (src[offset + i] & 0xFF) == 0x03
                    && i + 1 < length && (src[offset + i + 1] & 0xFF) <= 0x03) {
                continue;
            }
            out[n] = src[offset + i];
            map[n] = offset + i;
            n++;
        }
        return new Rbsp(Arrays.copyOf(out, n), Arrays.copyOf(map, n));
    }

    /**
     * Locates {@code video_full_range_flag} in the SPS RBSP by finding its
     * unique VUI color signature:
     * {@code colour_description_present(1) + primaries(9) + transfer + matrix(9)}.
     * The flag is the bit immediately before that signature.
     */
    private static Integer findRangeBit(Rbsp rbsp, boolean pq) {
        int[] signature = new int[25];
        int k = 0;
        signature[k++] = 1;
        k = appendByte(signature, k, PRIMARIES_BT2020);
        k = appendByte(signature, k, pq ? TRANSFER_PQ : TRANSFER_HLG);
        appendByte(signature, k, MATRIX_BT2020);
        int totalBits = rbsp.data.length * 8;
        Integer match = null;
        for (int i = 0; i + signature.length <= totalBits; i++) {
            boolean ok = true;
            for (int j = 0; j < signature.length; j++) {
                if (bitAt(rbsp.data, i + j) != signature[j]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                if (match != null) return null; // ambiguous, leave the file alone
                match = i;
            }
        }
        if (match == null || match < 1) return null;
        return match - 1;
    }

    private static int appendByte(int[] bits, int offset, int value) {
        for (int i = 7; i >= 0; i--) {
            bits[offset++] = (value >> i) & 1;
        }
        return offset;
    }

    private static int bitAt(byte[] data, int bit) {
        return (data[bit >> 3] >> (7 - (bit & 7))) & 1;
    }

    /**
     * Writes the rebuilt {@code moov} back. MediaRecorder always puts moov
     * last, where growing/shrinking it is safe (mdat stays put). A moov that
     * precedes mdat can only be replaced at the same size, because shifting
     * mdat would invalidate the chunk-offset tables.
     */
    private static void writeMoov(File file, long moovOffset, long moovSize,
            byte[] newMoov, long fileLength) throws IOException {
        boolean moovLast = moovOffset + moovSize == fileLength;
        if (!moovLast && newMoov.length != moovSize) {
            throw new IOException("moov not last, size change unsupported");
        }
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            raf.seek(moovOffset);
            raf.write(newMoov);
            if (moovLast) {
                raf.setLength(moovOffset + newMoov.length);
            }
        }
    }

    private static byte[] concat(List<byte[]> parts) {
        int size = 0;
        for (byte[] part : parts) {
            size += part.length;
        }
        byte[] out = new byte[size];
        int pos = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, pos, part.length);
            pos += part.length;
        }
        return out;
    }
}
