package com.eszdman.photoncamera.Render;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;

import com.eszdman.photoncamera.api.Interface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class Pipeline {
    public static void RunPipeline(ByteBuffer in, Parameters params){
        RenderScript rs = RenderScript.create(Interface.i.mainActivity);
        RUtils rUtils = new RUtils(rs,params.rawSize);
        Bitmap img = Bitmap.createBitmap(params.rawSize.x,params.rawSize.y, Bitmap.Config.ARGB_8888);
        Nodes nodes = new Nodes(rs);
        nodes.startT();
        Allocation input = rUtils.allocateIO(in,rUtils.RawSensor);
        Allocation output = rUtils.allocateO(rUtils.BGR8);
        Allocation imgout = Allocation.createFromBitmap(rs,img);
        nodes.endT("Allocation");
        nodes.initial.set_cfaPattern((byte) 1);
        nodes.initial.set_rawWidth(params.rawSize.x);
        nodes.initial.set_rawHeight(params.rawSize.y);
        nodes.initial.set_inputRawBuffer(input);
        nodes.initial.set_blacklevel(params.blacklevel);
        nodes.initial.set_whitelevel(params.whitelevel);
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        nodes.startT();
        nodes.initial.forEach_demosaicing(imgout);
        nodes.endT("Initial");
        imgout.copyTo(img);
        File file = new File(params.path);
        try {
            FileOutputStream fOut = new FileOutputStream(file);
            img.compress(Bitmap.CompressFormat.JPEG,100,fOut);
            fOut.flush();
            fOut.close();
            img.recycle();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
