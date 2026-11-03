package com.particlesdevs.photoncamera.gallery.helper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Background executors for the gallery.
 *
 * <p>Centralised so the gallery's thread budget is visible in one place and
 * every expensive touch point shares a bounded queue:
 * <ul>
 *   <li>{@link #io()} – MediaStore enumeration, folder grouping and EXIF
 *       parsing. Single threaded on purpose: {@code GalleryFileOperations}
 *       keeps static folder state, so two scans must never overlap, and the
 *       shallow queue keeps cursor/disk contention down while browsing.</li>
 *   <li>{@link #headers()} – the tiny Ultra HDR header scans (64 KB reads per
 *       file). Kept separate from {@link #decode()} so a scan for a neighbour
 *       can never delay the decode of the visible page.</li>
 *   <li>{@link #decode()} – preview/dimension decodes and DNG preloads. Two
 *       threads, capped deliberately for 4 GB devices.</li>
 *   <li>{@link #tiles()} – SubsamplingScaleImageView tile decodes. The view's
 *       default is the process-wide AsyncTask pool (core = CPU count), so this
 *       bounds tile concurrency instead of letting every page decode at once.</li>
 * </ul>
 * The histogram owns its own single worker (it must own its GL context), and
 * grid thumbnails their own small pool (MediaProvider binder round-trips).
 */
public final class GalleryExecutors {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(
            factory("GalleryIo", Thread.MIN_PRIORITY));
    private static final ExecutorService HEADERS = Executors.newSingleThreadExecutor(
            factory("GalleryHdr", Thread.MIN_PRIORITY));
    private static final ExecutorService DECODE = Executors.newFixedThreadPool(2,
            factory("GalleryDecode", Thread.NORM_PRIORITY - 1));
    private static final ExecutorService TILES = Executors.newFixedThreadPool(3,
            factory("GalleryTiles", Thread.NORM_PRIORITY - 1));

    private GalleryExecutors() {
    }

    /** Serialised MediaStore/EXIF IO queue. */
    public static ExecutorService io() {
        return IO;
    }

    /** Serialised Ultra HDR header scanner (tiny 64 KB reads). */
    public static ExecutorService headers() {
        return HEADERS;
    }

    /** Preview, dimension and DNG preload decodes. */
    public static ExecutorService decode() {
        return DECODE;
    }

    /** Tiled image region decodes for SubsamplingScaleImageView. */
    public static ExecutorService tiles() {
        return TILES;
    }

    private static ThreadFactory factory(String name, int priority) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setPriority(priority);
            return thread;
        };
    }
}
