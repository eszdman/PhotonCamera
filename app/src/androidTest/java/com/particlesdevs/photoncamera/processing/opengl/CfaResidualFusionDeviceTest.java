package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Point;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.particlesdevs.photoncamera.util.BufferUtils;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import static android.opengl.GLES31.*;
import static org.junit.Assert.*;

/** Opt-in, balanced GPU pass benchmark at the tester burst's 4096x3072 size. */
@RunWith(AndroidJUnit4.class)
public class CfaResidualFusionDeviceTest {
    private static void bind(GLProg prog, int mode, Point half, GLTexture atlas,
                             GLTexture raw, GLTexture base, GLTexture source, GLTexture out) {
        bind(prog,mode==2?2:3,mode==1,mode>=2,half,atlas,raw,base,source,out);
    }
    private static void bind(GLProg prog, int sampling, boolean motionField,
                             boolean residual, Point half, GLTexture atlas,
                             GLTexture raw, GLTexture base, GLTexture source, GLTexture out) {
        prog.setDefine("TILE_AL",32); prog.setDefine("GYRO_WARP",0);
        prog.setDefine("HALIDE_SAMPLING",sampling); prog.setDefine("HALIDE_MOTION_FIELD",motionField?1:0);
        prog.setDefine("CFA_RESIDUAL_FUSION",residual?1:0);
        prog.setLayout(8,8,1); prog.useAssetProgram("merge/mergeAlign",true);
        prog.setVar("rawHalf",half); prog.setVar("cfaShift",new Point(0,0));
        prog.setVar("alignmentSize",atlas.mSize); prog.setVar("shift",new Point(0,0));
        prog.setVar("exposure",1f); prog.setVar("border",new Point(16,16));
        prog.setVar("noiseS",.0004f); prog.setVar("noiseO",.000001f); prog.setVar("minLevel",.001f);
        prog.setVar("cfaResidualStrength",.5f); prog.setVar("cfaNoiseS",.0004f); prog.setVar("cfaNoiseO",.000001f);
        prog.setTexture("inTexture",raw); prog.setTexture("alignmentTexture",atlas);
        prog.setTextureCompute("baseTexture",base,false); prog.setTextureCompute("alterTexture",source,false);
        prog.setTextureCompute("outTexture",out,true);
    }
    @Test public void benchmarkMergePass() {
        Assume.assumeTrue(Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("cfaResidualBenchmark","false")));
        benchmark(false);
    }
    @Test public void benchmarkCubicVersusLanczos() {
        Assume.assumeTrue(Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("cfaSamplingBenchmark","false")));
        benchmark(true);
    }
    private static void bindBenchmark(GLProg prog, boolean compareSampling, int mode, Point half,
                                      GLTexture atlas, GLTexture raw, GLTexture base,
                                      GLTexture source, GLTexture out) {
        if (compareSampling) {
            // 0/1: cubic/Lanczos with production defaults; 2/3: residual strength 0.5.
            bind(prog,3+mode%2,false,mode>=2,half,atlas,raw,base,source,out);
        } else {
            bind(prog,mode,half,atlas,raw,base,source,out);
        }
    }
    private static void benchmark(boolean compareSampling) {
        Point half=new Point(2048,1536),size=new Point(129,97);
        float[] data=new float[half.x*half.y*4];
        for(int y=0;y<half.y;y++) for(int x=0;x<half.x;x++) for(int c=0;c<4;c++) {
            if (compareSampling) {
                // The two green sites sample one continuous field at distinct CFA positions.
                double rx=2*x+c%2, ry=2*y+c/2;
                double gain=c==0?.65:c==3?.45:1.0;
                data[(y*half.x+x)*4+c]=(float)(gain*(.3+.08*Math.sin(.3*rx+.075*ry)));
            } else {
                data[(y*half.x+x)*4+c]=(float)(.2+.12*c+.06*Math.sin(.6*x+.15*y+c));
            }
        }
        float[] vectors=new float[size.x*size.y*4];
        try(GLContext context=new GLContext(1,1);
            GLTexture base=new GLTexture(half,new GLFormat(GLFormat.DataType.FLOAT_16,4),BufferUtils.getFrom(data));
            GLTexture source=new GLTexture(half,new GLFormat(GLFormat.DataType.FLOAT_16,4),BufferUtils.getFrom(data));
            GLTexture out=new GLTexture(half,new GLFormat(GLFormat.DataType.FLOAT_16,4),null);
            GLTexture raw=new GLTexture(new Point(4096,3072),new GLFormat(GLFormat.DataType.FLOAT_16,1),null);
            GLTexture atlas=new GLTexture(size,new GLFormat(GLFormat.DataType.FLOAT_16,4),null)) {
            if (compareSampling) Log.i("CfaSamplingBench","GPU renderer="+glGetString(GL_RENDERER));
            for(int field=0;field<2;field++) {
                for(int y=0;y<size.y;y++) for(int x=0;x<size.x;x++) {
                    float dx=.375f+(field==0?0:.11f*(x%7)+.031f*(y%5));
                    float dy=-.625f+(field==0?0:.073f*(x%5)-.089f*(y%7));
                    int o=(y*size.x+x)*4;
                    vectors[o]=(float)Math.floor(dx)/half.x;vectors[o+1]=(float)Math.floor(dy)/half.y;
                    vectors[o+2]=dx-(float)Math.floor(dx);vectors[o+3]=dy-(float)Math.floor(dy);
                }
                atlas.loadData(BufferUtils.getFrom(vectors));
                for(int mode=0;mode<4;mode++) {
                    bindBenchmark(context.mProgram,compareSampling,mode,half,atlas,raw,base,source,out);
                    for(int warm=0;warm<3;warm++) context.mProgram.computeAuto(half,1);
                    glFinish(); assertEquals(GL_NO_ERROR,glGetError());
                }
                for(int round=0;round<(compareSampling?8:6);round++) for(int i=0;i<4;i++) {
                    int mode=compareSampling?(round%2==0?i:3-i):(round+i)%4;
                    bindBenchmark(context.mProgram,compareSampling,mode,half,atlas,raw,base,source,out);glFinish();
                    long start=SystemClock.elapsedRealtimeNanos();
                    for(int repeat=0;repeat<3;repeat++) context.mProgram.computeAuto(half,1);
                    glFinish();
                    Log.i(compareSampling?"CfaSamplingBench":"CfaResidualBench","TIMING field="+field+" round="+round+" mode="+mode+
                            " per_frame_ms="+(SystemClock.elapsedRealtimeNanos()-start)/3_000_000.0);
                    assertEquals(GL_NO_ERROR,glGetError());
                }
            }
        }
    }
}
