package com.eszdman.photoncamera.Render;

import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Float3;
import android.renderscript.Float4;
import android.renderscript.Matrix3f;
import android.renderscript.RenderScript;
import android.renderscript.ScriptC;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.ScriptIntrinsicConvolve3x3;
import android.renderscript.Type;
import android.util.Log;
import com.eszdman.photoncamera.ScriptC_initial;
import com.eszdman.photoncamera.api.Interface;

public class Nodes {
    private static String TAG = "Nodes";
    private long timer;
    RenderScript rs;
    ScriptC_initial initial;
    ScriptIntrinsicBlur blur;
    public Nodes(RenderScript rs){
        this.rs = rs;
        initial = new ScriptC_initial(rs);
        blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
    }
    public void initialParameters(Parameters params, RUtils rUtils){
        initial.set_cfaPattern(params.cfaPattern);
        initial.set_rawWidth(params.rawSize.x);
        initial.set_rawHeight(params.rawSize.y);
        initial.set_blacklevel(new Float4(params.blacklevel[0],params.blacklevel[1],params.blacklevel[2],params.blacklevel[3]));
        initial.set_whitelevel(params.whitelevel);
        initial.set_whitepoint(new Float3(params.whitepoint[0],params.whitepoint[1],params.whitepoint[2]));
        initial.set_ccm(params.ccm);
        initial.set_hasGainMap(params.hasGainMap);
        initial.set_gainMapWidth(params.mapsize.x);
        initial.set_gainMapHeight(params.mapsize.y);
        initial.set_gainMap(rUtils.allocateIO(params.gainmap, Type.createXY(rs,Element.F32_4(rs),params.mapsize.x,params.mapsize.y)));
        initial.set_saturationFactor((float)Interface.i.settings.saturation);
        initial.set_neutralPoint(new Float3(params.whitepoint[0],params.whitepoint[1],params.whitepoint[2]));
        initial.set_sensorToIntermediate(new Matrix3f(Converter.transpose(params.sensorToProPhoto)));
        initial.set_intermediateToSRGB(new Matrix3f(Converter.transpose(params.proPhotoToSRGB)));
        initial.set_toneMapCoeffs(new Float4(params.customTonemap[0],params.customTonemap[1],params.customTonemap[2],params.customTonemap[3]));
        initial.set_gain((float)Interface.i.settings.gain);
        initial.set_compression(0.8f);
    }
    public float[] sharp1 = { -0.60f*(float)Interface.i.settings.sharpness, -0.60f*(float)Interface.i.settings.sharpness, -0.60f*(float)Interface.i.settings.sharpness, -0.60f*(float)Interface.i.settings.sharpness,1.f+ 4.80f*(float)Interface.i.settings.sharpness, -0.60f*(float)Interface.i.settings.sharpness,
                -0.60f*(float)Interface.i.settings.sharpness, -0.60f*(float)Interface.i.settings.sharpness, -0.60f*(float)Interface.i.settings.sharpness};
    public float[] sharp2 = { 0.0f, -1.0f, 0.0f, -1.0f, 5.0f, -1.0f, 0.0f, -1.0f,
                0.0f};
    public float[] sharp3 = { -0.15f, -0.15f, -0.15f, -0.15f, 2.2f, -0.15f, -0.15f,
                -0.15f, -0.15f};

    public Bitmap doSharpen(Bitmap original, float[] radius) {
        Bitmap bitmap = Bitmap.createBitmap(
                original.getWidth(), original.getHeight(),
                Bitmap.Config.ARGB_8888);
        Allocation allocIn = Allocation.createFromBitmap(rs, original);
        Allocation allocOut = Allocation.createFromBitmap(rs, bitmap);
        ScriptIntrinsicConvolve3x3 convolution = ScriptIntrinsicConvolve3x3.create(rs, Element.U8_4(rs));
        convolution.setInput(allocIn);
        convolution.setCoefficients(radius);
        convolution.forEach(allocOut);
        allocOut.copyTo(bitmap);
        return bitmap;

    }
    public void startT(){
        timer = System.currentTimeMillis();
    }

    public void endT(String msg){
        Log.d(TAG,msg+" elapsed:"+(System.currentTimeMillis()-timer)+" ms");
    }

    public void endT(ScriptC in){
        Log.d(TAG,in.getName()+" elapsed:"+(System.currentTimeMillis()-timer)+" ms");
    }
}
