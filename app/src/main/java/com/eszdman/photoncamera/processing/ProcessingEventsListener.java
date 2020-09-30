package com.eszdman.photoncamera.processing;

import android.util.Log;

/**
 * Interface that listens to events related to processing of image after it has been captured
 */
public interface ProcessingEventsListener {
    String TAG = "ProcessingEvents";
    String FAILED_MSG = "Image Processing/Saving Failed!";

    void onProcessingStarted(Object obj);

    void onProcessingChanged(Object obj);

    void onProcessingFinished(Object obj);

    void onImageSaved(Object obj);

    void onSaveImage(Object obj);

    void onErrorOccured(Object obj);

    default void log(String msg) {
        Log.d(TAG, msg);
    }


}
