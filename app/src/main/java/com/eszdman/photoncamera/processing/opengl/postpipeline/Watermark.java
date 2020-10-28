package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLInterface;
import com.eszdman.photoncamera.processing.opengl.GLProg;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;

import java.nio.ByteBuffer;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_NEAREST;

public class Watermark extends Node {
    private int rotate;
    private boolean watermarkNeeded;
    private Bitmap watermark;
    public Watermark(int rotation,boolean isWaterNeeded) {
        super(0, "AddWatermark");
        rotate = rotation;
        watermarkNeeded = isWaterNeeded;
    }

    @Override
    public void Compile() {}

    @Override
    public void AfterRun() {
        if(watermark != null) watermark.recycle();
        //previousNode.WorkingTexture.close();
    }

    @Override
    public void Run() {
        if(watermarkNeeded) {
            glProg.useProgram(R.raw.addwatermark_rotate);
            watermark = BitmapFactory.decodeResource(PhotonCamera.getCameraActivity().getResources(), R.drawable.photoncamera_watermark);
            ByteBuffer buff = ByteBuffer.allocate(watermark.getByteCount());
            watermark.copyPixelsToBuffer(buff);
        Log.v("Watermark", "Buffer size:" + buff.capacity());
        buff.position(0);
        glProg.setTexture("Watermark", new GLTexture(watermark.getWidth(), watermark.getHeight(), new GLFormat(GLFormat.DataType.FLOAT_16, 1), buff));
        } else {
            glProg.useProgram(R.raw.rotate);
        }
        glProg.setTexture("InputBuffer", previousNode.WorkingTexture);
        int rot = -1;
        Point size = previousNode.WorkingTexture.mSize;
        Log.d(Name,"Rotation:"+rotate);
        switch (rotate){
            case 0:
                //WorkingTexture = new GLTexture(size.x,size.y, previousNode.WorkingTexture.mFormat, null);
                rot = 0;
                break;
            case 90:
                //WorkingTexture = new GLTexture(size.y,size.x, previousNode.WorkingTexture.mFormat, null);
                rot = 3;
                break;
            case 180:
                //WorkingTexture = new GLTexture(size, previousNode.WorkingTexture.mFormat, null);
                rot = 2;
                break;
            case 270:
                //WorkingTexture = new GLTexture(size.y,size.x, previousNode.WorkingTexture.mFormat, null);
                rot = 1;
                break;
        }
        Log.d(Name,"selected rotation:"+rot);
        glProg.setVar("rotate",rot);
    }
}
