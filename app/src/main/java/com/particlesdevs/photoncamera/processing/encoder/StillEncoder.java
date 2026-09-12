package com.particlesdevs.photoncamera.processing.encoder;

import android.graphics.Bitmap;

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
        boolean wantUhdr = PhotonCamera.getSettings().ultraHdr && gain != null;
        if (!useHeic) {
            return new Result(encodeJpeg(dest, sdr, wantUhdr ? gain : null, exif), dest);
        }
        if (!HeicSupport.isHeicEncodeSupported()) {
            Log.e(TAG, "HEIC requested but unsupported; falling back to JPEG sibling");
            return encodeJpegSibling(dest, sdr, gain, exif);
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
        if (res != null && HeicSupport.isUltraHdrHeicSupported()) {
            try {
                UltraHdrHeicEncoder.encodeToFile(dest, sdr, res, exif);
                return new Result(true, dest);
            } catch (Exception e) {
                Log.e(TAG, "HEIC Ultra HDR encode failed, SDR HEIC fallback", e);
                try {
                    if (res.gainMap != null && !res.gainMap.isRecycled()) {
                        res.gainMap.recycle();
                    }
                } catch (Exception ignored) {
                }
            }
        } else if (res != null) {
            Log.d(TAG, "Ultra HDR gain map available but HEIC gain maps need API 34+; SDR HEIC");
        }
        try {
            if (SdrHeicEncoder.encodeToFile(dest, sdr, exif)) {
                recycleGain(res, gain);
                return new Result(true, dest);
            }
        } catch (Exception e) {
            Log.e(TAG, "SDR HEIC encode failed: " + Log.getStackTraceString(e));
        }
        recycleGain(res, gain);
        return encodeJpegSibling(dest, sdr, gain, exif);
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
