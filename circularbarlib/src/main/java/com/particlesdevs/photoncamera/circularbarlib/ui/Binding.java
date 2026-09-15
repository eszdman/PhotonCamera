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

    public static void togglePanelVisibility(ViewGroup manualModeContainer, Boolean visible) {
        if (visible) {
            manualModeContainer.post(() -> {
                manualModeContainer.animate().translationY(0)
                        .setDuration(Motion.durationShort2(manualModeContainer.getContext()))
                        .setInterpolator(Motion.emphasized(manualModeContainer.getContext()))
                        .alpha(1f).start();
                manualModeContainer.setVisibility(View.VISIBLE);
            });
        } else {
            manualModeContainer.post(() -> manualModeContainer.animate()
                    .translationY(manualModeContainer.getResources().getDimension(R.dimen.standard_20))
                    .alpha(0f)
                    .setDuration(Motion.durationShort2(manualModeContainer.getContext()))
                    .setInterpolator(Motion.emphasizedDecelerate(manualModeContainer.getContext()))
                    .withEndAction(() -> manualModeContainer.setVisibility(View.GONE))
                    .start());
        }
    }

    public static void rotateKnobView(KnobView view, int orientation) {
        view.setKnobItemsRotation(Rotation.fromDeviceOrientation(orientation));
    }

    public static void rotateViewGroupChild(ViewGroup viewGroup, int orientation, long duration) {
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            viewGroup.getChildAt(i).animate().rotation(orientation).setDuration(duration)
                    .setInterpolator(Motion.emphasized(viewGroup.getContext())).start();
        }
    }


}
