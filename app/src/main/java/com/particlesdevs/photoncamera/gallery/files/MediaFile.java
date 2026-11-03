package com.particlesdevs.photoncamera.gallery.files;

import android.net.Uri;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * Created by Vibhor Srivastava on October 14, 2021
 */
public abstract class MediaFile {

    public abstract long getId();

    public abstract Uri getFileUri();

    public abstract long getLastModified();

    public abstract String getDisplayName();

    public abstract long getSize();

    public abstract String getAbsolutePath();

    /**
     * Lower-case file extension without the dot, "" when the name has none.
     * Computed once, because the gallery re-checks it on every bind.
     */
    public abstract String getExtension();

    /** Whether this file is a video container the gallery can play. */
    public abstract boolean isVideo();

    /** Video containers that appear in the gallery. */
    public static boolean isVideoExtension(@Nullable String ext) {
        return ext != null && (ext.equalsIgnoreCase("mp4")
                || ext.equalsIgnoreCase("3gp")
                || ext.equalsIgnoreCase("mkv")
                || ext.equalsIgnoreCase("webm")
                || ext.equalsIgnoreCase("mov")
                || ext.equalsIgnoreCase("m4v"));
    }

    /** Lower-case extension of a display name, "" when there is none. */
    public static String extensionOf(@Nullable String displayName) {
        if (displayName == null) return "";
        int dot = displayName.lastIndexOf('.');
        if (dot < 0 || dot == displayName.length() - 1) return "";
        return displayName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
