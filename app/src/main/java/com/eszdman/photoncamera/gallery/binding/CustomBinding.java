package com.eszdman.photoncamera.gallery.binding;

import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
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

    @BindingAdapter("imageFromBitmap")
    public static void setImageBitmap(ImageView view, Bitmap bitmap) {
//        setBitmapWithAnimation(view, bitmap);
        view.setImageBitmap(bitmap);
    }

    private static void setBitmapWithAnimation(ImageView view, Bitmap bitmap) {
        Animation anim_out = AnimationUtils.loadAnimation(view.getContext(), android.R.anim.fade_out);
        Animation anim_in = AnimationUtils.loadAnimation(view.getContext(), android.R.anim.fade_in);
        anim_out.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                view.setImageBitmap(bitmap);
                view.startAnimation(anim_in);
            }
        });
        view.startAnimation(anim_out);
    }
}
