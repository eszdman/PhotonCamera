package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import com.particlesdevs.photoncamera.util.Allocator;
import com.particlesdevs.photoncamera.util.Log;

import androidx.annotation.NonNull;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import static android.opengl.GLES31.*;
import static com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing.checkEglError;
import static javax.microedition.khronos.opengles.GL11.GL_TEXTURE_2D;
import static javax.microedition.khronos.opengles.GL11.GL_TEXTURE_MAG_FILTER;
import static javax.microedition.khronos.opengles.GL11.GL_TEXTURE_MIN_FILTER;

public class GLTexture implements AutoCloseable {
    public Point mSize;
    public final int mGLFormat;
    public final int mTextureID;
    public int mBuffer;
    public boolean isBuffered = false;
    private static int count = 0;
    public final GLFormat mFormat;
    private static boolean[] ids = new boolean[256];
    private static int[] textures = new int[256];
    public GLTexture(GLTexture in,GLFormat format) {
        this(in.mSize,new GLFormat(format),null,in.mFormat.filter,in.mFormat.wrap,0);
    }
    public GLTexture(GLTexture in) {
        this(in.mSize,in.mFormat,null,in.mFormat.filter,in.mFormat.wrap,0);
    }
    public GLTexture(int sizeX, int sizeY, GLFormat glFormat, Buffer pixels) {
        this(new Point(sizeX, sizeY), new GLFormat(glFormat), pixels, GL_LINEAR, GL_CLAMP_TO_EDGE,0);
    }
    public GLTexture(int sizeX, int sizeY, GLFormat glFormat, Buffer pixels,int textureFilter, int textureWrapper) {
        this(new Point(sizeX, sizeY), new GLFormat(glFormat), pixels, textureFilter, textureWrapper,0);
    }
    public GLTexture(Point size, GLFormat glFormat, Buffer pixels,int textureFilter, int textureWrapper) {
        this(new Point(size), new GLFormat(glFormat), pixels, textureFilter, textureWrapper,0);
    }
    public GLTexture(Point size, GLFormat glFormat, Buffer pixels) {
        this(new Point(size), new GLFormat(glFormat), pixels, GL_LINEAR, GL_CLAMP_TO_EDGE,0);
    }
    public GLTexture(int sizeX, int sizeY, GLFormat glFormat,int level) {
        this(new Point(sizeX, sizeY), new GLFormat(glFormat), null, GL_LINEAR, GL_CLAMP_TO_EDGE,level);
    }
    public GLTexture(int sizeX, int sizeY, GLFormat glFormat) {
        this(new Point(sizeX, sizeY), new GLFormat(glFormat), null, GL_LINEAR, GL_CLAMP_TO_EDGE,0);
    }
    public GLTexture(int sizeX, int sizeY, GLFormat glFormat,int textureFilter, int textureWrapper) {
        this(new Point(sizeX, sizeY), new GLFormat(glFormat), null, textureFilter, textureWrapper,0);
    }
    public GLTexture(Point size, GLFormat glFormat,int level) {
        this(new Point(size), new GLFormat(glFormat), null, GL_LINEAR, GL_CLAMP_TO_EDGE,level);
    }
    public GLTexture(Point size, GLFormat glFormat) {
        this(new Point(size), new GLFormat(glFormat), null, glFormat.filter, glFormat.wrap,0);
    }
    public GLTexture(Point point, GLFormat glFormat, int textureFilter, int textureWrapper) {
        this(new Point(point),new GLFormat(glFormat),null,textureFilter,textureWrapper);
    }
    public GLTexture(GLImage bmp){
        this(bmp,0);
    }
    public GLTexture(GLImage bmp,int level){
        this(bmp,GL_LINEAR,GL_CLAMP_TO_EDGE,level);
    }
    public GLTexture(GLImage bmp, int textureFilter, int textureWrapper,int level) {
        this.mSize = bmp.size;
        this.mFormat = bmp.glFormat;
        this.mGLFormat = mFormat.getGLFormatInternal();
        bmp.byteBuffer.position(0);
        mFormat.filter = textureFilter;
        mFormat.wrap = textureWrapper;
        int[] TexID = new int[1];
        glGenTextures(1,TexID,0);
        Log.d("GLTexture","TexID:"+TexID[0] + " Size:"+mSize.x+"x"+mSize.y + " Format:"+mFormat.getGLFormatInternal() + " Filter:"+textureFilter + " Wrapper:"+textureWrapper);
        for(int i = 1; i<ids.length;i++){
            if(!ids[i]){
                Log.d("GLTexture","get:"+i);
                if(count < i){
                    count = i;
                    //glGenTextures(1,TexID,0);
                }
                //TexID[0] = i;
                textures[i] = TexID[0];
                ids[i] = true;
                break;
            }
        }

        mTextureID = TexID[0];
        //Log.d("GLTexture","Size:"+size+" ID:"+mTextureID);
        glActiveTexture(GL_TEXTURE1+mTextureID);
        glBindTexture(GL_TEXTURE_2D, mTextureID);
        //if(bmp.byteBuffer != null) {
            glTexStorage2D(GL_TEXTURE_2D, 1, mFormat.getGLFormatInternal(),  mSize.x, mSize.y);
            checkEglError("glTexStorage2D");
            if(bmp.byteBuffer != null) {
                glTexSubImage2D(GL_TEXTURE_2D, level, 0, 0, mSize.x, mSize.y, mFormat.getGLFormatExternal(), mFormat.getGLType(), (ByteBuffer) bmp.byteBuffer);
            }
        //}
        //else glTexImage2D(GL_TEXTURE_2D, level,mFormat.getGLFormatInternal(), mSize.x, mSize.y,0, mFormat.getGLFormatExternal(), mFormat.getGLType(), null);
        checkEglError("glTexSubImage2D");
        reSetParameters();
        checkEglError("Tex glTexParameter");
    }
    public GLTexture(Point size, GLFormat glFormat, Buffer pixels, int textureFilter, int textureWrapper,int level) {
        mFormat = glFormat;
        mFormat.filter = textureFilter;
        mFormat.wrap = textureWrapper;
        this.mSize = size;
        this.mGLFormat = glFormat.getGLFormatInternal();
        int[] TexID = new int[1];
        glGenTextures(1,TexID,0);
        Log.d("GLTexture","TexID:"+TexID[0]);
        for(int i = 1; i<ids.length;i++){
            if(!ids[i]){
                Log.d("GLTexture","get:"+i);
                if(count < i){
                    count = i;
                    //glGenTextures(1,TexID,0);
                }
                //TexID[0] = i;
                textures[i] = TexID[0];
                ids[i] = true;
                break;
            }
        }
        mTextureID = TexID[0];
        //Log.d("GLTexture","Size:"+size+" ID:"+mTextureID);
        glActiveTexture(GL_TEXTURE1+mTextureID);
        glBindTexture(GL_TEXTURE_2D, mTextureID);
        //if(pixels != null) {
            glTexStorage2D(GL_TEXTURE_2D, 1, glFormat.getGLFormatInternal(),  size.x, size.y);
            checkEglError("glTexStorage2D");
            if(pixels != null) {
            glTexSubImage2D(GL_TEXTURE_2D, level, 0, 0, size.x, size.y, glFormat.getGLFormatExternal(), glFormat.getGLType(), (ByteBuffer) pixels);
            }
         //}
        //else glTexImage2D(GL_TEXTURE_2D, level,mFormat.getGLFormatInternal(), mSize.x, mSize.y,0, mFormat.getGLFormatExternal(), mFormat.getGLType(), null);
        checkEglError("glTexSubImage2D");
        reSetParameters();
        checkEglError("Tex glTexParameter");
    }

    public void loadData(Buffer pixels){
        glBindTexture(GL_TEXTURE_2D, mTextureID);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, mSize.x, mSize.y, mFormat.getGLFormatExternal(), mFormat.getGLType(), pixels);
    }
    void reSetParameters(){
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, mFormat.filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, mFormat.filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, mFormat.wrap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, mFormat.wrap);
    }
    public void Bufferize(){
        if(!isBuffered) {
            int[] frameBuffer = new int[1];
            glGenFramebuffers(1,frameBuffer,0);
            mBuffer = frameBuffer[0];
            isBuffered = true;
        }
    }

    public void BindBuffer(){
        glBindFramebuffer(GL_FRAMEBUFFER, mBuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mTextureID, 0);
    }

    public void BufferLoad() {
        Bufferize();
        glBindFramebuffer(GL_FRAMEBUFFER, mBuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mTextureID, 0);
        glViewport(0, 0, mSize.x, mSize.y);
        checkEglError("Tex BufferLoad");
    }

    public void bind(int slot) {
        glActiveTexture(slot);
        glBindTexture(GL_TEXTURE_2D, mTextureID);
        checkEglError("Tex " + mTextureID + " bind");
    }

    public void textureBuffer(GLFormat outputFormat,ByteBuffer output) {
        glReadPixels(0, 0, mSize.x, mSize.y, outputFormat.getGLFormatExternal(), outputFormat.getGLType(), output);
    }

    public ByteBuffer textureBuffer(GLFormat outputFormat,boolean direct) {
        // FLOAT_16 textures are transferred with GL_FLOAT (32-bit), so the CPU
        // buffer must hold 4 bytes/channel, not the 2-byte on-GPU storage size.
        int bytesPerCh = outputFormat.mFormat == GLFormat.DataType.FLOAT_16 ? 4 : outputFormat.mFormat.mSize;
        ByteBuffer buffer;
        if(!direct) buffer = ByteBuffer.allocate(mSize.x * mSize.y * bytesPerCh * outputFormat.mChannels);
        else buffer = ByteBuffer.allocateDirect(mSize.x * mSize.y * bytesPerCh * outputFormat.mChannels);
        glReadPixels(0, 0, mSize.x, mSize.y, outputFormat.getGLFormatExternal(), outputFormat.getGLType(), buffer);
        return buffer;
    }
    public ByteBuffer textureBuffer(GLFormat outputFormat) {
        int bytesPerCh = outputFormat.mFormat == GLFormat.DataType.FLOAT_16 ? 4 : outputFormat.mFormat.mSize;
        ByteBuffer buffer = ByteBuffer.allocate(mSize.x * mSize.y * bytesPerCh * outputFormat.mChannels);
        glReadPixels(0, 0, mSize.x, mSize.y, outputFormat.getGLFormatExternal(), outputFormat.getGLType(), buffer);
        return buffer;
    }

    /**
     * Half-float readback from a FLOAT_16 texture: stores the exact bits the
     * GPU already holds, at half the size of a GL_FLOAT transfer (8 vs
     * 16 B/pixel for RGBA). The buffer is backed by native memory
     * ({@link Allocator}); returns {@code null} on allocation failure or if
     * the driver refuses the packed transfer.
     */
    public ByteBuffer textureBufferHalfFloat() {
        ByteBuffer buffer = Allocator.allocate(mSize.x * mSize.y * 4 * 2);
        if (buffer == null) return null;
        while (GLES30.glGetError() != GLES30.GL_NO_ERROR) {} // clear stale errors
        glReadPixels(0, 0, mSize.x, mSize.y, GLES30.GL_RGBA, GLES30.GL_HALF_FLOAT, buffer);
        int err = GLES30.glGetError();
        if (err != GLES30.GL_NO_ERROR) {
            Log.d("GLTexture", "HALF_FLOAT readback failed: 0x" + Integer.toHexString(err));
            Allocator.free(buffer);
            return null;
        }
        buffer.rewind();
        return buffer;
    }

    /** Uploads packed half-float pixel data into this FLOAT_16 texture. */
    public void loadHalfFloat(Buffer pixels) {
        glBindTexture(GL_TEXTURE_2D, mTextureID);
        glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, mSize.x, mSize.y,
                mFormat.getGLFormatExternal(), GLES30.GL_HALF_FLOAT, pixels);
    }

    public Bitmap toBitmap(){
        ByteBuffer buffer = textureBuffer(mFormat);
        Bitmap bmp = Bitmap.createBitmap(mSize.x, mSize.y, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buffer);
        return bmp;
    }
    public int getByteCount(){
        return mSize.x * mSize.y * mFormat.mFormat.mSize * mFormat.mChannels;
    }


    @Override
    public String toString() {
        return "GLTexture{" +
                "mSize=" + mSize +
                ", mGLFormat=" + mGLFormat +
                ", mTextureID=" + mTextureID +
                ", mFormat=" + mFormat +
                '}';
    }
    public static void notClosed(){
        StringBuilder str = new StringBuilder();
        for(int i =0; i<ids.length;i++){
            if(ids[i]) {
                str.append(i);
                str.append(" ");
            }
        }
        Log.d("GLTexture","notClosed:"+str.toString());
    }

    public static void closeAll(){
        for(int i =0; i<ids.length;i++){
            if(ids[i]) {
                glDeleteTextures(1,new int[]{textures[i]},0);
                ids[i] = false;
            }
        }
        count = 0;
    }

    @Override
    public void close() {
        glDeleteTextures(1,new int[]{mTextureID},0);
        ids[mTextureID] = false;
        //Log.d("GLTexture","close ID:"+mTextureID);
        if(isBuffered) glDeleteBuffers(1,new int[]{mBuffer},0);
    }
}
