package com.particlesdevs.photoncamera.processing.encoder;

/**
 * Maps the single "Save" preference to container + RAW behavior.
 *
 * <p>Legacy values {@code 0/1/2} (JPEG / RAW+JPEG / RAW) are kept stable so
 * existing installs don't migrate; HEIC modes append as {@code 3/4}.
 */
public final class ImageFormatConfig {

    public static final int SAVE_JPEG = 0;
    public static final int SAVE_RAW_JPEG = 1;
    public static final int SAVE_RAW_ONLY = 2;
    public static final int SAVE_HEIC = 3;
    public static final int SAVE_HEIC_RAW = 4;

    private ImageFormatConfig() {}

    /** Normalizes unknown / legacy values to {@link #SAVE_JPEG}. */
    public static int normalize(int saveMode) {
        switch (saveMode) {
            case SAVE_JPEG:
            case SAVE_RAW_JPEG:
            case SAVE_RAW_ONLY:
            case SAVE_HEIC:
            case SAVE_HEIC_RAW:
                return saveMode;
            default:
                return SAVE_JPEG;
        }
    }

    public static boolean usesHeic(int saveMode) {
        return normalize(saveMode) == SAVE_HEIC
                || normalize(saveMode) == SAVE_HEIC_RAW;
    }

    /** True when a DNG is written alongside (or instead of) the still. */
    public static boolean savesRaw(int saveMode) {
        switch (normalize(saveMode)) {
            case SAVE_RAW_JPEG:
            case SAVE_RAW_ONLY:
            case SAVE_HEIC_RAW:
                return true;
            default:
                return false;
        }
    }

    public static boolean isRawOnly(int saveMode) {
        return normalize(saveMode) == SAVE_RAW_ONLY;
    }

    /** File extension for the rendered still (no dot). */
    public static String stillExtension(int saveMode) {
        return usesHeic(saveMode) ? "heic" : "jpg";
    }

    /** MIME type for media-scan / gallery intents. */
    public static String stillMimeType(int saveMode) {
        return usesHeic(saveMode) ? "image/heic" : "image/jpeg";
    }

    /**
     * Drops HEIC modes to their JPEG counterparts on devices without HEIC
     * encode. Used as a safety net; the settings UI hides HEIC entries so
     * this path should rarely trigger.
     */
    public static int withoutHeic(int saveMode) {
        switch (normalize(saveMode)) {
            case SAVE_HEIC:
                return SAVE_JPEG;
            case SAVE_HEIC_RAW:
                return SAVE_RAW_JPEG;
            default:
                return normalize(saveMode);
        }
    }
}
