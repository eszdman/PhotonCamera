package com.particlesdevs.photoncamera.processing.processor;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.util.Log;

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
import com.particlesdevs.photoncamera.processing.opengl.scripts.InterpolateGainMap;
import com.particlesdevs.photoncamera.processing.opengl.scripts.PyramidMerging;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.render.Parameters;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;

public class HdrxProcessor extends ProcessorBase {
    private static final String TAG = "HdrxProcessor";
    private ArrayList<Image> mImageFramesToProcess;
    private HashMap<Long, Double> exposures;
    private int imageFormat;
    /* config */
    private int alignAlgorithm;
    private int saveRAW;
    private CameraMode cameraMode;
    private ArrayList<GyroBurst> BurstShakiness;


    public HdrxProcessor(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
    }

    public void configure(int alignAlgorithm, int saveRAW, CameraMode cameraMode) {
        this.alignAlgorithm = alignAlgorithm;
        this.saveRAW = saveRAW;
        this.cameraMode = cameraMode;
    }

    public void start(Path dngFile, Path jpgFile,
                      ParseExif.ExifData exifData,
                      ArrayList<GyroBurst> BurstShakiness,
                      ArrayList<Image> imageBuffer,
                      HashMap<Long, Double> exposures,
                      int imageFormat,
                      int cameraRotation,
                      CameraCharacteristics characteristics,
                      CaptureResult captureResult,
                      CaptureRequest captureRequest,
                      ProcessingCallback callback) {
        this.jpgFile = jpgFile;
        this.dngFile = dngFile;
        this.exifData = exifData;
        this.BurstShakiness = new ArrayList<>(BurstShakiness);
        this.imageFormat = imageFormat;
        this.cameraRotation = cameraRotation;
        this.mImageFramesToProcess = imageBuffer;
        this.exposures = exposures;
        this.callback = callback;
        this.characteristics = characteristics;
        this.captureResult = captureResult;
        this.captureRequest = captureRequest;
        Log.d(TAG, "HdrxProcessor called start()");
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
            Log.e(TAG, "Error in HdrX Processing:"+Log.getStackTraceString(e));
            callback.onFailed();
            processingEventsListener.onProcessingError("HdrX Processing Failed");
        }
    }

    private void ApplyHdrX() {
        callback.onStarted();
        processingEventsListener.onProcessingStarted("HDRX");

        Log.d(TAG, "ApplyHdrX() called from" + Thread.currentThread().getName());

        long startTime = System.currentTimeMillis();
        Log.d(TAG, "ApplyHdrX() mImageFramesToProcess.size():" + mImageFramesToProcess.size());
        int width = mImageFramesToProcess.get(0).getPlanes()[0].getRowStride() /
                mImageFramesToProcess.get(0).getPlanes()[0].getPixelStride();
        int height = mImageFramesToProcess.get(0).getHeight();
        Log.d(TAG, "APPLY HDRX: buffer:" + mImageFramesToProcess.get(0).getPlanes()[0].getBuffer().asShortBuffer().remaining());
        Log.d(TAG, "Api WhiteLevel:" + characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL));
        Log.d(TAG, "Api BlackLevel:" + characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN));
        Parameters processingParameters = PhotonCamera.getParameters();
        processingParameters.FillConstParameters(characteristics, new Point(width, height));
        // sort by timestamp first
        mImageFramesToProcess.sort(Comparator.comparingLong(Image::getTimestamp));
        double minExpo = exposures.get(mImageFramesToProcess.get(0).getTimestamp());
        for (int i = 1; i < mImageFramesToProcess.size(); i++) {
            minExpo = Math.min(minExpo, exposures.get(mImageFramesToProcess.get(i).getTimestamp()));
        }
        Log.d(TAG, "Wrapper.init");
        ArrayList<ImageFrame> images = new ArrayList<>();
        ByteBuffer lowexp = null;
        ByteBuffer highexp = null;
        int ISO = 0;
        int normalFrames = 0;
        for (int i = 0; i < mImageFramesToProcess.size(); i++) {
            ByteBuffer byteBuffer;
            byteBuffer = mImageFramesToProcess.get(i).getPlanes()[0].getBuffer();
            ImageFrame frame = new ImageFrame(byteBuffer);
            frame.frameGyro = BurstShakiness.get(i);
            frame.image = mImageFramesToProcess.get(i);
            //Log.d(TAG,"Timestamp:"+frame.image.getTimestamp());
            //frame.pair = IsoExpoSelector.pairs.get(i % IsoExpoSelector.patternSize);
            frame.pair = IsoExpoSelector.fullpairs.get(i);
            frame.number = i;
            frame.pair.layerMpy = (float) (exposures.get(mImageFramesToProcess.get(i).getTimestamp()) / minExpo);
            if (frame.pair.layerMpy > 1.0) {
                frame.pair.curlayer = IsoExpoSelector.ExpoPair.exposureLayer.High;
            } else {
                frame.pair.curlayer = IsoExpoSelector.ExpoPair.exposureLayer.Normal;
                normalFrames++;
            }
            /*if(i == mImageFramesToProcess.size()-1){
                int ind = Math.max(0,mImageFramesToProcess.size()-2);
                frame.frameGyro = BurstShakiness.get(ind);
            }*/
            Log.d(TAG, "Mpy:" + frame.pair.layerMpy);
            images.add(frame);
            ISO += frame.pair.iso;
        }
        ISO /= mImageFramesToProcess.size();

        processingParameters.FillDynamicParameters(captureResult, captureRequest,ISO);
        processingParameters.cameraRotation = cameraRotation;

        exifData.IMAGE_DESCRIPTION = processingParameters.toString();
        ImageFrameDeblur imageFrameDeblur = new ImageFrameDeblur();
        imageFrameDeblur.firstFrameGyro = images.get(0).frameGyro.clone();
        for (int i = 0; i < images.size(); i++)
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
                ImageFrame cur = images.get(images.size() - 1);
                float curunlucky = cur.frameGyro.shakiness;
                if (curunlucky > unluckyavr * unluckypickiness) {
                    if(normalFrames == 1 && cur.pair.curlayer == IsoExpoSelector.ExpoPair.exposureLayer.Normal) {
                        continue;
                    }
                    if(cur.pair.curlayer == IsoExpoSelector.ExpoPair.exposureLayer.Normal){
                        normalFrames--;
                    }
                    Log.d(TAG, "Removing unlucky:" + curunlucky + " number:" + images.get(images.size() - 1).number);
                    images.get(images.size() - 1).image.close();
                    images.remove(images.size() - 1);
                }
            }
            Log.d(TAG, "Size after removal:" + images.size());
        }

        float minMpy = 1000.f;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).pair.layerMpy < minMpy) {
                minMpy = images.get(i).pair.layerMpy;
            }
        }
        /*
        if (images.get(0).pair.layerMpy != minMpy) {
            Log.d(TAG,"Replace 0 with minMpy");
            for (int i = 1; i < images.size(); i++) {
                if (images.get(i).pair.layerMpy == minMpy) {
                    ImageFrame frame = images.get(0);
                    images.set(0, images.get(i));
                    images.set(i, frame);
                    break;
                }
            }
        }*/
        int selected = 0;
        for (int i = 0; i < images.size(); i++) {
            if(images.get(i).pair.layerMpy == minMpy){
                selected = i;
                break;
            }
        }

        // move selected image to 0 index
        if(selected != 0){
            ImageFrame frame = images.get(0);
            images.set(0, images.get(selected));
            images.set(selected, frame);
        }
        selected = 0;

        processingParameters.noiseModeler.computeStackingNoiseModel(1);
        float NoiseS = processingParameters.noiseModeler.computeModel[0].first.floatValue() +
                processingParameters.noiseModeler.computeModel[1].first.floatValue() +
                processingParameters.noiseModeler.computeModel[2].first.floatValue();
        float NoiseO = processingParameters.noiseModeler.computeModel[0].second.floatValue() +
                processingParameters.noiseModeler.computeModel[1].second.floatValue() +
                processingParameters.noiseModeler.computeModel[2].second.floatValue();
        NoiseS /= 3.f;
        NoiseO /= 3.f;
        double noisempy = Math.pow(2.0, PhotonCamera.getSettings().mergeStrength);
        int cnt = (int) ((NoiseS + NoiseO) * PhotonCamera.getSettings().frameCount * Math.pow(2.0, PhotonCamera.getSettings().mergeStrength) / (0.001f));
        Log.d(TAG, "Desired Frame count0:" + cnt);
        cnt = Math.max(cnt, 3);
        //cnt = Math.min(cnt,images.size());
        cnt = images.size();
        processingParameters.noiseModeler.computeStackingNoiseModel(cnt);
        Log.d(TAG, "Desired Frame count1:" + cnt);
        NoiseS = (float) Math.max(NoiseS * noisempy, Float.MIN_NORMAL);
        NoiseO = (float) Math.max(NoiseO * noisempy, Float.MIN_NORMAL);
        FrameNumberSelector.frameCount = cnt;

        Log.d(TAG, "White Level:" + processingParameters.whiteLevel);
        Log.d(TAG, "Wrapper.loadFrame");
        //float noiseLevel = (float) Math.sqrt((CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)) *
        //        IsoExpoSelector.getMPY() - 40.)*6400.f / (6.2f*IsoExpoSelector.getISOAnalog());


        ByteBuffer output = null;
        InterpolateGainMap interpolateGainMap;
        interpolateGainMap = new InterpolateGainMap(new Point(width, height));
        interpolateGainMap.parameters = processingParameters;
        interpolateGainMap.Run();
        interpolateGainMap.close();
        int fx = width/(2*processingParameters.tile) + 1;
        int fy = height/(2*processingParameters.tile) + 1;
        if(alignAlgorithm != 2) {
            if(alignAlgorithm == 1){
                output = ByteBuffer.allocateDirect(fx * fy * 4 * 2 * (cnt-1));
            } else {
                output = ByteBuffer.allocateDirect(images.get(0).buffer.capacity());
            }
        } else {
            output = ByteBuffer.allocateDirect(images.get(0).buffer.capacity()*3);
        }

        if(alignAlgorithm == 1) {
            PyramidMerging pyramidMerging = new PyramidMerging(new Point(width, height), images, output);
            pyramidMerging.parameters = processingParameters;
            pyramidMerging.Run();
            pyramidMerging.close();
            output.clear();
            output = pyramidMerging.Output;
            for (int i = 1; i < images.size(); i++) {
                images.get(i).image.close();
            }
        }
        //interpolateGainMap.Output.clear();
        float[] oldBL = processingParameters.blackLevel.clone();

        Log.d(TAG, "HDRX Alignment elapsed:" + (System.currentTimeMillis() - startTime) + " ms");
        //Black shot fix
        ByteBuffer result = null;
        if(alignAlgorithm != 2) {
            images.get(0).image.getPlanes()[0].getBuffer().position(0);
            images.get(0).image.getPlanes()[0].getBuffer().put(output);
            output.clear();
            images.get(0).image.getPlanes()[0].getBuffer().position(0);
            result = images.get(0).image.getPlanes()[0].getBuffer();
        } else {
            result = output;
        }
        if ((saveRAW >= 1) && alignAlgorithm != 2) {
            int patchWL = (int) FAKE_WL;

            Camera2ApiAutoFix.patchWL(characteristics, captureResult, patchWL);
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
            if (saveRAW == 2) {
                processingEventsListener.onProcessingFinished("HdrX RAW Processing Finished");
                callback.onFinished();
                images.get(0).image.close();
                return;
            }
        }
        /*else {
            if(alignAlgorithm == 0) {
                parameters.mapSize = new Point(1,1);
                parameters.gainMap = new float[]{1.f,1.f,1.f,1.f};
            }
        }*/

        IncreaseWLBL();

        PostPipeline pipeline = new PostPipeline();
        pipeline.lowFrame = lowexp;
        pipeline.highFrame = highexp;

        Bitmap img = pipeline.Run(result, PhotonCamera.getParameters());

        img = overlay(img, pipeline.debugData.toArray(new Bitmap[0]));
        try {
            processingEventsListener.onProcessingFinished("HdrX JPG Processing Finished");
        }
        catch (Exception e){
            Log.d(TAG,"Error in processingEventsListener.onProcessingFinished:"+Log.getStackTraceString(e));
        }

        //Saves the final bitmap
        boolean imageSaved = ImageSaver.Util.saveBitmapAsJPG(jpgFile, img,
                ImageSaver.JPG_QUALITY, exifData);

        try {
            processingEventsListener.notifyImageSavedStatus(imageSaved, jpgFile);
        }
        catch (Exception e){
            Log.d(TAG,"Error in processingEventsListener.notifyImageSavedStatus:"+Log.getStackTraceString(e));
        }

        pipeline.close();

        //if(saveRAW)
        images.get(0).image.close();
        callback.onFinished();
    }

}
