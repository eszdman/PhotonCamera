package com.particlesdevs.photoncamera.gallery.adapters;

import android.content.Context;
import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.LruCache;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.bumptech.glide.signature.ObjectKey;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.decoder.SkiaPooledImageRegionDecoder;
import com.particlesdevs.photoncamera.gallery.compare.SSIVListener;
import com.particlesdevs.photoncamera.gallery.helper.HdrTiledRegionDecoder;
import com.particlesdevs.photoncamera.gallery.helper.UltraHdrGalleryUtil;
import com.particlesdevs.photoncamera.gallery.model.GalleryItem;
import com.particlesdevs.photoncamera.gallery.views.CustomSSIV;
import com.particlesdevs.photoncamera.util.Log;

import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Modernized adapter for ViewPager2 – tiled (real-gallery) approach.
 * All pages (SDR, HDR, DNG) use SubsamplingScaleImageView region tiling with sWidth = native dims:
 *   - memory is O(viewport) ~12-15 MB, independent of page count (no full-page cached bitmaps);
 *   - pan/zoom is full-image (no keyhole) because sWidth stays native;
 *   - HDR tiles preserve the gainmap via ImageDecoder ALLOCATOR_HARDWARE (API 31+).
 * This keeps baseline memory low on 4 GB devices and makes swipe seamless via ±1 tile warm-up.
 */
public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.Holder> {
    private static final String TAG = "ImageAdapter";
    // C: single shared pool (2 threads) vs 2×2 pools before — saves ~2 thread stacks (~2 MB) baseline and caps concurrency.
    private static final ExecutorService GALLERY_EXECUTOR = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "GalleryBg");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });

    private final List<GalleryItem> galleryItemList;
    private final boolean[] hdrRequested;
    private final boolean[] hdrActive;
    private final boolean[] hdrAvailable;
    private final boolean[] hdrChecked;
    private final Map<Integer, Future<?>> pendingHdrTasks = new ConcurrentHashMap<>();
    private final Map<Integer, Future<?>> pendingHeaderTasks = new ConcurrentHashMap<>();
    private final Map<Integer, Future<?>> pendingPreviewTasks = new ConcurrentHashMap<>();
    private final Map<Integer, CustomSSIV> activeViews = new ConcurrentHashMap<>();
    private final Map<Integer, Target<Bitmap>> dngTargets = new ConcurrentHashMap<>();
    // Application context used to decode previews independent of view attach state (fixes first-bind).
    private Context appContext;
    // C: small preview + native dimensions per position – shown immediately under tiles so no black flash.
    // Capped to 3 entries and 360px to keep baseline low on 4GB devices (was 6×480px ~9 MB -> now 3×360px ~1.5 MB).
    private final LruCache<Integer, Bitmap> previewCache = new LruCache<>(3);
    private final LruCache<Integer, Point> dimsCache = new LruCache<>(3);
    private static final int PREVIEW_SIDE = 360;
    // Phase1: DNG viewport cache for mixed scrolling OOM fix.
    // Full-res SIZE_ORIGINAL (50MP 8192x6144×4=192MB) OOMs with 2 entries (384MB).
    // Viewport 1080×1920×4≈8.3MB or 1920×1440×4≈11MB → 4-16× saving, still sharp at 1×,
    // soft only on deep zoom >2× (future Phase2 native tiled decoder will give true O(viewport) tiles).
    private final LruCache<Integer, Bitmap> dngBitmapCache = new LruCache<Integer, Bitmap>(2) {
        @Override
        protected int sizeOf(Integer key, Bitmap value) {
            return 1;
        }
    };

    private Point getViewportSize() {
        if (appContext != null) {
            try {
                android.util.DisplayMetrics dm = appContext.getResources().getDisplayMetrics();
                int w = dm.widthPixels;
                int h = dm.heightPixels;
                if (w > 0 && h > 0) return new Point(Math.max(w, h), Math.max(w, h)); // square viewport 1920 ensures portrait/landscape both fit
                // Fallback to max side to avoid aspect stretch
            } catch (Exception ignored) {}
        }
        return new Point(1920, 1920);
    }

    private Point getTargetViewportSize() {
        Point vp = getViewportSize();
        // Use display's longest side as bound – Glide fitCenter will preserve aspect.
        // Keep 1920 max side (≈11MB) for high-res but OOM-safe.
        int maxSide = Math.max(vp.x, vp.y);
        maxSide = Math.min(2048, Math.max(1080, maxSide));
        return new Point(maxSide, maxSide);
    }

    private ImageViewClickListener imageViewClickListener;
    private SSIVListener ssivListener;
    private SubsamplingScaleImageView.OnImageEventListener imageEventListener;
    private HdrStateListener hdrStateListener;

    public ImageAdapter(List<GalleryItem> galleryItemList) {
        this.galleryItemList = galleryItemList;
        int size = galleryItemList.size();
        this.hdrRequested = new boolean[size];
        this.hdrActive = new boolean[size];
        this.hdrAvailable = new boolean[size];
        this.hdrChecked = new boolean[size];
        setHasStableIds(true);
    }

    /**
     * Bind the application context once so preview decode never depends on whether a view is attached.
     */
    public void setAppContext(Context context) {
        if (context != null) this.appContext = context.getApplicationContext();
    }

    private void prefetchHdrHeaders(Context context, int centerPos) {
        if (!UltraHdrGalleryUtil.isDeviceHdrCapable(context)) return;
        int start = Math.max(0, centerPos - 2);
        int end = Math.min(galleryItemList.size() - 1, centerPos + 2);
        for (int i = start; i <= end; i++) {
            if (hdrChecked[i] || hdrRequested[i] || hdrAvailable[i]) continue;
            String ext = "";
            try { ext = FileUtils.getExtension(galleryItemList.get(i).getFile().getDisplayName()); } catch (Exception ignored) {}
            if ("dng".equalsIgnoreCase(ext)) {
                hdrChecked[i] = true;
                continue;
            }
            final int pos = i;
            Future<?> existing = pendingHeaderTasks.get(pos);
            if (existing != null && !existing.isDone()) continue;
            Future<?> f = GALLERY_EXECUTOR.submit(() -> {
                boolean candidate = UltraHdrGalleryUtil.isUltraHdrJpeg(context.getApplicationContext(), galleryItemList.get(pos).getFile().getFileUri());
                CustomSSIV view = activeViews.get(pos);
                Runnable update = () -> {
                    pendingHeaderTasks.remove(pos);
                    hdrChecked[pos] = true;
                    hdrAvailable[pos] = candidate;
                };
                if (view != null) view.post(update);
                else update.run();
            });
            pendingHeaderTasks.put(pos, f);
        }
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= galleryItemList.size()) return RecyclerView.NO_ID;
        GalleryItem item = galleryItemList.get(position);
        if (item.getFile() != null) return item.getFile().getId();
        return position;
    }

    public void setSsivListener(SSIVListener ssivListener) { this.ssivListener = ssivListener; }
    public void setImageEventListener(SubsamplingScaleImageView.OnImageEventListener l) { this.imageEventListener = l; }
    public void setHdrStateListener(HdrStateListener l) { this.hdrStateListener = l; }
    public void setImageViewClickListener(ImageViewClickListener l) { this.imageViewClickListener = l; }

    public int getSsivId(int position) {
        return ViewGroup.generateViewId();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        CustomSSIV ssiv = new CustomSSIV(parent.getContext());
        ssiv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return new Holder(ssiv);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        GalleryItem item = galleryItemList.get(position);
        CustomSSIV ssiv = holder.ssiv;
        String ext = "";
        try { ext = FileUtils.getExtension(item.getFile().getDisplayName()); } catch (Exception ignored) {}
        boolean isDng = "dng".equalsIgnoreCase(ext);
        // Fast-path for DNG memory cache: if we already have full-res bitmap, show instantly
        // without blanking/recycling. This fixes subsequent swipes staying low quality/rotated.
        if (isDng) {
            Bitmap cachedDng = dngBitmapCache.get(position);
            if (cachedDng != null && !cachedDng.isRecycled()) {
                // Cancel any stale Glide for old position but keep cached bitmap
                try {
                    Object oldTag = ssiv.getTag();
                    if (oldTag instanceof Integer) {
                        int oldPos = (Integer) oldTag;
                        if (oldPos != position) {
                            Target<Bitmap> oldTarget = dngTargets.remove(oldPos);
                            if (oldTarget != null) try { Glide.with(ssiv.getContext()).clear(oldTarget); } catch (Exception ignored) {}
                        }
                    }
                    Target<Bitmap> prev = dngTargets.remove(position);
                    if (prev != null) try { Glide.with(ssiv.getContext()).clear(prev); } catch (Exception ignored) {}
                } catch (Exception ignored) {}
                ssiv.setTag(position);
                ssiv.setId(ViewGroup.generateViewId());
                if (imageViewClickListener != null) ssiv.setOnClickListener(v -> imageViewClickListener.onImageViewClicked(v));
                if (ssivListener != null) { ssiv.setOnStateChangedListener(ssivListener); ssiv.setTouchCallBack(ssivListener); }
                ssiv.setOnImageEventListener(imageEventListener);
                ssiv.setRegionDecoderClass(SkiaPooledImageRegionDecoder.class);
                ssiv.setOrientation(SubsamplingScaleImageView.ORIENTATION_0);
                ssiv.setImage(ImageSource.cachedBitmap(cachedDng));
                prefetchHdrHeaders(ssiv.getContext(), position);
                return;
            }
        }
        // Cancel any in-flight Glide DNG for this recycled view before rebind.
        // Holder may have been used for oldPos ≠ position – clear that old target.
        try {
            Object oldTag = ssiv.getTag();
            if (oldTag instanceof Integer) {
                int oldPos = (Integer) oldTag;
                Target<Bitmap> oldTarget = dngTargets.remove(oldPos);
                if (oldTarget != null) try { Glide.with(ssiv.getContext()).clear(oldTarget); } catch (Exception ignored) {}
            }
            Target<Bitmap> prev = dngTargets.remove(position);
            if (prev != null) try { Glide.with(ssiv.getContext()).clear(prev); } catch (Exception ignored) {}
            // Also clear any view-bound target from previous bind (safety for view reuse)
            Glide.with(ssiv.getContext()).clear(ssiv);
        } catch (Exception ignored) {}
        // For DNG cache miss: show preview placeholder during swipe if available (fixes low quality during swipe)
        // Otherwise blank until Glide finishes (prevents stale JPEG tiles flash).
        if (isDng) {
            Bitmap preview = previewCache.get(position);
            Point dims = dimsCache.get(position);
            if (preview != null && dims != null && !preview.isRecycled()) {
                // Show preview instantly while full-res loads – avoids black flash and gives upright orientation via preview
                // Preview was decoded via decodePreview which already respects sampling; use it as placeholder
                ssiv.setTag(position);
                ssiv.setId(ViewGroup.generateViewId());
                if (imageViewClickListener != null) ssiv.setOnClickListener(v -> imageViewClickListener.onImageViewClicked(v));
                if (ssivListener != null) { ssiv.setOnStateChangedListener(ssivListener); ssiv.setTouchCallBack(ssivListener); }
                ssiv.setOnImageEventListener(imageEventListener);
                // Ensure preview dimensions path shows correctly – preview is small, will be replaced by full-res
                ssiv.setImage(ImageSource.cachedBitmap(preview));
                // Continue to load full-res in background (don't return) – fall through to Glide load after setup
                // But we must not recycle again; keep tag/id/listeners already set, reuse ssiv
                // So jump to Glide load with preview already shown
            } else {
                // No preview: reset stale tiled state/black placeholder so recycled JPEG tiles (with EXIF rotation)
                // don't flash before DNG Glide finishes. Use blank until correct bitmap arrives.
                ssiv.recycleIfNeeded();
                ssiv.setTag(position);
                ssiv.setId(ViewGroup.generateViewId());
                if (imageViewClickListener != null) ssiv.setOnClickListener(v -> imageViewClickListener.onImageViewClicked(v));
                if (ssivListener != null) { ssiv.setOnStateChangedListener(ssivListener); ssiv.setTouchCallBack(ssivListener); }
                ssiv.setOnImageEventListener(imageEventListener);
                // Warm preview for next swipe (also helps rotation: preview already has correct sampling)
                ensurePreview(position);
            }
        } else {
            ssiv.recycleIfNeeded();
            ssiv.setTag(position);
            ssiv.setId(ViewGroup.generateViewId());
            if (imageViewClickListener != null) ssiv.setOnClickListener(v -> imageViewClickListener.onImageViewClicked(v));
            if (ssivListener != null) { ssiv.setOnStateChangedListener(ssivListener); ssiv.setTouchCallBack(ssivListener); }
            ssiv.setOnImageEventListener(imageEventListener);
        }

        if ("dng".equalsIgnoreCase(ext)) {
            // Phase1: DNG viewport single bitmap (≈8-12MB) vs SIZE_ORIGINAL 192MB for 50MP.
            // Gives O(viewport) memory like JPEG tiling (~12MB), sharp at 1×, soft >2× zoom
            // (Phase2 native DngTiledRegionDecoder will give true tiles). Avoids mixed scroll OOM.
            final int bindPos = position;
            ssiv.setRegionDecoderClass(SkiaPooledImageRegionDecoder.class);
            ssiv.setOrientation(SubsamplingScaleImageView.ORIENTATION_0);
            final Uri dngUri = item.getFile().getFileUri();
            final String dngName = item.getFile().getDisplayName();
            final long dngModified = item.getFile().getLastModified();
            Target<Bitmap> prevDng = dngTargets.remove(bindPos);
            if (prevDng != null) try { Glide.with(ssiv.getContext()).clear(prevDng); } catch (Exception ignored) {}
            Point vp = getTargetViewportSize();
            final int reqW = vp.x;
            final int reqH = vp.y;
            CustomTarget<Bitmap> dngTarget = new CustomTarget<Bitmap>(reqW, reqH) {
                        @Override
                        public void onResourceReady(@NonNull Bitmap bitmap, Transition<? super Bitmap> transition) {
                            Bitmap out = bitmap;
                            // Always apply EXIF and cache even if view was recycled – ensures dngBitmapCache
                            // is populated for subsequent swipes (fixes rapid swipe never loads again).
                            try {
                                int orientation = 1;
                                try (InputStream is = ssiv.getContext().getContentResolver().openInputStream(dngUri)) {
                                    if (is != null) {
                                        androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(is);
                                        orientation = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                                                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);
                                    }
                                } catch (Exception ignored) {}
                                int rotation = 0;
                                if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                                else if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                                else if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
                                boolean needRotate = false;
                                if ((rotation == 90 || rotation == 270) && out.getWidth() > out.getHeight()) needRotate = true;
                                if (needRotate) {
                                    android.graphics.Matrix m = new android.graphics.Matrix();
                                    m.postRotate(rotation);
                                    Bitmap rotated = Bitmap.createBitmap(out, 0, 0, out.getWidth(), out.getHeight(), m, true);
                                    if (rotated != out) out = rotated;
                                }
                            } catch (Exception ignored) {}
                            // Cache unconditionally – even if tag mismatched (view recycled during decode),
                            // next onBind will hit cache and show instantly.
                            if (out != null && !out.isRecycled()) dngBitmapCache.put(bindPos, out);
                            dngTargets.remove(bindPos, this);
                            Object tag2 = ssiv.getTag();
                            if (!(tag2 instanceof Integer) || (Integer) tag2 != bindPos) return;
                            ssiv.setImage(ImageSource.cachedBitmap(out));
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                            Object tag = ssiv.getTag();
                            if (tag instanceof Integer && (Integer) tag == bindPos) {
                                ssiv.recycleIfNeeded();
                            }
                            dngTargets.remove(bindPos, this);
                        }
                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            dngTargets.remove(bindPos, this);
                        }
                    };
            dngTargets.put(bindPos, dngTarget);
            Glide.with(ssiv.getContext())
                    .asBitmap()
                    .load(dngUri)
                    .apply(new RequestOptions()
                            .signature(new ObjectKey(dngName + dngModified))
                            .override(reqW, reqH)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .skipMemoryCache(false)
                            .format(DecodeFormat.PREFER_ARGB_8888))
                    .into(dngTarget);
            // No tiling preview needed for DNG; keep HDR prefetch skip.
            prefetchHdrHeaders(ssiv.getContext(), position);
            return;
        }
        // JPEG: restore EXIF orientation (DNG branch set ORIENTATION_0)
        ssiv.setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF);
        // Tiling (real gallery): HDR active -> HW gainmap tiles, HDR off but available -> SOFTWARE
        // tonemapped tiles, else/Skia for plain SDR. All keep sWidth = native -> full pan, O(viewport).
        if (hdrActive[position]) {
            ssiv.setRegionDecoderFactory(() -> new HdrTiledRegionDecoder(true));
        } else if (hdrAvailable[position]) {
            ssiv.setRegionDecoderFactory(() -> new HdrTiledRegionDecoder(false));
        } else if (hdrChecked[position]) {
            ssiv.setRegionDecoderClass(SkiaPooledImageRegionDecoder.class);
        } else {
            // Unknown yet – optimistic tonemapped to avoid clipped flash for HDR images.
            ssiv.setRegionDecoderFactory(() -> new HdrTiledRegionDecoder(false));
        }
        setImage(ssiv, item.getFile().getFileUri(), position);
        // Prefetch headers for the neighbors so the next swipe knows HDR availability instantly.
        prefetchHdrHeaders(ssiv.getContext(), position);
    }

    @Override
    public void onViewAttachedToWindow(@NonNull Holder holder) {
        super.onViewAttachedToWindow(holder);
        int pos = holder.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_POSITION) {
            activeViews.put(pos, holder.ssiv);
            prefetchHdrHeaders(holder.itemView.getContext(), pos);
        }
        Object tag = holder.ssiv.getTag();
        if (tag instanceof Integer) activeViews.put((Integer) tag, holder.ssiv);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull Holder holder) {
        super.onViewDetachedFromWindow(holder);
        Object tag = holder.ssiv.getTag();
        if (tag instanceof Integer) activeViews.remove((Integer) tag);
        int pos = holder.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_POSITION) activeViews.remove(pos);
    }

    @Nullable
    public CustomSSIV getActiveSsiv(int position) {
        return activeViews.get(position);
    }

    /**
     * Sets the tiled image for a position, passing a small preview + native dimensions (if cached) so
     * SSIV paints the preview immediately instead of black while the base tiles decode.
     */
    private void setImage(CustomSSIV ssiv, Uri uri, int position) {
        Bitmap preview = previewCache.get(position);
        Point dims = dimsCache.get(position);
        if (preview != null && dims != null && !preview.isRecycled()) {
            ssiv.setImage(
                    ImageSource.uri(uri).dimensions(dims.x, dims.y),
                    ImageSource.cachedBitmap(preview));
        } else {
            // Preview not ready yet – tiled image only. Prefetch the preview so future binds are instant.
            ssiv.setImage(ImageSource.uri(uri));
            ensurePreview(position);
        }
    }

    /**
     * Eagerly decodes small preview bitmaps + native dimensions for the viewport window (center ± 2)
     * so incoming neighbor pages have a placeholder ready before a swipe. Uses the app context, so it
     * works regardless of view attach state (fixes the first-bind black flash).
     * Also preloads DNG full-res for neighbors (fixes first swipe low quality: ViewPager2 animates
     * neighboring holder before Glide finishes; with preload the full-res is already in dngBitmapCache
     * and onBind shows high quality instantly).
     */
    public void preloadPreviews(int centerPos) {
        if (appContext == null) return;
        int start = Math.max(0, centerPos - 2);
        int end = Math.min(galleryItemList.size() - 1, centerPos + 2);
        for (int i = start; i <= end; i++) {
            ensurePreview(i);
            preloadDngFull(i);
        }
    }

    private void preloadDngFull(int position) {
        if (position < 0 || position >= galleryItemList.size()) return;
        GalleryItem item = galleryItemList.get(position);
        String ext = "";
        try { ext = FileUtils.getExtension(item.getFile().getDisplayName()); } catch (Exception ignored) {}
        if (!"dng".equalsIgnoreCase(ext)) return;
        if (dngBitmapCache.get(position) != null) return;
        if (dngTargets.containsKey(position)) return;
        try {
            Context ctx = appContext;
            Uri uri = item.getFile().getFileUri();
            String name = item.getFile().getDisplayName();
            long mod = item.getFile().getLastModified();
            final int pos = position;
            Point vp2 = getTargetViewportSize();
            final int pw = vp2.x;
            final int ph = vp2.y;
            CustomTarget<Bitmap> preloadTarget = new CustomTarget<Bitmap>(pw, ph) {
                @Override
                public void onResourceReady(@NonNull Bitmap bitmap, Transition<? super Bitmap> transition) {
                    Bitmap out = bitmap;
                    try (InputStream is = ctx.getContentResolver().openInputStream(uri)) {
                        if (is != null) {
                            androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(is);
                            int orientation = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL);
                            int rotation = 0;
                            if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90) rotation = 90;
                            else if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180) rotation = 180;
                            else if (orientation == androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270) rotation = 270;
                            boolean needRotate = false;
                            if ((rotation == 90 || rotation == 270) && out.getWidth() > out.getHeight()) needRotate = true;
                            if (needRotate) {
                                android.graphics.Matrix m = new android.graphics.Matrix();
                                m.postRotate(rotation);
                                Bitmap rotated = Bitmap.createBitmap(out, 0, 0, out.getWidth(), out.getHeight(), m, true);
                                if (rotated != out) out = rotated;
                            }
                        }
                    } catch (Exception ignored) {}
                    dngBitmapCache.put(pos, out);
                    dngTargets.remove(pos, this);
                    CustomSSIV v = activeViews.get(pos);
                    if (v != null) {
                        Object tag = v.getTag();
                        if (tag instanceof Integer && (Integer) tag == pos) {
                            Bitmap finalOut = out;
                            v.post(() -> {
                                Object t2 = v.getTag();
                                if (t2 instanceof Integer && (Integer) t2 == pos) v.setImage(ImageSource.cachedBitmap(finalOut));
                            });
                        }
                    }
                }
                @Override public void onLoadCleared(@Nullable Drawable placeholder) { dngTargets.remove(pos, this); }
                @Override public void onLoadFailed(@Nullable Drawable errorDrawable) { dngTargets.remove(pos, this); }
            };
            dngTargets.put(pos, preloadTarget);
            Glide.with(ctx)
                    .asBitmap()
                    .load(uri)
                    .apply(new RequestOptions()
                            .signature(new ObjectKey(name + mod))
                            .override(pw, ph)
                            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                            .format(DecodeFormat.PREFER_ARGB_8888))
                    .into(preloadTarget);
        } catch (Exception ignored) {}
    }

    /**
     * Decodes a small preview bitmap + native dimensions in the background so the page can show a
     * placeholder (instead of black) and provide .dimensions() for SSIV's preview-source requirement.
     * Skipped for DNG – DNG uses full-res dngBitmapCache + Glide, tiling preview would overwrite full-res.
     */
    private void ensurePreview(int position) {
        if (position < 0 || position >= galleryItemList.size()) return;
        if (appContext == null) return;
        // DNG never uses tiling preview – avoid clobbering full-res cachedBitmap with 360px tile
        try {
            String ext = FileUtils.getExtension(galleryItemList.get(position).getFile().getDisplayName());
            if ("dng".equalsIgnoreCase(ext)) return;
        } catch (Exception ignored) {}
        if (previewCache.get(position) != null || dimsCache.get(position) != null) return;
        if (pendingPreviewTasks.containsKey(position)) return;
        GalleryItem item = galleryItemList.get(position);
        Future<?> f = GALLERY_EXECUTOR.submit(() -> {
            try {
                ContentResolver cr = appContext.getContentResolver();
                Point dims = decodeBounds(cr, item.getFile().getFileUri());
                if (dims == null) return;
                Bitmap preview = decodePreview(cr, item.getFile().getFileUri(), dims);
                // Post to main thread to update caches and re-apply to a matching active view.
                CustomSSIV view = activeViews.get(position);
                Runnable apply = () -> {
                    pendingPreviewTasks.remove(position);
                    if (dims != null) dimsCache.put(position, dims);
                    if (preview != null) previewCache.put(position, preview);
                    CustomSSIV v = activeViews.get(position);
                    if (v != null) {
                        Object tag = v.getTag();
                        if (tag instanceof Integer && (Integer) tag == position) {
                            setImage(v, item.getFile().getFileUri(), position);
                        }
                    }
                };
                if (view != null) view.post(apply);
                else apply.run();
            } catch (Exception ignored) {
                pendingPreviewTasks.remove(position);
            }
        });
        pendingPreviewTasks.put(position, f);
    }

    @Nullable
    private Point decodeBounds(ContentResolver cr, Uri uri) {
        // Try BitmapFactory first (fast for JPEG)
        try (InputStream is = cr.openInputStream(uri)) {
            if (is != null) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeStream(is, null, o);
                if (o.outWidth > 0 && o.outHeight > 0) return new Point(o.outWidth, o.outHeight);
            }
        } catch (IOException ignored) {}
        // Fallback for DNG/raw where BitmapFactory fails: use ImageDecoder (API 28+) or ExifInterface
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source src = ImageDecoder.createSource(appContext != null ? appContext.getContentResolver() : cr, uri);
                final Point[] holder = new Point[1];
                try {
                    // Decode a 1x1 to capture info size without full cost
                    ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                        holder[0] = new Point(info.getSize().getWidth(), info.getSize().getHeight());
                        decoder.setTargetSize(1, 1);
                        decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    });
                } catch (Exception ignored) {}
                if (holder[0] != null && holder[0].x > 0 && holder[0].y > 0) return holder[0];
            } catch (Exception ignored) {}
        }
        // Last resort: ExifInterface width/height (works for many DNGs via embedded thumbnail EXIF)
        try (InputStream is2 = cr.openInputStream(uri)) {
            if (is2 != null) {
                androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(is2);
                int w = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_WIDTH, 0);
                int h = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_IMAGE_LENGTH, 0);
                if (w <= 0 || h <= 0) {
                    // Try pixel dimensions
                    w = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_PIXEL_X_DIMENSION, 0);
                    h = exif.getAttributeInt(androidx.exifinterface.media.ExifInterface.TAG_PIXEL_Y_DIMENSION, 0);
                }
                // For DNG, also check orientation – if 90/270, dimensions may be swapped vs stored; return stored
                if (w > 0 && h > 0) return new Point(w, h);
                // Fallback: try to get default image width via MediaStore columns if available is not sufficient
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Nullable
    private Bitmap decodePreview(ContentResolver cr, Uri uri, Point dims) {
        int max = Math.max(dims.x, dims.y);
        int sample = 1;
        while (max / sample > PREVIEW_SIDE * 2) sample *= 2; // generous oversample then downscale
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source src = ImageDecoder.createSource(cr, uri);
                return ImageDecoder.decodeBitmap(src, (decoder, info, source) -> {
                    int w = info.getSize().getWidth();
                    int h = info.getSize().getHeight();
                    int m = Math.max(w, h);
                    if (m > PREVIEW_SIDE) {
                        float ratio = (float) PREVIEW_SIDE / m;
                        decoder.setTargetSize(Math.round(w * ratio), Math.round(h * ratio));
                    }
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setUnpremultipliedRequired(false);
                });
            } catch (Exception e) {
                return null;
            }
        } else {
            try (InputStream is = cr.openInputStream(uri)) {
                if (is == null) return null;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                o.inPreferredConfig = Bitmap.Config.ARGB_8888;
                return BitmapFactory.decodeStream(is, null, o);
            } catch (IOException e) {
                return null;
            }
        }
    }

    @Override
    public void onViewRecycled(@NonNull Holder holder) {
        super.onViewRecycled(holder);
        CustomSSIV ssiv = holder.ssiv;
        // Cancel Glide DNG load if in-flight – prevents late bitmap to recycled holder.
        try {
            Object tagTmp = ssiv.getTag();
            if (tagTmp instanceof Integer) {
                int p = (Integer) tagTmp;
                Target<Bitmap> t = dngTargets.remove(p);
                if (t != null) try { Glide.with(ssiv.getContext()).clear(t); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        try { Glide.with(holder.itemView.getContext()).clear(ssiv); } catch (Exception ignored) {}
        try { Glide.with(ssiv.getContext()).clear(ssiv); } catch (Exception ignored) {}
        Object tag = ssiv.getTag();
        if (tag instanceof Integer) {
            int pos = (Integer) tag;
            Target<Bitmap> dngT = dngTargets.remove(pos);
            if (dngT != null) try { Glide.with(ssiv.getContext()).clear(dngT); } catch (Exception ignored) {}
            Future<?> f = pendingHdrTasks.remove(pos);
            if (f != null) f.cancel(true);
            Future<?> hf = pendingHeaderTasks.remove(pos);
            if (hf != null) hf.cancel(true);
            Future<?> pf = pendingPreviewTasks.remove(pos);
            if (pf != null) pf.cancel(true);
            hdrRequested[pos] = false;
        }
        ssiv.recycleIfNeeded();
        ssiv.setTag(null);
        ssiv.setOnImageEventListener(null);
        ssiv.setOnStateChangedListener((SubsamplingScaleImageView.OnStateChangedListener) null);
        ssiv.setTouchCallBack(null);
        ssiv.setOnClickListener(null);
    }

    @Override
    public int getItemCount() { return galleryItemList.size(); }

    public boolean isHdrActive(int position) { return inBounds(position) && hdrActive[position]; }
    public boolean isHdrAvailable(int position) { return inBounds(position) && hdrAvailable[position]; }

    /**
     * Enable HDR for the position. Tiling keeps sWidth = native for both HDR and SDR, so we only
     * swap to the hardware (gainmap) decoder and set the window color mode. No full-page decode.
     */
    public void loadHdrForPosition(CustomSSIV scaleImageView, int position) {
        if (scaleImageView == null || !inBounds(position)) return;
        if (hdrRequested[position] || hdrActive[position]) return;
        Context ctx = scaleImageView.getContext();
        if (!UltraHdrGalleryUtil.isDeviceHdrCapable(ctx)) return;
        String ext = "";
        try { ext = FileUtils.getExtension(galleryItemList.get(position).getFile().getDisplayName()); } catch (Exception ignored) {}
        if ("dng".equalsIgnoreCase(ext)) return;
        hdrRequested[position] = true;
        if (hdrChecked[position] && hdrAvailable[position]) {
            activateHdr(scaleImageView, position);
            return;
        }
        if (hdrChecked[position] && !hdrAvailable[position]) {
            hdrRequested[position] = false;
            if (hdrStateListener != null) hdrStateListener.onHdrAvailabilityChanged(position, false);
            return;
        }
        Future<?> f = GALLERY_EXECUTOR.submit(() -> {
            boolean candidate = UltraHdrGalleryUtil.isUltraHdrJpeg(ctx, galleryItemList.get(position).getFile().getFileUri());
            scaleImageView.post(() -> {
                if (!hdrRequested[position] || !inBounds(position)) return;
                hdrRequested[position] = false;
                hdrChecked[position] = true;
                pendingHeaderTasks.remove(position);
                if (!candidate) {
                    hdrAvailable[position] = false;
                    if (hdrStateListener != null) hdrStateListener.onHdrAvailabilityChanged(position, false);
                    return;
                }
                hdrAvailable[position] = true;
                if (hdrStateListener != null) hdrStateListener.onHdrAvailabilityChanged(position, true);
                activateHdr(scaleImageView, position);
            });
        });
        pendingHdrTasks.put(position, f);
        prefetchHdrHeaders(ctx, position);
    }

    private void activateHdr(CustomSSIV scaleImageView, int position) {
        if (!inBounds(position)) return;
        float curScale = scaleImageView.isReady() ? scaleImageView.getScale() : scaleImageView.getMinScale();
        android.graphics.PointF curCenter = scaleImageView.getCenter();
        scaleImageView.setRegionDecoderFactory(() -> new HdrTiledRegionDecoder(true));
        setImage(scaleImageView, galleryItemList.get(position).getFile().getFileUri(), position);
        if (curCenter != null && curScale > 0) {
            final float s = curScale;
            final android.graphics.PointF c = new android.graphics.PointF(curCenter.x, curCenter.y);
            scaleImageView.post(() -> {
                try { scaleImageView.setScaleAndCenter(s, c); } catch (Exception ignored) {}
            });
        }
        hdrActive[position] = true;
        hdrRequested[position] = false;
        if (hdrStateListener != null) hdrStateListener.onHdrStateChanged(position, true);
    }

    public void releaseHdrForPosition(CustomSSIV scaleImageView, int position) {
        if (!inBounds(position)) return;
        Future<?> f = pendingHdrTasks.remove(position);
        if (f != null) f.cancel(true);
        boolean wasActive = hdrActive[position];
        hdrRequested[position] = false;
        hdrActive[position] = false;
        if (wasActive && scaleImageView != null) {
            // Tiling keeps sWidth = native for SDR too, so scale/center preserved directly.
            float curScale = scaleImageView.isReady() ? scaleImageView.getScale() : scaleImageView.getMinScale();
            android.graphics.PointF curCenter = scaleImageView.getCenter();
            scaleImageView.setRegionDecoderFactory(() -> new HdrTiledRegionDecoder(false));
            setImage(scaleImageView, galleryItemList.get(position).getFile().getFileUri(), position);
            if (curCenter != null && curScale > 0) {
                final float s = curScale;
                final android.graphics.PointF c = new android.graphics.PointF(curCenter.x, curCenter.y);
                scaleImageView.post(() -> {
                    try { scaleImageView.setScaleAndCenter(s, c); } catch (Exception ignored) {}
                });
            }
            if (hdrStateListener != null) hdrStateListener.onHdrStateChanged(position, false);
        }
    }

    /**
     * Trim preview caches on memory pressure (called from Fragment.onTrimMemory).
     * Eagerly clears previews/dims ± bitmap eviction to drop baseline on 4GB devices.
     */
    public void trimCaches(int level) {
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW
                || level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            previewCache.evictAll();
            dimsCache.evictAll();
            dngBitmapCache.evictAll();
        } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            // lighter trim: keep current page, drop others
            if (previewCache.size() > 1) previewCache.evictAll();
            if (dngBitmapCache.size() > 1) dngBitmapCache.evictAll();
        }
    }

    public void clearPreviewCaches() {
        previewCache.evictAll();
        dimsCache.evictAll();
        // Keep dngBitmapCache for swipe-back; it will be evicted on trim or recycle
    }

    public void clearDngCache() {
        dngBitmapCache.evictAll();
    }

    private boolean inBounds(int position) { return position >= 0 && position < galleryItemList.size(); }

    public static class Holder extends RecyclerView.ViewHolder {
        public final CustomSSIV ssiv;
        Holder(CustomSSIV ssiv) { super(ssiv); this.ssiv = ssiv; }
        public CustomSSIV getSsiv() { return ssiv; }
    }

    public interface ImageViewClickListener { void onImageViewClicked(android.view.View v); }
    public interface HdrStateListener {
        void onHdrStateChanged(int position, boolean isHdr);
        void onHdrAvailabilityChanged(int position, boolean isUltraHdr);
    }
}
