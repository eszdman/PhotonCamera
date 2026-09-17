package com.particlesdevs.photoncamera.ui.widget;

import android.annotation.SuppressLint;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;

/**
 * Adds an M3E spring press-scale to a view (used by the gallery FABs). The
 * touch listener never consumes the event, so ripples, clicks and long presses
 * keep working as before.
 */
public final class FabPressSpring {
    private static final float PRESSED_SCALE = 0.92f;

    private FabPressSpring() {
    }

    @SuppressLint("ClickableViewAccessibility")
    public static void attach(@NonNull View view) {
        SpringAnimation scaleX = new SpringAnimation(view, DynamicAnimation.SCALE_X, 1f)
                .setSpring(MorphShapeDrawable.spatialSpring(view.getContext()));
        SpringAnimation scaleY = new SpringAnimation(view, DynamicAnimation.SCALE_Y, 1f)
                .setSpring(MorphShapeDrawable.spatialSpring(view.getContext()));
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    scaleX.animateToFinalPosition(PRESSED_SCALE);
                    scaleY.animateToFinalPosition(PRESSED_SCALE);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    scaleX.animateToFinalPosition(1f);
                    scaleY.animateToFinalPosition(1f);
                    break;
                default:
                    break;
            }
            return false;
        });
    }
}
