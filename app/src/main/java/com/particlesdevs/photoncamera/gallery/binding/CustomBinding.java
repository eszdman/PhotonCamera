package com.particlesdevs.photoncamera.gallery.binding;

import android.graphics.Bitmap;
import android.net.Uri;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;

import androidx.databinding.BindingAdapter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.signature.ObjectKey;
import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.gallery.helper.ThumbnailLoader;
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
        // null clears the view: keeping the previous image's model would show
        // a histogram belonging to a different photo.
        histogram.setHistogramModel(model);
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
        if (galleryItem != null && galleryItem.getFile() != null && galleryItem.getFile().getFileUri() != null) {
            Uri uri = galleryItem.getFile().getFileUri();
            // A previous Glide request for this (recycled) view must not land on
            // top of the provider thumbnail.
            try {
                Glide.with(imageView).clear(imageView);
            } catch (Exception ignored) {
            }
            int target = ThumbnailLoader.targetSize(imageView.getContext());
            boolean providerStarted = ThumbnailLoader.loadInto(imageView, uri, galleryItem.getFile().getId(),
                    galleryItem.isVideo(), target, () -> glideLoad(imageView, galleryItem, uri, target));
            if (!providerStarted) {
                glideLoad(imageView, galleryItem, uri, target);
            }
        } else {
            clearImage(imageView);
        }
    }

    /**
     * Fallback for files MediaProvider has no thumbnail for: decode through
     * Glide at the same target size the provider path asked for.
     */
    private static void glideLoad(ImageView imageView, GalleryItem galleryItem, Uri uri, int targetPx) {
        Glide.with(imageView)
                .asBitmap()
                .load(uri)
                .apply(new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                        .signature(new ObjectKey(galleryItem.getFile().getDisplayName() + galleryItem.getFile().getLastModified()))
                        .override(targetPx, targetPx)
                        .centerCrop()
                )
                .into(imageView);
    }

    public static void clearImage(ImageView imageView) {
        try {
            Glide.with(imageView).clear(imageView);
        } catch (Exception ignored) {}
        // Invalidate an in-flight MediaProvider thumbnail request for this view.
        imageView.setTag(R.id.gallery_thumbnail_request, null);
        imageView.setImageDrawable(null);
    }
}
