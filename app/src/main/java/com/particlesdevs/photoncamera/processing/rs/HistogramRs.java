package com.particlesdevs.photoncamera.processing.rs;

import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
import android.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;

public class HistogramRs {

    public static int[][] getHistogram(Bitmap inBit){
        RenderScript rs = PhotonCamera.getRenderScript();
        RUtils rUtils = new RUtils(rs);
        Allocation input = Allocation.createFromBitmap(rs,inBit);
        ScriptIntrinsicHistogram histogram = ScriptIntrinsicHistogram.create(rs, input.getElement());
        Allocation output = rUtils.allocateO(rUtils.CreateU32_4(256));
        histogram.setOutput(output);
        histogram.forEach(input);
        int[] outp = new int[output.getBytesSize()/4];
        output.copyTo(outp);
        int[][] outputArr = new int[4][256];

        for(int i =0; i<outp.length;i++){
            //if(i%4 == 3) continue;
            outputArr[i%4][i/4] = outp[i];
        }

        for(int i =0; i<outputArr[0].length;i++){
            if (i - 1 >= 0 && i + 1 < outputArr[0].length){
                outputArr[0][i] = (outputArr[0][i]*2 + outputArr[0][i+1] + outputArr[0][i-1])/4;
                outputArr[1][i] = (outputArr[1][i]*2 + outputArr[1][i+1] + outputArr[1][i-1])/4;
                outputArr[2][i] = (outputArr[2][i]*2 + outputArr[2][i+1] + outputArr[2][i-1])/4;
            }
        }
        int[] t;
        t = outputArr[2];
        outputArr[2] = outputArr[0];
        outputArr[0] = t;
        return outputArr;
    }
}
