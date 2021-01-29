package com.particlesdevs.photoncamera.ui.camera.binding;

import android.view.View;

import android.view.ViewGroup;

import androidx.databinding.BindingAdapter;
import com.particlesdevs.photoncamera.ui.camera.views.manualmode.knobview.KnobView;
import com.particlesdevs.photoncamera.ui.camera.views.manualmode.knobview.Rotation;
import com.particlesdevs.photoncamera.ui.camera.model.CameraFragmentModel;

/**
 * class to handel custom bindings that should get applied when a model change
 */
public class CustomBinding {

    //handel the rotation that should get applied when the CameraFragmentModels rotation change
    //the view item must add bindRotate="@{uimodel}"/>
    @BindingAdapter("bindRotate")
    public static void rotateView(View view, CameraFragmentModel model) {
        if (model != null)
            view.animate().rotation(model.getOrientation()).setDuration(model.getDuration()).start();
    }

    //handle the rotation that should get applied to "@+id/knobView" when the CameraFragmentModel's rotation changes
    //the ui item must add bindKnobRotate="@{uimodel}"/>
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

    @BindingAdapter("selected")
    public static void setSelected(View view, Boolean selected) {
        if (selected != null && view != null) {
            view.setSelected(selected);
        }
    }
}
