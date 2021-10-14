package com.particlesdevs.photoncamera.gallery.binding;

import android.graphics.Bitmap;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import androidx.databinding.BindingAdapter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.particlesdevs.photoncamera.gallery.model.GalleryItem;
import com.particlesdevs.photoncamera.gallery.views.Histogram;

public class CustomBinding {
    /**
     * Updates {@link Histogram.HistogramModel} associated with the {@link Histogram}(here, Histogram with id "histogram_view")
     *
     * @param histogram the {@link Histogram} object which has used the attribute bindHistogram="@{exifmodel.histogramModel}"
     * @param model     the {@link Histogram.HistogramModel} object associated with the parent layout of this viewGroup
     */
    @BindingAdapter("bindHistogram")
    public static void updateHistogram(Histogram histogram, Histogram.HistogramModel model) {
        if (model != null) {
            histogram.setHistogramModel(model);
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

    @BindingAdapter("loadImage")
    public static void loadImage(ImageView imageView, GalleryItem galleryItem) {
        if (galleryItem != null) {
            Glide
                    .with(imageView)
                    .asBitmap()
                    .load(galleryItem.getFile().getFileUri())
                    .apply(new RequestOptions()
                            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                            .signature(new ObjectKey(galleryItem.getFile().getDisplayName() + galleryItem.getFile().getLastModified()))
                            .override(200, 200)
                            .centerCrop()
                    )
                    .into(imageView);
        }
    }
}
