package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Point;
import android.os.SystemClock;
import android.util.Half;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.cpu.HalideAlignment;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.util.BufferUtils;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.stream.Collectors;
import static org.junit.Assert.*;
import static android.opengl.GLES31.*;

@RunWith(AndroidJUnit4.class)
public class HalideSubpixelDeviceTest {
    private static final String TAG="HalideSubpixelTest";
    private static final int W=512,H=512;
    private static String originalCubic;

    private static String originalCubic() {
        if(originalCubic==null) {
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(
                    InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                            .open("shaders/mergeAlignCubicReference.glsl")))) {
                originalCubic=reader.lines().collect(Collectors.joining("\n"));
            } catch(IOException e) {throw new IllegalStateException(e);}
        }
        return originalCubic;
    }

    private static float scene(double x,double y,int c) {
        int color=c==2?1:c;
        return (float)(0.2+0.13*color+0.07*Math.sin(x*0.34+y*0.12)
                +0.05*Math.cos(y*0.27)+0.04*Math.sin(x*0.13-y*0.08));
    }

    private static ImageFrame frame(float dx,float dy) {
        ByteBuffer pixels=ByteBuffer.allocateDirect(W*H*2).order(ByteOrder.nativeOrder());
        for(int y=0;y<H;y++) for(int x=0;x<W;x++)
            pixels.putShort(Half.toHalf(scene(x-dx,y-dy,(x&1)+2*(y&1))));
        pixels.rewind();
        ImageFrame frame=new ImageFrame(pixels);
        frame.width=W;frame.height=H;frame.fp16=true;
        frame.pair=new IsoExpoSelector.ExpoPair(10_000_000,1_000_000,100_000_000,100,100,6400,100);
        frame.pair.layerMpy=1;
        frame.pair.curlayer=IsoExpoSelector.ExpoPair.exposureLayer.Normal;
        return frame;
    }

    private static float[] packed(ImageFrame frame) {
        float[] values=new float[W*H];
        ByteBuffer raw=frame.buffer.duplicate().order(ByteOrder.nativeOrder());
        for(int y=0;y<H/2;y++) for(int x=0;x<W/2;x++) for(int c=0;c<4;c++)
            values[(y*(W/2)+x)*4+c]=Half.toFloat(raw.getShort(((2*y+c/2)*W+2*x+c%2)*2));
        return values;
    }

    private static void bind(GLProg prog,int mode,int tile,Point rawHalf,GLTexture atlas,
                             GLTexture raw,GLTexture base,GLTexture source,GLTexture out) {
        prog.setDefine("TILE_AL",tile);prog.setDefine("GYRO_WARP",0);
        prog.setDefine("HALIDE_SAMPLING",mode==4?3:mode);
        prog.setLayout(8,8,1);
        if(mode==4) prog.useProgram(originalCubic(),true);
        else prog.useAssetProgram("merge/mergeAlign",true);
        prog.setVar("rawHalf",rawHalf);prog.setVar("cfaShift",new Point(0,0));
        prog.setVar("alignmentSize",atlas.mSize);prog.setVar("shift",new Point(0,0));
        prog.setVar("exposure",1f);prog.setVar("border",new Point(16,16));
        prog.setVar("noiseS",0.01f);prog.setVar("noiseO",0.0001f);prog.setVar("minLevel",0.001f);
        prog.setTexture("inTexture",raw);prog.setTexture("alignmentTexture",atlas);
        prog.setTextureCompute("baseTexture",base,false);
        prog.setTextureCompute("alterTexture",source,false);
        prog.setTextureCompute("outTexture",out,true);
    }

    @Test public void nativeFractionalVectorsImproveTheMergedImage() {
        assertTrue(HalideAlignment.isAvailable());
        try(GLContext context=new GLContext(1,1)) {
            for(boolean refine:new boolean[]{false,true}) {
                double[] errors=new double[5];
                int fractional=0,total=0;
                for(float[] delta:new float[][]{{1.25f,-0.75f},{-0.65f,1.4f},{2.75f,3.25f}}) {
                    ArrayList<ImageFrame> frames=new ArrayList<>();
                    frames.add(frame(0,0));frames.add(frame(delta[0],delta[1]));
                    int tile=refine?16:32;
                    Parameters p=new Parameters();p.rawSize=new Point(W,H);p.tile=16;p.tilesX=1;
                    p.alignmentSize=new Point(W/tile+1,H/tile+1);
                    try(HalideAlignment align=new HalideAlignment(p.alignmentSize,frames)) {
                        align.parameters=p;align.refineFlow=refine;align.Run();
                        try {
                            align.Result.BufferLoad();
                            FloatBuffer vectors=align.Result.textureBuffer(new GLFormat(GLFormat.DataType.FLOAT_32,4),true)
                                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
                            for(int y=3;y<p.alignmentSize.y-3;y++) for(int x=3;x<p.alignmentSize.x-3;x++) {
                                int o=(y*p.alignmentSize.x+x)*4;
                                float dx=Math.round(vectors.get(o)*(W/2))+vectors.get(o+2);
                                float dy=Math.round(vectors.get(o+1)*(H/2))+vectors.get(o+3);
                                if(Math.abs(dx-Math.round(dx))>0.03 || Math.abs(dy-Math.round(dy))>0.03) fractional++;
                                total++;
                            }
                            Point half=new Point(W/2,H/2);
                            float[] truth=packed(frames.get(0));
                            try(GLTexture base=new GLTexture(half,new GLFormat(GLFormat.DataType.FLOAT_16,4),BufferUtils.getFrom(truth));
                                GLTexture source=new GLTexture(half,new GLFormat(GLFormat.DataType.FLOAT_16,4),BufferUtils.getFrom(packed(frames.get(1))));
                                GLTexture out=new GLTexture(half,new GLFormat(GLFormat.DataType.FLOAT_16,4),null);
                                GLTexture raw=new GLTexture(p.rawSize,new GLFormat(GLFormat.DataType.FLOAT_16,1),null)) {
                                FloatBuffer optimizedPixels=null;
                                for(int mode=0;mode<5;mode++) {
                                    bind(context.mProgram,mode,tile,half,align.Result,raw,base,source,out);
                                    context.mProgram.computeAuto(half,1);out.BufferLoad();
                                    FloatBuffer actual=out.textureBuffer(new GLFormat(GLFormat.DataType.FLOAT_32,4),true)
                                            .order(ByteOrder.nativeOrder()).asFloatBuffer();
                                    if(mode==3) optimizedPixels=actual;
                                    if(mode==4) {
                                        // R/B retain the old optimized cubic. Greens now
                                        // use the quincunx lattice and intentionally differ.
                                        for(int i=0;i<truth.length;i++) if(i%4==0 || i%4==3)
                                            assertEquals("Old/new cubic R/B at "+i,actual.get(i),optimizedPixels.get(i),0.0005f);
                                    }
                                    int samples=0;double error=0;
                                    for(int y=48;y<half.y-48;y++) for(int x=48;x<half.x-48;x++) for(int c=0;c<4;c++) {
                                        int o=(y*half.x+x)*4+c;
                                        float value=actual.get(o);assertTrue(Float.isFinite(value));
                                        assertTrue("Finite fp16 reference",Float.isFinite(truth[o]));
                                        double d=value-truth[o];error+=d*d;samples++;
                                    }
                                    errors[mode]+=error/samples;
                                }
                            }
                        } finally {align.Result.close();}
                    } finally {for(ImageFrame frame:frames) frame.close();}
                }
                // The hierarchical confidence gate can return integer vectors for an
                // entire pair. Across these pairs we must still exercise real fractions.
                assertTrue("Halide should supply fractional displacements",fractional>total/3);
                for(int mode=0;mode<5;mode++) errors[mode]=Math.sqrt(errors[mode]/3);
                Log.i(TAG,"NATIVE refine="+refine+" RMSE [floor,nearest,bilinear,cubic,original_cubic]="+Arrays.toString(errors));
                assertTrue("Fractional CFA sampling should beat discarded fractions",errors[3]<errors[0]);
                // Coarse vector error can dominate either interpolation kernel.
                // Compare reconstruction sharpness after subpixel flow refinement.
                if(refine) assertTrue("Cubic should retain more detail than bilinear",errors[3]<errors[2]);
            }
            assertEquals(GL_NO_ERROR,glGetError());
        }
    }

    @Test public void sameGridSubpixelSwitchImprovesAlignment() {
        assertTrue(HalideAlignment.isAvailable());
        double[] errors=new double[2];
        try(GLContext context=new GLContext(1,1)) {
            for(int mode=0;mode<2;mode++) {
                int count=0;
                for(float[] delta:new float[][]{{1.25f,-0.75f},{-0.65f,1.4f},{2.75f,3.25f}}) {
                    ArrayList<ImageFrame> frames=new ArrayList<>();
                    frames.add(frame(0,0));frames.add(frame(delta[0],delta[1]));
                    Parameters p=new Parameters();p.rawSize=new Point(W,H);p.tile=16;p.tilesX=1;
                    p.alignmentSize=new Point(W/32+1,H/32+1);
                    try(HalideAlignment align=new HalideAlignment(p.alignmentSize,frames)) {
                        align.parameters=p;align.refineFlow=false;align.accurateSubpixel=mode==1;align.Run();
                        try {
                            assertEquals("Same coarse atlas width",p.alignmentSize.x,align.Result.mSize.x);
                            assertEquals("Same coarse atlas height",p.alignmentSize.y,align.Result.mSize.y);
                            align.Result.BufferLoad();
                            FloatBuffer vectors=align.Result.textureBuffer(new GLFormat(GLFormat.DataType.FLOAT_32,4),true)
                                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
                            for(int y=3;y<p.alignmentSize.y-3;y++) for(int x=3;x<p.alignmentSize.x-3;x++) {
                                int o=(y*p.alignmentSize.x+x)*4;
                                float dx=2*(Math.round(vectors.get(o)*(W/2))+vectors.get(o+2));
                                float dy=2*(Math.round(vectors.get(o+1)*(H/2))+vectors.get(o+3));
                                assertTrue(Float.isFinite(dx)&&Float.isFinite(dy));
                                errors[mode]+=Math.hypot(dx-delta[0],dy-delta[1]);count++;
                            }
                        } finally {align.Result.close();}
                    } finally {for(ImageFrame frame:frames)frame.close();}
                }
                errors[mode]/=count;
            }
            Log.i(TAG,"SAME_GRID mean raw-pixel EPE [original,accurate]="+Arrays.toString(errors));
            assertTrue("Subpixel fit improves the original field at unchanged density",errors[1]<errors[0]);
            assertEquals(GL_NO_ERROR,glGetError());
        }
    }

    @Test public void denseRefinementKeepsItsOriginalSeed() {
        assertTrue(HalideAlignment.isAvailable());
        try(GLContext context=new GLContext(1,1)) {
            ArrayList<ImageFrame> frames=new ArrayList<>();
            frames.add(frame(0,0));frames.add(frame(1.25f,-0.75f));
            Parameters p=new Parameters();p.rawSize=new Point(W,H);p.tile=16;p.tilesX=1;
            p.alignmentSize=new Point(W/16+1,H/16+1);
            float[] reference=null;
            try {
                for(boolean accurate:new boolean[]{false,true}) {
                    try(HalideAlignment align=new HalideAlignment(p.alignmentSize,frames)) {
                        align.parameters=p;align.refineFlow=true;align.accurateSubpixel=accurate;align.Run();
                        try {
                            align.Result.BufferLoad();
                            FloatBuffer values=align.Result.textureBuffer(new GLFormat(GLFormat.DataType.FLOAT_32,4),true)
                                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
                            float[] actual=new float[values.remaining()];values.get(actual);
                            if(reference==null)reference=actual;
                            else assertArrayEquals("Coarse-fit switch must not alter dense refinement",reference,actual,0f);
                        } finally {align.Result.close();}
                    }
                }
            } finally {for(ImageFrame frame:frames)frame.close();}
        }
    }

    /** Opt-in isolated full-resolution GPU timing; no capture or settings changes. */
    @Test public void benchmarkSamplingCost() {
        Assume.assumeTrue(Boolean.parseBoolean(InstrumentationRegistry.getArguments().getString("halideSamplingBenchmark","false")));
        Point half=new Point(2048,1152),atlasSize=new Point(4096/32+1,2304/32+1);
        float[] sourceData=new float[half.x*half.y*4],baseData=new float[sourceData.length];
        for(int y=0;y<half.y;y++) for(int x=0;x<half.x;x++) for(int c=0;c<4;c++) {
            sourceData[(y*half.x+x)*4+c]=scene(2*x-0.75,2*y+1.25,c);
            baseData[(y*half.x+x)*4+c]=scene(2*x,2*y,c);
        }
        float[] atlasData=new float[atlasSize.x*atlasSize.y*4];
        for(int i=0;i<atlasData.length;i+=4) {
            atlasData[i+1]=-1f/half.y;atlasData[i+2]=0.375f;atlasData[i+3]=0.375f;
        }
        try(GLContext context=new GLContext(1,1);
            GLTexture base=new GLTexture(half,new GLFormat(GLFormat.DataType.FLOAT_16,4),BufferUtils.getFrom(baseData));
            GLTexture source=new GLTexture(half,new GLFormat(GLFormat.DataType.FLOAT_16,4),BufferUtils.getFrom(sourceData));
            GLTexture out=new GLTexture(half,new GLFormat(GLFormat.DataType.FLOAT_16,4),null);
            GLTexture raw=new GLTexture(new Point(half.x*2,half.y*2),new GLFormat(GLFormat.DataType.FLOAT_16,1),null);
            GLTexture atlas=new GLTexture(atlasSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),BufferUtils.getFrom(atlasData))) {
            for(String field:new String[]{"constant","varying"}) {
                for(int y=0;y<atlasSize.y;y++) for(int x=0;x<atlasSize.x;x++) {
                    float dx=0.375f,dy=-0.625f;
                    if(field.equals("varying")) {
                        dx += 0.11f*(x%7)+0.031f*(y%5);
                        dy += 0.073f*(x%5)-0.089f*(y%7);
                    }
                    int o=(y*atlasSize.x+x)*4;
                    atlasData[o]=(float)Math.floor(dx)/half.x;
                    atlasData[o+1]=(float)Math.floor(dy)/half.y;
                    atlasData[o+2]=dx-(float)Math.floor(dx);
                    atlasData[o+3]=dy-(float)Math.floor(dy);
                }
                atlas.loadData(BufferUtils.getFrom(atlasData));
                for(int mode=0;mode<5;mode++) {
                    bind(context.mProgram,mode,32,half,atlas,raw,base,source,out);
                    for(int warm=0;warm<3;warm++) context.mProgram.computeAuto(half,1);
                    glFinish();
                }
                for(int round=0;round<5;round++) for(int i=0;i<5;i++) {
                    int mode=(i+round)%5;
                    bind(context.mProgram,mode,32,half,atlas,raw,base,source,out);glFinish();
                    long start=SystemClock.elapsedRealtimeNanos();
                    for(int frame=0;frame<5;frame++) context.mProgram.computeAuto(half,1);
                    glFinish();
                    Log.i(TAG,"TIMING field="+field+" round="+round+" mode="+mode+" per_frame_ms="
                            +(SystemClock.elapsedRealtimeNanos()-start)/5_000_000.0);
                }
            }
            assertEquals(GL_NO_ERROR,glGetError());
        }
    }
}
