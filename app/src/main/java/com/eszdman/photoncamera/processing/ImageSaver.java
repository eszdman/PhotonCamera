package com.eszdman.photoncamera.processing;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.media.Image;
import android.media.ImageReader;
import android.util.Log;
import androidx.exifinterface.media.ExifInterface;
import com.eszdman.photoncamera.api.CameraMode;
import com.eszdman.photoncamera.api.ParseExif;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.capture.CaptureController;
import com.eszdman.photoncamera.processing.parameters.FrameNumberSelector;
import com.eszdman.photoncamera.util.FileManager;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
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

    public ImageSaver(ProcessingEventsListener processingEventsListener) {
        this.processingEventsListener = processingEventsListener;
        this.hdrxProcessor = new HdrxProcessor(processingEventsListener);
        this.mUnlimitedProcessor = new UnlimitedProcessor(processingEventsListener);
    }

    public int getImageBufferSize() {
        return IMAGE_BUFFER.size();
    }

    public void initProcess(ImageReader mReader) {
        Log.v(TAG, "initProcess() : called from \"" + Thread.currentThread().getName() + "\" Thread");
        Image mImage = null;
        try {
            mImage = mReader.acquireNextImage();
        } catch (Exception e) {
            mReader.close();
        }
        if (mImage == null)
            return;
        int format = mImage.getFormat();

        switch (format) {
            case ImageFormat.JPEG:
                saveJPEG(mImage, mReader);
                break;

            case ImageFormat.YUV_420_888:
                saveYUV(mImage, mReader);
                break;

            //case ImageFormat.RAW10:
            case ImageFormat.RAW_SENSOR:
                saveRAW(mImage, mReader);
                break;

            default:
                Log.e(TAG, "Cannot save image, unexpected image format:" + format);
                break;
        }
    }

    private void saveJPEG(Image mImage, ImageReader mReader) {
        ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
        try {
            IMAGE_BUFFER.add(mImage);
            byte[] bytes = new byte[buffer.remaining()];
            if (IMAGE_BUFFER.size() == FrameNumberSelector.frameCount && PhotonCamera.getSettings().frameCount != 1) {
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
                mImage.close();
                processingEventsListener.onProcessingFinished("JPEG: Single Frame, Not Processed!");
                processingEventsListener.notifyImageSavedStatus(true, jpgPath);
            }
        } catch (IOException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    private void saveYUV(Image mImage, ImageReader mReader) {
        Log.d(TAG, "start buffersize:" + IMAGE_BUFFER.size());
        IMAGE_BUFFER.add(mImage);
        if (IMAGE_BUFFER.size() == FrameNumberSelector.frameCount && PhotonCamera.getSettings().frameCount != 1) {

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

    private void saveRAW(Image mImage, ImageReader mReader) {
        if (PhotonCamera.getSettings().selectedMode == CameraMode.UNLIMITED) {
            mUnlimitedProcessor.unlimitedCycle(mImage);
        } else {
            Log.d(TAG, "start buffer size:" + IMAGE_BUFFER.size());
            IMAGE_BUFFER.add(mImage);
            if (IMAGE_BUFFER.size() == FrameNumberSelector.frameCount && PhotonCamera.getSettings().frameCount != 1) {
                Path dngFile = Util.newDNGFilePath();
                Path jpgFile = Util.newJPGFilePath();
                hdrxProcessor.start(dngFile, jpgFile, IMAGE_BUFFER, mImage.getFormat(),
                        CaptureController.mCameraCharacteristics, CaptureController.mCaptureResult,
                        () -> clearImageReader(mReader));
                IMAGE_BUFFER.clear();
            }
            if (PhotonCamera.getSettings().frameCount == 1) {
                Path dngFile = Util.newDNGFilePath();

                boolean imageSaved = Util.saveSingleRaw(dngFile, mImage,
                        CaptureController.mCameraCharacteristics, CaptureController.mCaptureResult);

                mImage.close();

                processingEventsListener.notifyImageSavedStatus(imageSaved, dngFile);

                processingEventsListener.onProcessingFinished("Saved Unprocessed RAW");

                IMAGE_BUFFER.clear();
            }
        }
    }

    private void clearImageReader(ImageReader reader) {
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

    public void unlimitedStart() {
        Path dngFile = Util.newDNGFilePath();
        Path jpgFile = Util.newJPGFilePath();
        mUnlimitedProcessor.unlimitedStart(dngFile, jpgFile,
                CaptureController.mCameraCharacteristics, CaptureController.mCaptureResult);
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
            DngCreator dngCreator = new DngCreator(characteristics, captureResult);
            try {
                OutputStream outputStream = Files.newOutputStream(dngFilePath);

                dngCreator.setDescription(PhotonCamera.getParameters().toString());
                dngCreator.setOrientation(ParseExif.getOrientation());
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
            Date currentDate = new Date();
            DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String dateText = dateFormat.format(currentDate);
            return "IMG_" + dateText;
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