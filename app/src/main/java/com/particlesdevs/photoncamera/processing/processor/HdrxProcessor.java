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
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.ImageSaver;
import com.particlesdevs.photoncamera.processing.ProcessingEventsListener;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.particlesdevs.photoncamera.processing.opengl.rawpipeline.RawPipeline;
import com.particlesdevs.photoncamera.processing.opengl.scripts.BinnedRaw;
import com.particlesdevs.photoncamera.processing.opengl.scripts.InterpolateGainMap;
import com.particlesdevs.photoncamera.processing.parameters.FrameNumberSelector;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.processing.rs.Align;

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
    private ArrayList<Float> BurstShakiness;


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
                      ArrayList<Float> BurstShakiness,
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
        PhotonCamera.getParameters().FillConstParameters(characteristics, new Point(width, height));
        PhotonCamera.getParameters().FillDynamicParameters(captureResult);
        PhotonCamera.getParameters().cameraRotation = cameraRotation;

        exifData.IMAGE_DESCRIPTION = PhotonCamera.getParameters().toString();

        Log.d(TAG, "Wrapper.init");
        RawPipeline rawPipeline = new RawPipeline();
        ArrayList<ImageFrame> images = new ArrayList<>();
        ByteBuffer lowexp = null;
        ByteBuffer highexp = null;
        float avr = BurstShakiness.get(0);
        for (int i = 0; i < mImageFramesToProcess.size(); i++) {
            ByteBuffer byteBuffer;
            byteBuffer = mImageFramesToProcess.get(i).getPlanes()[0].getBuffer();
            ImageFrame frame = new ImageFrame(byteBuffer);
            frame.luckyParameter = BurstShakiness.get(i);
            frame.luckyParameter = (frame.luckyParameter + avr) / 2;
            avr = frame.luckyParameter;
            frame.image = mImageFramesToProcess.get(i);
            //frame.pair = IsoExpoSelector.pairs.get(i % IsoExpoSelector.patternSize);
            frame.pair = IsoExpoSelector.fullpairs.get(i);
            frame.number = i;
            images.add(frame);
        }
        if (mImageFramesToProcess.size() >= 3)
            images.sort((img1, img2) -> Float.compare(img1.luckyParameter, img2.luckyParameter));
        double unluckypickiness = 1.05;
        float unluckyavr = 0;
        for (ImageFrame image : images) {
            unluckyavr += image.luckyParameter;
            Log.d(TAG, "unlucky map:" + image.luckyParameter + "n:" + image.number);
        }
        unluckyavr /= images.size();
        if (images.size() >= 4) {
            int size = (int) (images.size() - FrameNumberSelector.throwCount);
            Log.d(TAG, "Throw Count:" + size);
            Log.d(TAG, "Image Count:" + images.size());
            if (size == images.size()) size = (int) (images.size() * 0.75);
            for (int i = images.size(); i > size; i--) {
                float curunlucky = images.get(images.size() - 1).luckyParameter;
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
                Wrapper.loadFrame(images.get(i).buffer, ((FAKE_WL) / PhotonCamera.getParameters().whiteLevel) * mpy);
            }
        }
        rawPipeline.imageObj = mImageFramesToProcess;
        rawPipeline.images = images;
        Log.d(TAG, "White Level:" + PhotonCamera.getParameters().whiteLevel);
        Log.d(TAG, "Wrapper.loadFrame");
        float denoiseLevel = (float) Math.sqrt((CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY)) * IsoExpoSelector.getMPY() - 40.)*6400.f / (6.2f*IsoExpoSelector.getISOAnalog());
        Log.d(TAG, "Denoising level:" + denoiseLevel);

        ByteBuffer output = null;
        Parameters parameters = PhotonCamera.getParameters();
        //InterpolateGainMap interpolateGainMap;
        if (!debugAlignment) {
            /*interpolateGainMap = new InterpolateGainMap(new Point(width, height));
            interpolateGainMap.parameters = PhotonCamera.getParameters();
            interpolateGainMap.Run();
            interpolateGainMap.close();
            Wrapper.loadInterpolatedGainMap(interpolateGainMap.Output);*/

            output = Wrapper.processFrame(35*(0.6f+denoiseLevel)/2.f, 150*denoiseLevel, 512,0.f, 0.f, 0.f, parameters.whiteLevel
                    ,parameters.whitePoint[0], parameters.whitePoint[1], parameters.whitePoint[2], parameters.cfaPattern);
        } else {
            switch (alignAlgorithm){
                case 1:{
                    rawPipeline.alignAlgorithm = alignAlgorithm;
                    output = rawPipeline.Run();
                    break;
                }
                case 2:{
                    Log.d(TAG,"Entering hybrid alignment");
                    Align alignrs = new Align(new Point(width,height),images.get(0).buffer);
                    alignrs.AlignFrame(images.get(1).buffer);

                    rawPipeline.alignAlgorithm = alignAlgorithm;
                    output = rawPipeline.Run();
                    break;
                }
            }

        }
        float[] oldBL = parameters.blackLevel.clone();

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
            parameters.blackLevel[0] = oldBL[0];
            parameters.blackLevel[1] = oldBL[1];
            parameters.blackLevel[2] = oldBL[2];
            parameters.blackLevel[3] = oldBL[3];
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

    //================================================Private Methods================================================

/*
    private void ProcessRaw(ByteBuffer input) {
        if (PhotonCamera.getSettings().rawSaver) {
            Path dngFile = ImageSaver.Util.newDNGFilePath();
            boolean saved = ImageSaver.Util.saveStackedRaw(dngFile, mImageFramesToProcess.get(0), 0);
            processingEventsListener.notifyImageSavedStatus(saved, dngFile);
            return;
        }
        Log.d(TAG, "Wrapper.processFrame()");
//        PhotonCamera.getParameters().path = path;
        PostPipeline pipeline = new PostPipeline();
        Bitmap bitmap = pipeline.Run(mImageFramesToProcess.get(0).getPlanes()[0].getBuffer(), PhotonCamera.getParameters());
        Path jpgFile = ImageSaver.Util.newJPGFilePath();
        boolean imageSaved = ImageSaver.Util.saveBitmapAsJPG(jpgFile, bitmap,
                ImageSaver.JPG_QUALITY, CaptureController.mCaptureResult);

        processingEventsListener.notifyImageSavedStatus(imageSaved, jpgFile);

        pipeline.close();
        mImageFramesToProcess.get(0).close();
    }
*/


    /*final ORB orb = ORB.create();
    final DescriptorMatcher matcher = DescriptorMatcher.createBFMatcher(org.bytedeco.opencv.opencv_features2d.DescriptorMatcher.BRUTEFORCE_HAMMING);
    Mat findFrameHomography(GpuMat need, GpuMat from) {
        GpuMat descriptors1 = new GpuMat(), descriptors2 = new GpuMat();
        KeyPointVector keyPoints1 = new KeyPointVector();
        KeyPointVector keyPoints2 = new KeyPointVector();
        orb.detectAndCompute(need, new GpuMat(), keyPoints1, descriptors1);
        orb.detectAndCompute(from, new GpuMat(), keyPoints2, descriptors2);
        DMatchVector matches = new DMatchVector();
        matcher.match(descriptors1, descriptors2, matches, new GpuMat());
        MatOfPoint2f points1 = new MatOfPoint2f(), points2 = new MatOfPoint2f();
        DMatch[] arr = matches.get();
        ArrayList<org.opencv.core.Point> keypoints1f = new ArrayList<>();
        ArrayList<org.opencv.core.Point> keypoints2f = new ArrayList<>();
        for (DMatch dMatch : arr) {
            Point2f on1 = keyPoints1.get(dMatch.queryIdx()).pt();
            Point2f on2 = keyPoints2.get(dMatch.trainIdx()).pt();
            if (dMatch.distance() < 50) {
                keypoints1f.add(new org.opencv.core.Point(on1.x(),(int)on1.y()));
                keypoints2f.add(new org.opencv.core.Point((int)on2.x(),(int)on2.y()));
            }
        }
        points1.fromArray(keypoints1f.toArray(new org.opencv.core.Point[0]));
        points2.fromArray(keypoints2f.toArray(new org.opencv.core.Point[0]));
        Mat h = null;
        if (!points1.empty() && !points2.empty()) h = findHomography(points2, points1, RANSAC,3.0);
        keyPoints1.clear();
        keyPoints2.clear();

        return h;
    }*/




    /*void processingstep() {
        processingEventsListener.onProcessingChanged(null);
    }*/

}
