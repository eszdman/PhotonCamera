package com.eszdman.photoncamera.Render;

import android.graphics.Point;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

public class RUtils {
    private RenderScript rs;
    public Type RawSensor;
    public Type BGR16;
    public Type RGBA8888;
    public Type BGR8;
    RUtils(RenderScript rs, Point size){
        this.rs = rs;
        RawSensor = CreateRaw(size);
        BGR8 = CreateBgr8(size);
        BGR16 = CreateBgr16(size);
        RGBA8888 = CreateRGBA8888(size);
    }
    public Allocation allocateIO(ByteBuffer in, Type type){
        Allocation allocate = Allocation.createTyped(rs,type);
        //ShortBuffer sb = in.asShortBuffer();
        byte[] input = new byte[in.remaining()];
        in.get(input);
        allocate.copyFromUnchecked(input);
        return allocate;
    }
    public Allocation allocateIO(Object in, Type type){
        Allocation allocate = Allocation.createTyped(rs,type);
        //ShortBuffer sb = in.asShortBuffer();
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
    public Type CreateBgr8(Point size){
        return Type.createXY(rs,Element.U8_3(rs),size.x,size.y);
    }
    public Type CreateBgr16(Point size){ return Type.createXY(rs,Element.U16_3(rs),size.x,size.y); }
    public Type CreateRGBA8888(Point size) { return Type.createXY(rs,Element.U8_4(rs),size.x,size.y);}
}
