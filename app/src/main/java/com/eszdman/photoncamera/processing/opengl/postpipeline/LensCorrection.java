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
        Parameters params = glInt.parameters;
        glProg.setTexture("InputBuffer",previousNode.WorkingTexture);
        glProg.setVar("intens",-.15f);
        glProg.setVar("start",0.5f);
        glProg.setVar("size",previousNode.WorkingTexture.mSize);
        GLTexture GainMapTex = new GLTexture(params.mapSize, new GLFormat(GLFormat.DataType.FLOAT_16,4), FloatBuffer.wrap(params.gainMap));
        glProg.setTexture("GainMap",GainMapTex);
        float br = 0.f;
        br = params.gainMap[0];
        float[][] compressedmap = new float[params.mapSize.x][params.mapSize.y];
        for(int y=0;y<params.mapSize.y; y++){
            for(int x=0; x<params.mapSize.x;x++){
                compressedmap[x][y] =
                        params.gainMap[y*params.mapSize.x*4 + x]+
                                params.gainMap[y*params.mapSize.x*4 + x+1]+
                                params.gainMap[y*params.mapSize.x*4 + x+2]+
                                params.gainMap[y*params.mapSize.x*4 + x+3];
            }
        }
        int maxrad = (int)(Math.sqrt(params.mapSize.x*params.mapSize.x + params.mapSize.y*params.mapSize.y))/2;
        float[] polar = new float[maxrad];
        for(int rad = 0; rad<params.mapSize.x/2;rad++){
            int cnt = 0;
            for(int pi = 0;pi<512;pi++){
                int xc = (int)((double)(params.mapSize.x)/2.0 + (Math.cos(pi*Math.PI/256.0)*rad));
                int yc = (int)((double)(params.mapSize.y)/2.0 + (Math.sin(pi*Math.PI/256.0)*rad));
                if(xc >= params.mapSize.x || yc >= params.mapSize.y || xc < 0 || yc < 0) continue;
                polar[rad] += compressedmap[xc][yc];
                cnt++;
            }
            polar[rad]/=cnt;
            Log.d(Name,"Polar["+rad+"]:"+polar[rad]);
        }

        GLTexture PolarMap = new GLTexture(new Point(maxrad,1), new GLFormat(GLFormat.DataType.FLOAT_16,1), FloatBuffer.wrap(polar));
        glProg.setTexture("PolarMap",PolarMap);
        for(int i =0; i<params.gainMap.length;i++){
            br=(br+params.gainMap[i])/2.f;
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
