package com.particlesdevs.photoncamera.processing;

import android.media.Image;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.DefaultSaver;
import com.particlesdevs.photoncamera.processing.ImagePath;
import com.particlesdevs.photoncamera.processing.ProcessingEventsListener;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

public class JPEGSaver extends DefaultSaver {
    private static final String TAG = "JPEGSaver";
    public JPEGSaver(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
    }

    public void addImage(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        try {
            IMAGE_BUFFER.add(image);
            byte[] bytes = new byte[buffer.remaining()];
            if (IMAGE_BUFFER.size() == PhotonCamera.getCaptureController().mMeasuredFrameCnt && PhotonCamera.getSettings().frameCount != 1) {
                Path jpgPath = ImagePath.newJPGFilePath();
                buffer.duplicate().get(bytes);
                Files.write(jpgPath, bytes);

//                hdrxProcessor.start(dngFile, jpgFile, IMAGE_BUFFER, mImage.getFormat(),
//                        CaptureController.mCameraCharacteristics, CaptureController.mCaptureResult,
//                        () -> clearImageReader(mReader));

                IMAGE_BUFFER.clear();
            }
            if (PhotonCamera.getSettings().frameCount == 1) {
                Path jpgPath = ImagePath.newJPGFilePath();
                IMAGE_BUFFER.clear();
                buffer.get(bytes);
                Files.write(jpgPath, bytes);
                image.close();
                processingEventsListener.onProcessingFinished("JPEG: Single Frame, Not Processed!");
                processingEventsListener.notifyImageSavedStatus(true, jpgPath);
            }
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
    }
}
