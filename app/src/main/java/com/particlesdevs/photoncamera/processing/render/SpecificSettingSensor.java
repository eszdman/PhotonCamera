package com.particlesdevs.photoncamera.processing.render;

import android.hardware.camera2.params.ColorSpaceTransform;
import android.util.Rational;

//Device current sensor specific
public class SpecificSettingSensor {
    public int id = 0;
    public boolean isRawColorCorrection = false;
    public float[] blackLevel;
    public float captureSharpeningS = 1.05f;
    public float captureSharpeningIntense = 0.5f;
    public float[] aberrationCorrection;

    public float[][] CalibrationTransform1;
    public float[][] CalibrationTransform2;
    public float[][] ColorTransform1;
    public float[][] ColorTransform2;
    public float[][] ForwardMatrix1;
    public float[][] ForwardMatrix2;

    public ColorSpaceTransform calibrationTransform1;
    public ColorSpaceTransform calibrationTransform2;
    public ColorSpaceTransform colorTransform1;
    public ColorSpaceTransform colorTransform2;
    public ColorSpaceTransform forwardMatrix1;
    public ColorSpaceTransform forwardMatrix2;
    public boolean CCTExists = false;

    public int referenceIlluminant1 = -1;
    public int referenceIlluminant2 = -1;
    public boolean overrideRawColors = false;
    //Noise model [A,B,C,D][bayer]
    public double[][] NoiseModelerArr;
    public boolean ModelerExists = false;
    public SpecificSettingSensor(){
        NoiseModelerArr = new double[4][4];

        aberrationCorrection = new float[8];
    }
    ColorSpaceTransform transform(float[][] matrix){
        if(matrix == null) return null;
        CCTExists = true;
        Rational[] rationalsRes = new Rational[matrix.length*matrix.length];
        for(int i =0; i<matrix.length; i++){
            for(int j =0; j<matrix.length;j++){
                rationalsRes[i*matrix.length+j] = new Rational((int)(matrix[i][j]*4096),4096);
            }
        }
        return new ColorSpaceTransform(rationalsRes);
    }

    public void updateTransforms(){
        calibrationTransform1 = transform(CalibrationTransform1);
        calibrationTransform2 = transform(CalibrationTransform2);

        colorTransform1 = transform(ColorTransform1);
        colorTransform2 = transform(ColorTransform2);

        forwardMatrix1 = transform(ForwardMatrix1);
        forwardMatrix2 = transform(ForwardMatrix2);
    }
}
