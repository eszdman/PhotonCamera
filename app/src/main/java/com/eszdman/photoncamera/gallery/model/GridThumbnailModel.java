package com.eszdman.photoncamera.gallery.model;

import android.graphics.Bitmap;
import androidx.databinding.BaseObservable;
import androidx.databinding.Bindable;

public class GridThumbnailModel extends BaseObservable {
    private Bitmap bitmap;
    private String fileext;

    public GridThumbnailModel(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    @Bindable
    public String getFileext() {
        return fileext;
    }

    public void setFileext(String fileext) {
        this.fileext = fileext;
    }

    @Bindable
    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }
}
