package com.particlesdevs.photoncamera.ui.camera.views;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatButton;
import androidx.core.content.ContextCompat;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.settings.PreferenceKeys;

/**
 * Top-bar frame-rate button showing one icon per fps mode (auto/24/30/60).
 * The icon is swapped directly (like the quick-settings entry) instead of
 * going through drawable states, so all four modes get distinct glyphs.
 */
public class FpsButton extends AppCompatButton {

    public FpsButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        // In the layout editor PreferenceKeys is not initialised yet.
        if (!isInEditMode()) {
            // The top-bar button only exists in video mode: it shows the
            // video/RAW-video rate, not the photo/motion one.
            setFpsModeState(PreferenceKeys.getVideoFpsMode());
        }
    }

    public void setFpsModeState(int fpsMode) {
        int res;
        switch (fpsMode) {
            case 1:
                res = R.drawable.fps24_select_24px;
                break;
            case 2:
                res = R.drawable.fps30_select_24px;
                break;
            case 3:
                res = R.drawable.fps60_select_24px;
                break;
            default:
                res = R.drawable.autofps_select_24px;
                break;
        }
        // Same 11dp inset sibling topbar buttons get from their XML wrappers
        // so the full-bleed glyphs render at matching size.
        int inset = getResources().getDimensionPixelSize(R.dimen.topbar_button_inset);
        android.graphics.drawable.Drawable icon =
                ContextCompat.getDrawable(getContext(), res);
        if (icon != null) {
            setBackground(new android.graphics.drawable.InsetDrawable(icon, inset));
        } else {
            setBackgroundResource(res);
        }
    }
}
