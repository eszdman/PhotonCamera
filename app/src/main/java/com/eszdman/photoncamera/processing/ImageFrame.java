package com.eszdman.photoncamera.processing;

import java.nio.ByteBuffer;

public class ImageFrame {
    public ByteBuffer buffer;
    long luckyParameter;
    public ImageFrame(ByteBuffer in){
        buffer = in;
    }
}
