package com.particlesdevs.photoncamera.processing.rs;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Script;
import android.renderscript.ScriptIntrinsic;
import android.renderscript.Type;

import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class RUtils {
    private RenderScript rs;
    RUtils(RenderScript rs){
        this.rs = rs;
    }
    public Allocation allocateIO(ByteBuffer in, Type type){
        Allocation allocate = Allocation.createTyped(rs,type,Allocation.USAGE_GRAPHICS_RENDER_TARGET);
        byte[] input = new byte[in.remaining()];
        in.get(input);
        allocate.copyFromUnchecked(input);
        return allocate;
    }
    public Allocation allocateIO(Mat in, Type type){
        Allocation allocate = Allocation.createTyped(rs,type,Allocation.USAGE_GRAPHICS_RENDER_TARGET);
        byte[] input = new byte[in.rows()*in.cols()*in.channels()];
        in.get(0,0,input);
        allocate.copyFromUnchecked(input);
        return allocate;
    }
    public Allocation allocateIO(Object in, Type type){
        Allocation allocate = Allocation.createTyped(rs,type,Allocation.USAGE_GRAPHICS_RENDER_TARGET);
        allocate.copyFromUnchecked(in);
        return allocate;
    }
    public Allocation allocateO(Type type){
        Allocation allocate = Allocation.createTyped(rs,type);
        return allocate;
    }
    public Type CreateRaw(Point size){
        return Type.createXY(rs,Element.U16(rs),size.x,size.y);
    }
    public Type CreateF32_3(Point size){
        return Type.createXY(rs,Element.F32_3(rs),size.x,size.y);
    }
    public Type CreateF16_3(Point size){
        return Type.createXY(rs,Element.F16_3(rs),size.x,size.y);
    }
    public ScriptIntrinsic.LaunchOptions Range(Point from, Point to){
        return new Script.LaunchOptions().setX(from.x, to.x).setY(from.y, to.y);
    }
    public Type CreateBgr8(Point size){
        return Type.createXY(rs,Element.U8_3(rs),size.x,size.y);
    }
    public Type CreateBgr16(Point size){ return Type.createXY(rs,Element.U16_3(rs),size.x,size.y); }
    public Type CreateRGBA8888(Point size) { return Type.createXY(rs,Element.U8_4(rs),size.x,size.y);}
    public Type CreateU16(Point size) { return Type.createXY(rs,Element.U16(rs),size.x,size.y);}
    public Type CreateRGBA8888(int size) { return Type.createX(rs,Element.U8_4(rs),size);}
    public Type CreateU16(int size) { return Type.createX(rs,Element.U16(rs),size);}

    public Type CreateU32(int size) { return Type.createX(rs,Element.U32(rs),size);}
    public Type CreateU32_3(int size) { return Type.createX(rs,Element.U32_3(rs),size);}
    public Type CreateU32_4(int size) { return Type.createX(rs,Element.U32_4(rs),size);}
    public Bitmap SameBit(Bitmap original){
        return Bitmap.createBitmap(
                original.getWidth(), original.getHeight(),
                Bitmap.Config.ARGB_8888);
    }
}
