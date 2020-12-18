package com.eszdman.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import android.util.Log;

import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.processing.opengl.GLFormat;
import com.eszdman.photoncamera.processing.opengl.GLTexture;
import com.eszdman.photoncamera.processing.opengl.nodes.Node;
import com.eszdman.photoncamera.processing.render.Parameters;

import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

public class LensCorrection extends Node {

    public LensCorrection() {
        super(R.raw.lenscorrection, "LensCorrection");
    }

    @Override
    public void Run() {
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.setVar("intens",-.15f);
        glProg.setVar("start",0.5f);
        glProg.setVar("size",previousNode.WorkingTexture.mSize);
        GLTexture GainMapTex = new GLTexture(basePipeline.mParameters.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16,4),
                FloatBuffer.wrap(basePipeline.mParameters.gainMap));
        glProg.setTexture("GainMap",GainMapTex);
        float br = 0.f;
        br = basePipeline.mParameters.gainMap[0];
        float[][] compressedmap = new float[basePipeline.mParameters.mapSize.x][basePipeline.mParameters.mapSize.y];
        for(int y=0;y<basePipeline.mParameters.mapSize.y; y++){
            for(int x=0; x<basePipeline.mParameters.mapSize.x;x++){
                compressedmap[x][y] =
                        basePipeline.mParameters.gainMap[y*basePipeline.mParameters.mapSize.x*4 + x]+
                                basePipeline.mParameters.gainMap[y*basePipeline.mParameters.mapSize.x*4 + x+1]+
                                basePipeline.mParameters.gainMap[y*basePipeline.mParameters.mapSize.x*4 + x+2]+
                                basePipeline.mParameters.gainMap[y*basePipeline.mParameters.mapSize.x*4 + x+3];
            }
        }
        int maxrad = (int)(Math.sqrt(basePipeline.mParameters.mapSize.x*basePipeline.mParameters.mapSize.x +
                basePipeline.mParameters.mapSize.y*basePipeline.mParameters.mapSize.y))/2;

        float[] polar = new float[maxrad];
        for(int rad = 0; rad<basePipeline.mParameters.mapSize.x/2;rad++){
            int cnt = 0;
            for(int pi = 0;pi<512;pi++){
                int xc = (int)((double)(basePipeline.mParameters.mapSize.x)/2.0 + (Math.cos(pi*Math.PI/256.0)*rad));
                int yc = (int)((double)(basePipeline.mParameters.mapSize.y)/2.0 + (Math.sin(pi*Math.PI/256.0)*rad));
                if(xc >= basePipeline.mParameters.mapSize.x || yc >= basePipeline.mParameters.mapSize.y || xc < 0 || yc < 0) continue;
                polar[rad] += compressedmap[xc][yc];
                cnt++;
            }
            polar[rad]/=cnt;
            Log.d(Name,"Polar["+rad+"]:"+polar[rad]);
        }

        GLTexture PolarMap = new GLTexture(new Point(maxrad,1), new GLFormat(GLFormat.DataType.FLOAT_16,1), FloatBuffer.wrap(polar));
        glProg.setTexture("PolarMap",PolarMap);
        for(int i =0; i<basePipeline.mParameters.gainMap.length;i++){
            br=(br+basePipeline.mParameters.gainMap[i])/2.f;
        }
        br = polar[0];
        for (float v : polar) br = (br + v) / 2.f;
        Log.d(Name,"avrbr:"+br);
        glProg.setVar("avrbr",br);
        WorkingTexture = basePipeline.getMain();
        //glProg.drawBlocks(WorkingTexture);
        //WorkingTexture = glUtils.blur(WorkingTexture,1.5);
        //glProg.close();
    }
}
