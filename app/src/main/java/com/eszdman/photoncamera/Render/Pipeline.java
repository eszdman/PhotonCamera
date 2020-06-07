package com.eszdman.photoncamera.Render;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Float3;
import android.renderscript.Float4;
import android.renderscript.Int4;
import android.renderscript.Matrix3f;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.ScriptIntrinsicResize;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Short4;
import android.renderscript.Type;

import com.eszdman.photoncamera.api.Interface;

import java.io.File;
import java.io.FileNotFoundException;
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
        params.rawSize = new Point(params.rawSize.x/2,params.rawSize.y/2);
        nodes.endT("Allocation");
        nodes.initial.set_cfaPattern(params.cfaPattern);
        nodes.initial.set_rawWidth(params.rawSize.x);
        nodes.initial.set_rawHeight(params.rawSize.y);
        nodes.initial.set_inputRawBuffer(input);
        nodes.initial.set_blacklevel(new Float4(params.blacklevel[0],params.blacklevel[1],params.blacklevel[2],params.blacklevel[3]));
        nodes.initial.set_whitelevel(params.whitelevel);
        nodes.initial.set_whitepoint(new Float3(params.whitepoint[0],params.whitepoint[1],params.whitepoint[2]));
        nodes.initial.set_ccm(params.ccm);
        nodes.initial.set_hasGainMap(params.hasGainMap);
        nodes.initial.set_gainMapWidth(params.mapsize.x);
        nodes.initial.set_gainMapHeight(params.mapsize.y);
        nodes.initial.set_gainMap(rUtils.allocateIO(params.gainmap,Type.createXY(rs,Element.F32_4(rs),params.mapsize.x,params.mapsize.y)));
        nodes.initial.set_saturationFactor((float)Interface.i.settings.saturation);
        nodes.initial.set_neutralPoint(new Float3(params.whitepoint[0],params.whitepoint[1],params.whitepoint[2]));
        nodes.initial.set_sensorToIntermediate(new Matrix3f(Converter.transpose(params.sensorToProPhoto)));
        nodes.initial.set_intermediateToSRGB(new Matrix3f(Converter.transpose(params.proPhotoToSRGB)));
        nodes.initial.set_toneMapCoeffs(new Float4(params.customTonemap[0],params.customTonemap[1],params.customTonemap[2],params.customTonemap[3]));
        nodes.initial.set_gain((float)Interface.i.settings.gain);
        nodes.initial.set_compression(1.2f);
        nodes.startT();
        nodes.initial.forEach_demosaicing(imgout, new Script.LaunchOptions().setX(1,params.rawSize.x-1).setY(1,params.rawSize.y-1));
        nodes.endT("Initial");
        imgout.copyTo(img);
        img = Bitmap.createBitmap(img,0,0,params.rawSize.x,params.rawSize.y);
        //img = nodes.doSharpen(img,nodes.sharp1);
        img = nodes.doSharpen(img,nodes.sharp1);

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
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
