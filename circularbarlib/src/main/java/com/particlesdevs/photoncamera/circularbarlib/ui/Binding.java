package com.particlesdevs.photoncamera.circularbarlib.ui;

import android.view.View;
import android.view.ViewGroup;

import com.particlesdevs.photoncamera.circularbarlib.R;
import com.particlesdevs.photoncamera.circularbarlib.control.models.ManualModel;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobView;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.Rotation;

/**
 * Created by vibhorSrv
 */
public class Binding {
    public static void setKnobVisibility(KnobView knobView, Boolean knobVisible) {
        if (knobView != null && knobVisible != null) {
            if (knobVisible) {
                knobView.animate().translationY(0).scaleY(1).scaleX(1)
                        .setDuration(Motion.durationShort4(knobView.getContext()))
                        .setInterpolator(Motion.emphasized(knobView.getContext()))
                        .alpha(1f).start();
                knobView.setVisibility(View.VISIBLE);
            } else {
                knobView.animate().translationY(knobView.getHeight() / 2.5f)
                        .scaleY(.2f).scaleX(.2f)
                        .setDuration(Motion.durationShort4(knobView.getContext()))
                        .setInterpolator(Motion.emphasizedDecelerate(knobView.getContext()))
                        .alpha(0f)
                        .withEndAction(() -> knobView.setVisibility(View.GONE)).start();
            }
        }
    }

    public static void setModelToKnob(KnobView knobView, ManualModel<?> manualModel) {
        if (manualModel != null && knobView != null) {
            knobView.setKnobViewChangedListener(manualModel);
            knobView.setKnobInfo(manualModel.getKnobInfo());
            knobView.setKnobItems(manualModel.getKnobInfoList());
            knobView.setTickByValue(manualModel.getCurrentInfo().value);
        } else if (manualModel == null) {
            knobView.setKnobViewChangedListener(null);
        }
    }

    public static void resetKnob(KnobView knobView, boolean toReset) {
        if (toReset) {
            knobView.resetKnob();
        }
    }

    /**
     * Reveals/hides the manual palette with the same M3E motion as the quick
     * settings panel: fade + slide (by {@code manual_panel_slide}) with an
     * emphasized curve over medium2. The option bar collapses through its own
     * scale so its blur region (and the lens cluster's offset math) stays
     * exact — the container itself is never scaled. The wheel keeps its own
     * snappy show/hide.
     */
    public static void togglePanelVisibility(ViewGroup manualModeContainer, Boolean visible) {
        if (manualModeContainer == null || visible == null) {
            return;
        }
        View optionBar = manualModeContainer.findViewById(R.id.buttons_container);
        float slide = manualModeContainer.getResources().getDimension(R.dimen.manual_panel_slide);
        if (visible) {
            manualModeContainer.post(() -> {
                if (manualModeContainer.getVisibility() != View.VISIBLE) {
                    // Establish the hidden state so the first reveal animates too.
                    manualModeContainer.setAlpha(0f);
                    manualModeContainer.setTranslationY(slide);
                    if (optionBar != null) {
                        optionBar.setScaleX(0f);
                        optionBar.setScaleY(0f);
                    }
                }
                manualModeContainer.setVisibility(View.VISIBLE);
                manualModeContainer.animate()
                        .alpha(1f).translationY(0f)
                        .setDuration(Motion.durationMedium2(manualModeContainer.getContext()))
                        .setInterpolator(Motion.emphasized(manualModeContainer.getContext()))
                        .start();
                if (optionBar != null) {
                    optionBar.animate().scaleX(1f).scaleY(1f)
                            .setDuration(Motion.durationMedium2(optionBar.getContext()))
                            .setInterpolator(Motion.emphasized(optionBar.getContext()))
                            .start();
                }
            });
        } else {
            manualModeContainer.post(() -> {
                manualModeContainer.animate()
                        .alpha(0f).translationY(slide)
                        .setDuration(Motion.durationMedium2(manualModeContainer.getContext()))
                        .setInterpolator(Motion.emphasizedDecelerate(manualModeContainer.getContext()))
                        .withEndAction(() -> manualModeContainer.setVisibility(View.GONE))
                        .start();
                if (optionBar != null) {
                    optionBar.animate().scaleX(0f).scaleY(0f)
                            .setDuration(Motion.durationMedium2(optionBar.getContext()))
                            .setInterpolator(Motion.emphasizedDecelerate(optionBar.getContext()))
                            .start();
                }
            });
        }
    }

    public static void rotateKnobView(KnobView view, int orientation) {
        view.setKnobItemsRotation(Rotation.fromDeviceOrientation(orientation));
    }

    /**
     * Rotates each option label inside its (unrotated) cell. The selection pill
     * lives on the cell, so keeping it upright prevents the rotated pill from
     * being clipped to a hard-cornered rectangle by the bar in landscape.
     */
    public static void rotateManualOptionContent(ViewGroup bar, int orientation, long duration) {
        if (bar == null) {
            return;
        }
        for (int i = 0; i < bar.getChildCount(); i++) {
            View child = bar.getChildAt(i);
            if (child instanceof ViewGroup) {
                ViewGroup cell = (ViewGroup) child;
                for (int j = 0; j < cell.getChildCount(); j++) {
                    cell.getChildAt(j).animate().rotation(orientation).setDuration(duration)
                            .setInterpolator(Motion.emphasized(cell.getContext())).start();
                }
            }
        }
    }

}
