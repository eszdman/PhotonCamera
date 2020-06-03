package com.eszdman.photoncamera.api;

import android.os.Handler;
import android.os.Looper;

import com.eszdman.photoncamera.Control.Swipe;
import com.eszdman.photoncamera.ImageProcessing;
import com.eszdman.photoncamera.Wrapper;
import com.eszdman.photoncamera.ui.CameraFragment;
import com.eszdman.photoncamera.ui.MainActivity;

import org.opencv.img_hash.Img_hash;

public class Interface {
    public static Interface i;
    public MainActivity mainActivity;
    public CameraFragment camera;
    public Settings settings;
    public Photo photo;
    public Wrapper wrapper;
    public ImageProcessing processing;
    public Handler processinghandler;
    public Swipe swipedetection;
    public Interface() {
        i = this;
        settings = new Settings();
        photo = new Photo();
        wrapper = new Wrapper();
        processing = new ImageProcessing();
        swipedetection = new Swipe();
        //processinghandler = new Handler(Looper.getMainLooper());
    }
}
