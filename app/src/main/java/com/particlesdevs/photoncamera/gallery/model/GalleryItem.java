package com.particlesdevs.photoncamera.gallery.model;

import android.widget.Checkable;

import com.particlesdevs.photoncamera.gallery.files.ImageFile;
import com.particlesdevs.photoncamera.gallery.files.MediaFile;

import org.apache.commons.io.FileUtils;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Created by Vibhor Srivastava on October 14, 2021
 */
public class GalleryItem implements Checkable {
    private final MediaFile file;
    private final String mediaTypeTag;
    private boolean isChecked;
    private String displayName;
    private final ArrayList<GalleryItem> files = new ArrayList<>(0);

    public GalleryItem(MediaFile file) {
        this.file = file;
        this.mediaTypeTag = getTagName(file.getDisplayName());
        this.displayName = file.getDisplayName();
    }
    public static GalleryItem createEmpty(){
        return new GalleryItem(new ImageFile(0,null,"",0,0,""));
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
        } else if (isVideoExtension(ext)) {
            return "MP4";
        }
        return ext.toUpperCase(Locale.ROOT);
    }

    public boolean isVideo() {
        if (file == null) return false;
        try {
            return isVideoExtension(FileUtils.getExtension(file.getDisplayName()));
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isVideoExtension(String ext) {
        return ext != null && (ext.equalsIgnoreCase("mp4")
                || ext.equalsIgnoreCase("3gp")
                || ext.equalsIgnoreCase("mkv")
                || ext.equalsIgnoreCase("webm")
                || ext.equalsIgnoreCase("mov")
                || ext.equalsIgnoreCase("m4v"));
    }

    public MediaFile getFile() {
        return file;
    }

    public ArrayList<GalleryItem> getFiles() {
        return files;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    public String getDisplayName() {
        return displayName;
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
