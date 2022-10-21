package com.particlesdevs.photoncamera.gallery.interfaces;

import android.view.View;

import com.particlesdevs.photoncamera.gallery.model.GalleryItem;

public interface GalleryItemClickedListener {
    void onItemClicked(View view, GalleryItem galleryItem);

    boolean onItemLongClicked(View view, GalleryItem galleryItem);
}
