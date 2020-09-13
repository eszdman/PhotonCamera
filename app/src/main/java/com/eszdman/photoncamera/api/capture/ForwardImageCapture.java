package com.eszdman.photoncamera.api.capture;

import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

public class ForwardImageCapture extends AbstractImageCapture {

    private String TAG = ForwardImageCapture.class.getSimpleName();

    public interface ImageEvents
    {
        void onImageAvailable(Image img);
    }

    private ImageEvents imageEventsListner;

    public ForwardImageCapture(int width, int height, int format, int maximgs, ImageEvents imageEventsListner) {
        super(width, height, format, maximgs);
        this.imageEventsListner = imageEventsListner;
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        Log.d(TAG, "onImageAvailable");
        Image img = imageReader.acquireLatestImage();
        if (imageEventsListner != null)
            imageEventsListner.onImageAvailable(img);
    }
}
