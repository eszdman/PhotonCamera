package com.particlesdevs.photoncamera.gallery.files;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Created by Vibhor Srivastava on October 13, 2021
 */
public final class ImageFile extends MediaFile {
    private final long id;
    private final Uri fileUri;
    private final String displayName;
    private final long lastModified;
    private final long size;

    public ImageFile(long id, Uri fileUri, String displayName, long lastModified, long size) {
        this.id = id;
        this.fileUri = fileUri;
        this.displayName = displayName;
        this.lastModified = lastModified;
        this.size = size;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public Uri getFileUri() {
        return fileUri;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public long getLastModified() {
        return lastModified;
    }

    @Override
    public long getSize() {
        return size;
    }

    @NonNull
    @Override
    public String toString() {
        return "ImageFile{" +
                "id=" + id +
                ", fileUri=" + fileUri +
                ", displayName='" + displayName + '\'' +
                ", lastModified=" + lastModified +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImageFile imageFile = (ImageFile) o;
        return id == imageFile.id && lastModified == imageFile.lastModified && displayName.equals(imageFile.displayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, displayName, lastModified);
    }
}
