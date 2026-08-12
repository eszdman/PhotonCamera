    package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLImage;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.ColorCorrectionTransform;
import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.BufferUtils;
import com.particlesdevs.photoncamera.util.FileManager;
import com.particlesdevs.photoncamera.util.SplineInterpolator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static com.particlesdevs.photoncamera.util.Math2.mix;

    public class Initial extends Node {
    public Initial() {
        super("", "Initial");
    }

    @Override
    public void AfterRun() {
        if(lutLoaded) {
            lutbm.close();
            lut.close();
        }
        if (postLut != null) postLut.close();
        if (interpolatedCurve != null) interpolatedCurve.close();
        if (GammaTexture != null) GammaTexture.close();
        if (HSVTexture != null) HSVTexture.close();
        if (LookupTexture != null) LookupTexture.close();
        if(((PostPipeline)basePipeline).FusionMap != null) ((PostPipeline)basePipeline).FusionMap.close();
        //TonemapCoeffs.close();
    }
    private boolean lutLoaded = false;
    private boolean lutEnabled = false;
    private ColorCorrectionTransform.CorrectionMode cctMode;
    private float[][] cctCube;
    private float[] cctMatrix;

    @Override
    public void Compile() {}
    GLTexture interpolatedCurve;
    GLTexture TonemapCoeffs;
    GLTexture lut;
    GLTexture postLut;
    GLTexture GammaTexture;
    GLTexture HSVTexture;
    GLTexture LookupTexture;
    GLImage lutbm;
    float highersatmpy = 1.0f;
    @Tunable(title = "Enable", category = "Color & Tone", defaultValue = 1, min = 0, max = 1, step = 1, description = "Enable camera matrix color correction, tone mapping and gamma correction")
    boolean enable;
    
    @Tunable(title = "Gamma Coefficient", category = "Color & Tone", min = 1.0f, max = 3.0f, defaultValue = 2.2f)
    float gammaKoefficientGenerator = 2.2f;
    
    @Tunable(title = "Gamma Model X1", category = "Color & Tone", min = -20.0f, max = 20.0f, defaultValue = 7.1896f)
    float gammax1 = 7.1896f;
    
    @Tunable(title = "Gamma Model X2", category = "Color & Tone", min = -100.0f, max = 100.0f, defaultValue = -50.8195f)
    float gammax2 = -50.8195f;
    
    @Tunable(title = "Gamma Model X3", category = "Color & Tone", min = -200.0f, max = 200.0f, defaultValue = 129.3564f)
    float gammax3 = 129.3564f;
    
    @Tunable(title = "Tonemap X1", category = "Color & Tone", min = -2.0f, max = 2.0f, defaultValue = -0.15f)
    float tonemapx1 =-0.15f;
    
    @Tunable(title = "Tonemap X2", category = "Color & Tone", min = -5.0f, max = 5.0f, defaultValue = 2.55f)
    float tonemapx2 = 2.55f;
    
    @Tunable(title = "Tonemap X3", category = "Color & Tone", min = -5.0f, max = 5.0f, defaultValue = -1.6f)
    float tonemapx3 = -1.6f;
    
    @Tunable(title = "Saturation Const", category = "Color & Tone", max = 3.0f, defaultValue = 1.0f)
    float saturationConst = 1.f;
    
    @Tunable(title = "Saturation Gauss", category = "Color & Tone", max = 3.0f, defaultValue = 1.5f)
    float saturationGauss = 1.5f;
    
    @Tunable(title = "Saturation Red", category = "Color & Tone", max = 3.0f, defaultValue = 1.0f)
    float saturationRed = 1.0f;
    
    @Tunable(title = "Epsilon", category = "Color & Tone", max = 0.01f, defaultValue = 0.0008f, step = 0.0001f)
    float eps = 0.0008f;
    
    //@Tunable(title = "Curve Points Count", category = "Color & Tone", min = 4.0f, max = 10.0f, defaultValue = 6.0f, step = 1.0f)
    int curvePointsCount = 6;
    
    @Tunable(title = "Vignette Correction", category = "Color & Tone", max = 2.0f, defaultValue = 1.0f)
    float vignetteCorrection = 1.0f;
    
    @Tunable(title = "Tone Mix", category = "Color & Tone", max = 1.0f, defaultValue = 0.5f)
    float toneMix = 0.5f;
    
    @Tunable(title = "LTM Mix", category = "Color & Tone", max = 1.0f, defaultValue = 0.0f)
    float ltmMix = 0.0f;

    @Tunable(title = "LUT png selector", category = "Color & Tone", description = "Select a square CLUT PNG file", allowedPngSizes = {512, 1000, 1728, 2744, 4096})
    File postlut;

    float[] intenseCurveX;
    float[] intenseCurveY;
    float[] intenseHardCurveX;
    float[] intenseHardCurveY;
    
    @Override
    public void Run() {
        PostPipeline postPipeline = (PostPipeline) basePipeline;
        glProg.setDefine("ULTRAHDR", postPipeline.ultraHdrEnabled);
        postPipeline.hdrLinearValid = enable;
        if (!enable) {
            WorkingTexture = super.previousNode.WorkingTexture;
            return;
        }
        // Values are automatically injected in BeforeRun()!
        intenseCurveX = new float[curvePointsCount];
        intenseCurveY = new float[curvePointsCount];

        intenseHardCurveX = new float[curvePointsCount];
        intenseHardCurveY = new float[curvePointsCount];
        for(int i = 0; i<curvePointsCount;i++){
            float line = i/((float)(curvePointsCount-1.f));
            intenseCurveX[i] = line;
            intenseCurveY[i] = 1.0f;

            intenseHardCurveX[i] = line;
            intenseHardCurveY[i] = 1.0f;
        }
        intenseCurveX[curvePointsCount-2] = 0.99f;
        intenseCurveY[curvePointsCount-2] = 1.f;

        intenseCurveY[curvePointsCount-1] = 0.f;

        intenseHardCurveX[curvePointsCount-2] = 0.99f;
        intenseHardCurveY[curvePointsCount-2] = 1.f;

        intenseHardCurveY[curvePointsCount-1] = 0.f;

        if(curvePointsCount == 6){
            intenseCurveX[0] = 0.0f;
            intenseCurveX[1] = 0.1f;
            intenseCurveX[2] = 0.2f;
            intenseCurveX[3] = 0.6f;
            intenseCurveX[4] = 0.95f;
            intenseCurveX[5] = 1.0f;

            intenseCurveY[0] = 1.0f;
            intenseCurveY[1] = 1.0f;
            intenseCurveY[2] = 1.0f;
            intenseCurveY[3] = 1.0f;
            intenseCurveY[4] = 1.0f;
            intenseCurveY[5] = 1.0f;

            intenseHardCurveX[0] = 0.0f;
            intenseHardCurveX[1] = 0.1f;
            intenseHardCurveX[2] = 0.2f;
            intenseHardCurveX[3] = 0.6f;
            intenseHardCurveX[4] = 0.95f;
            intenseHardCurveX[5] = 1.0f;

            intenseHardCurveY[0] = 1.0f;
            intenseHardCurveY[1] = 1.0f;
            intenseHardCurveY[2] = 1.0f;
            intenseHardCurveY[4] = 1.0f;
            intenseHardCurveY[3] = 1.0f;
            intenseHardCurveY[5] = 1.0f;
        }

        // Array values still use getTuning for now (can be enhanced later)
        intenseCurveX = getTuning("FusionIntenseCurveX", intenseCurveX);
        intenseCurveY = getTuning("FusionIntenseCurveY", intenseCurveY);
        intenseHardCurveX = getTuning("FusionIntenseHardCurveX", intenseHardCurveX);
        intenseHardCurveY = getTuning("FusionIntenseHardCurveY", intenseHardCurveY);
        ArrayList<Float> curveX = new ArrayList<>();
        ArrayList<Float> curveY = new ArrayList<>();
        ArrayList<Float> curveHardX = new ArrayList<>();
        ArrayList<Float> curveHardY = new ArrayList<>();
        for(int i =0; i<curvePointsCount;i++){
            curveX.add(intenseCurveX[i]);
            curveY.add(intenseCurveY[i]);
            curveHardX.add(intenseHardCurveX[i]);
            curveHardY.add(intenseHardCurveY[i]);
        }
        SplineInterpolator splineInterpolator = SplineInterpolator.createMonotoneCubicSpline(curveX,curveY);
        SplineInterpolator splineInterpolatorHard = SplineInterpolator.createMonotoneCubicSpline(curveHardX,curveHardY);
        float[] interpolatedCurveArr = new float[1024];
        float softLight = ((PostPipeline)(basePipeline)).softLight;
        for(int i =0 ;i<interpolatedCurveArr.length;i++){
            float line = i/ (interpolatedCurveArr.length-1.f);
            interpolatedCurveArr[i] = mix(splineInterpolatorHard.interpolate(line),splineInterpolator.interpolate(line),softLight);
        }

        interpolatedCurve = new GLTexture(new Point(interpolatedCurveArr.length,1),
                new GLFormat(GLFormat.DataType.FLOAT_16), BufferUtils.getFrom(interpolatedCurveArr),GL_LINEAR,GL_CLAMP_TO_EDGE);

        glProg.setDefine("GAMMAX1",  gammax1  );
        glProg.setDefine("GAMMAX2",  gammax2  );
        glProg.setDefine("GAMMAX3",  gammax3  );
        glProg.setDefine("TONEMAPX1",tonemapx1);
        glProg.setDefine("TONEMAPX2",tonemapx2);
        glProg.setDefine("TONEMAPX3",tonemapx3);
        glProg.setDefine("SATURATIONCONST",saturationConst);
        glProg.setDefine("SATURATIONGAUSS",saturationGauss);
        glProg.setDefine("SATURATIONRED",  saturationRed);
        glProg.setDefine("NOISEO",  basePipeline.noiseO);
        glProg.setDefine("NOISES",  basePipeline.noiseS);
        glProg.setDefine("EPS", eps);

        if(postlut != null && postlut.exists()){
            lutbm = new GLImage(postlut);
            postLut = new GLTexture(lutbm,GL_LINEAR,GL_CLAMP_TO_EDGE,0);
            glProg.setDefine("POSTLUT",true);
            int lutBase = (int)(0.1f+Math.pow(lutbm.size.x,1.0/3.0));
            Log.d(Name,"LutBase:"+lutBase);
            glProg.setDefine("POSTLUTSIZETILES", (float) lutBase);
            glProg.setDefine("POSTLUTSIZE", (float) (lutBase*lutBase));
        }

        glProg.setDefine("FUSIONGAIN",((PostPipeline)(basePipeline)).fusionGain);

        float sat =(float) basePipeline.mSettings.saturation;
        if(basePipeline.mSettings.cfaPattern == 4) {
            sat = 0.f;
        }
        glProg.setDefine("SATURATION2",sat);
        glProg.setDefine("SATURATION",sat*highersatmpy);
        //TonemapCoeffs = new GLTexture(new Point(256,1),new GLFormat(GLFormat.DataType.FLOAT_16,1),FloatBuffer.wrap(basePipeline.mSettings.toneMap),GL_LINEAR,GL_CLAMP_TO_EDGE);
        /*GLTexture oldT = TonemapCoeffs;
        TonemapCoeffs = glUtils.interpolate(TonemapCoeffs,2);
        oldT.close();
        oldT = TonemapCoeffs;
        TonemapCoeffs = glUtils.interpolate(TonemapCoeffs,2);
        oldT.close();*/
        float green = ((((PostPipeline)basePipeline).analyzedBL[0]+((PostPipeline)basePipeline).analyzedBL[2]+0.0002f)/2.f)/
                        (((PostPipeline)basePipeline).analyzedBL[1]+0.0001f);
        if(green > 0.0f && green < 1.7f) {
            float tcor = (green+1.f)/2.f;
            glProg.setDefine("TINT",tcor);
            glProg.setDefine("TINT2",((1.f/tcor+1.f)/2.f));
        }
        //float[] BL = ((PostPipeline)basePipeline).analyzedBL;
        float[] WP = basePipeline.mParameters.whitePoint;
        float minP = (WP[0]+WP[1]+WP[2])/3.f;
        if (basePipeline.mParameters.HSVMap != null)
            glProg.setDefine("USE_HSV", 1);
        if (basePipeline.mParameters.LookMap != null)
            glProg.setDefine("LOOKUP", 1);
        glProg.setDefine("MINP",minP);
        glProg.setDefine("NEUTRALPOINT",WP);
        glProg.setDefine("INSIZE",basePipeline.workSize);
        glProg.setDefine("CONTRAST", (float) basePipeline.mSettings.contrastMpy);
        glProg.setDefine("SHADOWS", (float) basePipeline.mSettings.shadows);
        glProg.setDefine("VIGNETTE", vignetteCorrection);
        glProg.setDefine("LTMMIX", ltmMix);
        cctMode = basePipeline.mParameters.CCT.correctionMode;
        cctCube = null;
        if(cctMode == ColorCorrectionTransform.CorrectionMode.CUBES || cctMode == ColorCorrectionTransform.CorrectionMode.CUBE){
            glProg.setDefine("CCT", 1);
            if(basePipeline.mParameters.CCT.correctionMode == ColorCorrectionTransform.CorrectionMode.CUBES)
                cctCube = basePipeline.mParameters.CCT.cubes[0].Combine(basePipeline.mParameters.CCT.cubes[1],basePipeline.mParameters.whitePoint);
            else
                cctCube = basePipeline.mParameters.CCT.cubes[0].cube;
        }
        if(((PostPipeline)basePipeline).FusionMap != null) glProg.setDefine("FUSION", 1);
        cctMatrix = basePipeline.mParameters.CCT.matrix;
        if(cctMode == ColorCorrectionTransform.CorrectionMode.MATRIXES){
            cctMatrix = basePipeline.mParameters.CCT.combineMatrix(basePipeline.mParameters.whitePoint);
            Log.d(Name,"CCT:"+ Arrays.toString(cctMatrix));
        }
        float[] gamma = new float[1024];
        for (int i = 0; i < gamma.length; i++) {
            double pos = ((float) i) / (gamma.length - 1.f);
            gamma[i] = (float) (Math.pow(pos, 1. / gammaKoefficientGenerator));
        }
        GammaTexture = new GLTexture(gamma.length,1,
                new GLFormat(GLFormat.DataType.FLOAT_16),BufferUtils.getFrom(gamma),GL_LINEAR,GL_CLAMP_TO_EDGE);
        File customlut = new File(FileManager.sPHOTON_TUNING_DIR,"initial_lut.png");
        lutEnabled = customlut.exists();
        if(customlut.exists()){
            lutbm = new GLImage(customlut);
            lutLoaded = true;
        } else {
            try {
                lutbm = new GLImage(PhotonCamera.getAssetLoader().getInputStream("initial_lut.png"));
                lutLoaded = true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(lutLoaded) {
            lut = new GLTexture(lutbm, GL_LINEAR, GL_CLAMP_TO_EDGE, 0);
        }
        if (basePipeline.mParameters.HSVMap != null) {
            HSVTexture = new GLTexture(new Point(basePipeline.mParameters.HSVMapSize[1], basePipeline.mParameters.HSVMapSize[0]), new GLFormat(GLFormat.DataType.FLOAT_32, 3), BufferUtils.getFrom(basePipeline.mParameters.HSVMap), GL_LINEAR, GL_CLAMP_TO_EDGE);
        }
        if (basePipeline.mParameters.LookMap != null) {
            LookupTexture = new GLTexture(new Point(basePipeline.mParameters.LookMapSize[2] * basePipeline.mParameters.LookMapSize[1], basePipeline.mParameters.LookMapSize[0]), new GLFormat(GLFormat.DataType.FLOAT_32, 3), BufferUtils.getFrom(basePipeline.mParameters.LookMap), GL_LINEAR, GL_CLAMP_TO_EDGE);
        }
        WorkingTexture = basePipeline.getMain();
        bindShaderProgram();
        glProg.drawBlocks(WorkingTexture);
        glProg.closed = true;
    }

    /**
     * Issues the shader defines and binds all programs/textures/uniforms for
     * the current initial.glsl variant. Defines are consumed and the texture
     * unit cache is cleared by every program switch, so this must be called
     * before the program is drawn.
     */
    private void bindShaderProgram() {
        glProg.setDefine("ULTRAHDR", ((PostPipeline) basePipeline).ultraHdrEnabled);
        glProg.setDefine("GAMMAX1", gammax1);
        glProg.setDefine("GAMMAX2", gammax2);
        glProg.setDefine("GAMMAX3", gammax3);
        glProg.setDefine("TONEMAPX1", tonemapx1);
        glProg.setDefine("TONEMAPX2", tonemapx2);
        glProg.setDefine("TONEMAPX3", tonemapx3);
        glProg.setDefine("SATURATIONCONST", saturationConst);
        glProg.setDefine("SATURATIONGAUSS", saturationGauss);
        glProg.setDefine("SATURATIONRED", saturationRed);
        glProg.setDefine("NOISEO", basePipeline.noiseO);
        glProg.setDefine("NOISES", basePipeline.noiseS);
        glProg.setDefine("EPS", eps);

        if (postlut != null && postlut.exists()) {
            glProg.setDefine("POSTLUT", true);
            int lutBase = (int) (0.1f + Math.pow(lutbm.size.x, 1.0 / 3.0));
            glProg.setDefine("POSTLUTSIZETILES", (float) lutBase);
            glProg.setDefine("POSTLUTSIZE", (float) (lutBase * lutBase));
        }

        glProg.setDefine("FUSIONGAIN", ((PostPipeline) basePipeline).fusionGain);

        float sat = (float) basePipeline.mSettings.saturation;
        if (basePipeline.mSettings.cfaPattern == 4) {
            sat = 0.f;
        }
        glProg.setDefine("SATURATION2", sat);
        glProg.setDefine("SATURATION", sat * highersatmpy);

        float green = ((((PostPipeline) basePipeline).analyzedBL[0] + ((PostPipeline) basePipeline).analyzedBL[2] + 0.0002f) / 2.f) /
                (((PostPipeline) basePipeline).analyzedBL[1] + 0.0001f);
        if (green > 0.0f && green < 1.7f) {
            float tcor = (green + 1.f) / 2.f;
            glProg.setDefine("TINT", tcor);
            glProg.setDefine("TINT2", ((1.f / tcor + 1.f) / 2.f));
        }
        float[] WP = basePipeline.mParameters.whitePoint;
        float minP = (WP[0] + WP[1] + WP[2]) / 3.f;
        if (lutEnabled) {
            glProg.setDefine("LUT", true);
        }
        if (basePipeline.mParameters.HSVMap != null) {
            glProg.setDefine("USE_HSV", 1);
        }
        if (basePipeline.mParameters.LookMap != null) {
            glProg.setDefine("LOOKUP", 1);
        }
        glProg.setDefine("MINP", minP);
        glProg.setDefine("NEUTRALPOINT", WP);
        glProg.setDefine("INSIZE", basePipeline.workSize);
        glProg.setDefine("CONTRAST", (float) basePipeline.mSettings.contrastMpy);
        glProg.setDefine("SHADOWS", (float) basePipeline.mSettings.shadows);
        glProg.setDefine("VIGNETTE", vignetteCorrection);
        glProg.setDefine("LTMMIX", ltmMix);
        if (cctMode == ColorCorrectionTransform.CorrectionMode.CUBES
                || cctMode == ColorCorrectionTransform.CorrectionMode.CUBE) {
            glProg.setDefine("CCT", 1);
        }
        if (((PostPipeline) basePipeline).FusionMap != null) {
            glProg.setDefine("FUSION", 1);
        }
        glProg.useAssetProgram("initial");
        if (cctCube != null) {
            glProg.setVar("CUBE0", cctCube[0]);
            glProg.setVar("CUBE1", cctCube[1]);
            glProg.setVar("CUBE2", cctCube[2]);
        }
        if (lutLoaded) {
            glProg.setTexture("LookupTable", lut);
        }
        if (postLut != null) {
            glProg.setTexture("PostLut", postLut);
        }
        if (HSVTexture != null) {
            glProg.setTexture("HSVMap", HSVTexture);
        }
        if (LookupTexture != null) {
            glProg.setTexture("LookMap", LookupTexture);
        }
        glProg.setTexture("GammaCurve", GammaTexture);
        glProg.setTexture("InputBuffer", super.previousNode.WorkingTexture);
        glProg.setTexture("IntenseCurve", interpolatedCurve);
        glProg.setTexture("GainMap", ((PostPipeline) basePipeline).GainMap);
        glProg.setVar("toneMapCoeffs", -2.f + 2.f * toneMix, 3.f - 3.f * toneMix, toneMix, 0.f);
        glProg.setVar("sensorToIntermediate", basePipeline.mParameters.sensorToProPhoto);
        glProg.setVar("intermediateToSRGB", cctMatrix);
        if (((PostPipeline) basePipeline).FusionMap != null) {
            glProg.setTexture("FusionMap", ((PostPipeline) basePipeline).FusionMap);
        }
        glProg.setVar("activeSize", 2, 2, basePipeline.mParameters.sensorPix.right - basePipeline.mParameters.sensorPix.left - 2,
                basePipeline.mParameters.sensorPix.bottom - basePipeline.mParameters.sensorPix.top - 2);
    }
}
