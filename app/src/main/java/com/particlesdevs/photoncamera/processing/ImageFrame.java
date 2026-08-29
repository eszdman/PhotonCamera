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
        // Keep crop dims even so a 2x2 Bayer binning (which halves them) never
        // produces an odd size, which the demosaicers require to be even.
        left = cropW % 2 == 0 ? left : Math.max(0, left + 1);
        top = cropH % 2 == 0 ? top : Math.max(0, top + 1);
        right = left + (cropW % 2 == 0 ? cropW : cropW - 1);
        bottom = top + (cropH % 2 == 0 ? cropH : cropH - 1);
        cropW = right - left;
        cropH = bottom - top;

        Allocator.binning = binning;
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
        if (direct == null) direct = Allocator.allocateAndCopy(in.capacity(), in, 0);
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
        frame.cropOriginX = crop.originX;
        frame.cropOriginY = crop.originY;
        frame.fullWidth = crop.fullWidth;
        frame.fullHeight = crop.fullHeight;
        return frame;
    }

    private ImageFrame() {
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
