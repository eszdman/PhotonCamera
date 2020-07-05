package com.eszdman.photoncamera.OpenGL.Nodes;

import android.graphics.Bitmap;
import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class BasePipeline {
    ArrayList<Node> Nodes = new ArrayList<Node>();
    void add(Node in){
        if(Nodes.size() != 0) in.previousNode = Nodes.get(Nodes.size()-1);
        Nodes.add(in);
    }
    Bitmap runAll(){
        for(int i = 0; i<Nodes.size();i++){
            Nodes.get(i).Compile();
            Nodes.get(i).Run(this);
        }
        GLInterface.i.glProc.drawBlocksToOutput(new GLFormat(GLFormat.DataType.UNSIGNED_8,4));
        return GLInterface.i.glProc.mOut;
    }
    ByteBuffer runAllRaw(){
        for(int i = 0; i<Nodes.size();i++){
            Nodes.get(i).Compile();
            Nodes.get(i).Run(this);
        }
        GLInterface.i.glProc.drawBlocksToOutput(new GLFormat(GLFormat.DataType.UNSIGNED_16));
        return GLInterface.i.glProc.mOutBuffer;
    }
}
