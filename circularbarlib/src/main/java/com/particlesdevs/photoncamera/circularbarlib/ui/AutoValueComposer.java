package com.particlesdevs.photoncamera.circularbarlib.ui;

public final class AutoValueComposer {

    private AutoValueComposer() {
    }

    /**
     * Composes a manual-bar cell. Untouched controls show only the live value
     * (no auto label, no dot); touched controls show their plain manual text.
     * The touched dot marker is rendered by the label view itself so it never
     * shifts the centered value.
     */
    public static String compose(String baseText, String autoValue, String autoLabel) {
        if (baseText == null) {
            return "";
        }
        if (autoLabel == null) {
            return baseText;
        }
        if (baseText.equals(autoLabel)) {
            // Untouched: live value only.
            return autoValue == null ? "" : autoValue;
        }
        return baseText;
    }

    /** True when the model text is a manual value rather than the auto sentinel. */
    public static boolean isTouched(String baseText, String autoLabel) {
        return baseText != null && autoLabel != null && !baseText.equals(autoLabel);
    }
}
