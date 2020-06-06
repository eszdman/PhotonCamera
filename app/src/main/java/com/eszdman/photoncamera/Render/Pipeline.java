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
import android.renderscript.Short4;
import android.renderscript.Type;

import com.eszdman.photoncamera.api.Interface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;

public class Pipeline {
    public static void RunPipeline(ByteBuffer in, Parameters params){
        RenderScript rs = Interface.i.rs;
        RUtils rUtils = new RUtils(rs,params.rawSize);
        Bitmap img = Bitmap.createBitmap(params.rawSize.x,params.rawSize.y, Bitmap.Config.ARGB_8888);
        Nodes nodes = Interface.i.nodes;
        nodes.startT();
        Allocation input = rUtils.allocateIO(in,rUtils.RawSensor);
        Allocation imgout = Allocation.createFromBitmap(rs,img);
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

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        nodes.startT();
        nodes.initial.forEach_demosaicing(imgout, new Script.LaunchOptions().setX(4,params.rawSize.x-4).setY(4,params.rawSize.y-4));
        nodes.endT("Initial");
        imgout.copyTo(img);
        //img = nodes.doSharpen(img,nodes.sharp1);
        File file = new File(params.path);
        try {
            FileOutputStream fOut = new FileOutputStream(file);
            img.compress(Bitmap.CompressFormat.JPEG,100,fOut);
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
