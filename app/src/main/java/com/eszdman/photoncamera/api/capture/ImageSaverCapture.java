package com.eszdman.photoncamera.api.capture;

import android.media.ImageReader;
import android.os.Message;

import com.eszdman.photoncamera.api.ImageSaver;

public class ImageSaverCapture extends AbstractImageCapture {

    private ImageSaver imageSaver;
    public ImageSaverCapture(int width, int height, int format, int maximgs, ImageSaver imageSaver) {
        super(width, height, format, maximgs);
        this.imageSaver = imageSaver;
    }

    @Override
    public void onImageAvailable(ImageReader imageReader) {
        Message msg = new Message();
        msg.obj = imageReader;
        imageSaver.ProcessCall.sendMessage(msg);
    }
}
