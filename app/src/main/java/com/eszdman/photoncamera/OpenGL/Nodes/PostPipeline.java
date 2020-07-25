package com.eszdman.photoncamera.OpenGL.Nodes;

import android.graphics.Bitmap;

import com.eszdman.photoncamera.OpenGL.GLCoreBlockProcessing;
import com.eszdman.photoncamera.OpenGL.GLFormat;
import com.eszdman.photoncamera.OpenGL.GLInterface;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.Render.Parameters;
import com.eszdman.photoncamera.api.Interface;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import static com.eszdman.photoncamera.api.ImageSaver.outimg;

public class PostPipeline extends BasePipeline {
    public int selectSharp(){
        long resolution = glint.parameters.rawSize.x*glint.parameters.rawSize.y;
        int output = R.raw.sharpen33;
        if(resolution >= 16000000) output = R.raw.sharpen55;
        return output;
    }
    public void Run(ByteBuffer inBuffer, Parameters parameters){
        Bitmap output = Bitmap.createBitmap(parameters.rawSize.x,parameters.rawSize.y, Bitmap.Config.ARGB_8888);
        GLCoreBlockProcessing glproc = new GLCoreBlockProcessing(parameters.rawSize,output, new GLFormat(GLFormat.DataType.UNSIGNED_8,4));
        glint = new GLInterface(glproc);
        glint.inputRaw = inBuffer;
        glint.parameters = parameters;
        if(Interface.i.settings.cfaPattern != -2) {
            add(new DemosaicPart1(R.raw.demosaicp1, "Demosaic Part 1"));
            add(new DemosaicPart2(R.raw.demosaicp2, "Demosaic Part 2"));
        } else {
            add(new MonoDemosaic(R.raw.monochrome, "Monochrome"));
        }
        add(new Initial(R.raw.initial,"Initial"));
        if(Interface.i.settings.hdrxNR) {
            add(new BilateralColor(R.raw.bilateralcolor, "BilateralColor"));
            add(new Bilateral(R.raw.bilateral, "Bilateral"));
        }

        add(new Sharpen(selectSharp(),"Sharpening"));
        Bitmap img = runAll();
        try {
            outimg.createNewFile();
            FileOutputStream fOut = new FileOutputStream(outimg);
            img.compress(Bitmap.CompressFormat.JPEG, 97, fOut);
            fOut.flush();
            fOut.close();
            img.recycle();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
