package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.opengl.GLUtils;

import androidx.annotation.NonNull;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import static android.opengl.GLES30.*;
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
    @NonNull
    public final GLFormat mFormat;
    private int Cur;
    public GLTexture(GLTexture in,GLFormat format) {
        this(in.mSize,new GLFormat(format),null,in.mFormat.filter,in.mFormat.wrap,0);
    }
    public GLTexture(GLTexture in) {
        this(in.mSize,in.mFormat,null,in.mFormat.filter,in.mFormat.wrap,0);
    }
    public GLTexture(int sizeX, int sizeY, GLFormat glFormat, Buffer pixels) {
        this(new Point(sizeX, sizeY), new GLFormat(glFormat), pixels, GL_NEAREST, GL_CLAMP_TO_EDGE,0);
    }
    public GLTexture(int sizeX, int sizeY, GLFormat glFormat, Buffer pixels,int textureFilter, int textureWrapper) {
        this(new Point(sizeX, sizeY), new GLFormat(glFormat), pixels, textureFilter, textureWrapper,0);
    }
    public GLTexture(Point size, GLFormat glFormat, Buffer pixels,int textureFilter, int textureWrapper) {
        this(new Point(size), new GLFormat(glFormat), pixels, textureFilter, textureWrapper,0);
    }
    public GLTexture(Point size, GLFormat glFormat, Buffer pixels) {
        this(new Point(size), new GLFormat(glFormat), pixels, GL_NEAREST, GL_CLAMP_TO_EDGE,0);
    }
    public GLTexture(int sizeX, int sizeY, GLFormat glFormat,int level) {
        this(new Point(sizeX, sizeY), new GLFormat(glFormat), null, GL_NEAREST, GL_CLAMP_TO_EDGE,level);
    }
    public GLTexture(int sizeX, int sizeY, GLFormat glFormat) {
        this(new Point(sizeX, sizeY), new GLFormat(glFormat), null, GL_NEAREST, GL_CLAMP_TO_EDGE,0);
    }
    public GLTexture(int sizeX, int sizeY, GLFormat glFormat,int textureFilter, int textureWrapper) {
        this(new Point(sizeX, sizeY), new GLFormat(glFormat), null, textureFilter, textureWrapper,0);
    }
    public GLTexture(Point size, GLFormat glFormat,int level) {
        this(new Point(size), new GLFormat(glFormat), null, GL_NEAREST, GL_CLAMP_TO_EDGE,level);
    }
    public GLTexture(Point size, GLFormat glFormat) {
        this(new Point(size), new GLFormat(glFormat), null, glFormat.filter, glFormat.wrap,0);
    }
    public GLTexture(Point point, GLFormat glFormat, int textureFilter, int textureWrapper) {
        this(new Point(point),new GLFormat(glFormat),null,textureFilter,textureWrapper);
    }
    public GLTexture(Bitmap bmp, int textureFilter, int textureWrapper,int level) {
        mFormat = null;
        this.mSize = new Point(bmp.getWidth(),bmp.getHeight());
        this.mGLFormat = 0;
        int[] TexID = new int[1];
        glGenTextures(TexID.length, TexID, 0);
        mTextureID = TexID[0];
        glActiveTexture(GL_TEXTURE1+mTextureID);
        glBindTexture(GL_TEXTURE_2D, mTextureID);
        GLUtils.texImage2D(GL_TEXTURE_2D,level,bmp,0);

        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, textureFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, textureFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, textureWrapper);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, textureWrapper);
        checkEglError("Tex glTexParameteri");
    }
    public GLTexture(Point size, GLFormat glFormat, Buffer pixels, int textureFilter, int textureWrapper,int level) {
        mFormat = glFormat;
        mFormat.filter = textureFilter;
        mFormat.wrap = textureWrapper;
        this.mSize = size;
        this.mGLFormat = glFormat.getGLFormatInternal();
        int[] TexID = new int[1];
        glGenTextures(TexID.length, TexID, 0);
        mTextureID = TexID[0];
        glActiveTexture(GL_TEXTURE1+mTextureID);
        glBindTexture(GL_TEXTURE_2D, mTextureID);
        //Log.d("GLTexture","Texture ID:"+mTextureID);
        /*
        glTexImage2D(GL_TEXTURE_2D, level, glFormat.getGLFormatInternal(), size.x, size.y, 0,
                glFormat.getGLFormatExternal(), glFormat.getGLType(), pixels);
         */
        glTexStorage2D(GL_TEXTURE_2D, 1, glFormat.getGLFormatInternal(),  size.x, size.y);
        checkEglError("glTexStorage2D");
        glTexSubImage2D(GL_TEXTURE_2D, level,0,0,size.x,size.y,glFormat.getGLFormatExternal(),glFormat.getGLType(),pixels);
        checkEglError("glTexSubImage2D");
        reSetParameters();
        checkEglError("Tex glTexParameteri");
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
            glGenFramebuffers(1, frameBuffer, 0);
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
        checkEglError("Tex bind");
    }

    public void textureBuffer(GLFormat outputFormat,ByteBuffer output) {
        glReadPixels(0, 0, mSize.x, mSize.y, outputFormat.getGLFormatExternal(), outputFormat.getGLType(), output);
    }

    public ByteBuffer textureBuffer(GLFormat outputFormat,boolean direct) {
        ByteBuffer buffer;
        if(!direct) buffer = ByteBuffer.allocate(mSize.x * mSize.y * outputFormat.mFormat.mSize * outputFormat.mChannels);
        else buffer = ByteBuffer.allocateDirect(mSize.x * mSize.y * outputFormat.mFormat.mSize * outputFormat.mChannels);
        glReadPixels(0, 0, mSize.x, mSize.y, outputFormat.getGLFormatExternal(), outputFormat.getGLType(), buffer);
        return buffer;
    }
    public ByteBuffer textureBuffer(GLFormat outputFormat) {
        ByteBuffer buffer = ByteBuffer.allocate(mSize.x * mSize.y * outputFormat.mFormat.mSize * outputFormat.mChannels);
        glReadPixels(0, 0, mSize.x, mSize.y, outputFormat.getGLFormatExternal(), outputFormat.getGLType(), buffer);
        return buffer;
    }
    public int getByteCount(){
        return mSize.x * mSize.y * mFormat.mFormat.mSize * mFormat.mChannels;
    }

    @androidx.annotation.NonNull
    @Override
    public String toString() {
        return "GLTexture{" +
                "mSize=" + mSize +
                ", mGLFormat=" + mGLFormat +
                ", mTextureID=" + mTextureID +
                ", mFormat=" + mFormat +
                '}';
    }

    @Override
    public void close() {
        glDeleteTextures(1, new int[]{mTextureID}, 0);
        if(isBuffered) glDeleteBuffers(1,new int[]{mBuffer},0);
    }
}
