package com.particlesdevs.photoncamera.processing;

import android.graphics.ImageFormat;
import android.util.Log;

public class ImageSaverSelector {
    private static final String TAG = "ImageSaverSelector";
    private int format;
    private SaverImplementation saverImplementation;
    ImageSaverSelector(int format, SaverImplementation saverImplementation) {
        this.format = format;
        this.saverImplementation = saverImplementation;
        ImageSaverSelect();
    }

    private void ImageSaverSelect() {
        switch (format) {
            case ImageFormat.JPEG:
                saverImplementation = new JPEGSaver(saverImplementation.processingEventsListener);
                break;

            case ImageFormat.YUV_420_888:
                saverImplementation = new YUVSaver(saverImplementation.processingEventsListener);
                break;

            //case ImageFormat.RAW10:
            case ImageFormat.RAW_SENSOR:
                saverImplementation = new RAW16Saver(saverImplementation.processingEventsListener);
                break;

            default:
                Log.e(TAG, "Cannot save image, unexpected image format:" + format);
                break;
        }
    }

    public SaverImplementation SelectedImageSaver() {
        return saverImplementation;
    }
}
