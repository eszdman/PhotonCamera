package com.eszdman.photoncamera.api;

import android.os.Handler;
import android.os.Looper;

import com.eszdman.photoncamera.ImageProcessing;
import com.eszdman.photoncamera.Wrapper;
import com.eszdman.photoncamera.ui.MainActivity;

import org.opencv.img_hash.Img_hash;

public class Interface {
    public static Interface i;
    public MainActivity mainActivity;
    public Settings settings;
    public Photo photo;
    public Wrapper wrapper;
    public ImageProcessing processing;
    public Handler processinghandler;
    public Interface() {
        i = this;
        settings = new Settings();
        photo = new Photo();
        wrapper = new Wrapper();
        processing = new ImageProcessing();
        //processinghandler = new Handler(Looper.getMainLooper());
    }
}
