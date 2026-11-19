package com.particlesdevs.photoncamera.gallery.files;

import android.net.Uri;
import java.util.Locale;

/**
 * Created by Vibhor Srivastava on October 14, 2021
 */
public abstract class MediaFile {

    public boolean isZip() {
        String name = getDisplayName();
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    public abstract long getId();

    public abstract Uri getFileUri();

    public abstract long getLastModified();

    public abstract String getDisplayName();

    public abstract long getSize();

    public abstract String getAbsolutePath();

}
