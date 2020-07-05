package com.eszdman.photoncamera.OpenGL.Nodes;

import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.Render.Parameters;

public class DemosaicAndColor extends Node {
    public DemosaicAndColor(int rid, String name) {
        super(rid, name);
    }
    @Override
    public void Run(BasePipeline pipeline) {
        startT();
        Node Previous = super.previousNode;
        GLProg glProg = GLInterface.i.glprogram;
        GLTexture glTexture;
        Parameters params = GLInterface.i.parameters;
        glTexture = new GLTexture(params.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16),GLInterface.i.inputRaw);
        glProg.setTexture("RawBuffer",glTexture);
        glProg.servar("RawSizeX",params.rawSize.x);
        glProg.servar("RawSizeY",params.rawSize.y);
        glProg.servar("CfaPattern",params.cfaPattern);
        glProg.servar("whiteLevel",(float)params.whitelevel);
        glProg.servar("blackLevel",params.blacklevel);
        //super.WorkingTexture = new GLTexture(params.rawSize,new GLFormat(GLFormat.DataType.UNSIGNED_8,4),null);
        endT();
    }
}
