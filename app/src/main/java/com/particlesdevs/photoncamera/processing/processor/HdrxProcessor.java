package com.particlesdevs.photoncamera.processing.processor;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.util.Log;

import com.particlesdevs.photoncamera.Wrapper;
import com.particlesdevs.photoncamera.api.Camera2ApiAutoFix;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.ImageFrameDeblur;
import com.particlesdevs.photoncamera.processing.ImageSaver;
import com.particlesdevs.photoncamera.processing.ProcessingEventsListener;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.particlesdevs.photoncamera.processing.opengl.rawpipeline.RawPipeline;
import com.particlesdevs.photoncamera.processing.opengl.scripts.InterpolateGainMap;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.render.Parameters;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;

public class HdrxProcessor extends ProcessorBase {
    private static final String TAG = "HdrxProcessor";
    private ArrayList<Image> mImageFramesToProcess;
    private int imageFormat;
    /* config */
    private int alignAlgorithm;
    private boolean saveRAW;
    private CameraMode cameraMode;
    private ArrayList<GyroBurst> BurstShakiness;


    public HdrxProcessor(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
    }

    public void configure(int alignAlgorithm, boolean saveRAW, CameraMode cameraMode) {
        this.alignAlgorithm = alignAlgorithm;
        this.saveRAW = saveRAW;
        this.cameraMode = cameraMode;
    }

    public void start(Path dngFile, Path jpgFile,
                      ParseExif.ExifData exifData,
                      ArrayList<GyroBurst> BurstShakiness,
                      ArrayList<Image> imageBuffer,
                      int imageFormat,
                      int cameraRotation,
                      CameraCharacteristics characteristics,
                      CaptureResult captureResult,
                      ProcessingCallback callback) {
        this.jpgFile = jpgFile;
        this.dngFile = dngFile;
        this.exifData = exifData;
        this.BurstShakiness = new ArrayList<>(BurstShakiness);
        this.imageFormat = imageFormat;
        this.cameraRotation = cameraRotation;
        this.mImageFramesToProcess = imageBuffer;
        this.callback = callback;
        this.characteristics = characteristics;
        this.captureResult = captureResult;
        Run();
    }

    public void Run() {
        try {
            Camera2ApiAutoFix.ApplyRes(captureResult);
            if (imageFormat == CaptureController.RAW_FORMAT) {
                ApplyHdrX();
            }
//            if (isYuv) {
//                ApplyStabilization();
//            }
        } catch (Exception e) {
            Log.e(TAG, ProcessingEventsListener.FAILED_MSG);
            e.printStackTrace();
            callback.onFailed();
            processingEventsListener.onProcessingError("HdrX Processing Failed");
        }
    }

    private void ApplyHdrX() {
        callback.onStarted();
        processingEventsListener.onProcessingStarted("HDRX");

        boolean debugAlignment = (alignAlgorithm > 0);

        Log.d(TAG, "ApplyHdrX() called from" + Thread.currentThread().getName());

        long startTime = System.currentTimeMillis();
        int width = mImageFramesToProcess.get(0).getPlanes()[0].getRowStride() /
                mImageFramesToProcess.get(0).getPlanes()[0].getPixelStride();
        int height = mImageFramesToProcess.get(0).getHeight();
        Log.d(TAG, "APPLY HDRX: buffer:" + mImageFramesToProcess.get(0).getPlanes()[0].getBuffer().asShortBuffer().remaining());
        Log.d(TAG, "Api WhiteLevel:" + characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL));
        Log.d(TAG, "Api BlackLevel:" + characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN));
        Parameters processingParameters = PhotonCamera.getParameters();
        processingParameters.FillConstParameters(characteristics, new Point(width, height));
        processingParameters.FillDynamicParameters(captureResult);
        processingParameters.cameraRotation = cameraRotation;

        exifData.IMAGE_DESCRIPTION = processingParameters.toString();

        Log.d(TAG, "Wrapper.init");
        RawPipeline rawPipeline = new RawPipeline();
        ArrayList<ImageFrame> images = new ArrayList<>();
        ByteBuffer lowexp = null;
        ByteBuffer highexp = null;
        for (int i = 0; i < mImageFramesToProcess.size(); i++) {
            ByteBuffer byteBuffer;
            byteBuffer = mImageFramesToProcess.get(i).getPlanes()[0].getBuffer();
            ImageFrame frame = new ImageFrame(byteBuffer);
            frame.frameGyro = BurstShakiness.get(i);
            frame.image = mImageFramesToProcess.get(i);
            //frame.pair = IsoExpoSelector.pairs.get(i % IsoExpoSelector.patternSize);
            frame.pair = IsoExpoSelector.fullpairs.get(i);
            frame.number = i;
            /*if(i == mImageFramesToProcess.size()-1){
                int ind = Math.max(0,mImageFramesToProcess.size()-2);
                frame.frameGyro = BurstShakiness.get(ind);
            }*/
            images.add(frame);
        }
        ImageFrameDeblur imageFrameDeblur = new ImageFrameDeblur();
        imageFrameDeblur.firstFrameGyro = images.get(0).frameGyro.clone();
        for(int i = 0; i< images.size();i++)
        imageFrameDeblur.processDeblurPosition(images.get(i));
        if (mImageFramesToProcess.size() >= 3)
            images.sort((img1, img2) -> Float.compare(img1.frameGyro.shakiness, img2.frameGyro.shakiness));
        double unluckypickiness = 1.05;
        float unluckyavr = 0;
        for (ImageFrame image : images) {
            unluckyavr += image.frameGyro.shakiness;
            Log.d(TAG, "unlucky map:" + image.frameGyro.shakiness + "n:" + image.number);
        }
        unluckyavr /= images.size();
        if (images.size() >= 4) {
            int size = (int) (images.size() - FrameNumberSelector.throwCount);
            Log.d(TAG, "Throw Count:" + size);
            Log.d(TAG, "Image Count:" + images.size());
            if (size == images.size()) size = (int) (images.size() * 0.75);
            for (int i = images.size(); i > size; i--) {
                float curunlucky = images.get(images.size() - 1).frameGyro.shakiness;
                if (curunlucky > unluckyavr * unluckypickiness) {
                    Log.d(TAG, "Removing unlucky:" + curunlucky + " number:" + images.get(images.size() - 1).number);
                    images.get(images.size() - 1).image.close();
                    images.remove(images.size() - 1);
                }
            }
            Log.d(TAG, "Size after removal:" + images.size());
        }

        float minMpy = 1000.f;
        for (int i = 0; i < IsoExpoSelector.fullpairs.size(); i++) {
            if (IsoExpoSelector.fullpairs.get(i).layerMpy < minMpy) {
                minMpy = IsoExpoSelector.fullpairs.get(i).layerMpy;
            }
        }
        if (images.get(0).pair.layerMpy != minMpy) {
            for (int i = 1; i < images.size(); i++) {
                if (images.get(i).pair.layerMpy == minMpy) {
                    ImageFrame frame = images.get(0);
                    images.set(0, images.get(i));
                    images.set(i, frame);
                    break;
                }
            }
        }
        FrameNumberSelector.frameCount = images.size();
        if (!debugAlignment) {
            Wrapper.init(width, height, images.size());
            for (int i = 0; i < images.size(); i++) {
                float mpy = minMpy / images.get(i).pair.layerMpy;
                //if (images.get(i).pair.curlayer == IsoExpoSelector.ExpoPair.exposureLayer.Normal)
                //    mpy = 1.f;
                //if(images.get(i).pair.curlayer == IsoExpoSelector.ExpoPair.exposureLayer.Low) mpy = 1.f;
                Log.d(TAG, "Load: i: " + i + " expo layer:" + images.get(i).pair.curlayer + " mpy:" + mpy);
                Wrapper.loadFrame(images.get(i).buffer, ((FAKE_WL) / processingParameters.whiteLevel) * mpy);
            }
        }
        rawPipeline.imageObj = mImageFramesToProcess;
        rawPipeline.images = images;
        Log.d(TAG, "White Level:" + processingParameters.whiteLevel);
        Log.d(TAG, "Wrapper.loadFrame");
        //float noiseLevel = (float) Math.sqrt((CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)) *
        //        IsoExpoSelector.getMPY() - 40.)*6400.f / (6.2f*IsoExpoSelector.getISOAnalog());



        ByteBuffer output = null;
        InterpolateGainMap interpolateGainMap;
        if (!debugAlignment) {
            interpolateGainMap = new InterpolateGainMap(new Point(width, height));
            interpolateGainMap.parameters = processingParameters;
            interpolateGainMap.Run();
            interpolateGainMap.close();
            Wrapper.loadInterpolatedGainMap(interpolateGainMap.Output);
            float NoiseS = processingParameters.noiseModeler.computeModel[0].first.floatValue()+
                    processingParameters.noiseModeler.computeModel[1].first.floatValue()+
                    processingParameters.noiseModeler.computeModel[2].first.floatValue();
            float NoiseO = processingParameters.noiseModeler.computeModel[0].second.floatValue()+
                    processingParameters.noiseModeler.computeModel[1].second.floatValue()+
                    processingParameters.noiseModeler.computeModel[2].second.floatValue();
            /*float noiseLevel = (processingParameters.noiseModeler.computeModel[0].first.floatValue()+
                    processingParameters.noiseModeler.computeModel[1].first.floatValue()+
                    processingParameters.noiseModeler.computeModel[2].first.floatValue())*0.7f;
            noiseLevel += processingParameters.noiseModeler.computeModel[0].second.floatValue()+
                    processingParameters.noiseModeler.computeModel[1].second.floatValue()+
                    processingParameters.noiseModeler.computeModel[2].second.floatValue();
            noiseLevel*=Math.pow(2.0,19.0+PhotonCamera.getSettings().noiseRstr);
            noiseLevel = Math.max(1.f,noiseLevel);
            Log.d(TAG, "Denoising level:" + noiseLevel);*/
            NoiseS/=3.f;
            NoiseO/=3.f;
            double noisempy = Math.pow(2.0,PhotonCamera.getSettings().noiseRstr+10.6);
            NoiseS = (float) Math.max(NoiseS*noisempy, Float.MIN_NORMAL);
            NoiseO = (float) Math.max(NoiseO*noisempy*16384, Float.MIN_NORMAL);
            output = ByteBuffer.allocateDirect(images.get(0).buffer.capacity());
            Wrapper.outputBuffer(output);

            Wrapper.processFrame(NoiseS, NoiseO, 6.f,1,0.f, 0.f, 0.f, processingParameters.whiteLevel
                    , processingParameters.whitePoint[0], processingParameters.whitePoint[1], processingParameters.whitePoint[2], processingParameters.cfaPattern);
        } else {
        rawPipeline.alignAlgorithm = alignAlgorithm;
        output = rawPipeline.Run();
        }
        float[] oldBL = processingParameters.blackLevel.clone();

        //Black shot fix
        images.get(0).image.getPlanes()[0].getBuffer().position(0);
        images.get(0).image.getPlanes()[0].getBuffer().put(output);
        images.get(0).image.getPlanes()[0].getBuffer().position(0);
        for (int i = 1; i < images.size(); i++) {
            //if ((i == 3 || i == 2) && IsoExpoSelector.HDR)
            //    continue;
            images.get(i).image.close();
        }
        if (debugAlignment) {
            rawPipeline.close();
        }
        Log.d(TAG, "HDRX Alignment elapsed:" + (System.currentTimeMillis() - startTime) + " ms");

        if (saveRAW) {
            int patchWL = (int) FAKE_WL;

            Camera2ApiAutoFix.patchWL(characteristics, captureResult, patchWL);
            /*if(!debugAlignment && parameters.hasGainMap) {
                NonIdealRaw nonIdealRaw = new NonIdealRaw(new Point(width,height));
                nonIdealRaw.parameters = parameters;
                nonIdealRaw.inp = images.get(0).image.getPlanes()[0].getBuffer();
                nonIdealRaw.prevmap = interpolateGainMap.Output;
                nonIdealRaw.Run();
                nonIdealRaw.close();
            }*/
            boolean imageSaved = ImageSaver.Util.saveStackedRaw(dngFile, images.get(0).image,
                    characteristics, captureResult, cameraRotation);


            Camera2ApiAutoFix.resetWL(characteristics, captureResult, patchWL);

            processingEventsListener.notifyImageSavedStatus(imageSaved, dngFile);
            processingParameters.blackLevel[0] = oldBL[0];
            processingParameters.blackLevel[1] = oldBL[1];
            processingParameters.blackLevel[2] = oldBL[2];
            processingParameters.blackLevel[3] = oldBL[3];
            Camera2ApiAutoFix.resetWL(characteristics, captureResult, (int) FAKE_WL);

            /*parameters.blackLevel[0] = 0.f;
            parameters.blackLevel[1] -= bl;
            parameters.blackLevel[2] -= bl;
            parameters.blackLevel[3] = 0.f;*/

            ;
        } /*else {
            if(!debugAlignment) {
                parameters.mapSize = new Point(1,1);
                parameters.gainMap = new float[]{1.f,1.f,1.f,1.f};
            }
        }*/

        IncreaseWLBL();

        PostPipeline pipeline = new PostPipeline();
        pipeline.lowFrame = lowexp;
        pipeline.highFrame = highexp;

        Bitmap img = pipeline.Run(images.get(0).image.getPlanes()[0].getBuffer(), PhotonCamera.getParameters());
        img = overlay(img,pipeline.debugData.toArray(new Bitmap[pipeline.debugData.size()]));
        processingEventsListener.onProcessingFinished("HdrX JPG Processing Finished");

        //Saves the final bitmap
        boolean imageSaved = ImageSaver.Util.saveBitmapAsJPG(jpgFile, img,
                ImageSaver.JPG_QUALITY, exifData);

        processingEventsListener.notifyImageSavedStatus(imageSaved, jpgFile);

        pipeline.close();
        images.get(0).image.close();
        callback.onFinished();
    }

}
