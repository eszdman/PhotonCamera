package com.eszdman.photoncamera.OpenGL.Nodes;

import java.util.ArrayList;

public class Pipeline {
    ArrayList<Node> Nodes = new ArrayList<Node>();
    public Pipeline(){
        //Example Node
        add(new Node());
        add(new Node());
    }
    void add(Node in){
        if(Nodes.size() != 0) in.previousNode = Nodes.get(Nodes.size()-1);
        Nodes.add(in);
    }
}
