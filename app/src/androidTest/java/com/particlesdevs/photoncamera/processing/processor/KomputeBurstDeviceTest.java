//
// Created by eszdman on 19.11.2026.
//

package com.particlesdevs.photoncamera.processing.processor;

import android.graphics.Point;
import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.particlesdevs.photoncamera.BuildConfig;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.postpipeline.PostPipeline;
import com.particlesdevs.photoncamera.processing.parameters.IsoExpoSelector;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.util.Allocator;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class KomputeBurstDeviceTest {
    @Test public void mergePackedAndUnpackedFramesForPostPipeline() {
        Assume.assumeTrue(BuildConfig.BUILD_ESD4D);
        final int width = 320, height = 192;
        Parameters p = new Parameters();
        p.rawSize = new Point(width, height);
        p.blackLevel = new float[]{64,64,64,64};
        p.whiteLevel = 1024;
        p.cfaPattern = 1;
        p.mapSize = new Point(1,1);
        p.gainMap = new float[]{1,1,1,1};
        p.iso = 100;
        Pair<Double,Double> n = new Pair<>(.0001,.00001);
        p.noiseModeler = new NoiseModeler(new Pair[]{n,n,n}, 800, 100, 1, null);
        ByteBuffer input = ByteBuffer.allocateDirect(width*height*2).order(ByteOrder.nativeOrder());
        for (int i=0; i<width*height; i++) input.putShort(i*2,(short)544);
        ArrayList<ImageFrame> frames = new ArrayList<>();
        KomputeBurst result = null;
        PostPipeline pipeline = new PostPipeline();
        try {
            for (int i=0; i<3; i++) {
                ImageFrame frame = new ImageFrame(input);
                frames.add(frame);
                frame.width = width;
                frame.height = height;
                frame.pair = new IsoExpoSelector.ExpoPair(10000,1000,1000000,100,25,800,100);
                if (i == 1) {
                    ByteBuffer packed = Allocator.packBits(frame.buffer,width*height,10,false);
                    assertNotNull(packed);
                    frame.close();
                    frame.buffer = packed;
                    frame.packedBits = 10;
                }
            }
            result = KomputeBurst.tryMerge(frames,p);
            assertNotNull("Test requires the Kompute backend and local alignment",result);
            for (ImageFrame frame : frames) assertNull(frame.buffer);
            assertEquals(width*height*2,result.bayer.capacity());
            java.nio.ShortBuffer pixels = result.bayer.order(ByteOrder.nativeOrder()).asShortBuffer();
            for (int i=0; i<pixels.capacity(); i++) assertEquals(0x3800,pixels.get(i),2);
            result.handOffKernels(pipeline);
            assertEquals(new Point(82,48),pipeline.kernelParamsSize);
            assertEquals(82*48*4,pipeline.kernelParams.capacity());
            for (int i=3; i<pipeline.kernelParams.capacity(); i+=4)
                assertEquals((short)0x3c00,pipeline.kernelParams.get(i));
        } finally {
            for (ImageFrame f : frames) if (f.buffer != null) f.close();
            if (result != null) {
                Allocator.free(result.bayer);
                result.releaseKernels();
            }
            if (pipeline.kernelParamsBase != null) Allocator.free(pipeline.kernelParamsBase);
        }
    }
}
