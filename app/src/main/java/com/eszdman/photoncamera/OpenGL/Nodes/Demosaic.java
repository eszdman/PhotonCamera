package com.eszdman.photoncamera.OpenGL.Nodes;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.Render.Parameters;

public class Demosaic extends Node {
    @Override
    public void Run() {
        super.Name="Demosaic";
        super.Rid = R.raw.demosaic;
        Node Previous = super.previousNode;
        GLProg glProg = GLInterface.i.glprogram;
        GLTexture glTexture;
        Parameters params = GLInterface.i.parameters;
        glTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16),GLInterface.i.inputRaw);
        glProg.setTexture("RawBuffer",glTexture);
        glProg.servar("RawSizeX",params.rawSize.x);
        glProg.servar("RawSizeY",params.rawSize.y);
        glProg.servar("CfaPattern",params.cfaPattern);
        super.WorkingTexture = new GLTexture(params.rawSize,new GLFormat(GLFormat.DataType.UNSIGNED_16,3),null);
        glProg.drawBlocks(super.WorkingTexture);
    }
}
