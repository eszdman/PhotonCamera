package com.particlesdevs.photoncamera.gallery.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.particlesdevs.photoncamera.gallery.files.GalleryFileOperations;
import com.particlesdevs.photoncamera.gallery.model.GalleryItem;

import java.util.List;
import java.util.stream.Collectors;

public class GalleryViewModel extends AndroidViewModel {
    private final MutableLiveData<List<GalleryItem>> allImageFiles = new MutableLiveData<>();

    public GalleryViewModel(@NonNull Application application) {
        super(application);
    }

    public void fetchAllImages() {
        allImageFiles.setValue(GalleryFileOperations.fetchAllImageFiles(getApplication().getContentResolver()).stream().map(GalleryItem::new).collect(Collectors.toList()));
    }

    public LiveData<List<GalleryItem>> getAllImageFilesData() {
        return allImageFiles;
    }
}
