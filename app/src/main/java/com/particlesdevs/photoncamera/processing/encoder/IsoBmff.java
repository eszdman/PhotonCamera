package com.particlesdevs.photoncamera.processing.encoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal ISO-BMFF box reader/writer for the manual HEIF gain-map mux.
 *
 * <p>Supports 32-bit sizes, 64-bit largesize, FullBox version/flags and
 * opaque child-box splitting. Anything unexpected throws — callers fall back
 * to SDR HEIC so a mux bug can never lose a shot.
 */
public final class IsoBmff {

    private IsoBmff() {}

    public static final class Box {
        public final String type;
        public final byte[] payload;
        /** Full header size (8 or 16) — needed to recompute data offsets. */
        public final int headerSize;

        public Box(String type, byte[] payload, int headerSize) {
            this.type = type;
            this.payload = payload;
            this.headerSize = headerSize;
        }
    }

    /** Splits a byte range into top-level (or child) boxes. */
    public static List<Box> parse(byte[] data, int offset, int length) {
        List<Box> out = new ArrayList<>();
        int pos = offset;
        int end = offset + length;
        while (pos + 8 <= end) {
            long size = u32(data, pos);
            String type = fourcc(data, pos + 4);
            int header = 8;
            if (size == 1) {
                if (pos + 16 > end) {
                    throw new IllegalArgumentException("Truncated largesize box: " + type);
                }
                size = u64(data, pos + 8);
                header = 16;
            } else if (size == 0) {
                size = end - pos; // to end of container
            }
            if (size < header || pos + size > end) {
                throw new IllegalArgumentException("Bad box size for " + type + ": " + size);
            }
            byte[] payload = new byte[(int) size - header];
            System.arraycopy(data, pos + header, payload, 0, payload.length);
            out.add(new Box(type, payload, header));
            pos += (int) size;
        }
        if (pos != end) {
            throw new IllegalArgumentException("Trailing " + (end - pos) + " bytes after boxes");
        }
        return out;
    }

    public static List<Box> parse(byte[] data) {
        return parse(data, 0, data.length);
    }

    public static byte[] buildBox(String type, byte[] payload) {
        long size = 8L + (payload == null ? 0 : payload.length);
        ByteBuffer bb;
        if (size > 0xFFFFFFFFL) {
            bb = ByteBuffer.allocate(16 + (payload == null ? 0 : payload.length));
            bb.order(ByteOrder.BIG_ENDIAN);
            bb.putInt(1);
            bb.put(fourccBytes(type));
            bb.putLong(size + 8);
        } else {
            bb = ByteBuffer.allocate((int) size);
            bb.order(ByteOrder.BIG_ENDIAN);
            bb.putInt((int) size);
            bb.put(fourccBytes(type));
        }
        if (payload != null) {
            bb.put(payload);
        }
        return bb.array();
    }

    /**
     * Builds a box from a list of payload chunks with a single allocation:
     * unlike {@code buildBox(type, concat(parts))} this never materializes the
     * concatenated payload first, which matters for the multi-megabyte mdat
     * and meta boxes of the HEIC mux.
     */
    public static byte[] buildBox(String type, List<byte[]> parts) {
        long payload = 0;
        if (parts != null) {
            for (byte[] p : parts) {
                if (p != null) {
                    payload += p.length;
                }
            }
        }
        long size = 8L + payload;
        if (payload > Integer.MAX_VALUE || size > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Box " + type + " too large: " + size);
        }
        ByteBuffer bb = ByteBuffer.allocate((int) size).order(ByteOrder.BIG_ENDIAN);
        bb.putInt((int) size);
        bb.put(fourccBytes(type));
        if (parts != null) {
            for (byte[] p : parts) {
                if (p != null) {
                    bb.put(p);
                }
            }
        }
        return bb.array();
    }

    public static byte[] fullBoxPayload(int version, int flags, byte[] body) {
        ByteBuffer bb = ByteBuffer.allocate(4 + (body == null ? 0 : body.length));
        bb.order(ByteOrder.BIG_ENDIAN);
        bb.put((byte) version);
        bb.put((byte) ((flags >> 16) & 0xFF));
        bb.put((byte) ((flags >> 8) & 0xFF));
        bb.put((byte) (flags & 0xFF));
        if (body != null) {
            bb.put(body);
        }
        return bb.array();
    }

    public static int fullVersion(byte[] fullBoxPayload) {
        return fullBoxPayload[0] & 0xFF;
    }

    public static byte[] concat(List<byte[]> parts) {
        long total = 0;
        for (byte[] p : parts) {
            if (p != null) {
                total += p.length;
            }
        }
        if (total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("concat too large: " + total);
        }
        byte[] out = new byte[(int) total];
        int pos = 0;
        for (byte[] p : parts) {
            if (p != null) {
                System.arraycopy(p, 0, out, pos, p.length);
                pos += p.length;
            }
        }
        return out;
    }

    public static long u32(byte[] d, int o) {
        return ((d[o] & 0xFFL) << 24) | ((d[o + 1] & 0xFFL) << 16)
                | ((d[o + 2] & 0xFFL) << 8) | (d[o + 3] & 0xFFL);
    }

    public static long u64(byte[] d, int o) {
        long hi = u32(d, o);
        long lo = u32(d, o + 4);
        return (hi << 32) | lo;
    }

    public static int u16(byte[] d, int o) {
        return ((d[o] & 0xFF) << 8) | (d[o + 1] & 0xFF);
    }

    public static void putU32(ByteBuffer bb, long v) {
        bb.putInt((int) (v & 0xFFFFFFFFL));
    }

    public static void putU16(ByteBuffer bb, int v) {
        bb.putShort((short) (v & 0xFFFF));
    }

    public static String fourcc(byte[] d, int o) {
        return new String(new byte[]{d[o], d[o + 1], d[o + 2], d[o + 3]}, StandardCharsets.US_ASCII);
    }

    public static byte[] fourccBytes(String type) {
        byte[] b = type.getBytes(StandardCharsets.US_ASCII);
        if (b.length != 4) {
            throw new IllegalArgumentException("Not a fourcc: " + type);
        }
        return b;
    }

    public static byte[] u32Bytes(long v) {
        ByteBuffer bb = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
        bb.putInt((int) (v & 0xFFFFFFFFL));
        return bb.array();
    }

    public static int indexOfType(List<Box> boxes, String type) {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).type.equals(type)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @return {dataStart, dataLength} of the first top-level box with the
     * given type, or null when absent. Handles 32-bit, largesize and
     * to-end (size 0) boxes.
     */
    public static long[] boxDataRange(byte[] file, String type) {
        int pos = 0;
        while (pos + 8 <= file.length) {
            long size = u32(file, pos);
            String t = fourcc(file, pos + 4);
            int header = 8;
            if (size == 1) {
                if (pos + 16 > file.length) {
                    return null;
                }
                size = u64(file, pos + 8);
                header = 16;
            } else if (size == 0) {
                size = file.length - pos;
            }
            if (size < header || pos + size > file.length) {
                return null;
            }
            if (t.equals(type)) {
                return new long[]{pos + header, size - header};
            }
            long next = (long) pos + size;
            if (next > Integer.MAX_VALUE) {
                return null;
            }
            pos = (int) next;
        }
        return null;
    }
}
