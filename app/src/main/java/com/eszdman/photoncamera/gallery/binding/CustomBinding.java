package com.eszdman.photoncamera.gallery.binding;

import android.view.View;
import android.view.ViewGroup;
import androidx.databinding.BindingAdapter;
import com.eszdman.photoncamera.gallery.model.ExifDialogModel;

public class CustomBinding {
    /**
     * Updates histogram view associated with the specified ViewGroup(here, LinearLayout with id "exif_histogram")
     *
     * @param viewGroup the ViewGroup object which has used the attribute bindHistogram="@{exifmodel}"
     * @param model     the ExifDialogModel object associated with the parent layout of this viewGroup
     */
    @BindingAdapter("bindHistogram")
    public static void updateHistogram(ViewGroup viewGroup, ExifDialogModel model) {
        if (model != null) {
            View view = model.getHistogram();
            if (view != null) {
                if (view.getParent() != null) {
                    ((ViewGroup) view.getParent()).removeAllViews();
                }
                viewGroup.removeAllViews();
                viewGroup.addView(view);
            }
        }
    }
}
