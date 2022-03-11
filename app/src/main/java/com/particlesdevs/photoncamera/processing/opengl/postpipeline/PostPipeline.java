package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.util.Log;

import com.particlesdevs.photoncamera.R;
import com.particlesdevs.photoncamera.api.CameraMode;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.GLBasePipeline;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLDrawParams;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLInterface;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.parameters.ResolutionSolution;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;
import com.particlesdevs.photoncamera.processing.render.Parameters;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class PostPipeline extends GLBasePipeline {
    public ByteBuffer stackFrame;
    public ByteBuffer lowFrame;
    public ByteBuffer highFrame;
    public GLTexture FusionMap;
    public GLTexture GainMap;
    public ArrayList<Bitmap> debugData = new ArrayList<>();
    public ArrayList<ImageFrame> SAGAIN;
    public float[] analyzedBL = new float[]{0.f,0.f,0.f};;
    float regenerationSense = 1.f;
    float totalGain = 1.f;
    float AecCorr = 1.f;
    float fusionGain = 1.f;
    float softLight = 1.f;
    public int getRotation() {
        int rotation = mParameters.cameraRotation;
        String TAG = "ParseExif";
        Log.d(TAG, "Gravity rotation:" + PhotonCamera.getGravity().getRotation());
        Log.d(TAG, "Sensor rotation:" + PhotonCamera.getCaptureController().mSensorOrientation);
        return rotation;
    }
    @SuppressWarnings("SuspiciousNameCombination")
    private Point getRotatedCoords(Point in){
        switch (getRotation()){
            case 0:
            case 180:
                return in;
            case 90:
            case 270:
                return new Point(in.y,in.x);
        }
        return in;
    }
    float constShift = 0.0f;
    public Bitmap Run(ByteBuffer inBuffer, Parameters parameters){
        mParameters = parameters;
        mSettings = PhotonCamera.getSettings();
        workSize = new Point(mParameters.rawSize.x,mParameters.rawSize.y);
        NoiseModeler modeler = mParameters.noiseModeler;
        noiseS = modeler.computeModel[0].first.floatValue()+
                modeler.computeModel[1].first.floatValue()+
                modeler.computeModel[2].first.floatValue();
        noiseO = modeler.computeModel[0].second.floatValue()+
                modeler.computeModel[1].second.floatValue()+
                modeler.computeModel[2].second.floatValue();
        noiseS/=3.f;
        noiseO/=3.f;
        double noisempy = Math.pow(2.0,mSettings.noiseRstr+constShift);
        Log.d("PostPipeline","noisempy:"+noisempy);
        noiseS*=noisempy;
        noiseO*=noisempy;
        Log.d("PostPipeline","NoiseS:"+noiseS+"\n"+"NoiseO:"+noiseO);
        if(!PhotonCamera.getSettings().hdrxNR){
            noiseO = 0.f;
            noiseS = 0.f;
        }
        noiseO = Math.max(noiseO,Float.MIN_NORMAL);

        Point rotated = getRotatedCoords(parameters.rawSize);
        if(PhotonCamera.getSettings().energySaving || mParameters.rawSize.x*mParameters.rawSize.y < ResolutionSolution.smallRes){
            GLDrawParams.TileSize = 8;
        } else {
            GLDrawParams.TileSize = 256;
        }
        /*if (PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT) {
            rotated.x/=2;
            rotated.y/=2;
        }*/
        Bitmap output = Bitmap.createBitmap(rotated.x,rotated.y, Bitmap.Config.ARGB_8888);

        GLCoreBlockProcessing glproc = new GLCoreBlockProcessing(rotated,output, new GLFormat(GLFormat.DataType.UNSIGNED_8,4));
        glint = new GLInterface(glproc);
        stackFrame = inBuffer;
        glint.parameters = parameters;

        BuildDefaultPipeline();

        return runAll();
    }
    private void BuildDefaultPipeline(){
        boolean nightMode = PhotonCamera.getSettings().selectedMode == CameraMode.NIGHT;
        add(new Bayer2Float());
        add(new ExposureFusionBayer2());
        switch (PhotonCamera.getSettings().cfaPattern){
            case -2:{
                add(new DemosaicQUAD());
                break;
            }
            case 4:{
                add(new MonoDemosaic());
                break;
            }
            default:{
                if(nightMode)
                    add(new HotPixelFilter());
                //if(PhotonCamera.getSettings().hdrxNR) {
                    //add(new ESD3DBayerCS());
                //}
                add(new Demosaic2());

                //add(new ImpulsePixelFilter());
                break;
            }
        }
        /*
         * * * All filters after demosaicing * * *
         */

        if(PhotonCamera.getSettings().hdrxNR) {
            if(nightMode)
                add(new Wavelet());
            add(new ESD3D());
        }

        add(new AWB());

        add(new Initial());

        add(new Equalization());

        add(new GlobalToneMapping());

        add(new CaptureSharpening());

        add(new CorrectingFlow());

        //add(new ChromaticFlow());

        add(new Sharpen2());

        add(new RotateWatermark(getRotation()));
    }
}
