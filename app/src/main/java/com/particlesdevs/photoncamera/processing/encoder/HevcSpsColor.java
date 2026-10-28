package com.particlesdevs.photoncamera.processing.encoder;

import com.particlesdevs.photoncamera.util.Log;

import java.io.ByteArrayOutputStream;

/**
 * Rewrites the HEVC SPS VUI color description inside HEIF {@code hvcC}
 * properties.
 *
 * <p>The HEVC encoder is configured with BT.709 color aspects because
 * {@code MediaFormat} has no Display P3 enum. The container-level {@code colr}
 * property is rewritten to Display P3 elsewhere, but some readers (and
 * MediaInfo-style metadata displays) report the SPS VUI instead. For P3 HEIC
 * output both descriptions must agree.
 *
 * <p>Only the bit values of the VUI color description are rewritten, so the
 * SPS RBSP length and every BMFF box/offset stay unchanged. The patched RBSP
 * is re-escaped and checked against the original payload length; if the patch
 * would change the escaped representation, the SPS is left untouched rather
 * than corrupting the stream.
 */
final class HevcSpsColor {

    private static final String TAG = "HevcSpsColor";
    private static final int NAL_TYPE_SPS = 33;
    private static final int PRIMARIES_DISPLAY_P3 = 12;
    private static final int TRANSFER_SRGB = 13;
    private static final int MATRIX_BT709 = 1;

    private HevcSpsColor() {}

    /**
     * Patches every SPS VUI color description found in every {@code hvcC}
     * property under the top-level {@code meta} boxes.
     *
     * @return true when at least one SPS was changed or already correct
     */
    static boolean patchDisplayP3(byte[] heic) {
        if (heic == null || heic.length < 8) {
            return false;
        }
        return patchBoxes(heic, 0, heic.length);
    }

    private static boolean patchBoxes(byte[] data, int start, int end) {
        boolean touched = false;
        int pos = start;
        while (pos + 8 <= end) {
            long size = IsoBmff.u32(data, pos);
            int header = 8;
            if (size == 1) {
                if (pos + 16 > end) {
                    return touched;
                }
                size = IsoBmff.u64(data, pos + 8);
                header = 16;
            } else if (size == 0) {
                size = end - pos;
            }
            if (size < header || pos + size > end) {
                return touched;
            }
            String type = IsoBmff.fourcc(data, pos + 4);
            int payloadStart = pos + header;
            int payloadEnd = (int) (pos + size);
            if (type.equals("hvcC")) {
                touched |= patchHvcC(data, payloadStart, payloadEnd);
            } else if (type.equals("meta")) {
                // meta is a FullBox: version/flags precede the child boxes.
                if (payloadStart + 4 <= payloadEnd) {
                    touched |= patchBoxes(data, payloadStart + 4, payloadEnd);
                }
            } else if (type.equals("iprp") || type.equals("ipco")) {
                touched |= patchBoxes(data, payloadStart, payloadEnd);
            }
            pos += (int) size;
        }
        return touched;
    }

    /**
     * Package-private entry point for tests: patches one hvcC payload.
     */
    static boolean patchHvcCPayload(byte[] hvcC) {
        return patchHvcC(hvcC, 0, hvcC.length);
    }

    private static boolean patchHvcC(byte[] data, int start, int end) {
        if (end - start < 23) {
            return false;
        }
        int numArrays = data[start + 22] & 0xFF;
        int off = start + 23;
        boolean touched = false;
        for (int a = 0; a < numArrays && off + 3 <= end; a++) {
            int nalType = data[off] & 0x3F;
            int numNalus = IsoBmff.u16(data, off + 1);
            off += 3;
            for (int n = 0; n < numNalus && off + 2 <= end; n++) {
                int nalLen = IsoBmff.u16(data, off);
                off += 2;
                if (nalLen < 3 || off + nalLen > end) {
                    return touched;
                }
                if (nalType == NAL_TYPE_SPS) {
                    touched |= patchSps(data, off, nalLen);
                }
                off += nalLen;
            }
        }
        return touched;
    }

    private static boolean patchSps(byte[] data, int nalStart, int nalLength) {
        int rbspStart = nalStart + 2;
        int rbspLength = nalLength - 2;
        byte[] original = new byte[rbspLength];
        System.arraycopy(data, rbspStart, original, 0, rbspLength);

        byte[] rbsp = deescape(original);
        int match = findColorDescription(rbsp, 1, 1, 1);
        boolean alreadyP3 = false;
        if (match < 0) {
            match = findColorDescription(rbsp, PRIMARIES_DISPLAY_P3,
                    TRANSFER_SRGB, MATRIX_BT709);
            alreadyP3 = match >= 0;
        }
        if (match < 1) {
            return false;
        }

        boolean changed = false;
        // video_full_range_flag is the bit immediately before
        // colour_description_present_flag; the still encoder writes full-range
        // YUV.
        if (bitAt(rbsp, match - 1) == 0) {
            setBit(rbsp, match - 1, 1);
            changed = true;
        }
        if (!alreadyP3) {
            int bit = match + 1;
            bit = writeByte(rbsp, bit, PRIMARIES_DISPLAY_P3);
            bit = writeByte(rbsp, bit, TRANSFER_SRGB);
            writeByte(rbsp, bit, MATRIX_BT709);
            changed = true;
        }
        if (!changed) {
            return true; // already correct
        }

        byte[] escaped = escape(rbsp);
        if (escaped.length != rbspLength) {
            Log.w(TAG, "SPS VUI patch would change escaped length ("
                    + rbspLength + " -> " + escaped.length + "); leaving SPS unchanged");
            return false;
        }
        System.arraycopy(escaped, 0, data, rbspStart, rbspLength);
        return true;
    }

    /**
     * Finds the unique VUI color description signature
     * {@code colour_description_present(1) + primaries(8) + transfer(8) + matrix(8)}.
     * Returns the bit index of the present flag, or -1 when absent/ambiguous.
     */
    private static int findColorDescription(byte[] rbsp, int primaries,
            int transfer, int matrix) {
        int[] signature = new int[25];
        int k = 0;
        signature[k++] = 1;
        k = appendByte(signature, k, primaries);
        k = appendByte(signature, k, transfer);
        appendByte(signature, k, matrix);
        int totalBits = rbsp.length * 8;
        int match = -1;
        for (int i = 0; i + signature.length <= totalBits; i++) {
            boolean ok = true;
            for (int j = 0; j < signature.length; j++) {
                if (bitAt(rbsp, i + j) != signature[j]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                if (match >= 0) {
                    return -1; // ambiguous, leave the stream alone
                }
                match = i;
            }
        }
        return match;
    }

    private static byte[] deescape(byte[] src) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(src.length);
        for (int i = 0; i < src.length; i++) {
            if (i >= 2 && src[i - 2] == 0 && src[i - 1] == 0
                    && (src[i] & 0xFF) == 0x03
                    && i + 1 < src.length && (src[i + 1] & 0xFF) <= 0x03) {
                continue;
            }
            out.write(src[i]);
        }
        return out.toByteArray();
    }

    /** Re-applies HEVC emulation-prevention bytes to an RBSP. */
    private static byte[] escape(byte[] rbsp) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(rbsp.length + 8);
        int zeros = 0;
        for (byte value : rbsp) {
            int b = value & 0xFF;
            if (zeros >= 2 && b <= 0x03) {
                out.write(0x03);
                zeros = 0;
            }
            out.write(b);
            zeros = b == 0 ? zeros + 1 : 0;
        }
        return out.toByteArray();
    }

    private static int writeByte(byte[] rbsp, int bit, int value) {
        for (int i = 7; i >= 0; i--) {
            setBit(rbsp, bit++, (value >> i) & 1);
        }
        return bit;
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

    private static void setBit(byte[] data, int bit, int value) {
        int index = bit >> 3;
        int mask = 1 << (7 - (bit & 7));
        if (value != 0) {
            data[index] |= (byte) mask;
        } else {
            data[index] &= (byte) ~mask;
        }
    }
}
