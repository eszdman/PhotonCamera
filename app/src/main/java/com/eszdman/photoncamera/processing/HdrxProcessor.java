package com.eszdman.photoncamera.processing;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.util.Log;
import com.eszdman.photoncamera.Wrapper;
import com.eszdman.photoncamera.api.Camera2ApiAutoFix;
import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.capture.CaptureController;
import com.eszdman.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.eszdman.photoncamera.processing.opengl.rawpipeline.RawPipeline;
import com.eszdman.photoncamera.processing.parameters.FrameNumberSelector;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;

public class HdrxProcessor extends ImageProcessorAbstract {
    private static final String TAG = "HdrxProcessor";
    private static final float FAKE_WL = 65535.f;
    private ArrayList<Image> mImageFramesToProcess;
    private int imageFormat;
    private CameraCharacteristics characteristics;
    private CaptureResult captureResult;


    protected HdrxProcessor(ProcessingEventsListener processingEventsListener) {
        super(processingEventsListener);
    }

    public void start(Path dngFile, Path jpgFile, ArrayList<Image> imageBuffer, int imageFormat,
                      CameraCharacteristics characteristics, CaptureResult captureResult,
                      ProcessingCallback callback) {
        this.jpgFile = jpgFile;
        this.dngFile = dngFile;
        this.imageFormat = imageFormat;
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
            processingEventsListener.onProcessingError(ProcessingEventsListener.FAILED_MSG);
        }
    }

    private void ApplyHdrX() {
        processingEventsListener.onProcessingStarted("HdrX Processing Started");

        boolean debugAlignment = (PhotonCamera.getSettings().alignAlgorithm == 1);

        Log.d(TAG, "ApplyHdrX() called from" + Thread.currentThread().getName());

        long startTime = System.currentTimeMillis();
        int width = mImageFramesToProcess.get(0).getPlanes()[0].getRowStride() /
                mImageFramesToProcess.get(0).getPlanes()[0].getPixelStride(); //mImageFramesToProcess.get(0).getWidth()*mImageFramesToProcess.get(0).getHeight()/(mImageFramesToProcess.get(0).getPlanes()[0].getRowStride()/mImageFramesToProcess.get(0).getPlanes()[0].getPixelStride());
        int height = mImageFramesToProcess.get(0).getHeight();
        Log.d(TAG, "APPLYHDRX: buffer:" + mImageFramesToProcess.get(0).getPlanes()[0].getBuffer().asShortBuffer().remaining());
        Log.d(TAG, "Api WhiteLevel:" + characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL));

        Object whiteLevel = characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);

        int levell = 1023;
        if (whiteLevel != null)
            levell = (int) whiteLevel;
        float fakelevel = levell;//(float)Math.pow(2,15)-1.f;//bits raw
        //if(debugAlignment) fakelevel = 16384;
        float k = fakelevel / levell;
        if (PhotonCamera.getParameters().realWL == -1) {
            PhotonCamera.getParameters().realWL = levell;
        } else {
            levell = PhotonCamera.getParameters().realWL;
        }
        Log.d(TAG, "Api WhiteLevel:" + characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL));
        Log.d(TAG, "Api BlackLevel:" + characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN));
        PhotonCamera.getParameters().FillParameters(captureResult, characteristics, new Point(width, height));
        if (PhotonCamera.getParameters().realWL == -1) {
            PhotonCamera.getParameters().realWL = levell;
        }
        Log.d(TAG, "Wrapper.init");
        RawPipeline rawPipeline = new RawPipeline();
        ArrayList<ImageFrame> images = new ArrayList<>();
        ByteBuffer lowexp = null;
        ByteBuffer highexp = null;
        long avr = PhotonCamera.getCaptureController().BurstShakiness.get(0);
        for (int i = 0; i < mImageFramesToProcess.size(); i++) {
            ByteBuffer byteBuffer;
            byteBuffer = mImageFramesToProcess.get(i).getPlanes()[0].getBuffer();
            if (i == 3 && IsoExpoSelector.HDR) {
                //rawPipeline.sensivity = k*0.7f;
                highexp = byteBuffer;
                continue;
            }
            if (i == 2 && IsoExpoSelector.HDR) {
                //rawPipeline.sensivity = k*6.0f;
                lowexp = byteBuffer;
                continue;
            }
            Log.d(TAG, "Sensivity:" + k);
            ImageFrame frame = new ImageFrame(byteBuffer);
            frame.luckyParameter = PhotonCamera.getCaptureController().BurstShakiness.get(i);
            frame.luckyParameter = (frame.luckyParameter + avr) / 2;
            avr = frame.luckyParameter;
            frame.image = mImageFramesToProcess.get(i);
            frame.pair = IsoExpoSelector.pairs.get(i % IsoExpoSelector.patternSize);
            frame.number = i;
            images.add(frame);
        }
        if (mImageFramesToProcess.size() >= 3)
            images.sort((img1, img2) -> Long.compare(img1.luckyParameter, img2.luckyParameter));
        double unluckypickiness = 1.05;
        long unluckyavr = 0;
        for (ImageFrame image : images) {
            unluckyavr += image.luckyParameter;
            Log.d(TAG, "unluckymap:" + image.luckyParameter + "n:" + image.number);
        }
        unluckyavr /= images.size();
        if (images.size() >= 4) {
            int size = (int) (images.size() - FrameNumberSelector.throwCount);
            Log.d(TAG, "ThrowCount:" + size);
            Log.d(TAG, "ImageCount:" + images.size());
            if (size == images.size()) size = (int) (images.size() * 0.75);
            for (int i = images.size(); i > size; i--) {
                long curunlucky = images.get(images.size() - 1).luckyParameter;
                if (curunlucky > unluckyavr * unluckypickiness) {
                    Log.d(TAG, "Removing unlucky:" + curunlucky + " number:" + images.get(images.size() - 1).number);
                    images.remove(images.size() - 1);
                }
            }
            Log.d(TAG, "Size after removal:" + images.size());
        }

        if (!debugAlignment) {
            Wrapper.init(width, height, images.size());
            for (int i = 0; i < images.size(); i++) {
                /*RawSensivity rawSensivity = new RawSensivity(new Point(width,height));
                rawSensivity.oldWhiteLevel = levell;
                rawSensivity.sensitivity = FAKE_WL/levell;
                rawSensivity.input = images.get(i).buffer;
                rawSensivity.Output = images.get(i).buffer;
                rawSensivity.Run();*/
                Wrapper.loadFrame(images.get(i).buffer);
                //rawSensivity.close();
            }
        }

        rawPipeline.imageObj = mImageFramesToProcess;
        rawPipeline.images = images;
        Log.d(TAG, "WhiteLevel:" + PhotonCamera.getParameters().whiteLevel);
        Log.d(TAG, "Wrapper.loadFrame");
        Object sensitivity = captureResult.get(CaptureResult.SENSOR_SENSITIVITY);
        if (sensitivity == null) {
            sensitivity = (int) 100;
        }
        Object exposure = captureResult.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        if (exposure == null) {
            exposure = (long) 100;
        }
        float deghostlevel = (float) Math.sqrt(((int) sensitivity) * IsoExpoSelector.getMPY() - 50.) / 16.2f;
        deghostlevel = Math.min(0.25f, deghostlevel);
        Log.d(TAG, "Deghosting level:" + deghostlevel);
        ByteBuffer output;
        if (!debugAlignment) {
            float ghosting = FAKE_WL / levell;
            if (PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT)
                ghosting = 0.f;
            output = Wrapper.processFrame(ghosting, ((float) (FAKE_WL)) / levell);
            debugAlignment = true;
        } else {
            output = rawPipeline.Run();
        }

       /*
        if (IsoExpoSelector.HDR) {
            Wrapper.init(width,height,2);
            RawSensivity rawSensivity = new RawSensivity(new android.graphics.Point(width,height),null);
            RawParams rawParams = new RawParams(res);
            rawParams.input = mImageFramesToProcess.get(0).getPlanes()[0].getBuffer();
            rawParams.sensivity = 0.7f;
            rawSensivity.additionalParams = rawParams;
            rawSensivity.Run();
            Wrapper.loadFrame(rawSensivity.Output);
            Wrapper.loadFrame(highexp);
            highexp = Wrapper.processFrame(0.9f+deghostlevel);

            Wrapper.init(width,height,2);
            rawSensivity = new RawSensivity(new android.graphics.Point(width,height),null);
            rawParams = new RawParams(res);
            rawParams.input = mImageFramesToProcess.get(0).getPlanes()[0].getBuffer();
            rawParams.sensivity = 6.0f;
            rawSensivity.Run();
            Wrapper.loadFrame(rawSensivity.Output);
            Wrapper.loadFrame(lowexp);
            lowexp = Wrapper.processFrame(0.9f+deghostlevel);
            rawSensivity.close();
        }
        */
        //Black shot fix
        images.get(0).image.getPlanes()[0].getBuffer().position(0);
        images.get(0).image.getPlanes()[0].getBuffer().put(output);
        images.get(0).image.getPlanes()[0].getBuffer().position(0);
        for (int i = 1; i < images.size(); i++) {
            if ((i == 3 || i == 2) && IsoExpoSelector.HDR)
                continue;
            images.get(i).image.close();
        }
        if (debugAlignment) {
            rawPipeline.close();
        }
        Log.d(TAG, "HDRX Alignment elapsed:" + (System.currentTimeMillis() - startTime) + " ms");

        if (PhotonCamera.getSettings().rawSaver) {

            boolean imageSaved = ImageSaver.Util.saveStackedRaw(dngFile, images.get(0).image,
                    debugAlignment ? (int) FAKE_WL : 0);

            processingEventsListener.notifyImageSavedStatus(imageSaved, dngFile);

        }

        if (debugAlignment) {
            for (int i = 0; i < 4; i++) {
                PhotonCamera.getParameters().blackLevel[i] *= FAKE_WL / PhotonCamera.getParameters().whiteLevel;
            }
            PhotonCamera.getParameters().whiteLevel = (int) (FAKE_WL);
        }
        Log.d(TAG, "Wrapper.processFrame()");
//        PhotonCamera.getParameters().path = path;
        PostPipeline pipeline = new PostPipeline();
        pipeline.lowFrame = lowexp;
        pipeline.highFrame = highexp;

        Bitmap img = pipeline.Run(images.get(0).image.getPlanes()[0].getBuffer(), PhotonCamera.getParameters());

        processingEventsListener.onProcessingFinished("HdrX JPG Processing Finished");

        //Saves the final bitmap
        boolean imageSaved = ImageSaver.Util.saveBitmapAsJPG(jpgFile, img,
                ImageSaver.JPG_QUALITY, captureResult);

        processingEventsListener.notifyImageSavedStatus(imageSaved, jpgFile);

        pipeline.close();
        images.get(0).image.close();

        callback.onFinished();
    }

    //================================================Private Methods================================================

/*
    private void ProcessRaw(ByteBuffer input) {
        if (PhotonCamera.getSettings().rawSaver) {
            Path dngFile = ImageSaver.Util.getNewDNGFilePath();
            boolean saved = ImageSaver.Util.saveStackedRaw(dngFile, mImageFramesToProcess.get(0), 0);
            processingEventsListener.notifyImageSavedStatus(saved, dngFile);
            return;
        }
        Log.d(TAG, "Wrapper.processFrame()");
//        PhotonCamera.getParameters().path = path;
        PostPipeline pipeline = new PostPipeline();
        Bitmap bitmap = pipeline.Run(mImageFramesToProcess.get(0).getPlanes()[0].getBuffer(), PhotonCamera.getParameters());
        Path jpgFile = ImageSaver.Util.getNewJPGFilePath();
        boolean imageSaved = ImageSaver.Util.saveBitmapAsJPG(jpgFile, bitmap,
                ImageSaver.JPG_QUALITY, CaptureController.mCaptureResult);

        processingEventsListener.notifyImageSavedStatus(imageSaved, jpgFile);

        pipeline.close();
        mImageFramesToProcess.get(0).close();
    }
*/


    //final ORB orb = ORB.create();
    //final DescriptorMatcher matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
    /*Mat findFrameHomography(Mat need, Mat from) {
        Mat descriptors1 = new Mat(), descriptors2 = new Mat();
        MatOfKeyPoint keyPoints1 = new MatOfKeyPoint();
        MatOfKeyPoint keyPoints2 = new MatOfKeyPoint();
        orb.detectAndCompute(need, new Mat(), keyPoints1, descriptors1);
        orb.detectAndCompute(from, new Mat(), keyPoints2, descriptors2);
        MatOfDMatch matches = new MatOfDMatch();
        matcher.match(descriptors1, descriptors2, matches, new Mat());
        MatOfPoint2f points1 = new MatOfPoint2f(), points2 = new MatOfPoint2f();
        DMatch[] arr = matches.toArray();
        List<KeyPoint> keypoints1 = keyPoints1.toList();
        List<KeyPoint> keypoints2 = keyPoints2.toList();
        ArrayList<Point> keypoints1f = new ArrayList<Point>();
        ArrayList<Point> keypoints2f = new ArrayList<Point>();
        for (DMatch dMatch : arr) {
            Point on1 = keypoints1.get(dMatch.queryIdx).pt;
            Point on2 = keypoints2.get(dMatch.trainIdx).pt;
            if (dMatch.distance < 50) {
                keypoints1f.add(on1);
                keypoints2f.add(on2);
            }
        }
        points1.fromArray(keypoints1f.toArray(new Point[0]));
        points2.fromArray(keypoints2f.toArray(new Point[0]));
        Mat h = null;
        if (!points1.empty() && !points2.empty()) h = findHomography(points2, points1, RANSAC);
        keyPoints1.release();
        keyPoints2.release();

        return h;
    }*/




    /*void processingstep() {
        processingEventsListener.onProcessingChanged(null);
    }*/

}
