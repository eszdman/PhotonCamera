package com.eszdman.photoncamera.OpenGL.Nodes;

import android.graphics.Bitmap;
import com.eszdman.photoncamera.OpenGL.GLCoreBlockProcessing;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.Render.Parameters;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import static com.eszdman.photoncamera.api.ImageSaver.outimg;

public class Pipeline extends BasePipeline {
    public void Run(ByteBuffer inBuffer, Parameters parameters){
        Bitmap output = Bitmap.createBitmap(parameters.rawSize.x,parameters.rawSize.y, Bitmap.Config.ARGB_8888);
        GLCoreBlockProcessing glproc = new GLCoreBlockProcessing(parameters.rawSize,output);
        GLInterface glint = new GLInterface(glproc);
        glint.inputRaw = inBuffer;
        glint.parameters = parameters;
        add(new DemosaicAndColor(R.raw.initial,"DemosaicAndColor"));
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
}
