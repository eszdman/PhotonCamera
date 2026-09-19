package com.particlesdevs.photoncamera.circularbarlib.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;

import com.particlesdevs.photoncamera.circularbarlib.R;
import com.particlesdevs.photoncamera.circularbarlib.control.models.ManualModel;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.ManualPaletteBackground;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.KnobView;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.knobview.Rotation;

/**
 * Created by vibhorSrv
 */
public class Binding {
    /**
     * Grows/retracts the wheel dome out of the palette bubble: one animator
     * drives the bubble background's dome, the knob items' ride-along squash
     * and the knob's alpha (which the lens cluster offset follows). The
     * running animator is parked on the knob view so a re-trigger mid-flight
     * reverses smoothly from the current progress.
     */
    public static void setKnobVisibility(ViewGroup manualModeContainer, KnobView knobView, Boolean knobVisible) {
        if (manualModeContainer == null || knobView == null || knobVisible == null) {
            return;
        }
        ManualPaletteBackground palette = findPaletteBackground(manualModeContainer);
        Object running = knobView.getTag(R.id.knobView);
        if (running instanceof ValueAnimator) {
            ((ValueAnimator) running).cancel();
        }
        float start = palette != null ? palette.getDomeProgress() : knobView.getAlpha();
        if (knobVisible) {
            knobView.setAlpha(start);
            knobView.setDomeProgress(start);
            knobView.setVisibility(View.VISIBLE);
        } else if (start <= 0f) {
            knobView.setVisibility(View.GONE);
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(start, knobVisible ? 1f : 0f);
        animator.setDuration(knobVisible
                ? Motion.durationMedium1(knobView.getContext())
                : Motion.durationShort4(knobView.getContext()));
        animator.setInterpolator(knobVisible
                ? Motion.emphasized(knobView.getContext())
                : Motion.emphasizedAccelerate(knobView.getContext()));
        animator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            if (palette != null) {
                palette.setDomeProgress(t);
            }
            knobView.setDomeProgress(t);
            knobView.setAlpha(t);
        });
        if (!knobVisible) {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    knobView.setVisibility(View.GONE);
                }
            });
        }
        animator.start();
        knobView.setTag(R.id.knobView, animator);
    }

    private static ManualPaletteBackground findPaletteBackground(ViewGroup manualModeContainer) {
        View bar = manualModeContainer.findViewById(R.id.buttons_container);
        return bar != null && bar.getBackground() instanceof ManualPaletteBackground
                ? (ManualPaletteBackground) bar.getBackground() : null;
    }

    /**
     * Wires the wheel(s): the selected control as the outer, full-size ruler
     * and the remembered previous control (when present) as the smaller inner
     * one. Both are re-snapped to their models' current values.
     */
    public static void setModelToKnob(KnobView knobView, ManualModel<?> primaryModel, ManualModel<?> secondaryModel) {
        if (knobView == null) {
            return;
        }
        if (primaryModel != null && primaryModel.getKnobInfo() != null) {
            knobView.setPrimaryWheel(primaryModel.getKnobInfo(), primaryModel.getKnobInfoList(),
                    primaryModel.getCurrentInfo().value, primaryModel);
        }
        if (secondaryModel != null && secondaryModel.getKnobInfo() != null
                && secondaryModel.getKnobInfoList().size() > 1) {
            knobView.setSecondaryWheel(secondaryModel.getKnobInfo(), secondaryModel.getKnobInfoList(),
                    secondaryModel.getCurrentInfo().value, secondaryModel);
        } else {
            knobView.clearSecondaryWheel();
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
     * exact — the container itself is never scaled. The wheel dome inflates
     * separately through the palette background.
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
                pinOptionBarPivot(optionBar);
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
                pinOptionBarPivot(optionBar);
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
     * The bar's bounds include the (usually collapsed) wheel dome zone above
     * the pill, so its default centre pivot sits above the visible bubble.
     * Pinning the pivot to the pill keeps the reveal scale, the predictive
     * back shrink and the blur math centred on what is actually shown.
     */
    public static void pinOptionBarPivot(View optionBar) {
        if (optionBar == null || optionBar.getHeight() <= 0) {
            return;
        }
        float pillCenterY = optionBar.getHeight()
                - (optionBar.getHeight() - optionBar.getPaddingTop()) / 2f;
        optionBar.setPivotY(pillCenterY);
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
