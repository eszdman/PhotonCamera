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

    private AudioCodecSupport() {}

    /**
     * Clamp a requested audio bitrate to what the device's AAC encoder
     * advertises. Returns {@code requested} unchanged when no encoder or no
     * bitrate range is available.
     */
    public static int clampAudioBitrate(int requested) {
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            int upper = -1;
            int lower = -1;
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
            if (upper <= 0) return requested;
            int clamped = Math.min(requested, upper);
            if (lower > 0) clamped = Math.max(clamped, lower);
            return clamped;
        } catch (Exception e) {
            Log.w(TAG, "clampAudioBitrate failed", e);
            return requested;
        }
    }
}
