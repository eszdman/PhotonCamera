package com.eszdman.photoncamera.OpenGL.Nodes;

import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;

public class Demosaic extends Node {
    @Override
    public void Run() {
        Node Previous = super.previousNode;
        GLProg glProg = super.Program;
        GLTexture glTexture = super.WorkingTexture;
    }
}
