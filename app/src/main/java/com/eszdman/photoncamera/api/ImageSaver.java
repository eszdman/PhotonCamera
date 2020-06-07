package com.eszdman.photoncamera.api;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.DngCreator;
import androidx.exifinterface.media.ExifInterface;
import android.media.Image;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.se.omapi.SEService;
import android.util.Log;
import com.eszdman.photoncamera.ui.CameraFragment;
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

import static android.content.ContentValues.TAG;

public class ImageSaver implements Runnable {

    /**
     * The Stream image
     */
    private final Image mImage;

    static int bcnt = 0;

    /**
     * Image frame buffer
     */
    public static ArrayList<Image> imageBuffer = new ArrayList<>();
    public ImageSaver(Image image) {
        mImage = image;
    }
    public ImageProcessing processing(){
        return Interface.i.processing;
    }
    public void done(ImageProcessing proc){
        //proc.Run();
        Interface.i.processing.curimgs = imageBuffer;
        proc.Run();
        for(int i =0; i<imageBuffer.size();i++){
            //imageBuffer.get(i).close();
        }
        imageBuffer = new ArrayList<>();
        Log.e("ImageSaver","ImageSaver Done!");
        bcnt =0;
    }
    static String curName(){
        Date currentDate = new Date();
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
        String dateText = dateFormat.format(currentDate);
        return "IMG_"+dateText;
    }
    static String curDir(){
        File dir = new File(Environment.getExternalStorageDirectory()+"//DCIM//Camera//");
        if(!dir.exists()) dir.mkdirs();
        return dir.getAbsolutePath();
    }
    private static void end(){
       imageBuffer.clear();
        CameraFragment.context.shot.setActivated(true);
        CameraFragment.context.shot.setClickable(true);
    }
    @Override
    public void run() {
        int format = mImage.getFormat();
        FileOutputStream output = null;
        switch (format){
            case ImageFormat.JPEG: {

                ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                try {
                    imageBuffer.add(mImage);
                    bcnt++;
                    byte[] bytes = new byte[buffer.remaining()];
                    File out =  new File(curDir(),curName()+".jpg");
                    if(bcnt == FrameNumberSelector.frameCount && FrameNumberSelector.frameCount != 1) {
                        output = new FileOutputStream(out);
                        buffer.duplicate().get(bytes);
                        output.write(bytes);
                        ExifInterface inter = new ExifInterface(out.getAbsolutePath());
                        ImageProcessing processing = processing();
                        processing.isyuv = false;
                        processing.israw = false;
                        processing.path = out.getAbsolutePath();
                        done(processing);
                        Thread.sleep(25);
                        inter.saveAttributes();
                        Photo.instance.SaveImg(out);
                        end();
                    }
                    if(Interface.i.settings.frameCount == 1){
                        imageBuffer = new ArrayList<>();
                        output = new FileOutputStream(out);
                        buffer.get(bytes);
                        output.write(bytes);
                        bcnt = 0;
                        mImage.close();
                        CameraFragment.context.shot.setActivated(true);
                        CameraFragment.context.shot.setClickable(true);
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

                File out = new File(curDir(), curName() + ".jpg");
                ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                try {
                    Log.d(TAG, "start buffersize:" + imageBuffer.size());
                    imageBuffer.add(mImage);
                    if(imageBuffer.size() > Interface.i.settings.frameCount){
                        imageBuffer.get(imageBuffer.size()-1).close();
                    } else
                    if (imageBuffer.size() == FrameNumberSelector.frameCount && Interface.i.settings.frameCount != 1) {
                        ImageProcessing processing = processing();
                        processing.isyuv = true;
                        processing.israw = false;
                        processing.path = out.getAbsolutePath();
                        done(processing);
                        Thread.sleep(25);
                        ExifInterface inter = ParseExif.Parse(CameraFragment.mCaptureResult, processing.path);
                        inter.saveAttributes();
                        Photo.instance.SaveImg(out);
                        end();
                    }
                    if (Interface.i.settings.frameCount == 1) {
                        imageBuffer = new ArrayList<>();
                        bcnt = 0;
                        CameraFragment.context.shot.setActivated(true);
                        CameraFragment.context.shot.setClickable(true);
                    }
                    bcnt++;
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                } finally {

                }
                break;
            }
            case ImageFormat.RAW10:
            case ImageFormat.RAW_SENSOR: {
                String ext = ".jpg";
                if(Interface.i.settings.rawSaver) ext = ".dng";
                File out =  new File(curDir(),curName()+ext);
                Log.e("ImageSaver","RawSensor:"+mImage);
                try {
                    //output = new FileOutputStream(new File(curDir(),curName()+".dng"));
                    Log.d(TAG,"start buffersize:"+imageBuffer.size());
                    imageBuffer.add(mImage);
                    if(imageBuffer.size() > Interface.i.settings.frameCount){
                        imageBuffer.get(imageBuffer.size()-1).close();
                    } else
                    if(imageBuffer.size() == FrameNumberSelector.frameCount && Interface.i.settings.frameCount != 1) {
                        ImageProcessing processing = processing();
                        processing.isyuv = false;
                        processing.israw = true;
                        processing.path = out.getAbsolutePath();
                        done(processing);
                        //Thread.sleep(50);
                        out = new File(out.getAbsolutePath());
                        ExifInterface inter = ParseExif.Parse(CameraFragment.mCaptureResult,out.getAbsolutePath());
                        if(!Interface.i.settings.rawSaver) inter.saveAttributes();
                        //dngCreator.writeImage(output, mImage);
                        out = new File(out.getAbsolutePath());
                        CameraFragment.context.shot.setActivated(true);
                        Photo.instance.SaveImg(out);
                        end();
                    }
                    if(Interface.i.settings.frameCount == 1) {
                        Log.d(TAG,"activearr:"+ CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE));
                        Log.d(TAG,"precorr:"+ CameraFragment.mCameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE));
                        Log.d(TAG,"image:"+mImage.getCropRect());
                        DngCreator dngCreator = new DngCreator(CameraFragment.mCameraCharacteristics, CameraFragment.mCaptureResult);
                        output = new FileOutputStream(new File(curDir(),curName()+".dng"));
                        dngCreator.writeImage(output, mImage);
                        imageBuffer = new ArrayList<>();
                        mImage.close();
                        output.close();
                        CameraFragment.context.shot.setActivated(true);
                        CameraFragment.context.shot.setClickable(true);
                    }
                    if(imageBuffer.size() > Interface.i.settings.frameCount) {
                        imageBuffer.get(imageBuffer.size()-1).close();
                        imageBuffer.remove(imageBuffer.size()-1);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    //mImage.close();
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