package com.eszdman.photoncamera.processing.opengl.postpipeline;

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

public class ShadowTexturing extends Node {
    public ShadowTexturing(int rid, String name) {
        super(rid, name);
    }

    @Override
    public void Run() {
        BitmapDrawable dr = (BitmapDrawable) PhotonCamera.getCameraActivity().getDrawable(R.drawable.shadowtex);
        ByteBuffer buff = ByteBuffer.allocate(dr.getBitmap().getByteCount());
        dr.getBitmap().copyPixelsToBuffer(buff);
        GLInterface glint = basePipeline.glint;
        Node Previous = super.previousNode;
        GLProg glProg = glint.glProgram;
        glProg.setTexture("InputBuffer", Previous.WorkingTexture);
        Log.v("ShadowTexturing", "Buffer size:" + buff.capacity());
        buff.position(0);
        glProg.setTexture("InputTex", new GLTexture(255, 255, new GLFormat(GLFormat.DataType.UNSIGNED_16, 2), buff));
        super.WorkingTexture = new GLTexture(Previous.WorkingTexture.mSize, Previous.WorkingTexture.mFormat, null);
    }
}
