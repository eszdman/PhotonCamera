package com.particlesdevs.photoncamera.processing.rs;

import android.graphics.Point;
import android.renderscript.Allocation;
import android.renderscript.Double2;
import android.renderscript.Int2;
import android.renderscript.RenderScript;

import com.particlesdevs.photoncamera.ScriptC_align;
import com.particlesdevs.photoncamera.ScriptC_gyro;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;

import java.util.stream.Stream;

public class GyroMap {
    private final RenderScript rs;
    private final Point kernelSize;
    private Point kernelCount;
    private final RUtils rUtils;
    private Point nsize;
    private ScriptC_gyro gyro;
    public GyroMap(Point kernelSize,Point kernelCount,Point nsize) {
        this.kernelSize = kernelSize;
        this.kernelCount = kernelCount;
        this.nsize = nsize;

        rs = PhotonCamera.getRenderScript();
        rUtils = new RUtils(rs);
        gyro = new ScriptC_gyro(rs);
    }
    public void FillKernels(ImageFrame input){
        Allocation move0,move1,move2;
        int temporal = input.frameGyro.movementss[0].length;
        move0 = rUtils.allocateIO(input.frameGyro.movementss[0],rUtils.CreateF16(temporal));
        move1 = rUtils.allocateIO(input.frameGyro.movementss[1],rUtils.CreateF16(temporal));
        move2 = rUtils.allocateIO(input.frameGyro.movementss[2],rUtils.CreateF16(temporal));
        gyro.bind_gyroSamples0(move0);
        gyro.bind_gyroSamples1(move1);
        gyro.bind_gyroSamples2(move2);
        gyro.set_kernelSize(new Int2(kernelSize.x,kernelSize.y));
        gyro.set_nsize(new Int2(nsize.x,nsize.y));
        gyro.set_temporalSize(temporal);
        gyro.set_perAngle(new Double2(PhotonCamera.getParameters().perXAngle,PhotonCamera.getParameters().perYAngle));
        Allocation gyroOut = rUtils.allocateO(rUtils.CreateU16(nsize, temporal));
        gyro.set_gyroOutput(gyroOut);
        gyroOut.copy3DRangeTo(0,0,0,nsize.x,nsize.y,temporal,input.BlurKernels);
    }
}
