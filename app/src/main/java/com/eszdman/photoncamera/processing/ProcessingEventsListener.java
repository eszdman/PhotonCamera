package com.eszdman.photoncamera.processing;

/**
 * Interface that listens to events related to processing of image after it has been captured
 */
public interface ProcessingEventsListener {
    String FAILED_MSG = "Image Processing/Saving Failed!";

    void onProcessingStarted(Object obj);

    void onProcessingChanged(Object obj);

    void onProcessingFinished(Object obj);

    void onImageSaved(Object obj);

    void onSaveImage(Object obj);

    void onErrorOccured(Object obj);


}
