package com.eszdman.photoncamera.processing;


import android.graphics.Bitmap;
import android.graphics.Point;
import android.media.Image;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.capture.CaptureController;
import com.eszdman.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.eszdman.photoncamera.processing.opengl.scripts.AverageParams;
import com.eszdman.photoncamera.processing.opengl.scripts.AverageRaw;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public class UnlimitedProcessor extends ImageProcessorAbstract {
    private static final String TAG = "UnlimitedProcessor";
    public static float fakeWL = 65535.f;
    public static int unlimitedCounter = 1;
    private static boolean unlimitedEnd = false;
    AverageRaw averageRaw;
    private boolean lock = false;

    public UnlimitedProcessor(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
    }

    public void unlimitedStart(Path dngFile, Path jpgFile) {
        this.dngFile = dngFile;
        this.jpgFile = jpgFile;
        unlimitedEnd = false;
        lock = false;
    }

    public void unlimitedCycle(Image image) {
        if (lock) {
            image.close();
            return;
        }
        int width = image.getPlanes()[0].getRowStride() / image.getPlanes()[0].getPixelStride();
        int height = image.getHeight();

        PhotonCamera.getParameters().rawSize = new Point(width, height);

        if (averageRaw == null) {
            PhotonCamera.getParameters().FillParameters(CaptureController.mCaptureResult,
                    CaptureController.mCameraCharacteristics, PhotonCamera.getParameters().rawSize);
            averageRaw = new AverageRaw(PhotonCamera.getParameters().rawSize, "UnlimitedAvr");
        }
        averageRaw.additionalParams = new AverageParams(null, image.getPlanes()[0].getBuffer());
        averageRaw.Run();
        unlimitedCounter++;
        if (unlimitedEnd) {
            unlimitedEnd = false;
            lock = true;
            unlimitedCounter = 0;
            processUnlimited(image);
        }
        image.close();//code block
    }

    private void processUnlimited(Image image) {
//        PhotonCamera.getParameters().path = ImageSaver.jpgFilePathToSave.getAbsolutePath();
        processingEventsListener.onProcessingStarted("Unlimited Processing Started");

        averageRaw.FinalScript();
        ByteBuffer unlimitedBuffer = averageRaw.Output;
        averageRaw.close();
        averageRaw = null;

        if (PhotonCamera.getSettings().rawSaver) {
            image.getPlanes()[0].getBuffer().position(0);
            image.getPlanes()[0].getBuffer().put(unlimitedBuffer);

            processingEventsListener.onProcessingFinished("Unlimited rawSaver Processing Finished");

            boolean imageSaved = ImageSaver.Util.saveStackedRaw(dngFile, image, (int) fakeWL);

            processingEventsListener.notifyImageSavedStatus(imageSaved, dngFile);

            return;
        }

        PostPipeline pipeline = new PostPipeline();
        Bitmap bitmap = pipeline.Run(unlimitedBuffer, PhotonCamera.getParameters());

        processingEventsListener.onProcessingFinished("Unlimited JPG Processing Finished");

        boolean imageSaved = ImageSaver.Util.saveBitmapAsJPG(jpgFile, bitmap,
                ImageSaver.JPG_QUALITY, CaptureController.mCaptureResult);

        processingEventsListener.notifyImageSavedStatus(imageSaved, jpgFile);

        pipeline.close();

    }

    public void unlimitedEnd() {
        unlimitedEnd = true;
    }

}