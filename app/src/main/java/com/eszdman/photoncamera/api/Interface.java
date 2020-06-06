package com.eszdman.photoncamera.api;

import android.renderscript.RenderScript;
import com.eszdman.photoncamera.Control.Gravity;
import com.eszdman.photoncamera.Control.Manual;
import com.eszdman.photoncamera.Control.Swipe;
import com.eszdman.photoncamera.ImageProcessing;
import com.eszdman.photoncamera.Render.Nodes;
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
    public Swipe swipedetection;
    public RenderScript rs;
    public Nodes nodes;
    public Gravity gravity;
    public Manual manual;
    public Interface(MainActivity act) {
        i = this;
        mainActivity = act;
        rs = RenderScript.create(mainActivity);
        gravity = new Gravity();
        nodes = new Nodes(rs);
        settings = new Settings();
        photo = new Photo();
        wrapper = new Wrapper();
        processing = new ImageProcessing();
        swipedetection = new Swipe();
    }
}
