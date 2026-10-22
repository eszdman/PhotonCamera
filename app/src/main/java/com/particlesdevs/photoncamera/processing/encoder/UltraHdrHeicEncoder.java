package com.particlesdevs.photoncamera.processing.encoder;

import android.graphics.Bitmap;
import android.os.Build;

import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.processing.ultrahdr.GainMapComputer;
import com.particlesdevs.photoncamera.util.Log;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ultra HDR HEIC encode (API 34+ only).
 *
 * <p>Takes the same {@link GainMapComputer.Result} as the JPEG path, encodes
 * the SDR base and the gain map with {@link StillHeicEncoder} into temp files
 * (both 8-bit), then muxes them with {@link UltraHdrHeicContainer} (manual
 * BMFF, no native dependency). The gain map is full-size and encoded at the
 * same depth as the base, so its declared {@code pixi} matches the stream.
 * Any failure throws so callers fall back to SDR HEIC — a mux bug can never
 * lose a shot.
 */
public final class UltraHdrHeicEncoder {

    private static final String TAG = "UltraHdrHeicEncoder";

    private UltraHdrHeicEncoder() {}

    /**
     * SDR base encode running on a worker thread. Independent of the gain map,
     * so callers with gain-map work to do (normalization passes) can start it
     * first and join it at the mux. Owns its temp file; {@link #abort()}
     * always deletes it.
     */
    public static final class BaseEncodeJob {
        private final Bitmap sdr;
        private final File baseTmp;
        private final Thread thread;
        private volatile Throwable error;

        private BaseEncodeJob(Bitmap sdr) throws java.io.IOException {
            this.sdr = sdr;
            this.baseTmp = File.createTempFile("uhdr_heic_base_", ".heic");
            this.thread = new Thread(this::run, "UhdrBaseEncode");
            this.thread.start();
        }

        private void run() {
            try {
                // Base: Exif is injected by the merge.
                StillHeicEncoder.encodeToFile(baseTmp.toPath(), sdr,
                        sdr.getWidth(), sdr.getHeight(), null, false);
            } catch (Throwable t) {
                error = t;
            }
        }

        private File join() throws Exception {
            boolean interrupted = false;
            while (thread.isAlive()) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            if (error != null) {
                throw new Exception("base HEIC encode failed", error);
            }
            return baseTmp;
        }

        /** Joins (if still running) and deletes the temp file. Idempotent. */
        public void abort() {
            try {
                join();
            } catch (Exception ignored) {
            }
            // noinspection ResultOfMethodCallIgnored
            baseTmp.delete();
        }
    }

    /**
     * Validates the environment and starts the SDR base encode on a worker.
     * Throws (caller falls back) when HEIC Ultra HDR is unavailable.
     */
    public static BaseEncodeJob startBaseEncode(Bitmap sdr) throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            throw new UnsupportedOperationException("HEIC Ultra HDR needs API 34+");
        }
        if (!HeicSupport.isUltraHdrHeicSupported()) {
            throw new UnsupportedOperationException("HEIC Ultra HDR not supported on this device");
        }
        if (sdr == null || sdr.isRecycled()) {
            throw new IllegalArgumentException("Null/recycled SDR bitmap");
        }
        return new BaseEncodeJob(sdr);
    }

    /**
     * Encodes and writes {@code dest} (must end in {@code .heic}).
     * Both bitmaps are recycled <b>only on success</b> — on failure they are
     * left alive so {@link StillEncoder} fallbacks can still use them.
     */
    public static void encodeToFile(Path dest, Bitmap sdr,
            GainMapComputer.Result gain, ParseExif.ExifData exif) throws Exception {
        BaseEncodeJob base = startBaseEncode(sdr);
        encodeWithBase(dest, sdr, gain, exif, base);
    }

    /**
     * Gain-map encode running on a worker. Owns its temp file; {@link #abort()}
     * joins (if needed) and deletes it.
     */
    private static final class GainEncodeJob {
        private final File tmp;
        private final Bitmap bmp;
        private final Thread thread;
        private volatile Throwable error;

        GainEncodeJob(File tmp, Bitmap bmp) {
            this.tmp = tmp;
            this.bmp = bmp;
            this.thread = new Thread(this::run, "UhdrGainEncode");
            this.thread.start();
        }

        private void run() {
            try {
                // Gain: full size, same depth as the base, so the declared
                // tmap pixi matches the stream.
                StillHeicEncoder.encodeToFile(tmp.toPath(), bmp,
                        bmp.getWidth(), bmp.getHeight(), null, false);
            } catch (Throwable t) {
                error = t;
            }
        }

        /** Waits for the encode; throws if it failed. */
        void join() throws Exception {
            boolean interrupted = false;
            while (thread.isAlive()) {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            if (error != null) {
                throw new Exception("gain HEIC encode failed", error);
            }
        }

        /** Joins if still running (discarding the result) and deletes the file. */
        void abort() {
            try {
                join();
            } catch (Exception ignored) {
            }
            // noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
    }

    /**
     * Mux variant that consumes a base encode already in flight (started via
     * {@link #startBaseEncode} while the caller prepared the gain map). Runs
     * the gain encode on its own worker so both HEIC encodes overlap, joins
     * both, then assembles the container.
     */
    public static void encodeWithBase(Path dest, Bitmap sdr,
            GainMapComputer.Result gain, ParseExif.ExifData exif,
            BaseEncodeJob base) throws Exception {
        if (sdr == null || sdr.isRecycled() || gain == null || gain.gainMap == null) {
            if (base != null) base.abort();
            throw new IllegalArgumentException("Null/recycled SDR or gain map");
        }
        if (exif != null) {
            exif.IMAGE_WIDTH = String.valueOf(sdr.getWidth());
            exif.IMAGE_LENGTH = String.valueOf(sdr.getHeight());
        }
        File gainTmp = File.createTempFile("uhdr_heic_gain_", ".heic");
        boolean success = false;
        try {
            // Gain encode on a worker: it reads the gain bitmap and writes its
            // own temp file, so it can run alongside the already-started base
            // encode instead of waiting for it.
            GainEncodeJob gainJob = new GainEncodeJob(gainTmp, gain.gainMap);
            File baseTmp;
            try {
                baseTmp = base.join();
            } catch (Exception e) {
                gainJob.abort();
                throw e;
            }
            try {
                gainJob.join();
            } catch (Exception concurrentFailure) {
                // Some SoCs allow only one hardware still-image encoder
                // session; retry serially now that the base session is
                // released. Transient session failures are not added to the
                // permanent unsupported set, so the retry is real.
                Log.w(TAG, "concurrent gain encode failed, retrying serially",
                        concurrentFailure);
                StillHeicEncoder.encodeToFile(gainTmp.toPath(), gain.gainMap,
                        gain.gainMap.getWidth(), gain.gainMap.getHeight(), null, false);
            }

            // Gain pixels are on disk now and only its metadata (captured
            // above as ints) is needed downstream: release the ~244 MB
            // (64 MP) before the read/merge/verify peak. sdr deliberately
            // stays alive: the SDR fallback needs it.
            recycleQuietly(gain.gainMap);
            byte[] baseBytes = Files.readAllBytes(baseTmp.toPath());
            byte[] gainBytes = Files.readAllBytes(gainTmp.toPath());
            Log.d(TAG, "HEIC mux inputs: base=" + (baseBytes.length / 1024)
                    + "KB gain=" + (gainBytes.length / 1024) + "KB");
            UltraHdrHeicContainer.Inputs in = new UltraHdrHeicContainer.Inputs();
            in.baseHeic = baseBytes;
            in.gainHeic = gainBytes;
            in.baseW = sdr.getWidth();
            in.baseH = sdr.getHeight();
            in.gainW = gain.gainW;
            in.gainH = gain.gainH;
            in.gainMapMin = gain.gainMapMin;
            in.gainMapMax = gain.gainMapMax;
            in.hdrCapacityMax = gain.hdrCapacityMax;
            in.exifPayload = ExifBlob.fromExifData(exif);
            if (in.exifPayload != null) {
                Log.d(TAG, "HEIC EXIF blob: " + in.exifPayload.length + " bytes"
                        + (ExifBlob.hasTiffPayload(in.exifPayload) ? " (TIFF ok)" : " (TIFF BAD)"));
            } else {
                Log.e(TAG, "EXIF blob null, HEIC will carry no EXIF");
            }
            byte[] merged = UltraHdrHeicContainer.merge(in);
            // Inputs served their purpose: release before the write/verify
            // window so base+gain+merged aren't all heap-resident at once.
            in.baseHeic = null;
            in.gainHeic = null;
            baseBytes = null;
            gainBytes = null;
            Log.d(TAG, "HEIC mux output: merged=" + (merged.length / 1024) + "KB");
            // Structural verify before writing: parses the boxes we just
            // assembled (layout + primary ispe) without ImageDecoder, whose
            // abort path could still decode the whole image.
            int[] primary = UltraHdrHeicContainer.primarySize(merged);
            if (primary[0] != sdr.getWidth() || primary[1] != sdr.getHeight()) {
                throw new IllegalStateException("merged HEIC dimensions "
                        + primary[0] + "x" + primary[1]
                        + " != expected " + sdr.getWidth() + "x" + sdr.getHeight());
            }
            try {
                Files.write(dest, merged);
            } catch (Exception writeFailed) {
                try {
                    Files.deleteIfExists(dest);
                } catch (Exception ignored) {
                }
                throw writeFailed;
            }
            success = true;
        } finally {
            if (base != null) base.abort();
            // noinspection ResultOfMethodCallIgnored
            gainTmp.delete();
            if (success) {
                recycleQuietly(sdr);
                recycleQuietly(gain.gainMap);
            }
        }
        Log.d(TAG, "HEIC Ultra HDR written: " + dest);
    }

    private static void recycleQuietly(Bitmap bitmap) {
        try {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        } catch (Exception ignored) {
        }
    }
}
