package com.particlesdevs.photoncamera.util;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Central helper for traditional camera-app system-bar behavior:
 * status bar hidden, navigation bar visible and transparent (modern edge-to-edge).
 *
 * <p>Previously the camera and gallery used {@code SYSTEM_UI_FLAG_IMMERSIVE |
 * HIDE_NAVIGATION | FULLSCREEN}, which hid both bars and required two swipes
 * to go home (first swipe reveals the bars). This helper uses
 * {@link WindowInsetsControllerCompat} instead (the legacy flags are deprecated
 * and ignored under Android 15 edge-to-edge enforcement) so a single swipe
 * always goes home.
 */
public final class SystemBarsHelper {
    private SystemBarsHelper() {
    }

    /**
     * Draws edge-to-edge with a transparent navigation bar, hides the status bar
     * and keeps the navigation bar visible. Idempotent — safe to call from
     * {@code onCreate}, {@code onResume} and {@code onWindowFocusChanged} to
     * re-hide the status bar after a transient swipe reveals it.
     */
    public static void applyCameraBars(Activity activity) {
        Window window = activity.getWindow();
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= 35) {
            window.setStatusBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(window, window.getDecorView());
        controller.hide(WindowInsetsCompat.Type.statusBars());
        controller.show(WindowInsetsCompat.Type.navigationBars());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    /**
     * Adds the navigation-bar / display-cutout bottom inset to the view's padding
     * so bottom controls (shutter row, mode switcher, gallery buttons, FABs) sit
     * above the transparent gesture bar instead of behind it. The view's initial
     * padding is preserved. Content views (viewfinder, photo pager) should
     * <em>not</em> use this so they stay full-bleed behind the transparent bar.
     */
    public static void padBottomForNavBar(View view) {
        int initialLeft = view.getPaddingLeft();
        int initialTop = view.getPaddingTop();
        int initialRight = view.getPaddingRight();
        int initialBottom = view.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets nav = insets.getInsets(
                    WindowInsetsCompat.Type.navigationBars()
                            | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(initialLeft + nav.left, initialTop,
                    initialRight + nav.right, initialBottom + nav.bottom);
            return insets;
        });
    }

    /**
     * Adds the status-bar / display-cutout top inset as the view's top margin
     * so headers (e.g. a settings toolbar) sit below notches and punch-hole
     * cameras instead of behind them. Margin (not padding) is used so fixed
     * heights like {@code ?attr/actionBarSize} keep their full content box.
     * Sides are included for landscape cutouts. Full-bleed content views
     * should <em>not</em> use this.
     */
    public static void padTopForStatusBar(View view) {
        ViewGroup.MarginLayoutParams layoutParams =
                view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
                        ? (ViewGroup.MarginLayoutParams) view.getLayoutParams()
                        : null;
        int initialTopMargin = layoutParams != null ? layoutParams.topMargin : 0;
        int initialLeft = view.getPaddingLeft();
        int initialRight = view.getPaddingRight();
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets top = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars()
                            | WindowInsetsCompat.Type.displayCutout());
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
                mlp.topMargin = initialTopMargin + top.top;
                v.setLayoutParams(mlp);
            }
            v.setPadding(initialLeft + top.left, v.getPaddingTop(),
                    initialRight + top.right, v.getPaddingBottom());
            return insets;
        });
    }
}
