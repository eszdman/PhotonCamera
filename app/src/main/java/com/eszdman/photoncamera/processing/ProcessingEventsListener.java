package com.eszdman.photoncamera.processing;

import androidx.annotation.Nullable;

import java.nio.file.Path;

/**
 * Interface that listens to events related to processing of image after it has been captured
 */
public interface ProcessingEventsListener {
    String FAILED_MSG = "Image Processing/Saving Failed!";

    void onProcessingStarted(Object obj);

    void onProcessingChanged(Object obj);

    void onProcessingFinished(Object obj);

    void notifyImageSavedStatus(boolean saved, Path savedFilePath);

    void onProcessingError(Object obj);

}
