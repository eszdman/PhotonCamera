package com.particlesdevs.photoncamera.processing.encoder;

/**
 * Maps the "Save" preference plus the "Save HEIC" toggle to container + RAW
 * behavior.
 *
 * <p>"Save" is a 3-option list ({@code 0/1/2}: JPEG / RAW+JPEG / RAW, kept
 * stable so existing installs don't migrate); the Save HEIC toggle swaps the
 * JPEG still for HEIC. Legacy values {@code 3/4} (HEIC / RAW+HEIC, briefly
 * stored by early HEIC builds) are folded back to their JPEG counterparts by
 * {@link #withoutHeic} — the toggle itself is migrated in
 * {@code PreferenceKeys.isSaveRaw()}.
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
     * Combines the 3-option "Save" value with the Save HEIC toggle into the
     * effective save mode. RAW-only is unaffected (there is no JPEG still to
     * swap); legacy HEIC values are folded to JPEG first so a stale stored
     * value can never enable HEIC on its own.
     */
    public static int resolve(int saveMode, boolean heic) {
        int normalized = withoutHeic(saveMode);
        if (!heic) {
            return normalized;
        }
        switch (normalized) {
            case SAVE_JPEG:
                return SAVE_HEIC;
            case SAVE_RAW_JPEG:
                return SAVE_HEIC_RAW;
            default:
                return normalized;
        }
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
