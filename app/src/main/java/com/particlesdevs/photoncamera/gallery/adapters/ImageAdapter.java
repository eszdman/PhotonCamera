package com.particlesdevs.photoncamera.gallery.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
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
import com.particlesdevs.photoncamera.gallery.compare.SSIVListener;
import com.particlesdevs.photoncamera.gallery.files.ImageFile;

import org.apache.commons.io.FileUtils;

import java.util.List;

import static com.particlesdevs.photoncamera.gallery.helper.Constants.DOUBLE_TAP_ZOOM_DURATION_MS;


public class ImageAdapter extends PagerAdapter {
    private static final int BASE_ID = View.generateViewId();
    private final List<ImageFile> imageFiles;
    private ImageViewClickListener imageViewClickListener;
    private SSIVListener ssivListener;

    public ImageAdapter(List<ImageFile> imageFiles) {
        this.imageFiles = imageFiles;
    }

    public void setSsivListener(SSIVListener ssivListener) {
        this.ssivListener = ssivListener;
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
        ImageFile file = imageFiles.get(position);
        String fileExt = FileUtils.getExtension(file.getDisplayName());

        CustomSSIV scaleImageView = new CustomSSIV(container.getContext());
        scaleImageView.setId(getSsivId(position));
        if (imageViewClickListener != null) {
            scaleImageView.setOnClickListener(v -> imageViewClickListener.onImageViewClicked(v));
        }
        if (ssivListener != null) {
            scaleImageView.setOnStateChangedListener(ssivListener);
            scaleImageView.setTouchCallBack(ssivListener);
        }
        if (fileExt.equalsIgnoreCase("jpg") || fileExt.equalsIgnoreCase("png")) {
            scaleImageView.setImage(ImageSource.uri(file.getFileUri()));
        } else if (fileExt.equalsIgnoreCase("dng")) { //For DNG Files
            Glide.with(container.getContext())
                    .asBitmap()
                    .load(file.getFileUri())
                    .apply(RequestOptions.signatureOf(new ObjectKey(file.getDisplayName() + file.getLastModified())))
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

    public void setImageViewClickListener(ImageViewClickListener imageViewClickListener) {
        this.imageViewClickListener = imageViewClickListener;
    }

    public int getSsivId(int position) {
        return BASE_ID + position;
    }

    public interface ImageViewClickListener {
        void onImageViewClicked(View v);
    }

    public static class CustomSSIV extends SubsamplingScaleImageView {
        private TouchCallBack touchCallBack;

        public CustomSSIV(Context context) {
            super(context);
            setMinimumDpi(40);
            setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
            setQuickScaleEnabled(true);
            setEagerLoadingEnabled(false);
            setDoubleTapZoomDuration(DOUBLE_TAP_ZOOM_DURATION_MS);
            setPreferredBitmapConfig(Bitmap.Config.ARGB_8888);
        }

        @Override
        public boolean onTouchEvent(@NonNull MotionEvent event) {
            if (touchCallBack != null) {
                touchCallBack.onTouched(getId());
            }
            return super.onTouchEvent(event);
        }

        public void setTouchCallBack(TouchCallBack touchCallBack) {
            this.touchCallBack = touchCallBack;
        }

        public interface TouchCallBack {
            void onTouched(int id);
        }
    }
}


