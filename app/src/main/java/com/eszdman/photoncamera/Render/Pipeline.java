package com.eszdman.photoncamera.Render;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.ScriptIntrinsic;
import android.renderscript.Type;
import com.eszdman.photoncamera.api.Interface;
import com.eszdman.photoncamera.api.Photo;

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
        //Allocation imgout = Allocation.createFromBitmap(rs,img);
        Allocation imgout = Allocation.createTyped(rs,rUtils.CreateRGBA8888(new Point(params.rawSize.x,params.rawSize.y)),Allocation.USAGE_GRAPHICS_TEXTURE);
        params.rawSize = new Point(params.rawSize.x/2,params.rawSize.y/2);
        nodes.initialParameters(params,rUtils);
        nodes.endT("Allocation");
        nodes.initial.set_inputRawBuffer(input);
        nodes.initial.set_iobuffer(imgout);
        ScriptIntrinsic.LaunchOptions def = rUtils.Range(new Point(2,2), new Point(params.rawSize.x - 2,params.rawSize.y - 2));
        Allocation demosaicout = Allocation.createTyped(rs,rUtils.CreateF16_3(params.rawSize),Allocation.USAGE_GRAPHICS_TEXTURE);
        Allocation remosaicIn1 = null;
        Bitmap remosimg = Bitmap.createBitmap(params.rawSize.x*2,params.rawSize.y*2, Bitmap.Config.ARGB_8888);
        Allocation remosaicOut = Allocation.createFromBitmap(rs,remosimg);
        nodes.initial.set_demosaicOut(demosaicout);
        nodes.initial.set_remosaicOut(remosaicOut);
        Allocation blurmosaic = Allocation.createTyped(rs,demosaicout.getType(),Allocation.USAGE_GRAPHICS_TEXTURE);
        nodes.initial.set_remosaicIn1(blurmosaic);
        Allocation gainmap = rUtils.allocateIO(params.gainmap, Type.createXY(rs,Element.F32_4(rs),params.mapsize.x,params.mapsize.y));
        nodes.initial.set_gainMap(gainmap);
        nodes.startT();
        nodes.initial.forEach_color(def);
        /*imgout.copyTo(img);
        img = Bitmap.createBitmap(img,0,0,params.rawSize.x,params.rawSize.y);
        img = nodes.doResize(img,2.f);
        img = nodes.doSharpen(img,nodes.sharp1);*/
        nodes.endT("Initial");
        nodes.startT();
        //nodes.initial.forEach_blurdem(def);
        nodes.initial.set_remosaicSharp((float)Interface.i.settings.sharpness*4.9f);
        if(false){
            //remosaicIn1 = Allocation.createTyped(rs,rUtils.CreateF16_3(params.rawSize),Allocation.USAGE_GRAPHICS_TEXTURE);
            //nodes.initial.set_remosaicIn1(remosaicIn1);
            //nodes.initial.forEach_blurdem(def);
            nodes.initial.forEach_demosaic2(rUtils.Range(new Point(2,2), new Point((params.rawSize.x*2 - 2),(params.rawSize.y*2 - 2))));
        } else {
            //remosaicIn1 = Allocation.createTyped(rs,rUtils.CreateF16_3(params.rawSize));
            //ByteBuffer buff = ByteBuffer.allocate(params.rawSize.x*2*params.rawSize.y*2*4);
            //Mat inp = new Mat(params.rawSize.y*2,params.rawSize.x*2, CvType.CV_16U,in);
            //Mat out = new Mat(params.rawSize.y*2,params.rawSize.x*2, CvType.CV_8UC3);
            //Imgproc.demosaicing(inp,out,Imgproc.COLOR_BayerBG2RGB);
            //remosaicIn1 = rUtils.allocateIO(out,rUtils.CreateBgr8(new Point(params.rawSize.x*2,params.rawSize.y*2)));
            //nodes.initial.set_remosaicIn1(remosaicIn1);
            nodes.initial.set_rawWidth(params.rawSize.x*2);
            nodes.initial.set_rawHeight(params.rawSize.y*2);
            nodes.initial.forEach_demosaicmask(rUtils.Range(new Point(2, 2), new Point((params.rawSize.x * 2 - 2), (params.rawSize.y * 2 - 2))));
        }
        //nodes.initial.forEach_remosaic2(rUtils.Range(new Point(0,0), new Point((params.rawSize.x*2)/2,(params.rawSize.y*2)/2)));
        //nodes.initial.forEach_remosaic2nopt(rUtils.Range(new Point(1,1), new Point(((params.rawSize.x*2 - 2)/4),((params.rawSize.y*2 - 2))/4)));
        remosaicOut.copyTo(remosimg);
        nodes.endT("Remosaic");
        img.recycle();
        img = remosimg;
        img = Bitmap.createBitmap(img,4,4,params.rawSize.x*2-8,params.rawSize.y*2-8);
        in.clear();
        //img = nodes.doSharpen(img,nodes.sharp1);
        //img = Bitmap.createBitmap(img,0,0,params.rawSize.x - params.rawSize.x%4,params.rawSize.y - params.rawSize.y%4);
        try {
            outimg.createNewFile();
            FileOutputStream fOut = new FileOutputStream(outimg);
            img.compress(Bitmap.CompressFormat.JPEG,100,fOut);
            fOut.flush();
            fOut.close();
            img.recycle();
            input.destroy();
            imgout.destroy();
            blurmosaic.destroy();
            remosaicOut.destroy();
            demosaicout.destroy();
            if(remosaicIn1 != null) remosaicIn1.destroy();
            remosimg.recycle();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
