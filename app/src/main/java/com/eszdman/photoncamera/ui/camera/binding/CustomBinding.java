package com.eszdman.photoncamera.ui.camera.binding;

import android.view.View;
import android.widget.LinearLayout;

import androidx.databinding.BindingAdapter;

import com.eszdman.photoncamera.R;
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
        if (model != null && view.getId() != R.id.layout_topbar)
            view.animate().rotation(model.getOrientation()).setDuration(model.getDuration()).start();
    }

    //handel the rotation that should get applied to "@+id/buttons_container" when the CameraFragmentModels rotation change
    //the ui item must add bindChildsRotate="@{uimodel}"/>
    @BindingAdapter("bindChildsRotate")
    public static void rotatetKnobView(View view, CameraFragmentModel model)
    {
        if (model != null && view.getId() != R.id.layout_topbar) {
            int orientation = model.getOrientation();
            int duration = model.getDuration();
            LinearLayout defaultKnobView = (LinearLayout) view;
            if (defaultKnobView != null) {
                for (int i = 0; i < defaultKnobView.getChildCount(); i++) {
                    defaultKnobView.getChildAt(i).animate().rotation(orientation).setDuration(duration).start();
                }
            }
        }
    }
}
