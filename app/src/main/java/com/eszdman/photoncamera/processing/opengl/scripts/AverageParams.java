package com.eszdman.photoncamera.processing.opengl.scripts;

import java.nio.ByteBuffer;

public class AverageParams {
    ByteBuffer inp1;
    ByteBuffer inp2;
    public AverageParams(ByteBuffer in1, ByteBuffer in2){
        inp1 = in1;
        inp2 = in2;
    }
}
