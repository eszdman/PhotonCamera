package com.particlesdevs.photoncamera.processing.aparapi;

import android.graphics.Point;

import com.aparapi.Kernel;
import com.aparapi.Range;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static com.particlesdevs.photoncamera.processing.opengl.GLDrawParams.TileSize;

public class Align {
    private Point rawSize;
    private float[] inFrame;
    public short[][] input;
    public short[][] output;
    public Align(Point size){
        rawSize = size;
    }
    public void AlignFrame(ByteBuffer frameBuffer, float[] referenceFrame, int downsampleRate){
        if(inFrame == null) inFrame = new float[referenceFrame.length];
        FloatBuffer floatBuffer = frameBuffer.asFloatBuffer();
        Point binned = new Point(rawSize.x/2,rawSize.y/2);
        new Kernel(){
            @Override
            public void run() {
                int id = getGlobalId(0);
                inFrame[id] = floatBuffer.get(id);
            }
        }.execute(Range.create(referenceFrame.length));
        new Kernel(){
            private float geti(int x, int y){
                if(x > binned.x) x = binned.x;
                else if(x<0) x = 0;
                if(y > binned.y) y = binned.y;
                else if(y<0) y = 0;
                return inFrame[y*binned.x + x];
            }
            private float getr(int x, int y){
                if(x > binned.x) x = binned.x;
                else if(x<0) x = 0;
                if(y > binned.y) y = binned.y;
                else if(y<0) y = 0;
                return referenceFrame[y*binned.x + x];
            }
            @Override
            public void run() {
                int x = getGlobalId(0);
                int y = getGlobalId(1);

                x*=TileSize/2;
                y*=TileSize/2;
                float dist = 0.f;
                float mindist = Float.MAX_VALUE;
                for(int dx = -4;dx<8;dx++){
                    for(int dy = -4;dy<8;dy++){

                        for(int i =0; i<TileSize/2;i++){
                            for(int j =0;j<TileSize/2;j++){
                                dist+=geti(x+dx+i,y+dy+j)-getr(x+i,y+j);
                            }
                        }
                        if(dist < mindist){
                            mindist = dist;

                        }
                    }
                }
            }
        }.execute(Range.create2D(rawSize.x/TileSize,rawSize.y/TileSize));
    }
}
