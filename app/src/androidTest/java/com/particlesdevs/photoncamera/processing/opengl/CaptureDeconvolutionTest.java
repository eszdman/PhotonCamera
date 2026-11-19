package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Point;
import android.os.Bundle;
import android.util.Half;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.particlesdevs.photoncamera.util.BufferUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import static android.opengl.GLES31.*;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
public class CaptureDeconvolutionTest {
    private static final GLFormat RGBA = new GLFormat(GLFormat.DataType.FLOAT_16, 4);

    private static float[] read(GLTexture texture) {
        texture.BufferLoad();
        FloatBuffer data = texture.textureBuffer(new GLFormat(GLFormat.DataType.FLOAT_32, 4), true)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        float[] result = new float[data.remaining()];
        data.get(result);
        return result;
    }

    private static float[] pattern(int w, int h) {
        float[] data = new float[w*h*4];
        for (int y=0; y<h; y++) for (int x=0; x<w; x++) {
            float v=(float)(0.25+0.08*Math.sin(x*0.61)+0.06*Math.cos(y*0.47));
            int p=(y*w+x)*4;
            data[p]=Half.toFloat(Half.toHalf(v));
            data[p+1]=Half.toFloat(Half.toHalf(v*0.8f));
            data[p+2]=Half.toFloat(Half.toHalf(v*0.6f));
            data[p+3]=0.75f;
        }
        return data;
    }

    @Test public void matchesCpuSolverIncludingBandSeamsAndPartialWorkgroups() {
        int w=67,h=113;
        float[] data=pattern(w,h);
        try (GLContext context=new GLContext(1,1);
             GLTexture input=new GLTexture(new Point(w,h),RGBA,BufferUtils.getFrom(data));
             GLTexture output=new GLTexture(new Point(w,h),RGBA)) {
            double worst=0, sumError2=0; int compared=0;
            for (float sigma : new float[]{0.5f,0.75f,1f,1.5f,2f}) {
                for (int iterations : new int[]{0,1,10}) {
                    try (GpuCaptureDeconvolution filter=new GpuCaptureDeconvolution(
                            context.mProgram,input.mSize,sigma,iterations,31)) {
                        filter.render(input,output,1f,0f,1f);
                        float[] actual=read(output);
                        double[] expected=solve(data,w,h,sigma,iterations);
                        for(int p=0;p<w*h;p++) {
                            double before=data[p*4]*0.212671+data[p*4+1]*0.715160+data[p*4+2]*0.072169;
                            for(int c=0;c<3;c++) {
                                double error=Math.abs(data[p*4+c]*expected[p]/before-actual[p*4+c]);
                                worst=Math.max(worst,error);sumError2+=error*error;compared++;
                                assertTrue("Nonfinite output",Float.isFinite(actual[p*4+c]));
                            }
                            assertEquals(0.75f,actual[p*4+3],0f);
                        }
                        assertEquals(GL_NO_ERROR,glGetError());
                    }
                }
            }
            double rms=Math.sqrt(sumError2/compared);
            Log.i("CaptureAccuracy","max="+worst+" rms="+rms);
            assertTrue("FP16 maximum error="+worst,worst<0.003);
            assertTrue("FP16 RMS error="+rms,rms<0.001);
        }
    }

    @Test public void preservesFlatBlackAndClippedAreasAndAmountZero() {
        int w=47,h=79;
        float[] data=pattern(w,h);
        // Entire top portion black, flat middle, bright patch at bottom.
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int p=(y*w+x)*4;
            if(y<20) Arrays.fill(data,p,p+3,0f);
            else if(y<50) { data[p]=0.25f;data[p+1]=0.125f;data[p+2]=0.0625f; }
            else if(x>20 && x<30) Arrays.fill(data,p,p+3,1f);
        }
        try(GLContext context=new GLContext(1,1);
            GLTexture input=new GLTexture(new Point(w,h),RGBA,BufferUtils.getFrom(data));
            GLTexture output=new GLTexture(new Point(w,h),RGBA);
            GpuCaptureDeconvolution filter=new GpuCaptureDeconvolution(context.mProgram,input.mSize,0.75f,10,23)) {
            filter.render(input,output,0f,0.5f,1f);
            float[] bypass=read(output);
            for(int i=0;i<data.length;i++) assertEquals(data[i],bypass[i],0.0005f);
            filter.render(input,output,1f,0.5f,1f);
            float[] actual=read(output);
            float detailChange=0;
            for(int y=0;y<h;y++) for(int x=0;x<w;x++) for(int c=0;c<4;c++) {
                int p=(y*w+x)*4+c;
                assertTrue(Float.isFinite(actual[p]));
                if(y<10 || (y>30 && y<40) || (y>=52 && x>=19 && x<=31) || c==3)
                    assertEquals("Protected pixel="+p,data[p],actual[p],0.0005f);
                if(y>55 && x<15 && c<3) detailChange+=Math.abs(actual[p]-data[p]);
            }
            assertTrue("Default contrast mask must retain actual detail",detailChange>0.1f);
            try(GpuCaptureDeconvolution full=new GpuCaptureDeconvolution(
                    context.mProgram,input.mSize,0.75f,10,h)) {
                full.render(input,output,1f,0.5f,1f);
                float[] whole=read(output);
                for(int i=0;i<actual.length;i++) assertEquals("Masked band seam="+i,whole[i],actual[i],0.0005f);
            }
            assertEquals(GL_NO_ERROR,glGetError());
        }
    }

    @Test public void noiseModelProtectsFlatAreasAndKeepsRealDetail() {
        final int w=132,h=195;
        final float noiseS=0.0002f,noiseO=0.000002f;
        float[] data=new float[w*h*4];
        java.util.Random random=new java.util.Random(2147);
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            float level=new float[]{0.03f,0.25f,0.6f}[y/65];
            float detail=x>=w/2 ? 0.02f*(1+y/65)*(float)Math.sin(x*0.65) : 0f;
            float signal=level+detail;
            float value=Half.toFloat(Half.toHalf((float)(signal+random.nextGaussian()
                    *Math.sqrt(noiseS*signal+noiseO))));
            int p=(y*w+x)*4;
            data[p]=data[p+1]=data[p+2]=value;data[p+3]=0.75f;
        }
        try(GLContext context=new GLContext(1,1);
            GLTexture input=new GLTexture(new Point(w,h),RGBA,BufferUtils.getFrom(data));
            GLTexture output=new GLTexture(new Point(w,h),RGBA);
            GpuCaptureDeconvolution filter=new GpuCaptureDeconvolution(context.mProgram,input.mSize,0.75f,10,31)) {
            filter.render(input,output,1f,0f,1f);
            float[] unprotected=read(output);
            filter.render(input,output,1f,0.5f,1f,noiseS,noiseO);
            float[] protectedImage=read(output);
            for(int band=0;band<3;band++) {
                double before=flatVariance(data,w,band),after=flatVariance(protectedImage,w,band);
                double unprotectedVariance=flatVariance(unprotected,w,band);
                Log.i("CaptureNoise","band="+band+" adaptiveVarianceRatio="+(after/before)
                        +" unprotectedVarianceRatio="+(unprotectedVariance/before));
                assertTrue("Noise variance gain="+after/before,after<=before*1.05);
                assertTrue("Unmasked fixture must actually amplify noise",unprotectedVariance>before*1.4);
            }
            double beforeDetail=detailAmplitude(data,w,h),afterDetail=detailAmplitude(protectedImage,w,h);
            Log.i("CaptureNoise","detailGain="+afterDetail/beforeDetail);
            assertTrue("True texture should still sharpen",afterDetail>beforeDetail*1.03);
            filter.render(input,output,1f,0.5f,1f,noiseS*8,noiseO*8);
            float[] higherNoise=read(output);
            assertTrue("Increasing predicted noise must suppress changes",
                    meanChange(higherNoise,data)<meanChange(protectedImage,data));
            filter.render(input,output,1f,1f,1f,noiseS,noiseO);
            float[] disabled=read(output);
            for(int i=0;i<data.length;i++) assertEquals("Ratio one bypass",data[i],disabled[i],0.0005f);
            try(GpuCaptureDeconvolution full=new GpuCaptureDeconvolution(context.mProgram,input.mSize,0.75f,10,h)) {
                full.render(input,output,1f,0.5f,1f,noiseS,noiseO);
                float[] whole=read(output);
                for(int i=0;i<whole.length;i++) assertEquals("Adaptive band seam",whole[i],protectedImage[i],0.0005f);
            }
            assertEquals(GL_NO_ERROR,glGetError());
        }
    }

    private static double flatVariance(float[] data,int width,int band) {
        double sum=0,sum2=0;int count=0;
        for(int y=band*65+8;y<(band+1)*65-8;y++) for(int x=8;x<width/2-8;x++) {
            double v=data[(y*width+x)*4];sum+=v;sum2+=v*v;count++;
        }
        return sum2/count-Math.pow(sum/count,2);
    }
    private static double detailAmplitude(float[] data,int width,int height) {
        double sum=0;
        for(int y=8;y<height-8;y++) for(int x=width/2+8;x<width-8;x++)
            sum+=data[(y*width+x)*4]*Math.sin(x*0.65);
        return sum;
    }
    private static double meanChange(float[] a,float[] b) {
        double sum=0;for(int i=0;i<a.length;i+=4) sum+=Math.abs(a[i]-b[i]);return sum;
    }

    private static double[] solve(float[] rgb,int w,int h,float sigma,int iterations) {
        int r=GpuCaptureDeconvolution.kernelRadius(sigma);
        double support=r==1?2:r==2?6.3504:r==3?11.9025:r==4?20.25:36;
        double[][] kernel=new double[2*r+1][2*r+1]; double sum=0;
        for(int y=-r;y<=r;y++) for(int x=-r;x<=r;x++) if(x*x+y*y<=support) {
            kernel[y+r][x+r]=Math.exp(-(x*x+y*y)/(2.0*sigma*sigma));sum+=kernel[y+r][x+r];
        }
        for(double[] row:kernel) for(int x=0;x<row.length;x++) row[x]/=sum;
        double[] original=new double[w*h];
        for(int p=0;p<w*h;p++) original[p]=rgb[p*4]*0.212671+rgb[p*4+1]*0.715160+rgb[p*4+2]*0.072169;
        double[] estimate=original.clone();
        for(int i=0;i<iterations;i++) {
            double[] ratio=blur(estimate,w,h,kernel,r);
            for(int p=0;p<ratio.length;p++) ratio[p]=original[p]/Math.max(ratio[p],1e-7);
            double[] correction=blur(ratio,w,h,kernel,r);
            for(int p=0;p<estimate.length;p++) estimate[p]*=correction[p];
        }
        return estimate;
    }
    private static double[] blur(double[] in,int w,int h,double[][] kernel,int r) {
        double[] out=new double[in.length];
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) for(int dy=-r;dy<=r;dy++) for(int dx=-r;dx<=r;dx++) {
            int sy=Math.max(0,Math.min(h-1,y+dy)),sx=Math.max(0,Math.min(w-1,x+dx));
            out[y*w+x]+=in[sy*w+sx]*kernel[dy+r][dx+r];
        }
        return out;
    }

    private static void assertHealthyContext() {
        if (glGetString(GL_VERSION).contains("OpenGL ES 3.2")) {
            assertEquals("GPU context reset",GL_NO_ERROR,android.opengl.GLES32.glGetGraphicsResetStatus());
        }
    }

    @Test public void benchmark50MegapixelsTenIterations() throws Exception {
        Bundle args=InstrumentationRegistry.getArguments();
        assumeTrue("Enable with -e captureBenchmark true",Boolean.parseBoolean(args.getString("captureBenchmark","false")));
        int w=Integer.parseInt(args.getString("width","8192")),h=Integer.parseInt(args.getString("height","6144"));
        int bandRows=Integer.parseInt(args.getString("bandRows","512"));
        try(GLContext context=new GLContext(1,1);
            GLTexture input=new GLTexture(new Point(w,h),RGBA);
            GLTexture output=new GLTexture(new Point(w,h),RGBA)) {
            GLProg p=context.mProgram;
            p.useProgram("layout(local_size_x=16, local_size_y=16) in;\n"
                    +"precision highp float; precision highp int;\n"
                    +"layout(rgba16f,binding=0) writeonly uniform highp image2D destination;\n"
                    +"void main(){ ivec2 p=ivec2(gl_GlobalInvocationID.xy);"
                    +"if(any(greaterThanEqual(p,imageSize(destination)))) return;"
                    +"float v=0.3+0.1*sin(float(p.x)*0.31)+0.08*cos(float(p.y)*0.27);"
                    +"imageStore(destination,p,vec4(v,v*0.8,v*0.6,1.0)); }",true);
            glBindImageTexture(0,input.mTextureID,0,false,0,GL_WRITE_ONLY,GL_RGBA16F);
            glDispatchCompute((w+15)/16,(h+15)/16,1);
            glMemoryBarrier(GL_ALL_BARRIER_BITS);glFinish();
            long start=System.nanoTime();
            try(GpuCaptureDeconvolution filter=new GpuCaptureDeconvolution(p,input.mSize,0.75f,10,bandRows)) {
                filter.render(input,output,1f,0.5f,1f,0.0002f,0.000002f);glFinish();
                assertHealthyContext();
                double firstRenderIncludingSetupMs=(System.nanoTime()-start)/1e6;
                double[] times=new double[3];
                for(int round=0;round<times.length;round++) {
                    glFinish();start=System.nanoTime();
                    filter.render(input,output,1f,0.5f,1f,0.0002f,0.000002f);glFinish();
                    assertHealthyContext();
                    times[round]=(System.nanoTime()-start)/1e6;
                    assertEquals(GL_NO_ERROR,glGetError());
                }
                // Read small samples from every band, including the last partial workgroup.
                output.BufferLoad();
                ByteBuffer pixel=ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
                for(int y=0;y<h;y+=255) {
                    glReadPixels(w/2,y,1,1,GL_RGBA,GL_FLOAT,pixel);
                    assertTrue(Float.isFinite(pixel.getFloat(0)));
                    assertTrue("Output row="+y+" red="+pixel.getFloat(0)+" alpha="+pixel.getFloat(12),pixel.getFloat(0)>0f);
                    assertEquals(1f,pixel.getFloat(12),0f);
                }
                Arrays.sort(times);
                String json="{\"width\":"+w+",\"height\":"+h+",\"iterations\":10,\"sigma\":0.75,\"contrastRatio\":0.5,\"noiseS\":0.0002,\"noiseO\":0.000002,"
                        +"\"renderer\":\""+glGetString(GL_RENDERER)+"\",\"firstRenderIncludingSetupMs\":"+firstRenderIncludingSetupMs
                        +",\"medianMs\":"+times[1]+",\"samplesMs\":"+Arrays.toString(times)
                        +",\"scratchBytes\":"+filter.scratchBytes()+",\"bandRows\":"+bandRows+",\"scratchFormat\":\"RGBA16F\"}";
                Log.i("CaptureBenchmark",json);
                java.io.File file=new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"capture-deconvolution-benchmark.json");
                java.nio.file.Files.write(file.toPath(),json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                assertEquals(GL_NO_ERROR,glGetError());
            }
        }
    }
}
