package com.particlesdevs.photoncamera.processing;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;

import androidx.exifinterface.media.ExifInterface;

import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.api.ParseExif;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.capture.CaptureController;
import com.particlesdevs.photoncamera.util.FileManager;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ImageSaver {
    /**
     * Image frame buffer
     */
    public static final int JPG_QUALITY = 97;
    private static final ArrayList<Image> IMAGE_BUFFER = new ArrayList<>();
    private static final String TAG = "ImageSaver";
    public static Path jpgFilePathToSave = null;//dummy file; not required

    private final ProcessingEventsListener processingEventsListener;
    private final UnlimitedProcessor mUnlimitedProcessor;
    private final HdrxProcessor hdrxProcessor;
    private ImageReader imageReader;
    private final ProcessorBase.ProcessingCallback processingCallback = new ProcessorBase.ProcessingCallback() {
        @Override
        public void onStarted() {
            CaptureController.isProcessing = true;
        }

        @Override
        public void onFailed() {
            onFinished();
        }

        @Override
        public void onFinished() {
            clearImageReader(imageReader);
            CaptureController.isProcessing = false;
        }
    };

    public ImageSaver(ProcessingEventsListener processingEventsListener) {
        this.processingEventsListener = processingEventsListener;
        this.hdrxProcessor = new HdrxProcessor(processingEventsListener);
        this.mUnlimitedProcessor = new UnlimitedProcessor(processingEventsListener);
    }

    public void initProcess(ImageReader mReader) {
        Log.v(TAG, "initProcess() : called from \"" + Thread.currentThread().getName() + "\" Thread");
        Image mImage = null;
        try {
            mImage = mReader.acquireNextImage();
        } catch (Exception ignored) {
            return;
        }
        if (mImage == null)
            return;
        int format = mImage.getFormat();
        imageReader = mReader;
        switch (format) {
            case ImageFormat.JPEG:
                addJPEG(mImage);
                break;

            case ImageFormat.YUV_420_888:
                addYUV(mImage);
                break;

            //case ImageFormat.RAW10:
            case ImageFormat.RAW_SENSOR:
                addRAW(mImage);
                break;

            default:
                Log.e(TAG, "Cannot save image, unexpected image format:" + format);
                break;
        }
    }

    private void addJPEG(Image image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        try {
            IMAGE_BUFFER.add(image);
            byte[] bytes = new byte[buffer.remaining()];
            if (IMAGE_BUFFER.size() == PhotonCamera.getCaptureController().mMeasuredFrameCnt && PhotonCamera.getSettings().frameCount != 1) {
                Path jpgPath = Util.newJPGFilePath();
                buffer.duplicate().get(bytes);
                Files.write(jpgPath, bytes);

//                hdrxProcessor.start(dngFile, jpgFile, IMAGE_BUFFER, mImage.getFormat(),
//                        CaptureController.mCameraCharacteristics, CaptureController.mCaptureResult,
//                        () -> clearImageReader(mReader));

                IMAGE_BUFFER.clear();
            }
            if (PhotonCamera.getSettings().frameCount == 1) {
                Path jpgPath = Util.newJPGFilePath();
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

    private void addYUV(Image image) {
        Log.d(TAG, "start buffersize:" + IMAGE_BUFFER.size());
        IMAGE_BUFFER.add(image);
        if (IMAGE_BUFFER.size() == PhotonCamera.getCaptureController().mMeasuredFrameCnt && PhotonCamera.getSettings().frameCount != 1) {

//            hdrxProcessor.start(dngFile, jpgFile, IMAGE_BUFFER, mImage.getFormat(),
//                        CaptureController.mCameraCharacteristics, CaptureController.mCaptureResult,
//                        () -> clearImageReader(mReader));

            IMAGE_BUFFER.clear();
        }
        if (PhotonCamera.getSettings().frameCount == 1) {
            IMAGE_BUFFER.clear();
            processingEventsListener.onProcessingFinished("YUV: Single Frame, Not Processed!");

        }
    }

    private void addRAW(Image image) {
        if (PhotonCamera.getSettings().selectedMode == CameraMode.UNLIMITED) {
            mUnlimitedProcessor.unlimitedCycle(image);
        } else {
            Log.d(TAG, "start buffer size:" + IMAGE_BUFFER.size());
            image.getFormat();
            IMAGE_BUFFER.add(image);
        }
    }

    public void runRaw(CameraCharacteristics characteristics, CaptureResult captureResult) {

        if (PhotonCamera.getSettings().frameCount == 1) {
            Path dngFile = Util.newDNGFilePath();
            boolean imageSaved = Util.saveSingleRaw(dngFile, IMAGE_BUFFER.get(0),
                    characteristics, captureResult);
            processingEventsListener.notifyImageSavedStatus(imageSaved, dngFile);
            processingEventsListener.onProcessingFinished("Saved Unprocessed RAW");
            IMAGE_BUFFER.clear();
            clearImageReader(imageReader);
            return;
        }
        Path dngFile = Util.newDNGFilePath();
        Path jpgFile = Util.newJPGFilePath();
        jpgFilePathToSave = jpgFile;
        //Remove broken images
            /*for(int i =0; i<IMAGE_BUFFER.size();i++){
                try{
                    IMAGE_BUFFER.get(i).getFormat();
                } catch (IllegalStateException e){
                    IMAGE_BUFFER.remove(i);
                    i--;
                    Log.d(TAG,"IMGBufferSize:"+IMAGE_BUFFER.size());
                    e.printStackTrace();
                }
            }*/
        hdrxProcessor.configure(
                PhotonCamera.getSettings().alignAlgorithm,
                PhotonCamera.getSettings().rawSaver,
                PhotonCamera.getSettings().selectedMode
        );
        hdrxProcessor.start(
                dngFile,
                jpgFile,
                IMAGE_BUFFER,
                imageReader.getImageFormat(),
                characteristics,
                captureResult,
                processingCallback
        );
        clearImageReader(imageReader);
        IMAGE_BUFFER.clear();
    }

    private void clearImageReader(ImageReader reader) {
        for (int i = 0; i < reader.getMaxImages(); i++) {
            try {
                Image cur = reader.acquireNextImage();
                if (cur == null) {
                    continue;
                }
                cur.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        PhotonCamera.getCaptureController().BurstShakiness.clear();
        //PhotonCamera.getCameraUI().unlockShutterButton();
    }

    public void unlimitedStart(CameraCharacteristics characteristics, CaptureResult captureResult) {

        Path dngFile = Util.newDNGFilePath();
        Path jpgFile = Util.newJPGFilePath();

        mUnlimitedProcessor.configure(PhotonCamera.getSettings().rawSaver);
        mUnlimitedProcessor.unlimitedStart(
                dngFile,
                jpgFile,
                characteristics,
                captureResult,
                processingCallback
        );
    }

    public void unlimitedEnd() {
        mUnlimitedProcessor.unlimitedEnd();
    }

    public static class Util {
        public static boolean saveBitmapAsJPG(Path fileToSave, Bitmap img, int jpgQuality, CaptureResult captureResult) {
            try {
                OutputStream outputStream = Files.newOutputStream(fileToSave);
                img.compress(Bitmap.CompressFormat.JPEG, jpgQuality, outputStream);
                outputStream.flush();
                outputStream.close();
                img.recycle();
                ExifInterface inter = ParseExif.Parse(captureResult, fileToSave.toFile());
                inter.saveAttributes();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        //Different method name just for clarity of usage
        public static boolean saveStackedRaw(Path dngFilePath,
                                             Image image,
                                             CameraCharacteristics characteristics,
                                             CaptureResult captureResult) {
            return saveSingleRaw(dngFilePath, image, characteristics, captureResult);
        }

        public static boolean saveSingleRaw(Path dngFilePath,
                                            Image image,
                                            CameraCharacteristics characteristics,
                                            CaptureResult captureResult) {

            Log.d(TAG, "activearr:" + characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
            Log.d(TAG, "precorr:" + characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE));
            Log.d(TAG, "image:" + image.getCropRect());
            DngCreator dngCreator =
                    new DngCreator(characteristics, captureResult)
                            .setDescription(PhotonCamera.getParameters().toString())
                            .setOrientation(ParseExif.getOrientation());
            try {
                OutputStream outputStream = Files.newOutputStream(dngFilePath);
                dngCreator.writeImage(outputStream, image);
//                image.close();
                outputStream.close();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }

        public static String generateNewFileName() {
            return "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        }

        public static Path newDNGFilePath() {
            return getNewImageFilePath("dng");
        }

        public static Path newJPGFilePath() {
            return getNewImageFilePath("jpg");
        }

        public static Path getNewImageFilePath(String extension) {
            File dir = FileManager.sDCIM_CAMERA;
            if (extension.equalsIgnoreCase("dng")) {
                dir = FileManager.sPHOTON_RAW_DIR;
            }
            return Paths.get(dir.getAbsolutePath(), generateNewFileName() + '.' + extension);
        }
    }
}