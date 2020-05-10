package com.eszdman.photoncamera;

import android.media.Image;
import android.util.Log;
import android.util.Xml;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Base64;

public class ImageProcessing {
    ArrayList<Image> curimgs;
    ImageProcessing(ArrayList<Image> images) {
        curimgs = images;
    }
    Mat loadPlane(Image image){
        Image.Plane plane = image.getPlanes()[0];
        ShortBuffer buffer = plane.getBuffer().asShortBuffer();
        short[] data = new short[buffer.capacity()];
        ((ShortBuffer) buffer.duplicate().clear()).get(data);
        int width = plane.getRowStride()/(plane.getPixelStride());
        Mat mat = new Mat(image.getWidth(),image.getHeight(), CvType.CV_16U);
        mat.put(0, 0, data);
        Core.divide(mat,new Scalar(256),mat);
        return mat;
    }
    public void Run(){
        Image.Plane plane = curimgs.get(0).getPlanes()[0];
        byte buffval = plane.getBuffer().get();
        Mat mat = loadPlane(curimgs.get(0));
        //Mat res = Imgcodecs.imdecode(mat,0);
        mat = Imgcodecs.imdecode(mat,Imgcodecs.IMREAD_UNCHANGED);
        Imgcodecs.imwrite(ImageSaver.curDir()+"//"+ImageSaver.curName()+"_CV.jpg",mat);
        Log.d("ImageProcessing","buffer parameters:");
        Log.d("ImageProcessing","bufferpixelstride"+plane.getPixelStride());
        Log.d("ImageProcessing","bufferrowstride"+plane.getRowStride());
        Log.d("ImageProcessing","buffervalue"+buffval);
    }
}
