package com.particlesdevs.photoncamera.processing;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.capture.ZoomController;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.util.Allocator;

import java.nio.ByteBuffer;

public class ImageFrame {
    public ByteBuffer buffer;
    /**
     * Effective bit depth of {@link #buffer} when the frame is packed
     * (LSB-first bitstream, see {@link Allocator#packBits}); 0 means the
     * buffer holds plain little-endian 16-bit samples.
     */
    public int packedBits = 0;
    public long timestamp;
    public int width, height;
    /** Offset of this frame's crop within the full sensor frame (0,0 when uncropped). */
    public int cropOriginX;
    public int cropOriginY;
    /** Full-frame (uncropped) dimensions; 0 when not derived from a crop. */
    public int fullWidth;
    public int fullHeight;
    public GyroBurst frameGyro;
    public float[][][] BlurKernels;
    public double posx, posy;
    public double rX, rY, rZ;
    public double[] HomographyMatrix;
    public double rotation;
    public int number;
    public IsoExpoSelector.ExpoPair pair;

    public long getTimestamp() {
        return timestamp;
    }

    public ImageFrame(ByteBuffer in, int format, int width, int row_stride, int shift, int capacity) {
        ByteBuffer direct;
        if (Allocator.binning) {
            int height = capacity / row_stride;
            if (format == 0x25) {
                direct = Allocator.allocateAndCopyConvertBinning(capacity, in, width, row_stride, shift);
            } else {
                direct = Allocator.allocateAndCopyBinning(capacity, in, width, height, row_stride);
            }
        } else {
            if(format == 0x25){
                direct = Allocator.allocateAndCopyConvert(capacity, in, width, row_stride, shift);
            } else {
                direct = Allocator.allocateAndCopy(capacity, in, shift);
            }
        }
        direct.position(0);
        buffer = direct;
    }

    public ImageFrame(ByteBuffer in) {
        ByteBuffer direct = Allocator.allocateAndCopy(in.capacity(), in, 0);
        direct.position(0);
        buffer = direct;
    }

    /**
     * Builds an {@link ImageFrame} that contains only a rectangular sub-region
     * of the source frame. The crop is expressed in the source buffer's logical
     * pixel space (the same width/height reported by {@link android.media.Image}).
     * This is the single place where a digital-zoom crop is taken out of the RAW
     * buffer, and because the stored JPEG and DNG both derive from this frame,
     * a single crop crops both outputs.
     *
     * @param in        source (uncropped) buffer
     * @param format    ImageFormat of the source (RAW16 or RAW10 = 0x25)
     * @param srcWidth  logical pixel width of the source
     * @param srcHeight logical pixel height of the source
     * @param rowStride source row stride in bytes
     * @param pixelStride source pixel stride in bytes
     * @param crop      crop rectangle in source pixel space (clamped inside)
     * @param binning   whether to additionally apply 2x2 Bayer binning
     * @return a frame whose buffer is the tightly-packed cropped region
     */
    public static ImageFrame fromCrop(ByteBuffer in, int format, int srcWidth, int srcHeight,
                                      int rowStride, int pixelStride, Rect crop, boolean binning) {
        int left = Math.max(0, crop.left);
        int top = Math.max(0, crop.top);
        int right = Math.min(srcWidth, crop.right);
        int bottom = Math.min(srcHeight, crop.bottom);
        int cropW = Math.max(0, right - left);
        int cropH = Math.max(0, bottom - top);
        if (cropW <= 0 || cropH <= 0) {
            return new ImageFrame(in, format, srcWidth, rowStride, 0, in.capacity());
        }
        left = cropW % 2 == 0 ? left : Math.max(0, left + 1);
        top = cropH % 2 == 0 ? top : Math.max(0, top + 1);
        right = left + (cropW % 2 == 0 ? cropW : cropW - 1);
        bottom = top + (cropH % 2 == 0 ? cropH : cropH - 1);
        cropW = right - left;
        cropH = bottom - top;
        if (format == 0x25 || binning) {
            cropW &= ~3;
            cropH &= ~3;
            if (cropW < 4) cropW = 4;
            if (cropH < 4) cropH = 4;
            left &= ~3;
            top &= ~3;
        }

        synchronized (Allocator.class) { Allocator.binning = binning; }
        ByteBuffer direct;
        if (format == 0x25) {
            // RAW10 packed (4 px = 5 bytes): align to 4-pixel groups.
            int colPx = (left / 4) * 4;
            int shift = rowStride * top + (colPx / 4) * 5;
            int cap = rowStride * cropH;
            if (binning) {
                direct = Allocator.allocateAndCopyConvertBinning(cap, in, cropW, rowStride, shift);
            } else {
                direct = Allocator.allocateAndCopyConvert(cap, in, cropW, rowStride, shift);
            }
        } else {
            // RAW16 (2 bytes/pixel)
            int shift = rowStride * top + left * 2;
            if (binning) {
                direct = Allocator.allocateAndCopyCropBinning(cropW, cropH, in, rowStride, shift);
            } else {
                direct = Allocator.allocateAndCopyCrop(cropW * 2, cropH, in, rowStride, shift);
            }
        }
        if (direct == null) return null;
        direct.position(0);

        ImageFrame frame = new ImageFrame();
        frame.buffer = direct;
        frame.cropOriginX = left;
        frame.cropOriginY = top;
        if (binning) {
            frame.width = cropW / 2;
            frame.height = cropH / 2;
        } else {
            frame.width = cropW;
            frame.height = cropH;
        }
        return frame;
    }

    /**
     * Convenience overload of {@link #fromCrop(ByteBuffer, int, int, int, int, int, Rect, boolean)}
     * that takes a pre-computed {@link ZoomController.CropRegion} (which carries the
     * crop origin and dimensions) instead of a {@link Rect}.
     */
    public static ImageFrame fromCrop(ByteBuffer in, int format, int srcWidth, int srcHeight,
                                      int rowStride, int pixelStride,
                                      ZoomController.CropRegion crop, boolean binning) {
        Rect rect = new Rect(crop.originX, crop.originY,
                crop.originX + crop.width, crop.originY + crop.height);
        ImageFrame frame = fromCrop(in, format, srcWidth, srcHeight, rowStride, pixelStride, rect, binning);
        if (frame == null) return null;
        frame.fullWidth = crop.fullWidth;
        frame.fullHeight = crop.fullHeight;
        return frame;
    }

    private ImageFrame() {
    }

    /**
     * Single-slot cache for the 10-bit unpack staging buffer. Every upload in
     * a shot is same-sized and strictly serialized (each try-with-resources
     * closes before the next opens, all on the GL thread), so one cached
     * buffer removes ~20 malloc/munmap + page-fault cycles per shot with an
     * identical peak: only one upload is ever in flight. The cache is
     * released at phase end ({@link #releaseUploadStaging()}) so tracked
     * memory returns to baseline outside the merge phase. Bytes produced are
     * identical to a fresh allocation (same unpack into same size).
     */
    private static ByteBuffer sStagingCache = null;

    private static synchronized ByteBuffer acquireUploadStaging(int bytes) {
        if (sStagingCache != null) {
            if (sStagingCache.capacity() == bytes) {
                ByteBuffer buf = sStagingCache;
                sStagingCache = null;
                buf.clear();
                return buf;
            }
            Allocator.free(sStagingCache);
            sStagingCache = null;
        }
        return Allocator.allocate(bytes);
    }

    private static synchronized void cacheUploadStaging(ByteBuffer buf) {
        if (buf == null) return;
        if (sStagingCache == null) {
            sStagingCache = buf;
        } else {
            // Concurrent or nested upload (not expected today): stay correct
            // by freeing instead of caching a second buffer.
            Allocator.free(buf);
        }
    }

    /** Drops the cached staging buffer, if any. Idempotent. */
    public static synchronized void releaseUploadStaging() {
        if (sStagingCache != null) {
            Allocator.free(sStagingCache);
            sStagingCache = null;
        }
    }

    /**
     * Packs a burst frame at arrival (exact inverse of {@link #upload()}'s
     * unpack; same guards and bit formula as HdrxProcessor's pack loop, which
     * stays as the fallback for any unpacked leftovers). No-op unless the
     * buffer is a tightly packed 16-bit frame and whiteLevel yields a valid
     * sub-16-bit depth. Never throws and never corrupts: a failed pack keeps
     * the 16-bit buffer untouched.
     */
    public static void packBurstAtArrival(ImageFrame frame, int whiteLevel, boolean verify) {
        if (frame == null || frame.buffer == null || frame.packedBits > 0) return;
        if (frame.buffer.capacity() != frame.width * frame.height * 2) return;
        int packBits = whiteLevel > 0 ? 32 - Integer.numberOfLeadingZeros(whiteLevel) : 0;
        if (packBits <= 0 || packBits >= 16) return;
        ByteBuffer packed = Allocator.packBits(frame.buffer, frame.width * frame.height,
                packBits, verify);
        if (packed == null) return;
        Allocator.free(frame.buffer);
        frame.buffer = packed;
        frame.packedBits = packBits;
    }

    /**
     * View of this frame's pixels for GL uploads. When the frame is packed,
     * unpacks it into a tightly-packed 16-bit native buffer that the
     * caller must release with {@link Upload#close()} (try-with-resources);
     * unpacked frames return their own buffer and free nothing.
     */
    public Upload upload() {
        if (packedBits <= 0) {
            return new Upload(buffer, false);
        }
        int pixels = width * height;
        ByteBuffer staging = acquireUploadStaging(pixels * 2);
        if (staging == null) {
            throw new IllegalStateException("Packed frame unpack allocation failed ("
                    + pixels * 2 + " B)");
        }
        Allocator.unpack16(staging, buffer, pixels, packedBits);
        return new Upload(staging, true);
    }

    /** Upload buffer plus ownership of a possible unpack staging copy. */
    public static final class Upload implements AutoCloseable {
        public final ByteBuffer buffer;
        private final boolean temporary;

        Upload(ByteBuffer buffer, boolean temporary) {
            this.buffer = buffer;
            this.temporary = temporary;
        }

        @Override
        public void close() {
            if (temporary && buffer != null) {
                cacheUploadStaging(buffer);
            }
        }
    }

    public void close() {
        if (buffer != null) {
            Allocator.free(buffer);
            buffer = null;
        } else {
            Log.d("ImageFrame", "Buffer is already null, nothing to close.");
        }
    }
}
