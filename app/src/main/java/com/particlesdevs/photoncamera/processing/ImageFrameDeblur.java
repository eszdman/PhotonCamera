package com.particlesdevs.photoncamera.processing;

import android.graphics.Point;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.processing.rs.GyroMap;

public class ImageFrameDeblur {
    public GyroBurst firstFrameGyro;
    private GyroMap gyroMap;
    private Point size, kernelSize, kernelCount, nsize;
    private Parameters parameters;

    public ImageFrameDeblur() {
        parameters = PhotonCamera.getParameters();
        size = parameters.rawSize;
        nsize = new Point(size.x / 4, size.y / 4);
        kernelSize = new Point(64, 64);
        kernelCount = new Point(nsize.x / kernelSize.x, nsize.y / kernelSize.y);
        gyroMap = new GyroMap(kernelSize, kernelCount, nsize);
    }

    public void processDeblurPosition(ImageFrame in) {
        /*
        double xzoom = (Math.PI*2)/(parameters.angleX);
        double yzoom = (Math.PI*2)/(parameters.angleY);
        in.BlurKernels = new float[kernelCount.x][kernelCount.y][kernelSize.x*kernelSize.y];
        for(int xk = 0; xk<kernelCount.x;xk++) {
            for (int yk = 0; yk < kernelCount.y; yk++) {
                int x0 = xk * kernelSize.x + kernelSize.x / 2 - nsize.x / 2;
                int y0 = yk * kernelSize.y + kernelSize.y / 2 - nsize.y / 2;
                double xf = 0, yf = 0, zf = 0;
                for (int t = 0; t < in.frameGyro.movementss[0].length; t++) {
                    xf += in.frameGyro.movementss[0][t];
                    yf += in.frameGyro.movementss[1][t];
                    zf += in.frameGyro.movementss[2][t];
                    int x = (int) (x0 * Math.cos(zf) - y0 * Math.sin(zf)
                            //+ xf * parameters.perXAngle
                            + nsize.x*Math.sin(xf)*xzoom
                    );
                    int y = (int) (x0 * Math.sin(zf) + y0 * Math.cos(zf)
                            //+ yf * parameters.perYAngle
                            + nsize.y*Math.sin(yf)*yzoom
                    );
                    x %= kernelSize.x;
                    y %= kernelSize.y;
                    if (x < 0) x += kernelSize.x;
                    if (y < 0) y += kernelSize.y;
                    in.BlurKernels[xk][yk]
                            [x + y * kernelSize.x] += 1.0f;
                    //Log.d("ImageFrameDeblur","Added: x:"+x+" y:"+y+" xf: "+zf);
                }
            }
        }

        Log.d("ImageFrameDeblur","posxy:"+in.posx+","+in.posy);
            Bitmap kernels = Utilities.drawKernels(in.BlurKernels,kernelSize,kernelCount);
            Utilities.saveBitmap(kernels,"kernels");

         */
        double xf, yf, zf;
        xf = in.frameGyro.integrated[0];
        yf = in.frameGyro.integrated[1];
        zf = in.frameGyro.integrated[2];

        in.rX = xf;
        in.rY = yf;
        in.rZ = zf;
        in.posx = xf * parameters.perXAngle;
        in.posy = yf * parameters.perYAngle;
        in.rotation = zf;
    }
}
