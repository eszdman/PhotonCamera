package com.eszdman.photoncamera.processing;


import android.graphics.Bitmap;
import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;
import com.eszdman.photoncamera.Wrapper;
import com.eszdman.photoncamera.api.Camera2ApiAutoFix;
import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.capture.CaptureController;
import com.eszdman.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.eszdman.photoncamera.processing.opengl.rawpipeline.RawPipeline;
import com.eszdman.photoncamera.processing.opengl.scripts.AverageParams;
import com.eszdman.photoncamera.processing.opengl.scripts.AverageRaw;
import com.eszdman.photoncamera.processing.parameters.FrameNumberSelector;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;


public class ImageProcessing {
    private static final String TAG = "ImageProcessing";
    public static float fakeWL = 65535.f;
    public static int unlimitedCounter = 1;
    public static boolean unlimitedEnd = false;
    private final ProcessingEventsListener processingEventsListener;
    AverageRaw averageRaw;
    private Path filePath;
    private int imageFormat;
    private ArrayList<Image> mImageFramesToProcess;
    private boolean lock = false;

    public ImageProcessing(ProcessingEventsListener processingEventsListener) {
        this.processingEventsListener = processingEventsListener;
    }

    public void start(Path fileToSave, ArrayList<Image> imageBuffer, int imageFormat) {
        this.imageFormat = imageFormat;
        this.filePath = fileToSave;
        this.mImageFramesToProcess = imageBuffer;
        Run();
    }

    public void end(ImageReader reader) {
        try {
            for (int i = 0; i < reader.getMaxImages(); i++) {
                Image cur = reader.acquireNextImage();
                if (cur == null) {
                    continue;
                }
                cur.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        PhotonCamera.getCaptureController().BurstShakiness.clear();
        //PhotonCamera.getCameraUI().unlockShutterButton();
    }

    /**
     * Applies Processing algorithms to Image
     * based on image type
     */
    public void Run() {
        try {
            Camera2ApiAutoFix.ApplyRes();
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
//        PhotonCamera.getParameters().path = ImageSaver.imageFilePathToSave.getAbsolutePath();
        processingEventsListener.onProcessingStarted("Unlimited Processing Started");

        averageRaw.FinalScript();
        ByteBuffer unlimitedBuffer = averageRaw.Output;
        averageRaw.close();
        averageRaw = null;

        if (PhotonCamera.getSettings().rawSaver) {
            image.getPlanes()[0].getBuffer().position(0);
            image.getPlanes()[0].getBuffer().put(unlimitedBuffer);

            processingEventsListener.onProcessingFinished("Unlimited rawSaver Processing Finished");

            boolean imageSaved = ImageSaver.Util.saveStackedRaw(ImageSaver.imageFilePathToSave, image, (int) fakeWL);

            processingEventsListener.notifyImageSavedStatus(imageSaved, ImageSaver.imageFilePathToSave);
            return;
        }

        PostPipeline pipeline = new PostPipeline();
        Bitmap bitmap = pipeline.Run(unlimitedBuffer, PhotonCamera.getParameters());

        processingEventsListener.onProcessingFinished("Unlimited JPG Processing Finished");

        boolean imageSaved = ImageSaver.Util.saveBitmapAsJPG(ImageSaver.imageFilePathToSave, bitmap,
                ImageSaver.JPG_QUALITY, CaptureController.mCaptureResult);

        processingEventsListener.notifyImageSavedStatus(imageSaved, ImageSaver.imageFilePathToSave);

        pipeline.close();

    }

    public void unlimitedEnd() {
        unlimitedEnd = true;
    }

    public void unlimitedStart() {
        unlimitedEnd = false;
        lock = false;
    }

    //================================================Private Methods================================================

    private void ApplyHdrX() {
        processingEventsListener.onProcessingStarted("HdrX Processing Started");

        boolean debugAlignment = (PhotonCamera.getSettings().alignAlgorithm == 1);

        Log.d(TAG, "ApplyHdrX() called from" + Thread.currentThread().getName());
        CaptureResult res = CaptureController.mCaptureResult;

        long startTime = System.currentTimeMillis();
        int width = mImageFramesToProcess.get(0).getPlanes()[0].getRowStride() /
                mImageFramesToProcess.get(0).getPlanes()[0].getPixelStride(); //mImageFramesToProcess.get(0).getWidth()*mImageFramesToProcess.get(0).getHeight()/(mImageFramesToProcess.get(0).getPlanes()[0].getRowStride()/mImageFramesToProcess.get(0).getPlanes()[0].getPixelStride());
        int height = mImageFramesToProcess.get(0).getHeight();
        Log.d(TAG, "APPLYHDRX: buffer:" + mImageFramesToProcess.get(0).getPlanes()[0].getBuffer().asShortBuffer().remaining());
        Log.d(TAG, "Api WhiteLevel:" + CaptureController.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL));

        Object whiteLevel = CaptureController.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);

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
        Log.d(TAG, "Api WhiteLevel:" + CaptureController.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL));
        Log.d(TAG, "Api BlackLevel:" + CaptureController.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN));
        PhotonCamera.getParameters().FillParameters(res, CaptureController.mCameraCharacteristics, new android.graphics.Point(width, height));
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
                rawSensivity.sensitivity = fakeWL/levell;
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
        Object sensitivity = CaptureController.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY);
        if (sensitivity == null) {
            sensitivity = (int) 100;
        }
        Object exposure = CaptureController.mCaptureResult.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        if (exposure == null) {
            exposure = (long) 100;
        }
        float deghostlevel = (float) Math.sqrt(((int) sensitivity) * IsoExpoSelector.getMPY() - 50.) / 16.2f;
        deghostlevel = Math.min(0.25f, deghostlevel);
        Log.d(TAG, "Deghosting level:" + deghostlevel);
        ByteBuffer output;
        if (!debugAlignment) {
            float ghosting = fakeWL / levell;
            if (PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT) ghosting = 0.f;
            output = Wrapper.processFrame(ghosting, ((float) (fakeWL)) / levell);
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
            processingEventsListener.onProcessingFinished("HdrX RawSaver Processing Finished");

            boolean imageSaved;

            imageSaved = ImageSaver.Util.saveStackedRaw(filePath, images.get(0).image, debugAlignment ? (int) fakeWL : 0);

            processingEventsListener.notifyImageSavedStatus(imageSaved, filePath);

            return;
        }

        if (debugAlignment) {
            for (int i = 0; i < 4; i++) {
                PhotonCamera.getParameters().blackLevel[i] *= fakeWL / PhotonCamera.getParameters().whiteLevel;
            }
            PhotonCamera.getParameters().whiteLevel = (int) (fakeWL);
        }
        Log.d(TAG, "Wrapper.processFrame()");
//        PhotonCamera.getParameters().path = path;
        PostPipeline pipeline = new PostPipeline();
        pipeline.lowFrame = lowexp;
        pipeline.highFrame = highexp;

        Bitmap img = pipeline.Run(images.get(0).image.getPlanes()[0].getBuffer(), PhotonCamera.getParameters());

        processingEventsListener.onProcessingFinished("HdrX JPG Processing Finished");

        //Saves the final bitmap
        boolean imageSaved = ImageSaver.Util.saveBitmapAsJPG(filePath, img,
                ImageSaver.JPG_QUALITY, CaptureController.mCaptureResult);

        processingEventsListener.notifyImageSavedStatus(imageSaved, filePath);

        pipeline.close();
        images.get(0).image.close();
    }


    private void ProcessRaw(ByteBuffer input) {
        if (PhotonCamera.getSettings().rawSaver) {
            boolean saved = ImageSaver.Util.saveStackedRaw(ImageSaver.imageFilePathToSave, mImageFramesToProcess.get(0), 0);
            processingEventsListener.notifyImageSavedStatus(saved, ImageSaver.imageFilePathToSave);
            return;
        }
        Log.d(TAG, "Wrapper.processFrame()");
//        PhotonCamera.getParameters().path = path;
        PostPipeline pipeline = new PostPipeline();
        Bitmap bitmap = pipeline.Run(mImageFramesToProcess.get(0).getPlanes()[0].getBuffer(), PhotonCamera.getParameters());

        boolean imageSaved = ImageSaver.Util.saveBitmapAsJPG(filePath, bitmap,
                ImageSaver.JPG_QUALITY, CaptureController.mCaptureResult);

        processingEventsListener.notifyImageSavedStatus(imageSaved, filePath);

        pipeline.close();
        mImageFramesToProcess.get(0).close();
    }


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