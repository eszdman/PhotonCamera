package com.eszdman.photoncamera.api;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.DngCreator;

import androidx.exifinterface.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import com.eszdman.photoncamera.ImageProcessing;
import com.eszdman.photoncamera.Parameters.FrameNumberSelector;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ImageSaver implements Runnable {
    private final String TAG = "ImageSaver";
    static int bcnt = 0;
    public static File outimg;

    /**
     * Image frame buffer
     */
    public static ArrayList<Image> imageBuffer = new ArrayList<>();
    public ImageProcessing processing(){
        return Interface.getImageProcessing();
    }
    public void done(ImageProcessing proc){
        //proc.Run();
        Interface.getImageProcessing().curimgs = imageBuffer;
        proc.Run();
        imageBuffer = new ArrayList<>();
        Log.d(TAG,"ImageSaver Done!");
        bcnt =0;
    }
    static String curName(){
        Date currentDate = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String dateText = dateFormat.format(currentDate);
        return "IMG_"+dateText;
    }
    static String curDir(){
        File dir;
        dir = new File(Environment.getExternalStorageDirectory()+"//DCIM//Camera//");
        if(Interface.getSettings().rawSaver) dir = new File(Environment.getExternalStorageDirectory()+"//DCIM//PhotonCamera//Raw//");
        if(!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return dir.getAbsolutePath();
    }
    private void end(ImageReader mReader){
        mReader.acquireLatestImage();
        try {
            for(int i = 0; i<mReader.getMaxImages();i++){
                Image cur = mReader.acquireNextImage();
                if(cur == null) break;
                cur.close();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        imageBuffer.clear();
        Interface.getCameraUI().burstUnlock();
    }
    public Handler ProcessCall;
    @Override
    public void run() {
        Log.d(TAG,"Thread Created");
        ProcessCall = new Handler(msg -> {
            Process((ImageReader) msg.obj);
            return true;
        });
    }
    public void Process(ImageReader mReader){
        Image mImage = mReader.acquireNextImage();
        int format = mImage.getFormat();
        FileOutputStream output = null;
        switch (format){
            case ImageFormat.JPEG: {
                ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                try {
                    imageBuffer.add(mImage);
                    bcnt++;
                    byte[] bytes = new byte[buffer.remaining()];
                    outimg =  new File(curDir(),curName()+".jpg");
                    if(imageBuffer.size() == FrameNumberSelector.frameCount && Interface.getSettings().frameCount != 1) {
                        //unlock();
                        output = new FileOutputStream(outimg);
                        buffer.duplicate().get(bytes);
                        output.write(bytes);
                        ExifInterface inter = new ExifInterface(outimg.getAbsolutePath());
                        ImageProcessing processing = processing();
                        processing.isyuv = false;
                        processing.israw = false;
                        processing.path = outimg.getAbsolutePath();
                        done(processing);
                        Thread.sleep(25);
                        inter.saveAttributes();
                        Photo.instance.SaveImg(outimg);
                        end(mReader);
                    }
                    if(Interface.getSettings().frameCount == 1){
                        imageBuffer = new ArrayList<>();
                        output = new FileOutputStream(outimg);
                        buffer.get(bytes);
                        output.write(bytes);
                        bcnt = 0;
                        mImage.close();
                        Interface.getCameraUI().burstUnlock();
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    //mImage.close();
                    if (null != output) {
                        try {
                            output.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                break;
            }
            case ImageFormat.YUV_420_888: {
                outimg = new File(curDir(), curName() + ".jpg");
                try {
                    Log.d(TAG, "start buffersize:" + imageBuffer.size());
                    imageBuffer.add(mImage);
                    if (imageBuffer.size() == FrameNumberSelector.frameCount && Interface.getSettings().frameCount != 1) {
                        //unlock();
                        ImageProcessing processing = processing();
                        processing.isyuv = true;
                        processing.israw = false;
                        processing.path = outimg.getAbsolutePath();
                        done(processing);
                        ExifInterface inter = ParseExif.Parse(CameraFragment.mCaptureResult, processing.path);
                        inter.saveAttributes();
                        Photo.instance.SaveImg(outimg);
                        end(mReader);
                    }
                    if (Interface.getSettings().frameCount == 1) {
                        imageBuffer = new ArrayList<>();
                        bcnt = 0;
                        Interface.getCameraUI().burstUnlock();
                    }
                    bcnt++;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
            //case ImageFormat.RAW10:
            case ImageFormat.RAW_SENSOR: {
                String ext = ".jpg";
                if(Interface.getSettings().rawSaver) ext = ".dng";
                outimg =  new File(curDir(),curName()+ext);
                String path = curDir()+curName()+ext;
                try {
                    Log.d(TAG,"start buffersize:"+imageBuffer.size());
                    if(Interface.getSettings().selectedMode == Settings.CameraMode.UNLIMITED){
                        ImageProcessing.UnlimitedCycle(mImage);
                        return;
                    }
                    imageBuffer.add(mImage);
                    if(imageBuffer.size() == FrameNumberSelector.frameCount && Interface.getSettings().frameCount != 1) {
                        //unlock();
                        ImageProcessing processing = processing();
                        processing.isyuv = false;
                        processing.israw = true;
                        processing.path = path;
                        done(processing);
                        ExifInterface inter = ParseExif.Parse(CameraFragment.mCaptureResult,outimg.getAbsolutePath());
                        if(!Interface.getSettings().rawSaver) inter.saveAttributes();
                        Photo.instance.SaveImg(outimg);
                        end(mReader);
                    }
                    if(Interface.getSettings().frameCount == 1) {
                        Log.d(TAG,"activearr:"+ CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
                        Log.d(TAG,"precorr:"+ CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE));
                        Log.d(TAG,"image:"+mImage.getCropRect());
                        DngCreator dngCreator = new DngCreator(CameraFragment.mCameraCharacteristics, CameraFragment.mCaptureResult);
                        output = new FileOutputStream(new File(curDir(),curName()+".dng"));
                        dngCreator.writeImage(output, mImage);
                        imageBuffer = new ArrayList<>();
                        mImage.close();
                        output.close();
                        Interface.getCameraUI().burstUnlock();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
            default: {
                Log.e(TAG, "Cannot save image, unexpected image format:" + format);
                break;
            }
        }
    }
}