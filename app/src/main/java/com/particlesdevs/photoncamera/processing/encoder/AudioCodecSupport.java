package com.particlesdevs.photoncamera.processing.encoder;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import com.particlesdevs.photoncamera.util.Log;

/**
 * Capability probes for the video-recording audio path
 * ({@code audio/mp4a-latm}, AAC).
 */
public final class AudioCodecSupport {
    private static final String TAG = "AudioCodecSupport";

    /** MIME used by {@link android.media.MediaRecorder.AudioEncoder#AAC}. */
    public static final String MIME_AAC = MediaFormat.MIMETYPE_AUDIO_AAC;

    private static final Object sClampLock = new Object();
    private static int sUpper = Integer.MIN_VALUE;
    private static int sLower = -1;

    private AudioCodecSupport() {}

    /**
     * Clamp a requested audio bitrate to what the device's AAC encoder
     * advertises. Returns {@code requested} unchanged when no encoder or no
     * bitrate range is available. The codec-list walk runs once per process;
     * results are cached because this sits on the video start path.
     */
    public static int clampAudioBitrate(int requested) {
        int upper;
        int lower;
        synchronized (sClampLock) {
            if (sUpper == Integer.MIN_VALUE) {
                int[] range = queryBitrateRange();
                sUpper = range[0];
                sLower = range[1];
            }
            upper = sUpper;
            lower = sLower;
        }
        if (upper <= 0) return requested;
        int clamped = Math.min(requested, upper);
        if (lower > 0) clamped = Math.max(clamped, lower);
        return clamped;
    }

    /** Walks the codec list once; returns {upper, lower}, -1s when unknown. */
    private static int[] queryBitrateRange() {
        int upper = -1;
        int lower = -1;
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (!info.isEncoder()) continue;
                boolean matches = false;
                for (String type : info.getSupportedTypes()) {
                    if (MIME_AAC.equalsIgnoreCase(type)) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) continue;
                try {
                    MediaCodecInfo.AudioCapabilities ac =
                            info.getCapabilitiesForType(MIME_AAC).getAudioCapabilities();
                    if (ac == null || ac.getBitrateRange() == null) continue;
                    int lo = ac.getBitrateRange().getLower();
                    int hi = ac.getBitrateRange().getUpper();
                    if (hi > upper) upper = hi;
                    if (lower < 0 || lo < lower) lower = lo;
                } catch (Exception e) {
                    Log.w(TAG, "clampAudioBitrate: skip " + info.getName(), e);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "clampAudioBitrate failed", e);
        }
        return new int[]{upper, lower};
    }
}
