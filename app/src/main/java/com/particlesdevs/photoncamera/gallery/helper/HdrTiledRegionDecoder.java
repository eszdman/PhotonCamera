package com.particlesdevs.photoncamera.gallery.helper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;

import java.io.IOException;

import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;

import java.io.InputStream;

/**
 * Tiled decoder for Ultra HDR that preserves gainmap per tile via ImageDecoder (API 31+).
 * Keeps sWidth = native dimensions, so SSIV pan range is full image (no keyhole) and memory is O(viewport).
 * Reuses a single ImageDecoder.Source for the lifetime of the decoder so tile decode is fast (no per-tile
 * header re-parse), matching SDR's Swipe smoothness. For SDR/pre-S fallback uses a SOFTWARE allocator.
 */
public class HdrTiledRegionDecoder implements ImageRegionDecoder {

    private Context context;
    private Uri uri;
    private Point dimensions;
    private ImageDecoder.Source source;
    private final boolean useHardware;

    public HdrTiledRegionDecoder() {
        this(true);
    }

    public HdrTiledRegionDecoder(boolean useHardware) {
        this.useHardware = useHardware;
    }

    @Override
    public Point init(Context context, Uri uri) throws Exception {
        this.context = context.getApplicationContext();
        this.uri = uri;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IOException("openInputStream null");
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);
            if (opts.outWidth <= 0 || opts.outHeight <= 0) throw new IOException("invalid bounds");
            // Return stored dimensions unrotated – let SSIV ORIENTATION_USE_EXIF + ImageDecoder auto-rotate handle it.
            // (Swapping here caused double-swap -> 3:4 displayed as 4:3 stretched.)
            dimensions = new Point(opts.outWidth, opts.outHeight);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            source = ImageDecoder.createSource(context.getContentResolver(), uri);
        }
        return dimensions;
    }

    @Override
    public Bitmap decodeRegion(Rect sRect, int sampleSize) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                ImageDecoder.Source src = source != null ? source
                        : ImageDecoder.createSource(context.getContentResolver(), uri);
                return ImageDecoder.decodeBitmap(src, (decoder, info, source2) -> {
                    decoder.setCrop(sRect);
                    if (sampleSize > 1) {
                        int tw = Math.max(1, sRect.width() / sampleSize);
                        int th = Math.max(1, sRect.height() / sampleSize);
                        decoder.setTargetSize(tw, th);
                    }
                    decoder.setAllocator(useHardware ? ImageDecoder.ALLOCATOR_HARDWARE : ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setUnpremultipliedRequired(false);
                });
            } catch (Exception e) {
                return fallbackDecode(sRect, sampleSize);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source src = source != null ? source
                        : ImageDecoder.createSource(context.getContentResolver(), uri);
                return ImageDecoder.decodeBitmap(src, (decoder, info, source2) -> {
                    // Pre-S setCrop(Rect) not available: decode sampled then crop manually.
                    if (sampleSize > 1) {
                        decoder.setTargetSize(info.getSize().getWidth() / sampleSize, info.getSize().getHeight() / sampleSize);
                    }
                    decoder.setAllocator(useHardware ? ImageDecoder.ALLOCATOR_HARDWARE : ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setUnpremultipliedRequired(false);
                });
            } catch (Exception e) {
                return fallbackDecode(sRect, sampleSize);
            }
        } else {
            return fallbackDecode(sRect, sampleSize);
        }
    }

    private Bitmap fallbackDecode(Rect sRect, int sampleSize) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap full = BitmapFactory.decodeStream(is, null, opts);
            if (full == null) return null;
            // Crop to sRect scaled by sampleSize.
            int sample = Math.max(1, sampleSize);
            int left = sRect.left / sample;
            int top = sRect.top / sample;
            int w = sRect.width() / sample;
            int h = sRect.height() / sample;
            left = Math.max(0, Math.min(left, full.getWidth() - 1));
            top = Math.max(0, Math.min(top, full.getHeight() - 1));
            w = Math.min(w, full.getWidth() - left);
            h = Math.min(h, full.getHeight() - top);
            if (w <= 0 || h <= 0) return full;
            Bitmap cropped = Bitmap.createBitmap(full, left, top, w, h);
            if (cropped != full) full.recycle();
            return cropped;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isReady() {
        return dimensions != null && uri != null;
    }

    @Override
    public void recycle() {
        dimensions = null;
        uri = null;
        context = null;
        source = null;
    }
}
