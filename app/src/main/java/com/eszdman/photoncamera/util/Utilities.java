package com.eszdman.photoncamera.util;

import java.util.Locale;

public class Utilities {
    public static String formatExposureTime(final double value) {
        String output;

        if (value < 1.0f) {
            output = String.format(Locale.getDefault(), "%d/%d", 1, (int) (0.5f + 1 / value));
        } else {
            final int integer = (int) value;
            final double time = value - integer;
            output = String.format(Locale.getDefault(), "%d''", integer);

            if (time > 0.0001f) {
                output += String.format(Locale.getDefault(), " %d/%d", 1, (int) (0.5f + 1 / time));
            }
        }

        return output;
    }
}
