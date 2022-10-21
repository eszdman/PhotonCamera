package com.particlesdevs.photoncamera.gallery.files;

import android.net.Uri;

/**
 * Created by Vibhor Srivastava on October 14, 2021
 */
public abstract class MediaFile {

    public abstract long getId();

    public abstract Uri getFileUri();

    public abstract long getLastModified();

    public abstract String getDisplayName();

    public abstract long getSize();

}
