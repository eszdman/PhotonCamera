package com.eszdman.photoncamera.OpenGL.Nodes;

import com.eszdman.photoncamera.OpenGL.Texture;

public class Node {
    public Texture WorkingTexture;
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
