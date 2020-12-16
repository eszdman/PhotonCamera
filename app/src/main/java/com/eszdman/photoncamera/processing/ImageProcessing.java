package com.eszdman.photoncamera.processing;


import android.graphics.Point;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import com.eszdman.photoncamera.Wrapper;
import com.eszdman.photoncamera.api.Camera2ApiAutoFix;
import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.api.ParseExif;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.eszdman.photoncamera.processing.opengl.rawpipeline.RawPipeline;
import com.eszdman.photoncamera.processing.opengl.scripts.AverageParams;
import com.eszdman.photoncamera.processing.opengl.scripts.AverageRaw;
import com.eszdman.photoncamera.processing.opengl.scripts.RawSensivity;
import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;
import com.eszdman.photoncamera.ui.camera.CameraFragment;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import static androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL;

public class ImageProcessing {
    private static final String TAG = "ImageProcessing";
    private final ProcessingEventsListener processingEventsListener;
    private Boolean isRaw;
    private Boolean isYuv;
    private String filePath;
    private ArrayList<Image> mImageFramesToProcess;
    public static float fakeWL = 65535.f;

    public ImageProcessing(ProcessingEventsListener processingEventsListener) {
        this.processingEventsListener = processingEventsListener;
    }

    /**
     * Applies Processing algorithms to Image
     * based on image type
     */
    public void Run() {
        try {
            Camera2ApiAutoFix.ApplyRes();
            processingEventsListener.onProcessingStarted("Multi Frames Processing Started");
            if (isRaw) {
                ApplyHdrX();
            }
//            if (isYuv) {
//                ApplyStabilization();
//            }
            processingEventsListener.onProcessingFinished((isRaw ? "HDRX" : isYuv ? "Stablization" : "") + " Processing Finished Successfully");
        } catch (Exception e) {
            Log.e(TAG, ProcessingEventsListener.FAILED_MSG);
            e.printStackTrace();
            processingEventsListener.onErrorOccurred(ProcessingEventsListener.FAILED_MSG);
        }
    }
    public static int unlimitedCounter = 1;
    public static boolean unlimitedEnd = false;
    private boolean lock = false;
    AverageRaw averageRaw;
    public void unlimitedCycle(Image input) {
        if(lock) {
            input.close();
            return;
        }
        int width = input.getPlanes()[0].getRowStride() / input.getPlanes()[0].getPixelStride();
        int height = input.getHeight();
        PhotonCamera.getParameters().rawSize = new android.graphics.Point(width, height);
        if(averageRaw == null) {
            PhotonCamera.getParameters().FillParameters(CameraFragment.mCaptureResult, CameraFragment.mCameraCharacteristics, PhotonCamera.getParameters().rawSize);
            averageRaw = new AverageRaw(PhotonCamera.getParameters().rawSize, "UnlimitedAvr");
        }
        averageRaw.additionalParams = new AverageParams(null, input.getPlanes()[0].getBuffer());
        averageRaw.Run();
        unlimitedCounter++;
        if(unlimitedEnd){
            unlimitedEnd = false;
            lock = true;
//        PhotonCamera.getParameters().path = ImageSaver.imageFileToSave.getAbsolutePath();
            unlimitedCounter = 0;
            processingEventsListener.onProcessingStarted("Unlimited Processing Started");
            averageRaw.FinalScript();
            ByteBuffer unlimitedBuffer = averageRaw.Output;
            averageRaw.close();
            averageRaw = null;
            if (PhotonCamera.getSettings().rawSaver) {
                input.getPlanes()[0].getBuffer().position(0);
                input.getPlanes()[0].getBuffer().put(unlimitedBuffer);
                saveRaw(input,(int)fakeWL);
                input.close();
                return;
            }
            PostPipeline pipeline = new PostPipeline();
            pipeline.Run(unlimitedBuffer, PhotonCamera.getParameters());
            pipeline.close();
            try {
                ExifInterface inter = ParseExif.Parse(CameraFragment.mCaptureResult, ImageSaver.imageFileToSave.getAbsolutePath());
                    try {
                        inter.saveAttributes();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            } catch (Exception e) {
                e.printStackTrace();
            }
            processingEventsListener.onProcessingFinished("Unlimited Processing Finished");
            processingEventsListener.onImageSaved(ImageSaver.imageFileToSave);
        }
        input.close();
    }

    public void unlimitedEnd() {
        unlimitedEnd = true;
    }
    public void unlimitedStart() {
        unlimitedEnd = false;
        lock = false;
    }

    //================================================Setters/Getters================================================

    public void setImageFramesToProcess(ArrayList<Image> mImageFramesToProcess) {
        this.mImageFramesToProcess = mImageFramesToProcess;
    }

    public void setRaw(Boolean raw) {
        isRaw = raw;
    }

    public void setYuv(Boolean yuv) {
        isYuv = yuv;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getFilePath() {
        return filePath;
    }

    //================================================Private Methods================================================

    private void ApplyHdrX() {
        boolean debugAlignment = false;
        if (PhotonCamera.getSettings().alignAlgorithm == 1) {
            debugAlignment = true;
        }
        CaptureResult res = CameraFragment.mCaptureResult;

//        processingstep();
        long startTime = System.currentTimeMillis();
        int width = mImageFramesToProcess.get(0).getPlanes()[0].getRowStride() / mImageFramesToProcess.get(0).getPlanes()[0].getPixelStride(); //mImageFramesToProcess.get(0).getWidth()*mImageFramesToProcess.get(0).getHeight()/(mImageFramesToProcess.get(0).getPlanes()[0].getRowStride()/mImageFramesToProcess.get(0).getPlanes()[0].getPixelStride());
        int height = mImageFramesToProcess.get(0).getHeight();
        Log.d(TAG, "APPLYHDRX: buffer:" + mImageFramesToProcess.get(0).getPlanes()[0].getBuffer().asShortBuffer().remaining());
        Log.d(TAG, "Api WhiteLevel:" + CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL));
        Object level = CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        int levell = 1023;
        if (level != null)
            levell = (int) level;
        float fakelevel = levell;//(float)Math.pow(2,15)-1.f;//bits raw
        //if(debugAlignment) fakelevel = 16384;
        float k = fakelevel / levell;
        if(PhotonCamera.getParameters().realWL == -1) PhotonCamera.getParameters().realWL = levell; else levell = PhotonCamera.getParameters().realWL;
        Log.d(TAG, "Api WhiteLevel:" + CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL));
        Log.d(TAG, "Api BlackLevel:" + CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN));
        PhotonCamera.getParameters().FillParameters(res, CameraFragment.mCameraCharacteristics, new android.graphics.Point(width, height));
        if (PhotonCamera.getParameters().realWL == -1) {
            PhotonCamera.getParameters().realWL = levell;
        }
        Log.d(TAG, "Wrapper.init");
        RawPipeline rawPipeline = new RawPipeline();
        ArrayList<ImageFrame> images = new ArrayList<>();
        ByteBuffer lowexp = null;
        ByteBuffer highexp = null;
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
            Log.d(TAG,"Sensivity:"+k);
            ImageFrame frame = new ImageFrame(byteBuffer);
            frame.luckyParameter = PhotonCamera.getCameraFragment().BurstShakiness.get(i);
            frame.image = mImageFramesToProcess.get(i);
            frame.pair = IsoExpoSelector.pairs.get(i%IsoExpoSelector.patternSize);
            images.add(frame);
        }
        if(mImageFramesToProcess.size() >= 3)
        images.sort((img1, img2) -> Long.compare(img1.luckyParameter, img2.luckyParameter));
        if(images.size() >= 4){
            int size = (int)((double)images.size()*0.7);
            for(int i =images.size(); i>size;i--){
                Log.d(TAG,"Removing unlucky:"+ images.get(images.size()-1).luckyParameter);
                images.remove(images.size()-1);
            }
            Log.d(TAG,"Size after removal:"+images.size());
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
        Object sensitivity = CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_SENSITIVITY);
        if (sensitivity == null) {
            sensitivity = (int) 100;
        }
        Object exposure = CameraFragment.mCaptureResult.get(CaptureResult.SENSOR_EXPOSURE_TIME);
        if (exposure == null) {
            exposure = (long)100;
        }
        float deghostlevel = (float) Math.sqrt(((int) sensitivity) * IsoExpoSelector.getMPY() - 50.) / 16.2f;
        deghostlevel = Math.min(0.25f, deghostlevel);
        Log.d(TAG, "Deghosting level:" + deghostlevel);
        ByteBuffer output;
        if (!debugAlignment) {
            float ghosting = fakeWL/levell;
            if(PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT) ghosting = 0.f;
            output = Wrapper.processFrame(ghosting,((float)(fakeWL))/levell);
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
        if (debugAlignment)
            rawPipeline.close();
        Log.d(TAG, "HDRX Alignment elapsed:" + (System.currentTimeMillis() - startTime) + " ms");
        if (PhotonCamera.getSettings().rawSaver) {
            if(debugAlignment) saveRaw(images.get(0).image, (int)fakeWL); else
            saveRaw(images.get(0).image, 0);
            return;
        }
        if(debugAlignment){
            for(int i =0; i<4;i++){
                PhotonCamera.getParameters().blackLevel[i]*=fakeWL/PhotonCamera.getParameters().whiteLevel;
            }
            PhotonCamera.getParameters().whiteLevel = (int)(fakeWL);
        }
        Log.d(TAG, "Wrapper.processFrame()");
//        PhotonCamera.getParameters().path = path;
        PostPipeline pipeline = new PostPipeline();
        pipeline.lowFrame = lowexp;
        pipeline.highFrame = highexp;
        pipeline.Run(images.get(0).image.getPlanes()[0].getBuffer(), PhotonCamera.getParameters());
        pipeline.close();
        images.get(0).image.close();
    }


    private void ProcessRaw(ByteBuffer input) {
        if (PhotonCamera.getSettings().rawSaver) {
            saveRaw(mImageFramesToProcess.get(0),0);
            return;
        }
        Log.d(TAG, "Wrapper.processFrame()");
//        PhotonCamera.getParameters().path = path;
        PostPipeline pipeline = new PostPipeline();
        pipeline.Run(mImageFramesToProcess.get(0).getPlanes()[0].getBuffer(), PhotonCamera.getParameters());
        pipeline.close();
        mImageFramesToProcess.get(0).close();
    }
    private void saveRaw(Image in,int patchWL) {
        if(patchWL != 0) {
            Camera2ApiAutoFix.WhiteLevel(CameraFragment.mCaptureResult, patchWL);
            Camera2ApiAutoFix.BlackLevel(CameraFragment.mCaptureResult, PhotonCamera.getParameters().blackLevel, (float) (patchWL) / PhotonCamera.getParameters().whiteLevel);
        }
        DngCreator dngCreator = new DngCreator(CameraFragment.mCameraCharacteristics, CameraFragment.mCaptureResult);
        try {
            FileOutputStream outB = new FileOutputStream(ImageSaver.imageFileToSave);
            dngCreator.setDescription(PhotonCamera.getParameters().toString());
            int rotation = PhotonCamera.getGravity().getCameraRotation();
            Log.d(TAG, "Gravity rotation:" + PhotonCamera.getGravity().getRotation());
            Log.d(TAG, "Sensor rotation:" + PhotonCamera.getCameraFragment().mSensorOrientation);
            int orientation = ORIENTATION_NORMAL;
            switch (rotation) {
                case 90:
                    orientation = ExifInterface.ORIENTATION_ROTATE_90;
                    break;
                case 180:
                    orientation = ExifInterface.ORIENTATION_ROTATE_180;
                    break;
                case 270:
                    orientation = ExifInterface.ORIENTATION_ROTATE_270;
                    break;
            }
            dngCreator.setOrientation(orientation);
            dngCreator.writeImage(outB, in);
            in.close();
            outB.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if(patchWL != 0) {
            Camera2ApiAutoFix.WhiteLevel(CameraFragment.mCaptureResult, PhotonCamera.getParameters().whiteLevel);
            Camera2ApiAutoFix.BlackLevel(CameraFragment.mCaptureResult, PhotonCamera.getParameters().blackLevel, 1.f);
        }
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