package com.particlesdevs.photoncamera.circularbarlib.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;

import com.particlesdevs.photoncamera.circularbarlib.R;
import com.particlesdevs.photoncamera.circularbarlib.control.models.ManualModel;
import com.particlesdevs.photoncamera.circularbarlib.util.Motion;
import com.particlesdevs.photoncamera.circularbarlib.ui.views.slider.ManualSliderView;

/**
 * Wires manual slider strips to their models and drives panel animations.
 */
public class Binding {
    /**
     * Shows/hides the slider rows above the option bar. The primary row slides
     * up and fades in; the secondary row (when present) is handled by
     * {@link #setModelToSlider} so the scrim height follows the row count.
     * The running animator is parked on the primary view so a re-trigger
     * mid-flight reverses smoothly from the current progress.
     */
    public static void setSliderVisibility(ViewGroup manualModeContainer,
                                           ManualSliderView primary,
                                           ManualSliderView secondary,
                                           Boolean visible) {
        if (manualModeContainer == null || visible == null) {
            return;
        }
        View sliderContainer = manualModeContainer.findViewById(R.id.sliderContainer);
        View target = sliderContainer != null ? sliderContainer : primary;
        if (target == null) {
            return;
        }
        Object running = target.getTag(R.id.sliderContainer);
        if (running instanceof ValueAnimator) {
            ((ValueAnimator) running).cancel();
        }
        float start = target.getAlpha();
        // When the container itself is gone, start from hidden.
        if (target.getVisibility() != View.VISIBLE && visible) {
            start = 0f;
        }
        if (visible) {
            if (primary != null && primary.getVisibility() != View.VISIBLE
                    && primary.getItems() != null && !primary.getItems().isEmpty()) {
                primary.setVisibility(View.VISIBLE);
            }
            target.setAlpha(start);
            target.setTranslationY((1f - start) * target.getResources()
                    .getDimension(R.dimen.manual_slider_row_height) * 0.5f);
            target.setVisibility(View.VISIBLE);
        } else if (start <= 0f && target.getVisibility() != View.VISIBLE) {
            return;
        }
        ValueAnimator animator = ValueAnimator.ofFloat(start, visible ? 1f : 0f);
        animator.setDuration(visible
                ? Motion.durationMedium1(target.getContext())
                : Motion.durationShort4(target.getContext()));
        animator.setInterpolator(visible
                ? Motion.emphasized(target.getContext())
                : Motion.emphasizedAccelerate(target.getContext()));
        float rowHeight = target.getResources().getDimension(R.dimen.manual_slider_row_height);
        animator.addUpdateListener(animation -> {
            float t = (float) animation.getAnimatedValue();
            target.setAlpha(t);
            target.setTranslationY((1f - t) * rowHeight * 0.5f);
            if (primary != null) {
                primary.setAlpha(t);
            }
            if (secondary != null && secondary.getVisibility() == View.VISIBLE) {
                secondary.setAlpha(t);
            }
        });
        if (!visible) {
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    target.setVisibility(View.GONE);
                }
            });
        }
        animator.start();
        target.setTag(R.id.sliderContainer, animator);
    }

    /**
     * Wires the slider row(s): the selected control as the primary (lower) row
     * and the remembered previous control (when present) as the secondary
     * (upper) row. Both are re-snapped to their models' current values.
     * The option bar's top padding grows to make room so the single scrim
     * background extends with the same rounded edges.
     */
    public static void setModelToSlider(ManualSliderView primary,
                                        ManualSliderView secondary,
                                        ViewGroup manualModeContainer,
                                        ManualModel<?> primaryModel,
                                        ManualModel<?> secondaryModel) {
        if (primary != null) {
            // Lower row always carries the primary selection color.
            primary.setSecondary(false);
            if (primaryModel != null && primaryModel.getSliderItems() != null
                    && !primaryModel.getSliderItems().isEmpty()) {
                primary.setItems(primaryModel.getSliderItems());
                primary.setListener(primaryModel);
                if (primaryModel.getCurrentInfo() != null) {
                    primary.setSelectedByValue(primaryModel.getCurrentInfo().value, true);
                }
                if (primary.getVisibility() != View.VISIBLE) {
                    primary.setVisibility(View.VISIBLE);
                }
            } else {
                primary.setListener(null);
                primary.setVisibility(View.GONE);
            }
        }
        if (secondary != null) {
            // Upper (remembered) row always carries the secondary selection
            // color, matching the bar's remembered-cell ring.
            secondary.setSecondary(true);
            if (secondaryModel != null && secondaryModel.getSliderItems() != null
                    && secondaryModel.getSliderItems().size() > 1) {
                secondary.setItems(secondaryModel.getSliderItems());
                secondary.setListener(secondaryModel);
                if (secondaryModel.getCurrentInfo() != null) {
                    secondary.setSelectedByValue(secondaryModel.getCurrentInfo().value, true);
                }
                if (secondary.getVisibility() != View.VISIBLE) {
                    secondary.setAlpha(primary != null ? primary.getAlpha() : 1f);
                    secondary.setVisibility(View.VISIBLE);
                    secondary.setTranslationY(secondary.getResources()
                            .getDimension(R.dimen.manual_slider_row_height) * 0.3f);
                    secondary.animate().translationY(0f)
                            .setDuration(Motion.durationMedium1(secondary.getContext()))
                            .setInterpolator(Motion.emphasized(secondary.getContext()))
                            .start();
                }
            } else if (secondary.getVisibility() != View.GONE) {
                secondary.animate().alpha(0f).translationY(
                        secondary.getResources().getDimension(R.dimen.manual_slider_row_height) * 0.3f)
                        .setDuration(Motion.durationShort4(secondary.getContext()))
                        .setInterpolator(Motion.emphasizedAccelerate(secondary.getContext()))
                        .withEndAction(() -> {
                            secondary.setVisibility(View.GONE);
                            secondary.setAlpha(1f);
                        })
                        .start();
                secondary.setListener(null);
            }
        }
        updateSliderPadding(manualModeContainer, primaryModel, secondaryModel);
    }

    private static void updateSliderPadding(ViewGroup manualModeContainer,
                                            ManualModel<?> primaryModel,
                                            ManualModel<?> secondaryModel) {
        if (manualModeContainer == null) {
            return;
        }
        View bar = manualModeContainer.findViewById(R.id.buttons_container);
        View sliderContainer = manualModeContainer.findViewById(R.id.sliderContainer);
        if (bar == null) {
            return;
        }
        float rowHeight = bar.getResources().getDimension(R.dimen.manual_slider_row_height);
        boolean hasPrimary = primaryModel != null && primaryModel.getSliderItems() != null
                && !primaryModel.getSliderItems().isEmpty();
        boolean hasSecondary = secondaryModel != null && secondaryModel.getSliderItems() != null
                && secondaryModel.getSliderItems().size() > 1;
        int rows = !hasPrimary ? 0 : (hasSecondary ? 2 : 1);
        int desiredPadding = (int) (rowHeight * rows);
        if (sliderContainer != null) {
            if (rows == 0 && sliderContainer.getVisibility() != View.GONE) {
                sliderContainer.setVisibility(View.GONE);
            } else if (rows > 0 && sliderContainer.getVisibility() != View.VISIBLE) {
                sliderContainer.setVisibility(View.VISIBLE);
            }
        }
        // Animate padding growth so the scrim extends smoothly with the new row.
        if (Math.abs(bar.getPaddingTop() - desiredPadding) < 1f) {
            return;
        }
        int from = bar.getPaddingTop();
        ValueAnimator animator = ValueAnimator.ofInt(from, desiredPadding);
        animator.setDuration(Motion.durationMedium1(bar.getContext()));
        animator.setInterpolator(Motion.emphasized(bar.getContext()));
        animator.addUpdateListener(a -> {
            int pad = (int) a.getAnimatedValue();
            bar.setPadding(bar.getPaddingLeft(), pad, bar.getPaddingRight(), bar.getPaddingBottom());
        });
        animator.start();
    }

    public static void resetSlider(ManualSliderView primary, boolean toReset) {
        if (toReset && primary != null && !primary.getItems().isEmpty()) {
            primary.setSelectedIndex(0, true);
        }
    }

    /**
     * Reveals/hides the manual palette with the same M3E motion as the quick
     * settings panel: fade + slide (by {@code manual_panel_slide}) with an
     * emphasized curve over medium2. The option bar collapses through its own
     * scale so its blur region (and the lens cluster's offset math) stays
     * exact — the container itself is never scaled.
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

    /**
     * The bar's bounds include the slider zone above the pill, so its default
     * centre pivot sits above the visible bubble. Pinning the pivot to the pill
     * keeps the reveal scale, the predictive back shrink and the blur math
     * centred on what is actually shown.
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
