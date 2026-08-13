package com.particlesdevs.photoncamera.gallery.adapters;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.signature.ObjectKey;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.particlesdevs.photoncamera.gallery.compare.SSIVListener;
import com.particlesdevs.photoncamera.gallery.helper.UltraHdrGalleryUtil;
import com.particlesdevs.photoncamera.gallery.model.GalleryItem;
import com.particlesdevs.photoncamera.gallery.views.CustomSSIV;

import org.apache.commons.io.FileUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;



public class ImageAdapter extends PagerAdapter {
    private static final int BASE_ID = View.generateViewId();
    private static final ExecutorService HDR_EXECUTOR = Executors.newSingleThreadExecutor();
    private final List<GalleryItem> galleryItemList;
    private final boolean[] hdrRequested;
    private final boolean[] hdrActive;
    private final Bitmap[] hdrBitmaps;
    private final Target<Bitmap>[] hdrTargets;
    private ImageViewClickListener imageViewClickListener;
    private SSIVListener ssivListener;
    private SubsamplingScaleImageView.OnImageEventListener imageEventListener;
    private HdrStateListener hdrStateListener;


    public ImageAdapter(List<GalleryItem> galleryItemList) {
        this.galleryItemList = galleryItemList;
        int size = galleryItemList.size();
        this.hdrRequested = new boolean[size];
        this.hdrActive = new boolean[size];
        this.hdrBitmaps = new Bitmap[size];
        this.hdrTargets = new Target[size];
    }

    public void setSsivListener(SSIVListener ssivListener) {
        this.ssivListener = ssivListener;
    }

    public void setImageEventListener(SubsamplingScaleImageView.OnImageEventListener imageEventListener) {
        this.imageEventListener = imageEventListener;
    }

    public void setHdrStateListener(HdrStateListener hdrStateListener) {
        this.hdrStateListener = hdrStateListener;
    }

    @Override
    public int getCount() {
        return galleryItemList.size();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view == object;
    }


    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        GalleryItem galleryItem = galleryItemList.get(position);
        String fileExt = FileUtils.getExtension(galleryItem.getFile().getDisplayName());

        CustomSSIV scaleImageView = new CustomSSIV(container.getContext());
        scaleImageView.setId(getSsivId(position));
        if (imageViewClickListener != null) {
            scaleImageView.setOnClickListener(v -> imageViewClickListener.onImageViewClicked(v));
        }
        if (ssivListener != null) {
            scaleImageView.setOnStateChangedListener(ssivListener);
            scaleImageView.setTouchCallBack(ssivListener);
        }
        scaleImageView.setOnImageEventListener(imageEventListener);
        if (!fileExt.equalsIgnoreCase("dng")) {
            scaleImageView.setImage(ImageSource.uri(galleryItem.getFile().getFileUri()));
        } else { //For DNG Files, load as a bitmap
            Glide.with(container.getContext())
                    .asBitmap()
                    .load(galleryItem.getFile().getFileUri())
                    .apply(RequestOptions.signatureOf(new ObjectKey(galleryItem.getFile().getDisplayName() + galleryItem.getFile().getLastModified())))
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
        releaseHdrResources(container.getContext(), position);
        container.removeView((View) object);
    }

    public void setImageViewClickListener(ImageViewClickListener imageViewClickListener) {
        this.imageViewClickListener = imageViewClickListener;
    }

    public int getSsivId(int position) {
        return BASE_ID + position;
    }

    /**
     * Whether the page at {@code position} is currently displaying an
     * Ultra HDR bitmap (gainmap attached) rather than the SDR tiled source.
     */
    public boolean isHdrActive(int position) {
        return inBounds(position) && hdrActive[position];
    }

    /**
     * Loads the Ultra HDR rendition for the page at {@code position} if the
     * file contains a gain map and the device can display it. Cheap header
     * scan first, then a full-resolution Glide decode which preserves the
     * gainmap. No-op for SDR files, DNGs, non-HDR devices or pages already
     * in HDR mode.
     */
    public void loadHdrForPosition(CustomSSIV scaleImageView, int position) {
        if (scaleImageView == null || !inBounds(position)) {
            return;
        }
        if (hdrRequested[position] || hdrActive[position]) {
            return;
        }
        Context context = scaleImageView.getContext();
        if (!UltraHdrGalleryUtil.isDeviceHdrCapable(context)) {
            return;
        }
        GalleryItem galleryItem = galleryItemList.get(position);
        if (FileUtils.getExtension(galleryItem.getFile().getDisplayName()).equalsIgnoreCase("dng")) {
            return;
        }
        hdrRequested[position] = true;
        HDR_EXECUTOR.execute(() -> {
            boolean candidate = UltraHdrGalleryUtil.isUltraHdrJpeg(context,
                    galleryItem.getFile().getFileUri());
            scaleImageView.post(() -> {
                if (!hdrRequested[position] || !inBounds(position)) {
                    return;
                }
                if (!candidate) {
                    return;
                }
                decodeHdrBitmap(scaleImageView, position);
            });
        });
    }

    /**
     * Releases the Ultra HDR bitmap of a page, reverting it to the SDR
     * tiled source. Keeps at most one full-resolution HDR bitmap alive.
     */
    public void releaseHdrForPosition(CustomSSIV scaleImageView, int position) {
        if (scaleImageView == null || !inBounds(position)) {
            return;
        }
        boolean wasActive = hdrActive[position];
        releaseHdrResources(scaleImageView.getContext(), position);
        if (wasActive) {
            scaleImageView.setImage(ImageSource.uri(galleryItemList.get(position).getFile().getFileUri()));
            if (hdrStateListener != null) {
                hdrStateListener.onHdrStateChanged(position, false);
            }
        }
    }

    private void releaseHdrResources(Context context, int position) {
        if (!inBounds(position)) {
            return;
        }
        hdrRequested[position] = false;
        hdrActive[position] = false;
        hdrBitmaps[position] = null;
        Target<Bitmap> target = hdrTargets[position];
        hdrTargets[position] = null;
        if (target != null && context != null) {
            Glide.with(context).clear(target);
        }
    }

    private void decodeHdrBitmap(CustomSSIV scaleImageView, int position) {
        GalleryItem galleryItem = galleryItemList.get(position);
        CustomTarget<Bitmap> target = new CustomTarget<Bitmap>(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL) {
            @Override
            public void onResourceReady(@NonNull Bitmap bitmap, @Nullable Transition<? super Bitmap> transition) {
                if (!hdrRequested[position] || !inBounds(position)) {
                    return;
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                        || !bitmap.hasGainmap()) {
                    return;
                }
                hdrActive[position] = true;
                hdrBitmaps[position] = bitmap;
                scaleImageView.setImage(ImageSource.cachedBitmap(bitmap));
                if (hdrStateListener != null) {
                    hdrStateListener.onHdrStateChanged(position, true);
                }
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
            }
        };
        hdrTargets[position] = target;
        Glide.with(scaleImageView.getContext())
                .asBitmap()
                .load(galleryItem.getFile().getFileUri())
                .apply(new RequestOptions()
                        .diskCacheStrategy(DiskCacheStrategy.DATA)
                        .skipMemoryCache(true))
                .into(target);
    }

    private boolean inBounds(int position) {
        return position >= 0 && position < galleryItemList.size();
    }

    public interface ImageViewClickListener {
        void onImageViewClicked(View v);
    }

    /**
     * Notifies the host fragment when a page's HDR state changes so it can
     * toggle the window color mode.
     */
    public interface HdrStateListener {
        void onHdrStateChanged(int position, boolean isHdr);
    }
}
