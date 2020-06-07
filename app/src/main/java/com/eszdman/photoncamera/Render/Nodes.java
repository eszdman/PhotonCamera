package com.eszdman.photoncamera.Render;

import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptC;
import android.renderscript.ScriptIntrinsicBlur;
import android.renderscript.ScriptIntrinsicConvolve3x3;
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
        rs.destroy();
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
