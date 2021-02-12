package com.particlesdevs.photoncamera.ui.camera.binding;

import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;

import androidx.databinding.BindingAdapter;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.manual.model.ManualModel;
import com.particlesdevs.photoncamera.ui.camera.model.AuxButtonsModel;
import com.particlesdevs.photoncamera.ui.camera.model.CameraFragmentModel;
import com.particlesdevs.photoncamera.ui.camera.model.KnobModel;
import com.particlesdevs.photoncamera.ui.camera.views.AuxButtonsLayout;
import com.particlesdevs.photoncamera.ui.camera.views.manualmode.knobview.KnobView;
import com.particlesdevs.photoncamera.ui.camera.views.manualmode.knobview.Rotation;

/**
 * Class to handle custom bindings that should get applied when a model change
 * <p>
 * Created by KillerInk on 02/Oct/2020
 * Modified by Vibhor
 */
public class CustomBinding {

    /**
     * Handle the rotation that should get applied when the CameraFragmentModels rotation change
     * the view item must add attribute 'bindRotate="@{uimodel}"'
     *
     * @param view  any view that needs to be rotated
     * @param model the cameraFragmentModel
     */
    @BindingAdapter("bindRotate")
    public static void rotateView(View view, CameraFragmentModel model) {
        if (model != null)
            view.animate().rotation(model.getOrientation()).setDuration(model.getDuration()).start();
    }

    /**
     * Handle the rotation that should get applied to "@+id/knobView" when the CameraFragmentModel's rotation changes
     * the ui item must add attribute 'bindKnobRotate="@{uimodel}"'
     *
     * @param view  the target KnobView
     * @param model the cameraFragmentModel
     */
    @BindingAdapter("bindKnobRotate")
    public static void rotateKnobView(KnobView view, CameraFragmentModel model) {
        if (model != null) {
            int orientation = model.getOrientation();
            view.setKnobItemsRotation(Rotation.fromDeviceOrientation(orientation));
        }
    }

    /**
     * Handle the rotation that should get applied to any ViewGroup when the CameraFragmentModels rotation change
     * Only the children views within the ViewGroup will rotate.
     * the ui item must add bindViewGroupChildrenRotate="@{uimodel}"
     *
     * @param viewGroup the container ViewGroup
     * @param model     the cameraFragmentModel
     */
    @BindingAdapter("bindViewGroupChildrenRotate")
    public static void rotateAuxButtons(ViewGroup viewGroup, CameraFragmentModel model) {
        if (model != null) {
            int orientation = model.getOrientation();
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                viewGroup.getChildAt(i).animate().rotation(orientation).setDuration(model.getDuration()).start();
            }
        }
    }

    /**
     * Change the selected state of any view
     *
     * @param view     the target view
     * @param selected whether selected
     */
    @BindingAdapter("android:selected")
    public static void setSelected(View view, Boolean selected) {
        if (selected != null && view != null) {
            view.setSelected(selected);
        }
    }

    /**
     * Toggles the visibility of {@link KnobView} with custom animation.
     * The attribute 'knobVisibility="@{knob_model.knobVisible}"' should be added to the KnobView instance in xml layout
     *
     * @param knobView    the target KnobView instance
     * @param knobVisible the target visibility
     */
    @BindingAdapter("knobVisibility")
    public static void setKnobVisibility(KnobView knobView, Boolean knobVisible) {
        if (knobView != null && knobVisible != null) {
            if (knobVisible) {
                knobView.animate().translationY(0).scaleY(1).scaleX(1).setDuration(200).alpha(1f).start();
                knobView.setVisibility(View.VISIBLE);
            } else {
                knobView.animate().translationY(knobView.getHeight() / 2.5f)
                        .scaleY(.2f).scaleX(.2f).setDuration(200).alpha(0f)
                        .withEndAction(() -> knobView.setVisibility(View.GONE)).start();
            }
        }
    }

    /**
     * Sets the {@link KnobModel} to the KnobView instance
     * The attribute 'setKnobModel="@{knob_model}"' should be added to the KnobView instance in xml layout
     *
     * @param knobView    the target KnobView instance
     * @param manualModel the ManualModel object
     */
    @BindingAdapter("setKnobModel")
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

    /**
     * Calls {@link KnobView#resetKnob()} if boolean value is true
     * Usage 'knobReset="@{knob_model.toReset}"'
     *
     * @param knobView the target KnobView instance
     * @param toReset  whether to reset
     */
    @BindingAdapter("knobReset")
    public static void resetKnob(KnobView knobView, boolean toReset) {
        if (toReset) {
            knobView.resetKnob();
        }
    }

    /**
     * Toggles the visibility with animation for {@link ViewGroup} containing manual controls, here {@link R.id#manual_mode}
     *
     * @param manualModeContainer parent ViewGroup
     * @param visible             target visibility
     */
    @BindingAdapter("manualPanelVisibility")
    public static void togglePanelVisibility(ViewGroup manualModeContainer, Boolean visible) {
        if (visible) {
            manualModeContainer.post(() -> {
                manualModeContainer.animate().translationY(0).setDuration(100).alpha(1f).start();
                manualModeContainer.setVisibility(View.VISIBLE);
            });
        } else {
            manualModeContainer.post(() -> manualModeContainer.animate()
                    .translationY(manualModeContainer.getResources().getDimension(R.dimen.standard_20))
                    .alpha(0f)
                    .setDuration(100)
                    .withEndAction(() -> manualModeContainer.setVisibility(View.GONE))
                    .start());
        }
    }

    /**
     * Selects/unselects the children of the target {@link ViewGroup} here {@link R.id#buttons_container}.
     * Only the child with given view id will be selected and rest of children will get unselected.
     *
     * @param viewGroup the target ViewGroup
     * @param viewID    id of the {@link CheckedTextView} to be checked
     */
    @BindingAdapter("selectViewIdInViewGroup")
    public static void selectViewIdInViewGroup(ViewGroup viewGroup, int viewID) {
        if (viewGroup != null) {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                viewGroup.getChildAt(i).setSelected(viewGroup.getChildAt(i).getId() == viewID);
            }
        }
    }

    @BindingAdapter("settingsBarVisibility")
    public static void toggleSettingsBarVisibility(ViewGroup viewGroup, boolean visible) {
        if (viewGroup != null) {
            if (visible) {
                viewGroup.post(() -> {
                    viewGroup.animate().setDuration(200).alpha(1).translationY(0).scaleX(1).scaleY(1).start();
                    viewGroup.setVisibility(View.VISIBLE);
                });
            } else {
                viewGroup.post(() -> viewGroup.animate().setDuration(200).alpha(0).translationY(-viewGroup.getResources().getDimension(R.dimen.standard_125))
                        .scaleX(0).scaleY(0).withEndAction(() -> viewGroup.setVisibility(View.INVISIBLE))
                        .start());
            }
        }

    }

    @BindingAdapter("setAuxButtonModel")
    public static void setAuxButtonModel(AuxButtonsLayout layout, AuxButtonsModel auxButtonsModel) {
        if (auxButtonsModel != null) {
            layout.setAuxButtonsModel(auxButtonsModel);
        }
    }

    @BindingAdapter("setActiveId")
    public static void setActiveCameraId(AuxButtonsLayout layout, String cameraId) {
        if (cameraId != null) {
            layout.setActiveId(cameraId);
        }
    }
}
