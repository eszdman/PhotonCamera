package com.particlesdevs.photoncamera.processing.encoder;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

/**
 * Capability gates for HEIC output.
 *
 * <ul>
 *   <li>SDR HEIC encode needs API 28+ plus a hardware HEVC image encoder
 *       ({@code androidx.heifwriter} has the same requirement and throws on
 *       emulators / encoder-less devices).</li>
 *   <li>HEIC + Ultra HDR gain map additionally needs API 34+, where the
 *       platform {@code Gainmap} / HDR display stack exists to verify and
 *       render the result. Below 34, HEIC save modes degrade to SDR HEIC
 *       (format choice wins) — see the save-mode contract.</li>
 * </ul>
 *
 * <p>Results are cached per process; the codec query walks the full codec
 * list once.
 */
public final class HeicSupport {

    private static volatile Boolean sHeicEncode;

    private HeicSupport() {}

    /** True when SDR HEIC encode is expected to work on this device. */
    public static boolean isHeicEncodeSupported() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return false;
        }
        Boolean cached = sHeicEncode;
        if (cached != null) {
            return cached;
        }
        boolean supported = queryHevcImageEncoder();
        sHeicEncode = supported;
        return supported;
    }

    /**
     * True when HEIC + Ultra HDR gain-map mux is enabled on this device
     * (API 34+ with working SDR HEIC encode).
     */
    public static boolean isUltraHdrHeicSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && isHeicEncodeSupported();
    }

    /** Test-only hook to override the cached probe result. */
    public static void setHeicEncodeSupportedForTest(Boolean value) {
        sHeicEncode = value;
    }

    /** Pure-logic helper: is this save-mode value one of the HEIC modes? */
    public static boolean isHeicSaveMode(int saveMode) {
        return saveMode == ImageFormatConfig.SAVE_HEIC
                || saveMode == ImageFormatConfig.SAVE_HEIC_RAW;
    }

    private static boolean queryHevcImageEncoder() {
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.ALL_CODECS);
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (!info.isEncoder()) {
                    continue;
                }
                for (String type : info.getSupportedTypes()) {
                    if ("image/vnd.android.heic".equalsIgnoreCase(type)
                            || "video/hevc".equalsIgnoreCase(type)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }
}
