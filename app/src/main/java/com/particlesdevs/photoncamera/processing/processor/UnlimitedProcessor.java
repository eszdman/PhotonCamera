package com.particlesdevs.photoncamera.processing.processor;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageSaver;
import com.particlesdevs.photoncamera.processing.ProcessingEventsListener;
import com.particlesdevs.photoncamera.processing.encoder.HeicSupport;
import com.particlesdevs.photoncamera.processing.encoder.ImageFormatConfig;
import com.particlesdevs.photoncamera.processing.encoder.StillEncoder;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.particlesdevs.photoncamera.processing.opengl.scripts.AverageParams;
import com.particlesdevs.photoncamera.processing.opengl.scripts.AverageRaw;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.processing.ultrahdr.GainMapComputer;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;
import com.particlesdevs.photoncamera.util.Allocator;
import com.particlesdevs.photoncamera.util.Log;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;

public class UnlimitedProcessor extends ProcessorBase {
    private static final String TAG = "UnlimitedProcessor";
    public static int unlimitedCounter = 1;
    private static boolean unlimitedEnd = false;

    private AverageRaw averageRaw;
    private boolean lock = false;
    private boolean fillParams = false;

    /* config */
    private int saveRAW;

    private Parameters parameters;

    public UnlimitedProcessor(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
    }

    public void configure(int saveRAW) {
        this.saveRAW = saveRAW;
    }

    public void unlimitedStart(Path dngFile, Path jpgFile, ParseExif.ExifData exifData,
                               CameraCharacteristics characteristics,
                               CaptureResult captureResult,
                               CaptureRequest captureRequest,
                               int cameraRotation,
                               ProcessingCallback callback) {
        this.dngFile = dngFile;
        this.imageFile = jpgFile;
        this.exifData = exifData;
        this.characteristics = characteristics;
        this.captureResult = captureResult;
        this.cameraRotation = cameraRotation;
        this.captureRequest = captureRequest;
        unlimitedEnd = false;
        lock = false;
        this.callback = callback;
        parameters = new Parameters();
        fillParams = false;
    }

    public void unlimitedCycle(Image image) {
        /*if (lock) {
            image.close();
            return;
        }*/
        int width = image.getPlanes()[0].getRowStride() / image.getPlanes()[0].getPixelStride();
        int height = image.getHeight();
        if(!fillParams){
            parameters.FillConstParameters(characteristics, new Point(width, height));
            parameters.FillDynamicParameters(captureResult, captureRequest, IsoExpoSelector.fullpairs.get(0).iso);
            parameters.cameraRotation = this.cameraRotation;
            ParseExif.syncWithParameters(exifData, parameters);
            fillParams = true;
        }
        if (averageRaw == null) {
            averageRaw = new AverageRaw(parameters.rawSize, "UnlimitedAvr");
        }
        averageRaw.additionalParams = new AverageParams(null, image.getPlanes()[0].getBuffer(), parameters);
        averageRaw.Run();
        unlimitedCounter++;
        /*
        if (unlimitedEnd) {
            unlimitedEnd = false;
            lock = true;
            FrameNumberSelector.frameCount = unlimitedCounter;
            parameters.noiseModeler.computeStackingNoiseModel();
            unlimitedCounter = 0;
            try {
                processUnlimited(image);
            } catch (Exception e) {
                callback.onFailed();
                processingEventsListener.onProcessingError("Unlimited Processing Failed!");
                e.printStackTrace();
            }
        }*/
    }

    private void processUnlimited() {
        callback.onStarted();
//        parameters.path = ImageSaver.jpgFilePathToSave.getAbsolutePath();
        processingEventsListener.onProcessingStarted("Unlimited");
        averageRaw.FinalScript();
        ByteBuffer unlimitedBuffer = averageRaw.Output;
        averageRaw.close();
        averageRaw = null;

        IncreaseWLBL(parameters);

        int saveMode = ImageFormatConfig.normalize(saveRAW);
        if (ImageFormatConfig.savesRaw(saveMode)) {

            processingEventsListener.onProcessingFinished("Unlimited rawSaver Processing Finished");
            unlimitedBuffer.position(0);

            boolean imageSaved = ImageSaver.Util.saveStackedRaw(dngFile, unlimitedBuffer, parameters);

            processingEventsListener.notifyImageSavedStatus(imageSaved, dngFile);
            if (ImageFormatConfig.isRawOnly(saveMode)) {
                processingEventsListener.onProcessingFinished("Unlimited RAW Processing Finished");
                callback.onFinished();
                return;
            }
        }


        PostPipeline pipeline = new PostPipeline();
        Bitmap bitmap = pipeline.Run(unlimitedBuffer, parameters);

        // The stacked RAW frame is dead once it has been rendered - free it
        // before the memory-heavy Ultra HDR gain-map pass (it previously
        // leaked entirely).
        Allocator.free(unlimitedBuffer);
        unlimitedBuffer = null;

        PostPipeline.GainMapRaw gm = null;
        if (PhotonCamera.getSettings().ultraHdr) {
            try {
                int down = PhotonCamera.getSettings().ultraHdr4x ? 4 : 1;
                gm = pipeline.RunHDRGainMap(parameters, bitmap, down, GainMapComputer.SCALE);
            } catch (Exception e) {
                Log.e("UnlimitedProcessor", "Ultra HDR gain-map pass failed, falling back to SDR JPEG", e);
            }
        }

        processingEventsListener.onProcessingFinished("Unlimited JPG Processing Finished");
        boolean useHeic = ImageFormatConfig.usesHeic(saveMode);
        if (useHeic && !HeicSupport.isHeicEncodeSupported()) {
            Log.e("UnlimitedProcessor", "HEIC save mode on unsupported device; JPEG fallback");
            useHeic = false;
        }
        imageFile = Paths.get(imageFile.toAbsolutePath() + (useHeic ? ".heic" : ".jpg"));
        StillEncoder.Result still = StillEncoder.encodeStill(
                imageFile, bitmap, gm, exifData, useHeic);
        boolean imageSaved = still.saved;
        imageFile = still.file;

        processingEventsListener.notifyImageSavedStatus(imageSaved, imageFile);

        pipeline.close();

        callback.onFinished();

    }

    public void unlimitedEnd() {
        unlimitedEnd = false;
        lock = true;
        FrameNumberSelector.frameCount = unlimitedCounter;
        parameters.noiseModeler.computeStackingNoiseModel();
        unlimitedCounter = 1;
        try {
            processUnlimited();
        } catch (Exception e) {
            callback.onFailed();
            processingEventsListener.onProcessingError("Unlimited Processing Failed!");
            e.printStackTrace();
        }
    }
}