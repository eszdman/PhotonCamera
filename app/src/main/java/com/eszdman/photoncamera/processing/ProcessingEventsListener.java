package com.eszdman.photoncamera.processing;

public interface ProcessingEventsListener {
    void onProcessingStarted(Object obj);

    void onProcessingChanged(Object obj);

    void onProcessingFinished(Object obj);

    void onImageSaved(Object obj);

    void onSaveImage(Object obj);


}
