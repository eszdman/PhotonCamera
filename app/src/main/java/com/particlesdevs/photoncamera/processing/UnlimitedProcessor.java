package com.particlesdevs.photoncamera.processing;


import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import com.particlesdevs.photoncamera.api.Camera2ApiAutoFix;
import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.particlesdevs.photoncamera.processing.opengl.scripts.AverageParams;
import com.particlesdevs.photoncamera.processing.opengl.scripts.AverageRaw;

import java.nio.ByteBuffer;
import java.nio.file.Path;

public class UnlimitedProcessor extends ProcessorBase {
    private static final String TAG = "UnlimitedProcessor";
    public static int unlimitedCounter = 1;
    private static boolean unlimitedEnd = false;
    private AverageRaw averageRaw;
    private boolean lock = false;
    private boolean fillParams = false;

    /* config */
    private boolean saveRAW;

    public UnlimitedProcessor(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
    }

    public void configure(boolean saveRAW) {
        this.saveRAW = saveRAW;
    }

    public void unlimitedStart(Path dngFile, Path jpgFile, ParseExif.ExifData exifData,
                               CameraCharacteristics characteristics,
                               CaptureResult captureResult,
                               int cameraRotation,
                               ProcessingCallback callback) {
        this.dngFile = dngFile;
        this.jpgFile = jpgFile;
        this.exifData = exifData;
        this.characteristics = characteristics;
        this.captureResult = captureResult;
        this.cameraRotation = cameraRotation;
        unlimitedEnd = false;
        lock = false;
        fillParams = false;
        this.callback = callback;
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


            averageRaw = new AverageRaw(PhotonCamera.getParameters().rawSize, "UnlimitedAvr");
        }
        if (!fillParams) {
            fillParams = true;
            PhotonCamera.getParameters().FillConstParameters(characteristics, PhotonCamera.getParameters().rawSize);
            PhotonCamera.getParameters().FillDynamicParameters(captureResult);

            exifData.IMAGE_DESCRIPTION =  PhotonCamera.getParameters().toString();
        }
        averageRaw.additionalParams = new AverageParams(null, image.getPlanes()[0].getBuffer());
        averageRaw.Run();
        unlimitedCounter++;
        if (unlimitedEnd) {
            unlimitedEnd = false;
            lock = true;
            unlimitedCounter = 0;
            try {
                processUnlimited(image);
            } catch (Exception e) {
                callback.onFailed();
                processingEventsListener.onProcessingError("Unlimited Processing Failed!");
                e.printStackTrace();
            }
        }
        image.close();//code block
    }

    private void processUnlimited(Image image) {
        callback.onStarted();
//        PhotonCamera.getParameters().path = ImageSaver.jpgFilePathToSave.getAbsolutePath();
        processingEventsListener.onProcessingStarted("Unlimited Processing Started");
        averageRaw.FinalScript();
        ByteBuffer unlimitedBuffer = averageRaw.Output;
        averageRaw.close();
        averageRaw = null;
        image.getPlanes()[0].getBuffer().position(0);
        image.getPlanes()[0].getBuffer().put(unlimitedBuffer);
        image.getPlanes()[0].getBuffer().position(0);
        if (saveRAW) {

            processingEventsListener.onProcessingFinished("Unlimited rawSaver Processing Finished");

            Camera2ApiAutoFix.patchWL(characteristics, captureResult, (int) FAKE_WL);

            boolean imageSaved = ImageSaver.Util.saveStackedRaw(dngFile, image,
                    characteristics, captureResult,cameraRotation);

            Camera2ApiAutoFix.resetWL(characteristics, captureResult, (int) FAKE_WL);

            processingEventsListener.notifyImageSavedStatus(imageSaved, dngFile);

            return;
        }

        IncreaseWLBL();
        PostPipeline pipeline = new PostPipeline();
        Bitmap bitmap = pipeline.Run(image.getPlanes()[0].getBuffer(), PhotonCamera.getParameters());

        processingEventsListener.onProcessingFinished("Unlimited JPG Processing Finished");

        boolean imageSaved = ImageSaver.Util.saveBitmapAsJPG(jpgFile, bitmap,
                ImageSaver.JPG_QUALITY, exifData);

        processingEventsListener.notifyImageSavedStatus(imageSaved, jpgFile);

        pipeline.close();

        callback.onFinished();

    }

    public void unlimitedEnd() {
        unlimitedEnd = true;
    }

}