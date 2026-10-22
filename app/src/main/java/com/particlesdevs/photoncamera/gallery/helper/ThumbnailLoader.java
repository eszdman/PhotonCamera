package com.particlesdevs.photoncamera.gallery.helper;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.util.Size;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.util.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Loads grid thumbnails from MediaProvider's thumbnail store instead of
 * decoding the original files.
 *
 * <p>For a 100 MP HEIC/DNG this is the difference between asking the media
 * provider for an already-generated thumbnail (HEIC even carries an embedded
 * thumbnail the provider can serve without touching image data) and asking the
 * platform decoder to open and sample the full-size item for every cell. Files
 * the provider does not know - external VIEW uris, unindexed files, formats
 * without a thumbnail - fail here and the caller falls back to Glide.
 *
 * <p>Requests run on their own small pool (binder round-trips to MediaProvider,
 * independent of the viewer's preview decodes) and a byte-bounded cache keeps
 * rebinds of the same cell from re-querying the provider.
 */
public final class ThumbnailLoader {
    private static final String TAG = "ThumbnailLoader";
    /**
     * MediaProvider generates and caches a thumbnail per requested size, so the
     * requested size is derived from the stable grid layout instead of the
     * (often not yet laid out) view, and stays the same for every cell.
     */
    private static final int MIN_SIZE_PX = 256;
    private static final int MAX_SIZE_PX = 512;
    private static final int CACHE_BYTES = 8 * 1024 * 1024;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2, new ThreadFactory() {
        @Override
        public Thread newThread(@NonNull Runnable runnable) {
            Thread thread = new Thread(runnable, "GalleryThumb");
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        }
    });
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    /** Guarded by itself: android.util.LruCache is not thread-safe. */
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(CACHE_BYTES) {
        @Override
        protected int sizeOf(@NonNull String key, @NonNull Bitmap value) {
            return value.getByteCount();
        }
    };

    private ThumbnailLoader() {
    }

    /** Stable requested thumbnail size for the grid's cell width. */
    public static int targetSize(Context context) {
        int width = context.getResources().getDisplayMetrics().widthPixels;
        int columns = Math.max(1, context.getResources().getInteger(R.integer.grid_columns));
        int cell = width / columns;
        return Math.max(MIN_SIZE_PX, Math.min(MAX_SIZE_PX, cell));
    }

    /**
     * Starts an asynchronous MediaProvider thumbnail request for {@code uri}.
     *
     * @return {@code false} when no provider thumbnail can be requested (the
     *         caller should load the image itself); otherwise the request is
     *         running and {@code onFallback} is invoked on the main thread if it
     *         turns out the provider has no usable thumbnail
     */
    public static boolean loadInto(@NonNull ImageView view, @NonNull Uri uri, long mediaId,
                                   boolean isVideo, int targetPx, @NonNull Runnable onFallback) {
        if (!isMediaStoreUri(uri)) {
            return false;
        }
        final Context appContext = view.getContext().getApplicationContext();
        final int size = Math.max(1, targetPx);
        final String cacheKey = uri + "@" + size;
        view.setTag(R.id.gallery_thumbnail_request, uri);
        EXECUTOR.execute(() -> {
            Bitmap thumbnail = null;
            try {
                synchronized (CACHE) {
                    thumbnail = CACHE.get(cacheKey);
                }
                if (thumbnail == null) {
                    thumbnail = queryThumbnail(appContext.getContentResolver(), uri, mediaId, isVideo, size);
                    if (thumbnail != null) {
                        synchronized (CACHE) {
                            CACHE.put(cacheKey, thumbnail);
                        }
                    }
                }
            } catch (Throwable t) {
                Log.d(TAG, "MediaStore thumbnail request failed " + Log.getStackTraceString(t));
            }
            final Bitmap result = thumbnail;
            MAIN_HANDLER.post(() -> {
                // Drop completions for a view that has since been rebound or
                // recycled (its request tag no longer matches this uri).
                if (!uri.equals(view.getTag(R.id.gallery_thumbnail_request))) return;
                if (result != null) {
                    view.setImageBitmap(result);
                } else {
                    onFallback.run();
                }
            });
        });
        return true;
    }

    @Nullable
    private static Bitmap queryThumbnail(ContentResolver resolver, Uri uri, long mediaId,
                                         boolean isVideo, int size) throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return resolver.loadThumbnail(uri, new Size(size, size), null);
        }
        // Pre-Q: ask the (deprecated but working) thumbnail tables directly.
        if (mediaId <= 0) return null;
        if (isVideo) {
            return MediaStore.Video.Thumbnails.getThumbnail(
                    resolver, mediaId, MediaStore.Video.Thumbnails.MINI_KIND, null);
        }
        return MediaStore.Images.Thumbnails.getThumbnail(
                resolver, mediaId, MediaStore.Images.Thumbnails.MINI_KIND, null);
    }

    private static boolean isMediaStoreUri(Uri uri) {
        return uri != null
                && ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())
                && MediaStore.AUTHORITY.equals(uri.getAuthority());
    }
}
