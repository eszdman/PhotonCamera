package com.eszdman.photoncamera;

import android.graphics.ImageFormat;
import android.hardware.camera2.DngCreator;
import android.media.ExifInterface;
import android.media.Image;
import android.os.Environment;
import android.util.Log;

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
     * The JPEG image
     */
    private final Image mImage;
    /**
     * The file we save the image into.
     */
    static int bcnt = 0;
    private final File mFile;
    static ArrayList<Image> imageBuffer = new ArrayList<>();
    ImageSaver(Image image, File file) {
        mImage = image;
        mFile = file;
    }
    public void done(){
        ImageProcessing proc = new ImageProcessing(imageBuffer);
        proc.israw = true;
        proc.Run();
        for(int i =0; i<imageBuffer.size()-1;i++){
            imageBuffer.get(i).close();
        }
        imageBuffer = new ArrayList<>();
        Log.e("ImageSaver","ImageSaver Done!");
        bcnt =0;
    }
    public void donejpg(File name){
        ImageProcessing proc = new ImageProcessing(imageBuffer);
        proc.israw = false;
        proc.path = name.getAbsolutePath();
        proc.Run();
        for(int i =0; i<imageBuffer.size()-1;i++){
            imageBuffer.get(i).close();
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
        File dir = new File(Environment.getExternalStorageDirectory()+"//DCIM//EsCamera//");
        dir.mkdirs();
        return dir.getAbsolutePath();
    }
    @Override
    public void run() {
        int format = mImage.getFormat();
        FileOutputStream output = null;

        Log.e("ImageSaver","Hello From ImageSaver");
        switch (format){
            case ImageFormat.JPEG: {
                File out =  new File(curDir(),curName()+".jpg");
                ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                try {
                    output = new FileOutputStream(out);
                    //output = new FileOutputStream(mFile);
                    imageBuffer.add(mImage);
                    Log.e("ImageSaver","ImageSaver imagebuffer size:"+imageBuffer.size());
                    bcnt++;

                    if(bcnt == Camera2Api.mburstcount) {
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.duplicate().get(bytes);
                        output.write(bytes);
                        output.close();
                        ExifInterface inter = new ExifInterface(out.getAbsolutePath());
                        MainActivity.inst.showToast("Processing...");
                        donejpg(out);
                        MainActivity.inst.showToast("Done!");
                        Thread.sleep(25);
                        inter.saveAttributes();
                        mImage.close();
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
            case ImageFormat.RAW_SENSOR: {
                DngCreator dngCreator = new DngCreator(Camera2Api.mCameraCharacteristics,Camera2Api.mCaptureResult);
                try {
                    output = new FileOutputStream(new File(curDir(),curName()+".dng"));
                    imageBuffer.add(mImage);
                    Log.e("ImageSaver","ImageSaver imagebuffer size:"+imageBuffer.size());
                    bcnt++;
                    if(bcnt == Camera2Api.mburstcount) {
                        MainActivity.inst.showToast("Processing...");
                        done();
                        dngCreator.writeImage(output, mImage);
                        MainActivity.inst.showToast("Done!");
                        mImage.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    //mImage.close();
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
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