package com.particlesdevs.photoncamera.gallery.model;

import android.widget.Checkable;

import com.particlesdevs.photoncamera.gallery.files.MediaFile;

import org.apache.commons.io.FileUtils;

import java.util.Locale;

/**
 * Created by Vibhor Srivastava on October 14, 2021
 */
public class GalleryItem implements Checkable {
    private final MediaFile file;
    private final String mediaTypeTag;
    private boolean isChecked;

    public GalleryItem(MediaFile file) {
        this.file = file;
        this.mediaTypeTag = getTagName(file.getDisplayName());
    }

    public String getMediaTypeTag() {
        return mediaTypeTag;
    }

    private String getTagName(String fileName) {
        String ext = FileUtils.getExtension(fileName);
        if (ext.equalsIgnoreCase("dng")) {
            return "RAW";
        } else if (ext.equalsIgnoreCase("jpg") || ext.equalsIgnoreCase("jpeg")) {
            return "";
        }
        return ext.toUpperCase(Locale.ROOT);
    }

    public MediaFile getFile() {
        return file;
    }

    @Override
    public boolean isChecked() {
        return isChecked;
    }

    @Override
    public void setChecked(boolean checked) {
        isChecked = checked;
    }

    @Override
    public void toggle() {
        isChecked = !isChecked;
    }
}
