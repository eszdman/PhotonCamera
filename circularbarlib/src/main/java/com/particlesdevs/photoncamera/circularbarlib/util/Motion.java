package com.particlesdevs.photoncamera.circularbarlib.util;

import android.animation.TimeInterpolator;
import android.content.Context;

import androidx.annotation.AttrRes;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.google.android.material.motion.MotionUtils;
import com.particlesdevs.photoncamera.circularbarlib.R;

/**
 * M3E motion tokens resolved against the current theme, falling back to the
 * spec values when the theme does not define them. Shared by the manual-mode
 * library and the app module.
 */
public final class Motion {
    private Motion() {
    }

    public static int durationShort2(Context context) {
        return resolveDuration(context, R.attr.motionDurationShort2, 100);
    }

    public static int durationShort3(Context context) {
        return resolveDuration(context, R.attr.motionDurationShort3, 150);
    }

    public static int durationShort4(Context context) {
        return resolveDuration(context, R.attr.motionDurationShort4, 200);
    }

    public static int durationMedium1(Context context) {
        return resolveDuration(context, R.attr.motionDurationMedium1, 250);
    }

    public static int durationMedium2(Context context) {
        return resolveDuration(context, R.attr.motionDurationMedium2, 300);
    }

    public static int durationLong1(Context context) {
        return resolveDuration(context, R.attr.motionDurationLong1, 450);
    }

    public static int durationLong2(Context context) {
        return resolveDuration(context, R.attr.motionDurationLong2, 500);
    }

    public static TimeInterpolator emphasized(Context context) {
        return resolveInterpolator(context, R.attr.motionEasingEmphasizedInterpolator);
    }

    public static TimeInterpolator emphasizedDecelerate(Context context) {
        return resolveInterpolator(context, R.attr.motionEasingEmphasizedDecelerateInterpolator);
    }

    public static TimeInterpolator emphasizedAccelerate(Context context) {
        return resolveInterpolator(context, R.attr.motionEasingEmphasizedAccelerateInterpolator);
    }

    public static TimeInterpolator standard(Context context) {
        return resolveInterpolator(context, R.attr.motionEasingStandardInterpolator);
    }

    private static int resolveDuration(Context context, @AttrRes int attr, int fallbackMs) {
        return MotionUtils.resolveThemeDuration(context, attr, fallbackMs);
    }

    private static TimeInterpolator resolveInterpolator(Context context, @AttrRes int attr) {
        return MotionUtils.resolveThemeInterpolator(context, attr, new FastOutSlowInInterpolator());
    }
}
