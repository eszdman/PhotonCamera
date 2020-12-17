package com.eszdman.photoncamera.gallery.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.signature.ObjectKey;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.List;


public class ImageAdapter extends PagerAdapter {
    private final List<File> imageFiles;

    public ImageAdapter(List<File> imageFiles) {
        this.imageFiles = imageFiles;
    }

    @Override
    public int getCount() {
        return imageFiles.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }


    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        File file = imageFiles.get(position);
        String fileExt = FileUtils.getExtension(file.getName());

        SubsamplingScaleImageView scaleImageView = new CustomSSIV(container.getContext());

        if (fileExt.equalsIgnoreCase("jpg") || fileExt.equalsIgnoreCase("png")) {
            scaleImageView.setImage(ImageSource.uri(Uri.fromFile(file)));
        } else if (fileExt.equalsIgnoreCase("dng")) { //For DNG Files
            Glide.with(container.getContext())
                    .asBitmap()
                    .load(file)
                    .apply(RequestOptions.signatureOf(new ObjectKey(file.getName() + file.lastModified())))
                    .into(new CustomViewTarget<SubsamplingScaleImageView, Bitmap>(scaleImageView) {
                        @Override
                        public void onResourceReady(@NonNull Bitmap bitmap, Transition<? super Bitmap> transition) {
                            scaleImageView.setImage(ImageSource.cachedBitmap(bitmap));
                        }

                        @Override
                        protected void onResourceCleared(@Nullable Drawable placeholder) {

                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {

                        }
                    });
        }
        container.addView(scaleImageView);
        return scaleImageView;
    }

    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
        container.removeView((View) object);
    }

    static class CustomSSIV extends SubsamplingScaleImageView {
        public CustomSSIV(Context context) {
            super(context);
            setMinimumDpi(40);
            setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
            setQuickScaleEnabled(true);
            setEagerLoadingEnabled(false);
            setDoubleTapZoomDuration(200);
            setPreferredBitmapConfig(Bitmap.Config.ARGB_8888);
        }
    }
}


