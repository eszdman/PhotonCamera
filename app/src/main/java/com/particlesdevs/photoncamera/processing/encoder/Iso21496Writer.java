package com.particlesdevs.photoncamera.processing.encoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * ISO 21496-1 gain-map binary metadata writer (no external dependency).
 *
 * <p>Layout reimplemented from the documented wire format (as implemented by
 * the reference encoder/decoder ecosystem): a {@code tmap} item's data is
 * one version byte ({@code 0x00}) followed by:
 * <pre>
 *   u16 min_version (=0), u16 writer_version (=0), u8 flags,
 *   u32 baseHdrN, u32 baseHdrD, u32 altHdrN, u32 altHdrD,
 *   per channel (single channel when all identical):
 *     s32 minN, u32 minD, s32 maxN, u32 maxD,
 *     u32 gammaN, u32 gammaD,
 *     s32 baseOffN, u32 baseOffD, s32 altOffN, u32 altOffD
 * </pre>
 * Flags: bit7 = multi-channel, bit6 = use-base-color-space. The plain
 * (explicit-denominator) form is emitted, matching what real-world
 * Apple/libavif/Lightroom files carry.
 *
 * <p>Value mapping (log2-domain, same numbers as the hdrgm XMP):
 * min/max = log2 content boost, gamma as-is, offsets as-is,
 * base headroom = log2(hdr_capacity_min ratio), alternate headroom =
 * log2(hdr_capacity_max ratio).
 */
public final class Iso21496Writer {

    /** Version byte prefixing every tmap payload (Hasselblad omits it). */
    public static final byte TMAP_VERSION = 0;
    private static final int FLAG_MULTI_CHANNEL = 0x80;
    private static final int FLAG_USE_BASE_COLOR_SPACE = 0x40;
    private static final long MAX_DENOMINATOR = 100000L;

    private Iso21496Writer() {}

    /**
     * @param gainMin   log2 minimum boost (0 for our single-channel maps)
     * @param gainMax   log2 maximum boost
     * @param hdrCap    log2 HDR capacity (alternate headroom)
     * @return tmap item payload: version byte + ISO binary metadata.
     */
    public static byte[] tmapPayload(float gainMin, float gainMax, float hdrCap) {
        if (!Float.isFinite(gainMin)) {
            gainMin = 0f;
        }
        if (!Float.isFinite(gainMax) || gainMax < gainMin) {
            gainMax = Math.max(gainMin, 1e-3f);
        }
        if (!Float.isFinite(hdrCap) || hdrCap < 0f) {
            hdrCap = Math.max(gainMax, 1e-3f);
        }
        long[] min = toFraction(gainMin);
        long[] max = toFraction(gainMax);
        long[] alt = toFraction(hdrCap);
        ByteBuffer bb = ByteBuffer.allocate(1 + 2 + 2 + 1 + 16 + 40)
                .order(ByteOrder.BIG_ENDIAN);
        bb.put(TMAP_VERSION);
        bb.putShort((short) 0); // min_version
        bb.putShort((short) 0); // writer_version
        bb.put((byte) FLAG_USE_BASE_COLOR_SPACE); // single channel, base space
        bb.putInt(0); // baseHdrN = log2(1.0)
        bb.putInt(1); // baseHdrD
        bb.putInt((int) alt[0]);
        bb.putInt((int) alt[1]);
        bb.putInt((int) min[0]);
        bb.putInt((int) min[1]);
        bb.putInt((int) max[0]);
        bb.putInt((int) max[1]);
        bb.putInt(1); // gammaN
        bb.putInt(1); // gammaD
        bb.putInt(1); // baseOffN = 1/64
        bb.putInt(64); // baseOffD
        bb.putInt(1); // altOffN = 1/64
        bb.putInt(64); // altOffD
        return bb.array();
    }

    /**
     * Continued-fraction rational approximation, exact for integers and
     * dyadic rationals (1/64), best-effort otherwise. Returns {n, d} with
     * d in [1, MAX_DENOMINATOR]; n fits s32 for sane photographic values.
     */
    static long[] toFraction(double value) {
        if (!Double.isFinite(value)) {
            value = 0;
        }
        boolean neg = value < 0;
        double x = Math.abs(value);
        long n0 = 0, n1 = 1, d0 = 1, d1 = 0;
        while (true) {
            long a = (long) Math.floor(x);
            long n2 = a * n1 + n0;
            long d2 = a * d1 + d0;
            if (d2 > MAX_DENOMINATOR || d2 <= 0) {
                break;
            }
            n0 = n1;
            n1 = n2;
            d0 = d1;
            d1 = d2;
            double frac = x - a;
            if (frac == 0 || !Double.isFinite(1.0 / frac)) {
                break;
            }
            x = 1.0 / frac;
        }
        long n = neg ? -n1 : n1;
        long d = d1 <= 0 ? 1 : d1;
        return new long[]{n, d};
    }
}
