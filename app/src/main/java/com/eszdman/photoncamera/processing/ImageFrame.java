package com.eszdman.photoncamera.processing;

import android.media.Image;

import java.nio.ByteBuffer;

public class ImageFrame {
    public ByteBuffer buffer;
    public Image image;
    long luckyParameter;
    double exposure;
    public ImageFrame(ByteBuffer in){
        buffer = in;
    }
}
