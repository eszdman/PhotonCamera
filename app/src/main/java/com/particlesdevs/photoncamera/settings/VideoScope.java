package com.particlesdevs.photoncamera.settings;

import com.particlesdevs.photoncamera.R;

/**
 * Per-resolution, per-frame-rate (and per-facing) scope for the video
 * tunable-key lists and the HDR/SDR session-type overrides, so 4K60, 4K30,
 * 1080p60, ... and the selfie camera can each carry their own values.
 *
 * <p>Ids and keys are derived from the resolution value ({@code "3840x2160"})
 * and the frame-rate setting ({@code auto/24/30/60}), sanitized into key-safe
 * tokens. The legacy global ids/keys and the previous resolution-only scope
 * stay as read-through fallbacks, so existing values keep applying until a
 * scoped value is set.
 */
public final class VideoScope {
    /** Token used when the resolution value is missing. */
    public static final String DEFAULT_TOKEN = "default";
    /** Suffix marking the front (selfie) camera's set. */
    public static final String SELFIE_SUFFIX = "_selfie";
    /** Frame-rate setting tokens, indexed by the fps mode (0=Auto .. 3=60). */
    private static final String[] FPS_TOKENS = {"auto", "24", "30", "60"};

    private VideoScope() {
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

    /** Token for the frame-rate setting (0=Auto, 1=24, 2=30, 3=60). */
    public static String fpsToken(int fpsMode) {
        if (fpsMode < 0 || fpsMode >= FPS_TOKENS.length) return FPS_TOKENS[0];
        return FPS_TOKENS[fpsMode];
    }

    /** String resource for the fps setting label (AUTO / 24fps / 30fps / 60fps). */
    public static int fpsLabelRes(int fpsMode) {
        switch (fpsMode) {
            case 1: return R.string.fps_24;
            case 2: return R.string.fps_30;
            case 3: return R.string.fps_60;
            default: return R.string.fps_auto;
        }
    }

    /** Full scope token: {@code <res>_<fps>} (e.g. "3840x2160_60"). */
    public static String scopeToken(String resolution, int fpsMode) {
        return token(resolution) + "_" + fpsToken(fpsMode);
    }

    /**
     * Tunable list id for the scope:
     * {@code video[_selfie]_<res>_<fps>} for SDR, {@code video_hdr[...]} for HDR.
     */
    public static String tunableId(boolean hdr, boolean selfie, String resolution, int fpsMode) {
        return tunableIdBase(hdr, selfie) + "_" + scopeToken(resolution, fpsMode);
    }

    /** Resolution-only tunable id (previous build's scope, kept as a fallback). */
    public static String resolutionTunableId(boolean hdr, boolean selfie, String resolution) {
        return tunableIdBase(hdr, selfie) + "_" + token(resolution);
    }

    private static String tunableIdBase(boolean hdr, boolean selfie) {
        return (hdr ? TunableKeyManager.VIDEO_HDR_TUNABLE_ID : TunableKeyManager.VIDEO_TUNABLE_ID)
                + (selfie ? SELFIE_SUFFIX : "");
    }

    /** Session-type preference key for the scope. */
    public static String sessionTypeKey(boolean hdr, boolean selfie, String resolution, int fpsMode) {
        return sessionTypeKeyBase(hdr, selfie) + "_" + scopeToken(resolution, fpsMode);
    }

    /** Resolution-only session-type key (previous build's scope, kept as fallback). */
    public static String resolutionSessionTypeKey(boolean hdr, boolean selfie, String resolution) {
        return sessionTypeKeyBase(hdr, selfie) + "_" + token(resolution);
    }

    private static String sessionTypeKeyBase(boolean hdr, boolean selfie) {
        return (hdr ? "pref_video_hdr_session_type" : "pref_video_sdr_session_type")
                + (selfie ? SELFIE_SUFFIX : "");
    }

    /** Human-readable resolution label for the settings summaries. */
    public static String label(String resolution) {
        return (resolution == null || resolution.trim().isEmpty())
                ? DEFAULT_TOKEN
                : resolution.trim();
    }

    /** Human-readable scope label, e.g. "1920x1080 · 60fps". */
    public static String label(String resolution, int fpsMode) {
        return label(resolution) + " \u00B7 " + fpsToken(fpsMode);
    }

    /** Full settings label, e.g. "1920x1080 · 60fps · Back". */
    public static String label(android.content.Context context, String resolution, int fpsMode,
            boolean selfie) {
        return label(resolution) + " \u00B7 " + context.getString(fpsLabelRes(fpsMode))
                + " \u00B7 " + context.getString(
                        selfie ? R.string.video_scope_selfie : R.string.video_scope_back);
    }
}
