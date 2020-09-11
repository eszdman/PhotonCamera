package com.eszdman.photoncamera.api.capture;

import android.media.ImageReader;
import android.view.Surface;

import com.eszdman.photoncamera.api.BackThreadController;

public abstract class AbstractImageCapture implements ImageReader.OnImageAvailableListener {
    private ImageReader imageReader;
    private BackThreadController threadController;

    public AbstractImageCapture(int width, int height, int format,int maximgs)
    {
        threadController = new BackThreadController();
        imageReader = ImageReader.newInstance(width,height,format,maximgs);
        imageReader.setOnImageAvailableListener(this::onImageAvailable,threadController.getmBackgroundHandler());
    }

    public int getWidth()
    {
        return imageReader.getWidth();
    }

    public int getHeight()
    {
        return imageReader.getHeight();
    }

    public void close()
    {
        threadController.close();
        imageReader.close();
    }

    public Surface getSurface()
    {
        return imageReader.getSurface();
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {

    }
}
