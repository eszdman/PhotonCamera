package com.particlesdevs.photoncamera.processing.encoder;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;

import com.particlesdevs.photoncamera.util.Log;

/**
 * Capability probes for the video recording path ({@code video/avc} and
 * {@code video/hevc}).
 *
 * <p>Results are cached per process; each query walks the full codec list at
 * most once. HDR video additionally needs a 10-bit HEVC encoder (Main10);
 * the Camera2 10-bit {@code DynamicRangeProfiles} check happens at record
 * time with graceful SDR fallback, so this class reports encoder-side
 * support only.
 */
public final class VideoCodecSupport {
    private static final String TAG = "VideoCodecSupport";

    private static volatile Boolean sHasAvc;
    private static volatile Boolean sHasHevc;
    private static volatile Boolean sHasHevcMain10;

    private VideoCodecSupport() {}

    public static boolean hasAvcEncoder() {
        Boolean cached = sHasAvc;
        if (cached != null) return cached;
        boolean supported = hasEncoderFor(MediaFormat.MIMETYPE_VIDEO_AVC);
        sHasAvc = supported;
        return supported;
    }

    /** True when any {@code video/hevc} encoder exists (needed for Save storage). */
    public static boolean hasHevcEncoder() {
        Boolean cached = sHasHevc;
        if (cached != null) return cached;
        boolean supported = hasEncoderFor(MediaFormat.MIMETYPE_VIDEO_HEVC);
        sHasHevc = supported;
        return supported;
    }

    /**
     * True when an HEVC encoder advertises the Main10 profile, i.e. it can
     * encode 10-bit HDR (BT.2020 HLG/PQ). This is the encoder-side gate for
     * the HDR video switch.
     */
    public static boolean hasHevcMain10() {
        Boolean cached = sHasHevcMain10;
        if (cached != null) return cached;
        boolean supported = queryHevcMain10();
        sHasHevcMain10 = supported;
        return supported;
    }

    /** HDR switch requires Save storage (HEVC) plus a Main10-capable encoder. */
    public static boolean isHdrVideoSupported() {
        return hasHevcEncoder() && hasHevcMain10();
    }

    /**
     * Clamp a requested bitrate to what the device's encoder for {@code mime}
     * advertises. Returns {@code requested} unchanged when no encoder or no
     * bitrate range is available.
     */
    public static int clampVideoBitrate(String mime, int requested) {
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            int upper = -1;
            int lower = -1;
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (!info.isEncoder()) continue;
                boolean matches = false;
                for (String type : info.getSupportedTypes()) {
                    if (mime.equalsIgnoreCase(type)) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) continue;
                try {
                    MediaCodecInfo.VideoCapabilities vc =
                            info.getCapabilitiesForType(mime).getVideoCapabilities();
                    if (vc == null || vc.getBitrateRange() == null) continue;
                    int lo = vc.getBitrateRange().getLower();
                    int hi = vc.getBitrateRange().getUpper();
                    if (hi > upper) upper = hi;
                    if (lower < 0 || lo < lower) lower = lo;
                } catch (Exception e) {
                    Log.w(TAG, "clampVideoBitrate: skip " + info.getName(), e);
                }
            }
            if (upper <= 0) return requested;
            int clamped = Math.min(requested, upper);
            if (lower > 0) clamped = Math.max(clamped, lower);
            return clamped;
        } catch (Exception e) {
            Log.w(TAG, "clampVideoBitrate failed", e);
            return requested;
        }
    }

    private static boolean hasEncoderFor(String mime) {
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (!info.isEncoder()) continue;
                for (String type : info.getSupportedTypes()) {
                    if (mime.equalsIgnoreCase(type)) return true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "hasEncoderFor(" + mime + ") failed", e);
        }
        return false;
    }

    private static boolean queryHevcMain10() {
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (!info.isEncoder()) continue;
                boolean isHevc = false;
                for (String type : info.getSupportedTypes()) {
                    if (MediaFormat.MIMETYPE_VIDEO_HEVC.equalsIgnoreCase(type)) {
                        isHevc = true;
                        break;
                    }
                }
                if (!isHevc) continue;
                try {
                    MediaCodecInfo.CodecCapabilities caps =
                            info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC);
                    if (caps == null || caps.profileLevels == null) continue;
                    for (MediaCodecInfo.CodecProfileLevel pl : caps.profileLevels) {
                        if (pl.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10) {
                            return true;
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "queryHevcMain10: skip " + info.getName(), e);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "queryHevcMain10 failed", e);
        }
        return false;
    }

    /** Test-only hook to override cached probe results. */
    public static void setForTest(Boolean hasHevc, Boolean hasMain10) {
        sHasHevc = hasHevc;
        sHasHevcMain10 = hasMain10;
    }
}
