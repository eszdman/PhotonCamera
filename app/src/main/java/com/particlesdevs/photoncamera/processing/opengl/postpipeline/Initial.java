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
import static android.opengl.GLES20.GL_NEAREST;
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
        if(((PostPipeline)basePipeline).exposureCurve != null) {
            ((PostPipeline)basePipeline).exposureCurve.close();
            ((PostPipeline)basePipeline).exposureCurve = null;
        }
        //TonemapCoeffs.close();
    }
    private boolean lutLoaded = false;

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
    
    @Tunable(title = "Highlight Softness", category = "Color & Tone", min = 0.5f, max = 1.0f, defaultValue = 0.8f, step = 0.01f, description = "Soft clamp knee for highlights; lower value rolls highlights off sooner")
    float highlightSoftness = 0.8f;
    
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
    public int halo() {
        // FUSION path taps +-2; default path is pointwise.
        return ((PostPipeline) basePipeline).FusionMap != null ? 2 : 0;
    }

    /**
     * Shot defines for the Initial program, shared by production Run() and
     * every harness band: re-issued before each program bind because useShader
     * consumes the define list on every use. Pure reads of params/settings;
     * no GL resources are created here (LUTs are built once in Run()).
     */
    private void renderInitialDefines() {
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
        glProg.setDefine("SOFTKNEE", highlightSoftness);

        if(postlut != null && postlut.exists()){
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
        float green = ((((PostPipeline)basePipeline).analyzedBL[0]+((PostPipeline)basePipeline).analyzedBL[2]+0.0002f)/2.f)/
                        (((PostPipeline)basePipeline).analyzedBL[1]+0.0001f);
        if(green > 0.0f && green < 1.7f) {
            float tcor = (green+1.f)/2.f;
            glProg.setDefine("TINT",tcor);
            glProg.setDefine("TINT2",((1.f/tcor+1.f)/2.f));
        }
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
        ColorCorrectionTransform.CorrectionMode mode =  basePipeline.mParameters.CCT.correctionMode;
        if(mode == ColorCorrectionTransform.CorrectionMode.CUBES || mode == ColorCorrectionTransform.CorrectionMode.CUBE){
            glProg.setDefine("CCT", 1);
        }
        if(((PostPipeline)basePipeline).FusionMap != null) glProg.setDefine("FUSION", 1);
        if(((PostPipeline)basePipeline).exposureCurve != null) glProg.setDefine("EXPOCURVE", 1);
    }

    /**
     * Full texture/uniform re-issue for an input, shared by production Run()
     * and every harness band (ABLC.renderLevels / Bayer2Float.drawMain
     * pattern). The program itself is bound once in Run(), never per band:
     * re-binding the already-bound program per band blacks a later draw on
     * Adreno, while re-issuing every uniform/texture is sufficient for
     * bit-exact bands. Selects the tile target while tileActive.
     */
    private void renderInitialBinds(GLTexture input) {
        ColorCorrectionTransform.CorrectionMode mode =  basePipeline.mParameters.CCT.correctionMode;
        float[][] cube = null;
        if(mode == ColorCorrectionTransform.CorrectionMode.CUBES || mode == ColorCorrectionTransform.CorrectionMode.CUBE){
            if(basePipeline.mParameters.CCT.correctionMode == ColorCorrectionTransform.CorrectionMode.CUBES)
            cube = basePipeline.mParameters.CCT.cubes[0].Combine(basePipeline.mParameters.CCT.cubes[1],basePipeline.mParameters.whitePoint);
            else
                cube = basePipeline.mParameters.CCT.cubes[0].cube;
        }
        if(mode == ColorCorrectionTransform.CorrectionMode.CUBE || mode == ColorCorrectionTransform.CorrectionMode.CUBES){
            glProg.setVar("CUBE0",cube[0]);
            glProg.setVar("CUBE1",cube[1]);
            glProg.setVar("CUBE2",cube[2]);
        }
        float[] cct = basePipeline.mParameters.CCT.matrix;
        if(mode == ColorCorrectionTransform.CorrectionMode.MATRIXES){
            cct = basePipeline.mParameters.CCT.combineMatrix(basePipeline.mParameters.whitePoint);
        }
        if(lutLoaded) {
            glProg.setTexture("LookupTable", lut);
        }
        if(postLut != null) glProg.setTexture("PostLut",postLut);
        if (basePipeline.mParameters.HSVMap != null) {
            glProg.setTexture("HSVMap", HSVTexture);
        }
        if (basePipeline.mParameters.LookMap != null) {
            glProg.setTexture("LookMap", LookupTexture);
        }
        glProg.setTexture("GammaCurve",GammaTexture);
        glProg.setTexture("InputBuffer",input);
        glProg.setVar("u_tileOrigin", 0, tileActive() ? tileY0 : 0);
        android.graphics.Point fullSize = basePipeline.workSize != null
                ? basePipeline.workSize : super.previousNode.WorkingTexture.mSize;
        glProg.setVar("u_fullSize", (float) fullSize.x, (float) fullSize.y);
        glProg.setTexture("IntenseCurve",interpolatedCurve);
        glProg.setTexture("GainMap", ((PostPipeline)basePipeline).GainMap);
        glProg.setVar("toneMapCoeffs", -2.f+2.f*toneMix, 3.f-3.f*toneMix, toneMix, 0.f);
        Log.d(Name,"sensorToIntermediate: "+ Arrays.toString(basePipeline.mParameters.sensorToProPhoto));
        glProg.setVar("sensorToIntermediate",basePipeline.mParameters.sensorToProPhoto);
        Log.d(Name,"intermediateToSRGB: "+ Arrays.toString(cct));
        glProg.setVar("intermediateToSRGB",cct);
        if(((PostPipeline)basePipeline).FusionMap != null) glProg.setTexture("FusionMap",((PostPipeline)basePipeline).FusionMap);
        if(((PostPipeline)basePipeline).exposureCurve != null) {
            glProg.setTexture("ExposureCurve",((PostPipeline)basePipeline).exposureCurve);
            glProg.setVar("adaptiveWhitePoint", ((PostPipeline)basePipeline).adaptiveWhitePoint);
        }
        Log.d(Name,"SensorPix:"+basePipeline.mParameters.sensorPix);
        glProg.setVar("activeSize",2,2,basePipeline.mParameters.sensorPix.right-basePipeline.mParameters.sensorPix.left-2,
                basePipeline.mParameters.sensorPix.bottom-basePipeline.mParameters.sensorPix.top-2);
        WorkingTexture = tileActive() ? tileOut : basePipeline.getMain();
    }

    public void Run() {
        // Cheap-pass support: keep the linear buffer (Initial's input = post
        // demosaic/denoise/ABLC) so the Ultra HDR gain-map pass can measure
        // the pre-local-tone-map scene.
        if (((PostPipeline) basePipeline).captureDemosaic) {
            ((PostPipeline) basePipeline).captureDemosaicLinear(super.previousNode.WorkingTexture);
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

        if(postlut != null && postlut.exists()){
            lutbm = new GLImage(postlut);
            postLut = new GLTexture(lutbm,GL_LINEAR,GL_CLAMP_TO_EDGE,0);
        }

        // Shot defines live in renderInitialDefines() (re-issued per band).
        // Program bind plus uniforms/textures live in renderInitialBinds().
        float[] gamma = new float[1024];
        for (int i = 0; i < gamma.length; i++) {
            double pos = ((float) i) / (gamma.length - 1.f);
            gamma[i] = (float) (Math.pow(pos, 1. / gammaKoefficientGenerator));
        }
        GammaTexture = new GLTexture(gamma.length,1,
                new GLFormat(GLFormat.DataType.FLOAT_16),BufferUtils.getFrom(gamma),GL_LINEAR,GL_CLAMP_TO_EDGE);
        File customlut = new File(FileManager.sPHOTON_TUNING_DIR,"initial_lut.png");
        boolean loaded = false;
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
        //glProg.setTexture("TonemapTex",TonemapCoeffs);
        renderInitialDefines();
        glProg.useAssetProgram("Initial/initial");
        renderInitialBinds(super.previousNode.WorkingTexture);
        // Historical position: set after the program bind, so it stays out of
        // Initial's own compile key exactly as before (pending downstream).
        if (customlut.exists()) glProg.setDefine("LUT", true);
        //((PostPipeline)basePipeline).GainMap.close();
    }

    @Override
    public void postDrawOracle() {
        // Deferred-draw node: the full output only exists after
        // drawProgramTexture runs (an in-Run oracle would compare a stale
        // unrendered texture).
        if (((PostPipeline) basePipeline).debugTiledCompare) {
            verifyRegions();
        }
    }

    /**
     * Harness oracle (debugTiledCompare): blits input bands into tile
     * textures (exactly as the production driver will) and requires
     * bit-exactness vs the full render. Skipped on the Fusion path (halo 2:
     * bands would need skirts; covered by the halo contract instead).
     */
    private void verifyRegions() {
        if (((PostPipeline) basePipeline).FusionMap != null) {
            Log.d("TiledHarness", "initial strips skipped (fusion path)");
            return;
        }
        GLTexture fullOut = WorkingTexture;
        GLTexture fullIn = super.previousNode.WorkingTexture;
        int imgW = fullOut.mSize.x;
        int imgH = fullOut.mSize.y;
        // 512-row bands (not 4-row): matches production tile scale and stays
        // clear of any tiny-FBO driver quirks while diagnosing.
        float worst = TileDriver.verifyNodeBands(fullOut, imgW, imgH, 512,
                "TiledHarness", (b0, rows) -> {
                    GLTexture inTile = new GLTexture(new android.graphics.Point(imgW, rows),
                            new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                            null, GL_NEAREST, GL_CLAMP_TO_EDGE);
                    TileDriver.blitBand(fullIn, inTile, b0, rows);
                    float inDiff = TileDriver.compareBand(fullIn, inTile, imgW, b0, rows,
                            "TiledHarness-blit");
                    if (inDiff != 0f) {
                        Log.e("TiledHarness", "initial blit band [" + b0 + "," + (b0 + rows)
                                + ") maxDiff=" + inDiff);
                    }
                    GLTexture reg = new GLTexture(new android.graphics.Point(imgW, rows),
                            new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                            null, GL_NEAREST, GL_CLAMP_TO_EDGE);
                    tileY0 = b0;
                    tileY1 = b0 + rows;
                    tileOut = reg;
                    // Full uniform/texture re-issue per band (no program rebind:
                    // it blacks a later draw); renderInitialBinds selects reg
                    // as WorkingTexture while tileActive.
                    renderInitialBinds(inTile);
                    logTileUniforms(b0);
                    glProg.drawBlocks(reg);
                    inTile.close();
                    return reg;
                });
        Log.d("TiledHarness", "initial strips maxDiff=" + worst);
        tileY0 = 0;
        tileY1 = -1;
        tileOut = null;
        WorkingTexture = fullOut;
        // Initial never sets closed=true, so runAllInternal redraws it after
        // Run returns: restore the legacy origin or the final draw shifts.
        glProg.setVar("u_tileOrigin", 0, 0);
        glProg.setTexture("InputBuffer", fullIn);
    }

    /**
     * Reads back the tile uniforms live from the bound program: proves the
     * setVar path (location valid, value latched) vs a stale/missing shader
     * declaration (location -1, stale values). Diagnostic only.
     */
    private void logTileUniforms(int b0) {
        try {
            int prog = glProg.mCurrentProgramActive;
            int locO = android.opengl.GLES30.glGetUniformLocation(prog, "u_tileOrigin");
            int[] o = new int[2];
            if (locO >= 0) {
                android.opengl.GLES30.glGetUniformiv(prog, locO, o, 0);
            }
            int locS = android.opengl.GLES30.glGetUniformLocation(prog, "u_fullSize");
            float[] s = new float[2];
            if (locS >= 0) {
                android.opengl.GLES30.glGetUniformfv(prog, locS, s, 0);
            }
            Log.d("TiledHarness", "tile uniforms band=" + b0 + " originLoc=" + locO
                    + " origin=(" + o[0] + "," + o[1] + ") sizeLoc=" + locS
                    + " size=(" + s[0] + "," + s[1] + ")");
        } catch (Throwable t) {
            Log.e("TiledHarness", "tile uniform readback failed", t);
        }
    }
}
