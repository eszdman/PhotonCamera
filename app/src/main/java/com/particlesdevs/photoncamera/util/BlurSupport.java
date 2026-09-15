package com.particlesdevs.photoncamera.util;

import android.app.Dialog;
import android.content.Context;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Backdrop blur effects. Every entry point degrades gracefully: below API 31
 * everything is a no-op, and cross-window blur additionally requires the
 * system toggle ({@link WindowManager#isCrossWindowBlurEnabled()}).
 */
public final class BlurSupport {
    /** Blur radius behind dialog windows, in dps. */
    private static final float DIALOG_BLUR_RADIUS_DP = 16f;

    private BlurSupport() {
    }

    /** In-window content blur ({@link RenderEffect}). Safe to call on any API. */
    public static boolean supportsRenderEffect() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    /** Cross-window blur-behind for dialogs. Off in battery saver or when disabled system-wide. */
    public static boolean crossWindowBlurEnabled(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false;
        }
        WindowManager windowManager = context.getSystemService(WindowManager.class);
        return windowManager != null && windowManager.isCrossWindowBlurEnabled();
    }

    /**
     * Shows a dialog and, where supported, blurs the content behind its window.
     * Drop-in replacement for {@link Dialog#show()} at the MaterialAlertDialog call sites.
     */
    public static void show(@Nullable Dialog dialog) {
        if (dialog == null) {
            return;
        }
        dialog.show();
        blurBehind(dialog);
    }

    /**
     * Registers a blur-behind pass for a dialog created ahead of time (e.g. from
     * a {@code DialogFragment}), applied once the window is attached.
     */
    public static void blurBehindOnShow(@NonNull Dialog dialog) {
        dialog.setOnShowListener(shown -> blurBehind(dialog));
    }

    private static void blurBehind(@NonNull Dialog dialog) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        Window window = dialog.getWindow();
        Context context = dialog.getContext();
        if (window == null || context == null) {
            return;
        }
        if (!crossWindowBlurEnabled(context)) {
            return;
        }
        window.setBackgroundBlurRadius(dpToPx(context, DIALOG_BLUR_RADIUS_DP));
    }

    /** Blurs a view's own content in place. No-op below API 31. */
    public static void blurView(@NonNull View view, float radiusPx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        view.setRenderEffect(RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP));
    }

    /** Removes any in-place blur previously applied with {@link #blurView}. */
    public static void clearBlur(@NonNull View view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        view.setRenderEffect(null);
    }

    public static int dpToPx(@NonNull Context context, float dp) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics()));
    }

    public static float pxToDp(@NonNull Context context, float px) {
        return px / context.getResources().getDisplayMetrics().density;
    }
}
