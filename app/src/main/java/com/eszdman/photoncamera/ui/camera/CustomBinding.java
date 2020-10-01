package com.eszdman.photoncamera.ui.camera;

import android.view.View;

import androidx.databinding.BindingAdapter;

import com.eszdman.photoncamera.R;

public class CustomBinding {

    @BindingAdapter("bindRotate")
    public static void rotatetView(View view, CameraFragmentModel model)
    {
        if (model != null && view.getId() != R.id.layout_topbar)
            view.animate().rotation(model.getOrientation()).setDuration(model.getDuration()).start();
    }
}
