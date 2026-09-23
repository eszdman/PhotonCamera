package com.particlesdevs.photoncamera.settings;

/**
 * Per-resolution (and per-facing) scope for the video tunable-key lists and the
 * HDR/SDR session-type overrides, so 4K, 1080p, ... and the selfie camera can
 * each carry their own values.
 *
 * <p>Ids and keys are derived from the resolution value itself
 * ({@code "3840x2160"}), sanitized into a key-safe token. The legacy global
 * ids/keys ({@link TunableKeyManager#VIDEO_TUNABLE_ID}, the fixed session-type
 * preferences) stay as read-through fallbacks so existing values keep applying
 * until a scoped value is set.
 */
public final class VideoResolutionScope {
    /** Token used when the resolution value is missing. */
    public static final String DEFAULT_TOKEN = "default";
    /** Suffix marking the front (selfie) camera's set. */
    public static final String SELFIE_SUFFIX = "_selfie";

    private VideoResolutionScope() {
    }

    /** Key-safe token for a resolution value; never empty. */
    public static String token(String resolution) {
        if (resolution == null) return DEFAULT_TOKEN;
        String trimmed = resolution.trim();
        if (trimmed.isEmpty()) return DEFAULT_TOKEN;
        StringBuilder sb = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            sb.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return sb.toString();
    }

    /**
     * Tunable list id for the scope:
     * {@code video[_selfie]_<res>} for SDR, {@code video_hdr[_selfie]_<res>} for
     * HDR.
     */
    public static String tunableId(boolean hdr, boolean selfie, String resolution) {
        return (hdr ? TunableKeyManager.VIDEO_HDR_TUNABLE_ID : TunableKeyManager.VIDEO_TUNABLE_ID)
                + (selfie ? SELFIE_SUFFIX : "")
                + "_" + token(resolution);
    }

    /** Session-type preference key for the scope. */
    public static String sessionTypeKey(boolean hdr, boolean selfie, String resolution) {
        return (hdr ? "pref_video_hdr_session_type" : "pref_video_sdr_session_type")
                + (selfie ? SELFIE_SUFFIX : "")
                + "_" + token(resolution);
    }

    /** Human-readable resolution label for the settings summaries. */
    public static String label(String resolution) {
        return (resolution == null || resolution.trim().isEmpty())
                ? DEFAULT_TOKEN
                : resolution.trim();
    }
}
