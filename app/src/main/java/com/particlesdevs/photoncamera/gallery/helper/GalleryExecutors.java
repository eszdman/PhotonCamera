package com.particlesdevs.photoncamera.gallery.helper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Background executors for the gallery.
 *
 * <p>Centralised so the gallery's thread budget is visible in one place and
 * every MediaStore touch point shares the same queues:
 * <ul>
 *   <li>{@link #io()} – MediaStore enumeration, folder grouping and EXIF
 *       parsing. Single threaded on purpose: {@code GalleryFileOperations}
 *       keeps static folder state, so two scans must never overlap, and the
 *       shallow queue keeps cursor/disk contention down while browsing.</li>
 * </ul>
 */
public final class GalleryExecutors {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor(
            factory("GalleryIo", Thread.MIN_PRIORITY));

    private GalleryExecutors() {
    }

    /** Serialised MediaStore/EXIF IO queue. */
    public static ExecutorService io() {
        return IO;
    }

    private static ThreadFactory factory(String name, int priority) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setPriority(priority);
            return thread;
        };
    }
}
