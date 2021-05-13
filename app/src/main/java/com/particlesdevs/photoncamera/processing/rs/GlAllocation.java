package com.particlesdevs.photoncamera.processing.rs;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static android.renderscript.Allocation.USAGE_GRAPHICS_RENDER_TARGET;

public class GlAllocation implements AutoCloseable {
    public GLTexture glTexture;
    public Allocation allocation;
    private ByteBuffer byteBuffer;
    RenderScript rs;
    public GlAllocation(GlAllocation in){
        this(in.glTexture.mSize.x,in.glTexture.mSize.y,in.glTexture.mFormat,in.glTexture.mFormat.filter,in.glTexture.mFormat.wrap);
    }
    public GlAllocation(GLTexture in){
        this(in.mSize.x,in.mSize.y,in.mFormat,in.mFormat.filter,in.mFormat.wrap);
    }
    public GlAllocation(int sizeX, int sizeY, GLFormat glFormat, int textureFilter, int textureWrapper){
        rs = PhotonCamera.getRenderScript();
        Log.d("GlAllocation","RSContext:"+rs);
        glTexture = new GLTexture(sizeX,sizeY,glFormat,textureFilter,textureWrapper);
        //allocation = Allocation.createTyped(rs, Type.createXY(rs,glFormat.getElement(rs),sizeX,sizeY),Allocation.USAGE_SHARED);
    }

    public GlAllocation(int sizeX, int sizeY, GLFormat glFormat) {
        this(sizeX,sizeY,glFormat,glFormat.filter,glFormat.wrap);
    }

    public void pushToAllocation(){
        byteBuffer = glTexture.textureBuffer(glTexture.mFormat);
        //glTexture.textureBuffer(glTexture.mFormat,allocation.getByteBuffer());
    }
    public void pushToTexture(){
        GLTexture prev =  glTexture;
        byte[] arr = new byte[allocation.getBytesSize()];
        allocation.getByteBuffer().asReadOnlyBuffer().get(arr);
        glTexture = new GLTexture(prev.mSize,prev.mFormat,ByteBuffer.wrap(arr));
        prev.close();
    }
    public void getAllocation(){
        allocation = Allocation.createTyped(rs,Type.createXY(rs,glTexture.mFormat.getElement(rs),glTexture.mSize.x,glTexture.mSize.y),USAGE_GRAPHICS_RENDER_TARGET);
        byte[] input = new byte[byteBuffer.remaining()];
        byteBuffer.asReadOnlyBuffer().get(input);
        allocation.copyFromUnchecked(input);
    }
    public void createAllocation(){
        allocation = Allocation.createTyped(rs, Type.createXY(rs,glTexture.mFormat.getElement(rs),glTexture.mSize.x,glTexture.mSize.y),USAGE_GRAPHICS_RENDER_TARGET);
    }

    @Override
    public void close() {
        glTexture.close();
        if(allocation != null) allocation.destroy();
    }
}
