package com.particlesdevs.photoncamera.processing.encoder;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageSaver;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.particlesdevs.photoncamera.processing.ultrahdr.GainMapComputer;
import com.particlesdevs.photoncamera.processing.ultrahdr.UltraHdrEncoder;
import com.particlesdevs.photoncamera.util.Log;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Single dispatch point for the rendered still (both HDRX and Unlimited
 * pipelines). Contract:
 *
 * <ul>
 *   <li>JPEG modes behave exactly as before (SDR q98 / Ultra HDR JPEG).</li>
 *   <li>HEIC modes need API 28+ HEVC encode; HEIC + gain map needs API 34+.
 *       On API 28–33 with UltraHDR ON, HEIC saves as SDR HEIC (format wins).</li>
 *   <li>Failures fall back within the same container; a runtime HEIC failure
 *       last-resorts to a JPEG sibling so the shot is never lost.</li>
 * </ul>
 */
public final class StillEncoder {

    private static final String TAG = "StillEncoder";

    private StillEncoder() {}

    /**
     * Tells the user that the requested HEIC encode failed and a JPEG was
     * written instead. Posted to the main looper (encoding runs on a worker).
     */
    private static void notifyHeicFallback() {
        try {
            Context ctx = PhotonCamera.getAppContext();
            if (ctx == null) {
                return;
            }
            String msg = ctx.getString(R.string.heic_failed_saved_jpeg);
            new Handler(Looper.getMainLooper()).post(
                    () -> Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show());
        } catch (Throwable ignored) {
        }
    }

    /**
     * Releases gain-map bitmaps once encoded (compute output and source may
     * alias when normalized in place; guarded). Never touches sdr: callers
     * keep it for SDR fallback until their own recycle.
     */
    private static void recycleGain(GainMapComputer.Result res, PostPipeline.GainMapRaw gain) {
        try {
            if (res != null && res.gainMap != null && !res.gainMap.isRecycled()) {
                res.gainMap.recycle();
            }
        } catch (Exception ignored) {
        }
        try {
            if (gain != null && gain.bitmap != null && !gain.bitmap.isRecycled()) {
                gain.bitmap.recycle();
            }
        } catch (Exception ignored) {
        }
    }

    public static final class Result {
        public final boolean saved;
        public final Path file;

        Result(boolean saved, Path file) {
            this.saved = saved;
            this.file = file;
        }
    }

    /**
     * @param dest   full path including the correct extension ({@code .jpg} /
     *               {@code .heic}); ownership of {@code sdr} passes to this
     *               method (recycled by the underlying encoder).
     * @param gain   raw gain map from {@code RunHDRGainMap}, may be null.
     */
    public static Result encodeStill(Path dest, Bitmap sdr, PostPipeline.GainMapRaw gain,
            ParseExif.ExifData exif, boolean useHeic) {
        try {
            return encodeStillInternal(dest, sdr, gain, exif, useHeic);
        } catch (Throwable t) {
            // Never lose the shot: an Error-level failure (missing JNI, OOM,
            // codec abort) that escaped the layered fallbacks gets one last
            // JPEG try.
            Log.e(TAG, "Still encode failed hard, JPEG last resort: " + describe(t));
            if (useHeic) {
                notifyHeicFallback();
            }
            try {
                return encodeJpegSibling(dest, sdr, gain, exif);
            } catch (Throwable t2) {
                Log.e(TAG, "JPEG last resort failed: " + describe(t2));
                return new Result(false, dest);
            }
        }
    }

    private static String describe(Throwable t) {
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }

    private static Result encodeStillInternal(Path dest, Bitmap sdr, PostPipeline.GainMapRaw gain,
            ParseExif.ExifData exif, boolean useHeic) {
        boolean wantUhdr = PhotonCamera.getSettings().ultraHdr && gain != null;
        if (!useHeic) {
            return new Result(encodeJpeg(dest, sdr, wantUhdr ? gain : null, exif), dest);
        }
        if (!HeicSupport.isHeicEncodeSupported()) {
            Log.e(TAG, "HEIC requested but unsupported; falling back to JPEG sibling");
            notifyHeicFallback();
            return encodeJpegSibling(dest, sdr, gain, exif);
        }
        // The SDR base encode does not depend on the gain map, so start it on
        // a worker before the gain-map normalization passes and join it at the
        // mux. Saves ~130 ms of serial encode time per show.
        UltraHdrHeicEncoder.BaseEncodeJob baseJob = null;
        boolean ultraHdrHeic = wantUhdr && HeicSupport.isUltraHdrHeicSupported();
        if (ultraHdrHeic) {
            try {
                baseJob = UltraHdrHeicEncoder.startBaseEncode(sdr);
            } catch (Throwable t) {
                Log.e(TAG, "SDR base pre-encode failed to start, encoding in order", t);
                baseJob = null;
            }
        }
        GainMapComputer.Result res = null;
        if (wantUhdr) {
            try {
                res = GainMapComputer.compute(gain.bitmap, gain.scale);
            } catch (Exception e) {
                Log.e(TAG, "GainMapComputer failed, SDR HEIC fallback", e);
                res = null;
            }
        }
        if (res != null && baseJob != null) {
            try {
                UltraHdrHeicEncoder.encodeWithBase(dest, sdr, res, exif, baseJob);
                return new Result(true, dest);
            } catch (Throwable e) {
                Log.e(TAG, "HEIC Ultra HDR encode failed, SDR HEIC fallback", e);
                recycleQuietly(res.gainMap);
            }
        } else if (res != null) {
            if (ultraHdrHeic) {
                // The pre-encode could not start; fall back to the classic
                // in-order Ultra HDR HEIC path rather than losing the gain map.
                try {
                    UltraHdrHeicEncoder.encodeToFile(dest, sdr, res, exif);
                    return new Result(true, dest);
                } catch (Throwable e) {
                    Log.e(TAG, "HEIC Ultra HDR encode failed, SDR HEIC fallback", e);
                    recycleQuietly(res.gainMap);
                }
            } else {
                Log.d(TAG, "Ultra HDR gain map available but HEIC gain maps need API 34+; SDR HEIC");
            }
        } else if (baseJob != null) {
            // Gain map unavailable: drop the pre-encode and let the SDR path
            // below do its own encode.
            baseJob.abort();
        }
        if (exif != null) {
            exif.IMAGE_WIDTH = String.valueOf(sdr.getWidth());
            exif.IMAGE_LENGTH = String.valueOf(sdr.getHeight());
        }
        try {
            StillHeicEncoder.encodeToFile(dest, sdr,
                    sdr.getWidth(), sdr.getHeight(),
                    ExifBlob.fromExifData(exif), true);
            recycleQuietly(sdr);
            recycleGain(res, gain);
            Log.d(TAG, "SDR HEIC written: " + dest);
            return new Result(true, dest);
        } catch (Throwable e) {
            Log.e(TAG, "HEIC encode failed, JPEG fallback: " + describe(e));
            try {
                Files.deleteIfExists(dest);
            } catch (Exception ignored) {
            }
        }
        recycleGain(res, gain);
        notifyHeicFallback();
        return encodeJpegSibling(dest, sdr, gain, exif);
    }

    private static void recycleQuietly(Bitmap bitmap) {
        try {
            if (bitmap != null && !bitmap.isRecycled()) {
                bitmap.recycle();
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean encodeJpeg(Path dest, Bitmap sdr, PostPipeline.GainMapRaw gain,
            ParseExif.ExifData exif) {
        if (sdr == null || sdr.isRecycled()) {
            Log.e(TAG, "encodeJpeg with null/recycled bitmap; nothing saved");
            return false;
        }
        if (gain != null) {
            GainMapComputer.Result res = null;
            try {
                res = GainMapComputer.compute(
                        gain.bitmap, gain.scale);
                byte[] uhdr = UltraHdrEncoder.encode(sdr, res, exif);
                Files.write(dest, uhdr);
                sdr.recycle();
                recycleGain(res, gain);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Ultra HDR encode failed, falling back to SDR JPEG", e);
                recycleGain(res, gain);
            }
        }
        return ImageSaver.Util.saveBitmapAsJPG(dest, sdr, ImageSaver.JPG_QUALITY, exif);
    }

    private static Result encodeJpegSibling(Path heicDest, Bitmap sdr,
            PostPipeline.GainMapRaw gain, ParseExif.ExifData exif) {
        // Sibling encodes SDR only; the gain bitmap is dead here.
        recycleGain(null, gain);
        if (sdr == null || sdr.isRecycled()) {
            Log.e(TAG, "encodeJpegSibling with null/recycled bitmap; nothing saved");
            return new Result(false, heicDest);
        }
        String abs = heicDest.toAbsolutePath().toString();
        Path jpgDest = abs.toLowerCase().endsWith(".heic")
                ? Paths.get(abs.substring(0, abs.length() - 5) + ".jpg")
                : heicDest;
        boolean ok = encodeJpeg(jpgDest, sdr, null, exif);
        return new Result(ok, jpgDest);
    }
}
