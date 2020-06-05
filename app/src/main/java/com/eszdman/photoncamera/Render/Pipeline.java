package com.eszdman.photoncamera.Render;

import android.graphics.Point;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;

import com.eszdman.photoncamera.api.Interface;

import java.nio.ByteBuffer;

public class Pipeline {
    public static ByteBuffer RunPipeline(ByteBuffer in, Point size){
        RenderScript rs = RenderScript.create(Interface.i.mainActivity);
        Type maintype = Type.createXY(rs,Element.U16(rs),size.x,size.y);
        Allocation allocation = Allocation.createAllocations(rs, maintype,Allocation.USAGE_IO_INPUT,1)[0];
        Type outType = Type.createXY(rs,Element.U16_4(rs),size.x,size.y);
        Allocation output = Allocation.createAllocations(rs, outType,Allocation.USAGE_IO_OUTPUT,1)[0];
        allocation.copy2DRangeFrom(0,0,size.x,size.y,in.asShortBuffer());
        output.copyTo(in);
        return in;
    }
}
