package com.particlesdevs.photoncamera.gallery.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.particlesdevs.photoncamera.gallery.files.GalleryFileOperations;
import com.particlesdevs.photoncamera.gallery.files.ImageFile;

import java.util.List;

public class GalleryViewModel extends AndroidViewModel {
    private final MutableLiveData<List<ImageFile>> allImageFiles = new MutableLiveData<>();

    public GalleryViewModel(@NonNull Application application) {
        super(application);
    }

    public void fetchAllImages() {
        allImageFiles.setValue(GalleryFileOperations.fetchAllImageFiles(getApplication().getContentResolver()));
    }

    public LiveData<List<ImageFile>> getAllImageFilesData() {
        return allImageFiles;
    }
}
