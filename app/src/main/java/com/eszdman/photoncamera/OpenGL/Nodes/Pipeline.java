package com.eszdman.photoncamera.OpenGL.Nodes;

import android.graphics.Bitmap;
import com.eszdman.photoncamera.OpenGL.GLCoreBlockProcessing;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.Render.Parameters;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import static com.eszdman.photoncamera.api.ImageSaver.outimg;

public class Pipeline {
    ArrayList<Node> Nodes = new ArrayList<Node>();
    public Pipeline(ByteBuffer inBuffer, Parameters parameters){
        Bitmap output = Bitmap.createBitmap(parameters.rawSize.x,parameters.rawSize.y, Bitmap.Config.ARGB_8888);
        GLCoreBlockProcessing glproc = new GLCoreBlockProcessing(output);
        GLInterface glint = new GLInterface(glproc);
        glint.inputRaw = inBuffer;
        glint.parameters = parameters;
        //Example Node
        add(new Demosaic());
        Bitmap img = runAll();
        try {
            outimg.createNewFile();
            FileOutputStream fOut = new FileOutputStream(outimg);
            img.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
            fOut.flush();
            fOut.close();
            img.recycle();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    void add(Node in){
        if(Nodes.size() != 0) in.previousNode = Nodes.get(Nodes.size()-1);
        Nodes.add(in);
    }
    Bitmap runAll(){
        for(int i = 0; i<Nodes.size();i++){
            Nodes.get(i).Run();
        }
        GLInterface.i.glProc.drawBlocksToOutput();
        return GLInterface.i.glProc.mOut;
    }
}
