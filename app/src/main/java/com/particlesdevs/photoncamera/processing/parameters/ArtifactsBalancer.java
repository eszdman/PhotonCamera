package com.particlesdevs.photoncamera.processing.parameters;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.control.GyroBurst;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;

//Balance algorithm between image blur and noise
public class ArtifactsBalancer {
    public static double GenerateRelativeBlurNoise(){
        GyroBurst circleBurst = PhotonCamera.getGyro().circleBurst;
        NoiseModeler noiseModeler = PhotonCamera.getPreviewParameters().noiseModeler;
        return 0;
    }
}
