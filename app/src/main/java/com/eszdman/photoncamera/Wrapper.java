package com.eszdman.photoncamera;

import java.nio.ByteBuffer;

public class Wrapper {
    public Wrapper(){
        System.loadLibrary("HdrX");
    }

public static native void init(int rows,int cols, int frames);
public static native void setCompGain(double compression, double gain, double contrast, int contr_const);
public static native void setBWLWB(int blackp, int whitep, double wbr,double wbg0,double wbg1,double wbb);
public static native void setSharpnessSaturation(double saturation, double sharpness);
public static native void setCFA(int CFA);
public static native void setCCM(double[] CCM);
public static native void loadFrame(ByteBuffer bufferptr);
public static native ByteBuffer processFrame();
}
