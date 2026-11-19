package com.particlesdevs.photoncamera.processing.opengl;

import android.util.Half;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.particlesdevs.photoncamera.processing.cpu.HalideAlignment;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;
import static org.junit.Assert.*;

/** Exercise model fallback through JNI and the actual ARM kernels. */
@RunWith(AndroidJUnit4.class)
public class NoiseAwareAlignmentDeviceTest {
    private static Object call(String name, Class<?>[] signature, Object... values) throws Exception {
        Method method = HalideAlignment.class.getDeclaredMethod(name, signature);
        method.setAccessible(true);
        return method.invoke(null, values);
    }

    private static ByteBuffer raw(int seed, float shift) {
        Random random = new Random(seed);
        ByteBuffer pixels = ByteBuffer.allocateDirect(1024 * 768 * 2).order(ByteOrder.nativeOrder());
        for (int y = 0; y < 768; y++) for (int x = 0; x < 1024; x++) {
            double signal = .025 + .008 * Math.sin((x - shift) * .07)
                    + .006 * Math.cos(y * .11 + (x - shift) * .04);
            double noisy = signal + random.nextGaussian() * Math.sqrt(.0025 * signal + .00004624);
            pixels.putShort(Half.toHalf((float)Math.max(0, noisy)));
        }
        pixels.rewind();
        return pixels;
    }

    @Test public void missingInvalidAndOverflowingNoisePreserveOriginalAlignment() throws Exception {
        assertTrue(HalideAlignment.isAvailable());
        Class<?>[] flag = {boolean.class}, model = {long.class, float.class, float.class};
        Class<?>[] frame = {long.class, ByteBuffer.class, float.class};
        call("nSetRefineEnabled", flag, false);
        call("nSetAccurateSubpixelEnabled", flag, true);
        ByteBuffer base = raw(51, 0), alt = raw(52, 1.3f);
        try {
            for (boolean coarse : new boolean[]{false, true}) {
                call("nSetCoarseGlobalEnabled", flag, coarse);
                long ctx = (Long)call("nInit", new Class[]{int.class, int.class}, 1024, 768);
                assertNotEquals(0, ctx);
                try {
                    assertEquals(0, ((Integer)call("nBase", frame, ctx, base, 1f)).intValue());
                    for (float exposure : new float[]{.5f, 1f, 2f}) {
                        call("nSetNoiseModel", model, ctx, 0f, 0f);
                        float[] expected = (float[])call("nAlignFrame", frame, ctx, alt, exposure);
                        for (float value : expected) assertTrue(Float.isFinite(value));
                        for (float[] invalid : new float[][]{
                                {Float.NaN, .00004624f}, {.0025f, Float.NaN},
                                {Float.POSITIVE_INFINITY, 0}, {0, Float.NEGATIVE_INFINITY},
                                {-.001f, .00004624f}, {.0025f, -.0001f},
                                {Float.MAX_VALUE, Float.MAX_VALUE}, {0, 0}}) {
                            // Also verify that resetting a previously valid context
                            // does not retain its old noise coefficients.
                            call("nSetNoiseModel", model, ctx, .0025f, .00004624f);
                            call("nSetNoiseModel", model, ctx, invalid[0], invalid[1]);
                            assertArrayEquals(expected,
                                    (float[])call("nAlignFrame", frame, ctx, alt, exposure), 0f);
                        }
                    }
                } finally {
                    call("nRelease", new Class[]{long.class}, ctx);
                }
            }
        } finally {
            call("nSetCoarseGlobalEnabled", flag, false);
        }
    }
    private static double textureHash(int x, int y) {
        int h=x*374761393+y*668265263+197*224682251;
        h=(h^(h>>>13))*1274126177;
        return ((h^(h>>>16))&65535)/65535.0;
    }

    private static double texture(double x, double y) {
        int ix=(int)Math.floor(x),iy=(int)Math.floor(y);
        double u=x-ix,v=y-iy;
        u=u*u*(3-2*u);v=v*v*(3-2*v);
        return (textureHash(ix,iy)*(1-u)+textureHash(ix+1,iy)*u)*(1-v)
                +(textureHash(ix,iy+1)*(1-u)+textureHash(ix+1,iy+1)*u)*v;
    }

    private static ByteBuffer translatedScene(int w, int h, double dx, double dy, int seed) {
        Random random = new Random(seed);
        ByteBuffer pixels = ByteBuffer.allocateDirect(w * h * 2).order(ByteOrder.nativeOrder());
        double[] gain={.8,1,1,.65};
        for (int y = 0; y < h; ++y) for (int x = 0; x < w; ++x) {
            double xx=x-dx, yy=y-dy;
            // Nonperiodic features at several scales make translation identifiable
            // in the coarsest level too. Sample both frames independently.
            double signal=.12+.18*texture(xx/96,yy/96)+.16*texture(xx/48+27,yy/48+18)
                    +.15*texture(xx/24,yy/24)+.12*texture(xx/8+14,yy/8+71)
                    +.055*texture(xx/3+5,yy/3+10);
            signal *= gain[(x&1)+2*(y&1)];
            double value=signal+random.nextGaussian()*Math.sqrt(.0001*signal+.0000005);
            pixels.putShort(Half.toHalf((float)Math.max(0,value)));
        }
        pixels.rewind();return pixels;
    }

    @Test public void precisePyramidAlignsKnownMotionAndKeepsTheGrid() throws Exception {
        assertTrue(HalideAlignment.isAvailable());
        Class<?>[] flag={boolean.class}, frame={long.class,ByteBuffer.class,float.class};
        Class<?>[] model={long.class,float.class,float.class};
        call("nSetRefineEnabled",flag,false);call("nSetAccurateSubpixelEnabled",flag,true);
        call("nSetCoarseGlobalEnabled",flag,false);
        double[] errors=new double[2];int count=0;
        // Include dimensions that do not divide the coarsest tile or SIMD width.
        for (int[] size:new int[][]{{1024,768},{1000,756}}) {
            int w=size[0],h=size[1],nx=(w/4-16)/8+1,ny=(h/4-16)/8+1;
            for (double[] shift:new double[][]{{1.25,-.75},{17.25,-3.5},{-5.2,6.4}}) {
                ByteBuffer base=translatedScene(w,h,0,0,91),alt=translatedScene(w,h,shift[0],shift[1],92);
                for(int mode=0;mode<2;mode++) {
                    long ctx=(Long)call("nInit",new Class[]{int.class,int.class},w,h);
                    try {
                        call("nSetNoiseModel",model,ctx,mode==0?0f:.0001f,mode==0?0f:.0000005f);
                        assertEquals(0,((Integer)call("nBase",frame,ctx,base,1f)).intValue());
                        float[] actual=(float[])call("nAlignFrame",frame,ctx,alt,1f);
                        assertEquals(2*nx*ny,actual.length);
                        for(float v:actual) assertTrue(Float.isFinite(v));
                        for(int y=2;y<ny-2;y++)for(int x=2;x<nx-2;x++) {
                            int i=y*nx+x;
                            errors[mode]+=Math.hypot(2*actual[i]-shift[0],2*actual[nx*ny+i]-shift[1]);
                            if(mode==0)count++;
                        }
                        if(mode==1) {
                            // A prepared precise pyramid must never be consumed
                            // by the original u8 kernel after the model is cleared.
                            call("nSetNoiseModel",model,ctx,0f,0f);
                            float[] cleared=(float[])call("nAlignFrame",frame,ctx,alt,1f);
                            for(float v:cleared)assertTrue(Float.isFinite(v));
                        }
                    } finally {call("nRelease",new Class[]{long.class},ctx);}
                }
            }
        }
        errors[0]/=count;errors[1]/=count;
        android.util.Log.i("NoiseAwareAlignmentTest","Known-motion EPE original="+errors[0]+" precise="+errors[1]);
        assertTrue("Known raw-pixel displacement",errors[1]<.5);
        assertTrue("New pyramid improves independent fractional samples",errors[1]<errors[0]);
    }

}
