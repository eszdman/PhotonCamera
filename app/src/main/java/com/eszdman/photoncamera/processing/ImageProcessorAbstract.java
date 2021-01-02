package com.eszdman.photoncamera.processing;

import java.nio.file.Path;

public abstract class ImageProcessorAbstract {
    protected final ProcessingEventsListener processingEventsListener;
    protected Path dngFile;
    protected Path jpgFile;
    protected ProcessingCallback callback;

    public ImageProcessorAbstract(ProcessingEventsListener processingEventsListener) {
        this.processingEventsListener = processingEventsListener;
    }

    public void process() {
    }


    public interface ProcessingCallback {
        void onFinished();
    }
}
