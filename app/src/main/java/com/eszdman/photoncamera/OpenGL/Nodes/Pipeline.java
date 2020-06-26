package com.eszdman.photoncamera.OpenGL.Nodes;

import com.eszdman.photoncamera.OpenGL.GLContext;

import java.util.ArrayList;

public class Pipeline {
    ArrayList<Node> Nodes = new ArrayList<Node>();
    public Pipeline(){
        GLContext context = new GLContext(4000,3000);
        //Example Node
        add(new Demosaic());
    }
    void add(Node in){
        if(Nodes.size() != 0) in.previousNode = Nodes.get(Nodes.size()-1);
        Nodes.add(in);
    }
}
