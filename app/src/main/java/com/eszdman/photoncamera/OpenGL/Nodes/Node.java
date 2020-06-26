package com.eszdman.photoncamera.OpenGL.Nodes;

import com.eszdman.photoncamera.OpenGL.GLProg;
import com.eszdman.photoncamera.OpenGL.GLTexture;

public class Node {
    public GLTexture WorkingTexture;
    public GLProg Program;
    public String Name = "Node";
    public Node previousNode;
    public Node(String name){
        Name = name;
    }
    public Node(){

    }
    public void Run(){

    }
}
