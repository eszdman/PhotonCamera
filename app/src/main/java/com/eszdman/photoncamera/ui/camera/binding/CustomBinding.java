package com.eszdman.photoncamera.ui.camera.binding;

import android.view.View;

import android.view.ViewGroup;
import androidx.databinding.BindingAdapter;

import com.eszdman.photoncamera.ui.camera.model.CameraFragmentModel;
import com.manual.KnobView;
import com.manual.Rotation;

/**
 * class to handel custom bindings that should get applied when a model change
 */
public class CustomBinding {

    //handel the rotation that should get applied when the CameraFragmentModels rotation change
    //the view item must add bindRotate="@{uimodel}"/>
    @BindingAdapter("bindRotate")
    public static void rotatetView(View view, CameraFragmentModel model)
    {
        if (model != null)
            view.animate().rotation(model.getOrientation()).setDuration(model.getDuration()).start();
    }

    //handel the rotation that should get applied to "@+id/buttons_container" when the CameraFragmentModels rotation change
    //the ui item must add bindChildsRotate="@{uimodel}"/>
    @BindingAdapter("bindChildsRotate")
    public static void rotatetKnobView(KnobView view, CameraFragmentModel model)
    {
        if (model != null) {
            int orientation = model.getOrientation();
            view.setKnobItemsRotation(Rotation.fromDeviceOrientation(orientation));
        }
    }

    /**
     * Handle the rotation that should get applied to any ViewGroup when the CameraFragmentModels rotation change
     * Only the children views within the ViewGroup will rotate.
     * the ui item must add bindViewGroupChildsRotate="@{uimodel}"
     */
    @BindingAdapter("bindViewGroupChildsRotate")
    public static void rotateAuxButtons(ViewGroup viewGroup, CameraFragmentModel model) {
        if (model != null) {
            int orientation = model.getOrientation();
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                viewGroup.getChildAt(i).animate().rotation(orientation).setDuration(model.getDuration()).start();
            }
        }
    }
}
