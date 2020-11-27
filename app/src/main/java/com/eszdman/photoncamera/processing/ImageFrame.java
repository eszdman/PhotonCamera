package com.eszdman.photoncamera.processing;

import android.media.Image;

import com.eszdman.photoncamera.processing.parameters.IsoExpoSelector;

import java.nio.ByteBuffer;

public class ImageFrame {
    public ByteBuffer buffer;
    public Image image;
    long luckyParameter;
    IsoExpoSelector.ExpoPair pair;
    public ImageFrame(ByteBuffer in){
        buffer = in;
    }
}
