package com.particlesdevs.photoncamera.util;

import android.graphics.Bitmap;
import android.os.Debug;

import com.particlesdevs.photoncamera.app.PhotonCamera;

import java.nio.ByteBuffer;
public class Allocator{
    static {
        System.loadLibrary("allocator");
    }

    public static volatile boolean binning = false;

    public native static ByteBuffer allocate(int capacity);

    /**
     * Locks a software ARGB_8888 bitmap and wraps its pixel memory in a
     * direct ByteBuffer for direct GL readback. Returns null if the bitmap
     * cannot be wrapped (wrong config/stride or lock failure). The buffer
     * must NOT be passed to {@link #free}; release it with
     * {@link #unlockBitmap} instead.
     */
    public native static ByteBuffer wrapBitmap(Bitmap bitmap);

    public native static boolean unlockBitmap(Bitmap bitmap);

    public native static ByteBuffer allocateAndCopy(int capacity, ByteBuffer origin, int offset);
    /**
     * Copies a rectangular sub-region of a 2D image row by row, supporting an
     * arbitrary XY offset (unlike {@link #allocateAndCopy(int, ByteBuffer, int)}).
     * Output is tightly packed (no stride padding).
     *
     * @param cropWidthBytes bytes to copy per row (cropWidth * bytesPerPixel)
     * @param cropHeight     number of rows to copy
     * @param origin         source buffer (full frame)
     * @param row_stride     source row stride in bytes
     * @param offset         byte offset to the crop's top-left corner
     */
    public native static ByteBuffer allocateAndCopyCrop(int cropWidthBytes, int cropHeight, ByteBuffer origin, int row_stride, int offset);
    public native static ByteBuffer allocateAndCopyCropBinning(int cropWidth, int cropHeight, ByteBuffer origin, int row_stride, int offset);
    public native static ByteBuffer allocateAndCopyConvert(int capacity, ByteBuffer origin, int width, int row_stride, int offset);
    public native static ByteBuffer allocateAndCopyConvertBinning(int capacity, ByteBuffer origin, int width, int row_stride, int offset);
    public native static ByteBuffer allocateAndCopyBinning(int capacity, ByteBuffer origin, int width, int height, int row_stride);

    /**
     * Converts a packed uint16 raw frame (width*height, sensor site order
     * (x&1)+(y&1)*2) into a NEW direct ByteBuffer of white/black-level
     * normalized fp16 samples, ready for FLOAT_16 GL texture upload.
     * Implemented by the RawF16 class (rawF16.cpp, NEON-vectorized); free
     * with {@link #free}.
     */
    public native static ByteBuffer createF16(ByteBuffer origin, int width, int height, int whiteLevel, float[] blackLevel);

    /**
     * Inverse of {@link #createF16}: re-encodes a normalized fp16 buffer back
     * into raw uint16 counts (u = round(clamp(f,0,1)*(wl-bl[site])+bl[site]))
     * in a NEW direct ByteBuffer, e.g. for the uint16 DNG save path.
     */
    public native static ByteBuffer createU16FromF16(ByteBuffer origin, int width, int height, int whiteLevel, float[] blackLevel);

    /**
     * Packs a tightly-packed 16-bit frame at its effective bit depth
     * (the low {@code ceil(log2(whiteLevel+1))} bits of every sample) into a
     * new native buffer as an LSB-first bitstream. Returns null on failure
     * (caller keeps the original). When {@code verify} is set the stream is
     * unpacked and byte-compared against the source before returning, so a
     * mismatch cannot silently corrupt a frame.
     */
    public native static ByteBuffer packBits(ByteBuffer src, int pixels, int bits, boolean verify);

    /** Inverse of {@link #packBits}: writes {@code pixels} little-endian shorts into {@code dst}. */
    public native static void unpack16(ByteBuffer dst, ByteBuffer packed, int pixels, int bits);

    /**
     * Fused {@link #unpack16} + {@link #createF16} for 10-bit packed frames:
     * decodes the bitstream in cache-resident chunks and writes normalized
     * fp16 directly into {@code dst} ({@code width*height*2} bytes), avoiding
     * the 2-byte-per-pixel unpack staging buffer and its round trip through
     * memory. Returns false (caller falls back to unpack16 + createF16) when
     * the buffers are undersized or the geometry is invalid. Output is
     * bit-identical to the two-step path: same 10-bit regrouping, same
     * per-site black/white normalization.
     */
    public native static boolean unpackNormalizeF16TenBit(ByteBuffer dst, ByteBuffer packed,
                                                          int width, int height,
                                                          float whiteLevel, float[] blackLevel);

    /**
     * Converts one tile of tightly packed RGBA8888 into 8-bit YUV420
     * full-range BT.709 (2x2 box-averaged chroma), writing the Y/U/V plane
     * buffers of an encoder input {@code Image} (strides in bytes). Edge
     * tiles replicate the last valid row/column.
     */
    public native static void rgbaToYuv420Tile(ByteBuffer src,
            int fullWidth, int fullHeight, int tileX, int tileY,
            int tileWidth, int tileHeight,
            ByteBuffer yPlane, int yRowStride, int yPixelStride,
            ByteBuffer uPlane, int uRowStride, int uPixelStride,
            ByteBuffer vPlane, int vRowStride, int vPixelStride);

    public native static void free(ByteBuffer buffer);
    public native static long getMemoryCount();

    /**
     * Per-shot peak of any MemStage entry (max MB value seen across
     * tracked/nativeHeap/dalvikHeap samples). Reset at processing start,
     * read before encode for EXIF. No new stages; fed by logStage() only.
     * DEBUG-only: release builds neither sample nor track.
     */
    private static long sPeakMemoryBytes = 0;

    public static void resetPeakMemory() {
        if (!PhotonCamera.DEBUG) {
            return;
        }
        synchronized (Allocator.class) {
            sPeakMemoryBytes = 0;
        }
    }

    public static long getPeakMemoryMB() {
        if (!PhotonCamera.DEBUG) {
            return 0;
        }
        synchronized (Allocator.class) {
            return sPeakMemoryBytes / 1048576;
        }
    }

    /**
     * Logs current native memory totals with a pipeline-stage label so peak
     * usage can be compared across stages, frame counts and resolutions.
     * Logging only; zero behavior change.
     *
     * <p>Two numbers: the Allocator-tracked malloc total (burst buffers,
     * snapshots, staging) and the whole-process native heap (which also
     * covers Bitmap pixels and other native allocations that bypass
     * Allocator). GPU texture (VRAM) usage is tracked by neither.
     *
     * <p>Also feeds the per-shot PeakMemory tracker (see sPeakMemoryBytes):
     * the highest MB value of this entry becomes the new peak if larger.
     *
     * <p>DEBUG-only: no-op in release builds (sampling walks native/debug
     * memory APIs and every line is flushed to the PhotonLog file).
     */
    public static void logStage(String logTag, String stage) {
        if (!PhotonCamera.DEBUG) {
            return;
        }
        long tracked = getMemoryCount();
        long heap = Debug.getNativeHeapAllocatedSize();
        long dalvik = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long samplePeak = Math.max(tracked, Math.max(heap, dalvik));
        synchronized (Allocator.class) {
            if (samplePeak > sPeakMemoryBytes) {
                sPeakMemoryBytes = samplePeak;
            }
        }
        Log.d(logTag, "MemStage[" + stage + "] tracked=" + (tracked / 1048576)
                + "MB nativeHeap=" + (heap / 1048576)
                + "MB dalvikHeap=" + (dalvik / 1048576) + "MB");
    }

    /**
     * Logs whole-process memory truth with a pipeline-stage label: device-wide
     * free/low-memory state plus the Dalvik/native/other PSS split from
     * Debug.getMemoryInfo. Also tracks a self-measured high-water proxy (max
     * over samples of native heap + dalvik heap + live GPU textures +
     * renderbuffers) — monotonic within the process, missing only opaque
     * codec/mmap residents. Context comes from the app singleton; everything
     * is guarded and never throws. Logging only; zero behavior change.
     *
     * <p>Deliberately not /proc/self/status: modern SELinux policies deny
     * procfs reads to apps, so those fields would log garbage forever.
     *
     * <p>DEBUG-only: no-op in release builds.
     */
    private static long sHwmProxy = 0;

    public static void logProc(String logTag, String stage) {
        if (!PhotonCamera.DEBUG) {
            return;
        }
        long devFree = -1;
        boolean devLow = false;
        try {
            android.content.Context ctx =
                    com.particlesdevs.photoncamera.app.PhotonCamera.getAppContext();
            if (ctx != null) {
                android.app.ActivityManager am = (android.app.ActivityManager)
                        ctx.getSystemService(android.content.Context.ACTIVITY_SERVICE);
                if (am != null) {
                    android.app.ActivityManager.MemoryInfo mi = new android.app.ActivityManager.MemoryInfo();
                    am.getMemoryInfo(mi);
                    devFree = mi.availMem;
                    devLow = mi.lowMemory;
                }
            }
        } catch (Throwable ignored) {
        }
        int pss = -1, dPss = -1, nPss = -1, oPss = -1;
        try {
            Debug.MemoryInfo mi = new Debug.MemoryInfo();
            Debug.getMemoryInfo(mi);
            pss = mi.getTotalPss();
            dPss = mi.dalvikPrivateDirty;
            nPss = mi.nativePrivateDirty;
            oPss = mi.otherPrivateDirty;
        } catch (Throwable ignored) {
        }
        long gpu = 0;
        try {
            gpu = com.particlesdevs.photoncamera.processing.opengl.GLTexture.liveBytes()
                    + com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing.liveRenderBytes();
        } catch (Throwable ignored) {
        }
        long hwmSample = Debug.getNativeHeapAllocatedSize()
                + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                + gpu;
        synchronized (Allocator.class) {
            if (hwmSample > sHwmProxy) {
                sHwmProxy = hwmSample;
            }
        }
        Log.d(logTag, "ProcStage[" + stage + "] devFree=" + (devFree / 1048576)
                + "MB devLow=" + (devLow ? 1 : 0)
                + " hwm~=" + (sHwmProxy / 1048576)
                + "MB pss=" + (pss / 1024)
                + "MB dPss=" + (dPss / 1024) + "MB nPss=" + (nPss / 1024)
                + "MB oPss=" + (oPss / 1024) + "MB");
    }
}
