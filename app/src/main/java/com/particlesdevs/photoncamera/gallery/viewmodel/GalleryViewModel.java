package com.particlesdevs.photoncamera.gallery.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.particlesdevs.photoncamera.gallery.files.GalleryFileOperations;
import com.particlesdevs.photoncamera.gallery.files.ImageFile;
import com.particlesdevs.photoncamera.gallery.helper.GalleryExecutors;
import com.particlesdevs.photoncamera.gallery.model.GalleryItem;
import com.particlesdevs.photoncamera.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GalleryViewModel extends AndroidViewModel {
    private static final String TAG = "GalleryViewModel";

    private final MutableLiveData<GalleryItem> allSelectedImagesFolder = new MutableLiveData<>(GalleryItem.createEmpty());
    private final MutableLiveData<List<GalleryItem>> selectedDisplayFolders = new MutableLiveData<>(new ArrayList<>(0));

    private final MutableLiveData<List<GalleryItem>> currentFolderImages = new MutableLiveData<>(new ArrayList<>(0));
    private final MutableLiveData<Boolean> updatePendingLiveData = new MutableLiveData<>(false);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** Guards the fetch-in-flight state and the callback queue below. */
    private final Object fetchLock = new Object();
    private boolean fetchInFlight;
    private boolean fetchQueued;
    private final ArrayList<Runnable> fetchCallbacks = new ArrayList<>();


    public MutableLiveData<Boolean> getUpdatePending() {
        return updatePendingLiveData;
    }

    public void setUpdatePending(boolean updatePending) {
        updatePendingLiveData.setValue(updatePending);
    }

    public GalleryViewModel(@NonNull Application application) {
        super(application);
    }

    /** Enumerates MediaStore in the background and publishes the folder lists. */
    public void fetchAllMedia() {
        fetchAllMedia(null);
    }

    /**
     * Enumerates MediaStore off the main thread and publishes
     * {@link #getAllSelectedImageFolder()} / {@link #getSelectedDisplayFolders()}.
     * Runs on the UI thread when the folders have been posted, so callers can
     * immediately project the fresh data (e.g. set the current folder).
     *
     * <p>Requests are coalesced: while one scan is running any further request
     * queues a single rerun, because the running scan may have started before
     * the change that triggered the request.
     *
     * @param onFetched optional callback invoked on the main thread after the
     *                  lists were published (never invoked on failure)
     */
    public void fetchAllMedia(@Nullable Runnable onFetched) {
        synchronized (fetchLock) {
            if (onFetched != null) {
                fetchCallbacks.add(onFetched);
            }
            if (fetchInFlight) {
                fetchQueued = true;
                return;
            }
            fetchInFlight = true;
        }
        GalleryExecutors.io().execute(this::enumerateAndPublish);
    }

    private void enumerateAndPublish() {
        FetchResult result = null;
        try {
            result = enumerateMedia();
        } catch (Throwable t) {
            Log.d(TAG, "Media enumeration failed " + Log.getStackTraceString(t));
        }
        final FetchResult fetched = result;
        mainHandler.post(() -> {
            if (fetched != null) {
                if (fetched.allFolder != null) {
                    allSelectedImagesFolder.setValue(fetched.allFolder);
                }
                selectedDisplayFolders.setValue(fetched.folders);
            }
            List<Runnable> callbacks;
            boolean rerun;
            synchronized (fetchLock) {
                callbacks = new ArrayList<>(fetchCallbacks);
                fetchCallbacks.clear();
                rerun = fetchQueued;
                fetchQueued = false;
                if (!rerun) {
                    fetchInFlight = false;
                }
            }
            for (Runnable callback : callbacks) {
                try {
                    callback.run();
                } catch (Throwable t) {
                    Log.d(TAG, "Media fetch callback failed " + Log.getStackTraceString(t));
                }
            }
            if (rerun) {
                GalleryExecutors.io().execute(this::enumerateAndPublish);
            }
        });
    }

    /** Called on the {@link GalleryExecutors#io()} thread only. */
    private FetchResult enumerateMedia() {
        List<GalleryItem> allFolders = GalleryFileOperations._fetchSelectedFolders(getApplication().getContentResolver()).stream().map((Function<GalleryFileOperations.ImagesFolder, GalleryItem>) imagesFolder -> {
            GalleryItem folder = new GalleryItem(imagesFolder.getTopImage());
            imagesFolder.getAllImageFiles().forEach(imageFile -> folder.getFiles().add(new GalleryItem(imageFile)));
            folder.setDisplayName(imagesFolder.getFolderName());
            return folder;
        }).collect(Collectors.toList());

        ArrayList<ImageFile> all = (ArrayList<ImageFile>) GalleryFileOperations.extractAllSelectedImages();
        GalleryItem allFolder = null;
        if (!all.isEmpty()) {
            allFolder = new GalleryItem(all.get(0));
            allFolder.setDisplayName("ALL");
            allFolder.getFiles().addAll(all.stream().map(GalleryItem::new).collect(Collectors.toList()));

            allFolders.add(0, allFolder);
        }
        return new FetchResult(allFolder, allFolders);
    }

    /**
     * Loads the folder list for the folder picker off the main thread. The
     * callback receives a snapshot (never the shared mutable folder list) and
     * runs on the main thread, or with {@code null} when the scan failed.
     */
    public void loadAllFolders(Consumer<ArrayList<GalleryFileOperations.ImagesFolder>> onLoaded) {
        GalleryExecutors.io().execute(() -> {
            ArrayList<GalleryFileOperations.ImagesFolder> folders = null;
            try {
                folders = new ArrayList<>(GalleryFileOperations.FindAllFoldersWithImages(getApplication().getContentResolver()));
            } catch (Throwable t) {
                Log.d(TAG, "Folder scan failed " + Log.getStackTraceString(t));
            }
            final ArrayList<GalleryFileOperations.ImagesFolder> result = folders;
            mainHandler.post(() -> onLoaded.accept(result));
        });
    }

    public LiveData<GalleryItem> getAllSelectedImageFolder() {
        return allSelectedImagesFolder;
    }

    public MutableLiveData<List<GalleryItem>> getCurrentFolderImages() {
        return currentFolderImages;
    }

    public void setCurrentFolderImages(GalleryItem currentFolder) {
        currentFolderImages.setValue(currentFolder.getFiles());
    }

    public MutableLiveData<List<GalleryItem>> getSelectedDisplayFolders() {
        return selectedDisplayFolders;
    }

    private static final class FetchResult {
        final GalleryItem allFolder;
        final List<GalleryItem> folders;

        FetchResult(GalleryItem allFolder, List<GalleryItem> folders) {
            this.allFolder = allFolder;
            this.folders = folders;
        }
    }
}
