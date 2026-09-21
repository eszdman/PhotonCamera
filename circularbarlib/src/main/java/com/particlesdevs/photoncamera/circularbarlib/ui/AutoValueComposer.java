package com.particlesdevs.photoncamera.circularbarlib.ui;

public final class AutoValueComposer {

    private AutoValueComposer() {
    }

    /**
     * Shows a live value next to the auto label (e.g. "A 800"); manual or
     * unsupported labels (e.g. "Fixed") are returned untouched.
     */
    public static String compose(String baseText, String autoValue, String autoLabel) {
        if (baseText == null) {
            return "";
        }
        if (autoValue == null || autoValue.isEmpty()) {
            return baseText;
        }
        if (autoLabel == null || !baseText.equals(autoLabel)) {
            return baseText;
        }
        return baseText + " " + autoValue;
    }
}
