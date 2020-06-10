package com.eszdman.photoncamera.Render;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.ScriptIntrinsic;

import com.eszdman.photoncamera.api.Interface;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

import static com.eszdman.photoncamera.api.ImageSaver.outimg;

public class Pipeline {
    public static void RunPipeline(ByteBuffer in, Parameters params){
        RenderScript rs = RenderScript.create(Interface.i.mainActivity);
        RUtils rUtils = new RUtils(rs,params.rawSize);
        Bitmap img = Bitmap.createBitmap(params.rawSize.x,params.rawSize.y, Bitmap.Config.ARGB_8888);
        Nodes nodes = new Nodes(rs);
        nodes.startT();
        Allocation input = rUtils.allocateIO(in,rUtils.RawSensor);
        Allocation imgout = Allocation.createFromBitmap(rs,img);
        Allocation hist = Allocation.createTyped(rs,rUtils.CreateU32(256));
        params.rawSize = new Point(params.rawSize.x/2,params.rawSize.y/2);
        nodes.initialParameters(params,rUtils);
        nodes.endT("Allocation");
        nodes.startT();
        nodes.initial.set_inputRawBuffer(input);
        nodes.initial.set_iobuffer(imgout);
        nodes.initial.set_hist(hist);
        int max = params.rawSize.x*params.rawSize.y/16;
        ScriptIntrinsic.LaunchOptions def = new Script.LaunchOptions().setX(1,params.rawSize.x-1).setY(1,params.rawSize.y-1);
        nodes.initial.forEach_demosaicing(def);
        nodes.initial.forEach_histparams(new Script.LaunchOptions().setX(1,params.rawSize.x/2 - 1).setY(1,params.rawSize.y/2 - 1));
        hist = nodes.initial.get_hist();
        int[] histarr = new int[256];
        hist.copyTo(histarr);
        short min = 0;
        for(int i =0; i<histarr.length;i++) if(histarr[i] > max/18) {min = (short)i; break;}
        //min = 0;
        nodes.initial.set_histmin(min);
        //nodes.initial.forEach_histEQ(def);
        imgout = nodes.initial.get_iobuffer();
        nodes.endT("Initial");
        imgout.copyTo(img);
        img = Bitmap.createBitmap(img,0,0,params.rawSize.x,params.rawSize.y);

        //img = nodes.doSharpen(img,nodes.sharp1);
        img = nodes.doSharpen(img,nodes.sharp1);
        //Mat test = new Mat(params.rawSize.y,params.rawSize.x, CvType.CV_16U,in);
        //Imgcodecs.imwrite(params.path+"_t2.jpg", test, new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 100));
        try {
            outimg.createNewFile();
            FileOutputStream fOut = new FileOutputStream(outimg);
            //FileOutputStream fOutT = new FileOutputStream(tes);
            img.compress(Bitmap.CompressFormat.JPEG,100,fOut);
            //img.compress(Bitmap.CompressFormat.JPEG,100,fOutT);
            fOut.flush();
            fOut.close();
            img.recycle();
            imgout.destroy();
            input.destroy();
            rs.destroy();
            in.clear();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
