package com.particlesdevs.photoncamera.processing.opengl;

import android.graphics.Point;
import android.os.Bundle;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.util.BufferUtils;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static android.opengl.GLES31.*;
import static org.junit.Assert.*;

/** Opt-in diagnostic: expose the production convolution without temporal blending. */
@RunWith(AndroidJUnit4.class)
public class CfaConvolutionDeviceTest {
    @Test public void renderConvolution() throws Exception {
        Bundle args=InstrumentationRegistry.getArguments();
        Assume.assumeTrue(Boolean.parseBoolean(args.getString("cfaConvolution","false")));
        File root=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"burst-replay");
        String input=args.getString("convolutionInput","conv-min");
        assertTrue(input.matches("[a-zA-Z0-9_-]+"));
        Point size=new Point(2048,1536),kernelSize=new Point(1024,768);
        byte[] raw=java.nio.file.Files.readAllBytes(new File(root,input+".u16").toPath());
        assertEquals(size.x*size.y*8,raw.length);
        ByteBuffer bytes=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        float[] packed=new float[size.x*size.y*4];
        for(int y=0;y<size.y*2;y++) for(int x=0;x<size.x*2;x++)
            packed[((y/2)*size.x+x/2)*4+(y%2)*2+x%2]=(bytes.getShort()&65535)/65535f;
        byte[] kernel=java.nio.file.Files.readAllBytes(new File(root,input+"-merge/kernels.f32").toPath());
        assertEquals(kernelSize.x*kernelSize.y*16,kernel.length);
        ByteBuffer kernelBytes=ByteBuffer.allocateDirect(kernel.length).order(ByteOrder.LITTLE_ENDIAN);
        kernelBytes.put(kernel).rewind();
        String code=PhotonCamera.getAssetLoader().getString("shaders/merge/mergeCombineWeight1.glsl");
        String output="imageStore(outTexture, xy, mix(base, diff, weight * comb));";
        assertTrue(code.contains(output));
        code=code.replace(output,"imageStore(outTexture, xy, localDiff2 / Z);");
        try(GLContext ctx=new GLContext(1,1);
            GLTexture src=new GLTexture(size,new GLFormat(GLFormat.DataType.FLOAT_16,4),BufferUtils.getFrom(packed));
            GLTexture dst=new GLTexture(size,new GLFormat(GLFormat.DataType.FLOAT_16,4),null);
            GLTexture kernels=new GLTexture(kernelSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),kernelBytes)) {
            GLProg p=ctx.mProgram;p.setLayout(8,8,1);p.setDefine("RESIDUAL_ROBUSTNESS",0);p.useProgram(code,true);
            p.setTexture("kernelsMap",kernels);p.setTextureCompute("inTexture",src,false);
            p.setTextureCompute("diffTexture",src,false);p.setTextureCompute("outTexture",dst,true);
            p.setVar("weight",.5f);p.setVar("noiseS",0f);p.setVar("noiseO",.0001f);p.setVar("exposure",1f);
            p.computeAuto(size,1);glFinish();assertEquals(GL_NO_ERROR,glGetError());
            // Float CPU readback does not allocate an RGBA32 texture.
            ByteBuffer result=ByteBuffer.allocateDirect(size.x*size.y*16).order(ByteOrder.nativeOrder());
            dst.BufferLoad();dst.textureBuffer(new GLFormat(GLFormat.DataType.FLOAT_32,4),result,0,0,size.x,size.y);
            assertEquals(GL_NO_ERROR,glGetError());
            try(FileOutputStream stream=new FileOutputStream(new File(root,input+"-convolution.f32"))) {
                result.rewind();while(result.hasRemaining())stream.getChannel().write(result);
            }
        }
    }
}
