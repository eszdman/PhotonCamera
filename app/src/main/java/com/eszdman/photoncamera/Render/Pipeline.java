package com.eszdman.photoncamera.Render;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.ScriptIntrinsic;
import android.renderscript.ScriptIntrinsicBLAS;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.ScriptIntrinsicResize;
import android.util.Log;

import com.eszdman.photoncamera.api.Interface;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

import static com.eszdman.photoncamera.api.ImageSaver.outimg;

public class Pipeline {
    private static String TAG = "Pipeline";
    public static void RunPipeline(ByteBuffer in){
        RenderScript rs = Interface.i.rs;
        Parameters params = Interface.i.parameters;
        RUtils rUtils = new RUtils(rs,params.rawSize);
        Bitmap img = Bitmap.createBitmap(params.rawSize.x,params.rawSize.y, Bitmap.Config.ARGB_8888);
        Nodes nodes = Interface.i.nodes;
        nodes.startT();
        Allocation input = rUtils.allocateIO(in,rUtils.RawSensor);
        Allocation imgout = Allocation.createFromBitmap(rs,img);
        params.rawSize = new Point(params.rawSize.x/2,params.rawSize.y/2);
        nodes.initialParameters(params,rUtils);
        nodes.endT("Allocation");
        nodes.initial.set_inputRawBuffer(input);
        nodes.initial.set_iobuffer(imgout);
        ScriptIntrinsic.LaunchOptions def = rUtils.Range(new Point(2,2), new Point(params.rawSize.x - 2,params.rawSize.y - 2));
        Allocation demosaicout = Allocation.createTyped(rs,rUtils.CreateF16_3(params.rawSize));
        Allocation remosaicIn1 = Allocation.createTyped(rs,rUtils.CreateF16_3(params.rawSize));
        Bitmap remosimg = Bitmap.createBitmap(params.rawSize.x*2,params.rawSize.y*2, Bitmap.Config.ARGB_8888);
        Allocation remosaicOut = Allocation.createFromBitmap(rs,remosimg);
        nodes.initial.set_demosaicOut(demosaicout);
        nodes.initial.set_remosaicIn1(remosaicIn1);
        nodes.initial.set_remosaicOut(remosaicOut);
        Allocation blurmosaic = Allocation.createTyped(rs,demosaicout.getType());
        nodes.initial.set_remosaicIn1(blurmosaic);
        nodes.startT();
        nodes.initial.forEach_color(def);
        input.destroy();
        in.clear();
        imgout.copyTo(img);
        img = Bitmap.createBitmap(img,0,0,params.rawSize.x,params.rawSize.y);
        img = nodes.doSharpen(img,nodes.sharp1);
        img = nodes.doResize(img,2.f);
        nodes.endT("Initial");
        nodes.startT();
        //nodes.initial.forEach_blurdem(def);
        //nodes.initial.set_remosaicSharp((float)Interface.i.settings.sharpness*3.7f);
        //nodes.initial.forEach_demosaicmask(rUtils.Range(new Point(4,4), new Point(params.rawSize.x*2 - 4,params.rawSize.y*2 - 4)));
        //remosaicOut.copyTo(remosimg);
        nodes.endT("Remosaic");

        //img = Bitmap.createBitmap(img,0,0,params.rawSize.x - params.rawSize.x%4,params.rawSize.y - params.rawSize.y%4);
        try {
            outimg.createNewFile();
            FileOutputStream fOut = new FileOutputStream(outimg);
            img.compress(Bitmap.CompressFormat.JPEG,100,fOut);
            fOut.flush();
            fOut.close();
            img.recycle();
            imgout.destroy();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
