package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;

public class AEC extends Node {
    public AEC(String name) {
        super("", name);
    }

    private final int SIZE = 256;

    private short[][] Histogram(Bitmap in) {
        short[][] brMap;
        brMap = new short[3][SIZE];
        for (int h = 0; h < in.getHeight(); h++) {
            for (int w = 0; w < in.getWidth(); w++) {
                int rgba = in.getPixel(w, h);
                int r = ((rgba) & 0xff);
                int g = ((rgba >> 8) & 0xff);
                int b = ((rgba >> 16) & 0xff);
                brMap[0][r]++;
                brMap[1][g]++;
                brMap[2][b]++;
            }
        }
        return brMap;
    }

    @Override
    public void Compile() {
    }

    private float MpyAEC(short[][] brH) {
        double avr = 0.0;
        int cnt = 0;
        for (int k = 0; k < 3; k++)
            for (int i = 70; i < 255; i++) {
                if (brH[k][i] < 1) continue;
                avr += brH[k][i];
                cnt++;
            }
        avr /= cnt;
        int ind80 = 204;
        int indo = -1;
        int indmax = -1;
        double tmax = -1.0;
        for (int i = 253; i >= 90; i--) {
            double br = (brH[0][i] + brH[1][i] + brH[2][i]) / 24.0;
            Log.d(Name, "AEC br:" + br);
            if (br > avr) {
                indo = i;
                break;
            }
            if (br > tmax) {
                tmax = br;
                indmax = i;
            }
        }
        Log.d(Name, "AEC avr:" + avr);
        Log.d(Name, "AEC ind:" + indo);
        Log.d(Name, "AEC max:" + indmax);
        if (indo > ind80 && (indmax > ind80 || indmax == -1)) return 1.f;
        if (indo > ind80) indo = indmax;
        float corr = ((float) ind80) / indo;
        if (indo == -1) corr = ((float) ind80) / indmax;
        if (indmax > ind80) corr = 1.f;
        Log.d(Name, "Corr:" + corr);
        return corr;
    }

    @Override
    public void Run() {
        GLTexture r0 = glUtils.interpolate(previousNode.WorkingTexture, new Point(previousNode.WorkingTexture.mSize.x / 8, previousNode.WorkingTexture.mSize.x / 8));
        float reg = ((PostPipeline) basePipeline).regenerationSense;
        GLTexture r2 = glUtils.mpy(r0, new float[]{reg, reg, reg});
        GLTexture r1 = glUtils.interpolate(r2, new Point(40, 40));
        //GLTexture r2 = glUtils.mpy(r1,new float[]{reg,reg,reg});
        GLFormat bitmapF = new GLFormat(GLFormat.DataType.UNSIGNED_8, 4);
        Bitmap preview = Bitmap.createBitmap(r1.mSize.x, r1.mSize.y, bitmapF.getBufferedImageConfig());
        preview.copyPixelsFromBuffer(glInt.glProcessing.drawBlocksToOutput(r1.mSize, bitmapF));
        if (basePipeline.mSettings.DebugData) glUtils.SaveProgResult(r1.mSize, "debAEC");
        ((PostPipeline) basePipeline).AecCorr = MpyAEC(Histogram(preview));
        WorkingTexture = previousNode.WorkingTexture;
        preview.recycle();
        glProg.closed = true;
    }
}
