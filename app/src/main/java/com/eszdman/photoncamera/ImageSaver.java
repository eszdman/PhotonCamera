package com.eszdman.photoncamera;

import android.graphics.ImageFormat;
import android.hardware.camera2.DngCreator;
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
    ArrayList<Image> imageBuffer = new ArrayList<>();
    ImageSaver(Image image, File file) {
        mImage = image;
        mFile = file;
    }
    public void done(){
        ImageProcessing proc = new ImageProcessing(imageBuffer);
        proc.Run();
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
                ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                try {

                    output = new FileOutputStream(  new File(curDir(),curName()+".jpg"));
                    //output = new FileOutputStream(mFile);
                    output.write(bytes);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    mImage.close();
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
                    Log.e("ImageSaver","ImageSaver burstcnt:"+bcnt);
                    bcnt++;
                    if(bcnt == Camera2Api.mburstcount) done();
                    dngCreator.writeImage(output, mImage);
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    mImage.close();
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