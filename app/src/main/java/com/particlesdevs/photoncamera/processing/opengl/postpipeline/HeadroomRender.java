package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;

import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.ColorCorrectionTransform;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.BufferUtils;
import com.particlesdevs.photoncamera.util.Log;

import java.util.Arrays;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

/**
 * Sky headroom renderer, replacing the Initial role.
 *
 * Renders the SDR base with matrix-only color (sensor -> ProPhoto -> sRGB,
 * white-point WB; no CCT cubes/CLUTs), the lens shading GainMap and the
 * ExposureFusionBayer2 FusionMap as linear gains, and the sky
 * log-headroom tone curve driven by the {@link LinearExposure} display gain:
 * sceneWhite = clamp(headroomScale*displayGain, 1, sceneWhiteMax).
 */
public class HeadroomRender extends Node {
    @Tunable(title = "Output Exposure", category = "Color & Tone", min = 0.50f, max = 1.20f, defaultValue = 0.80f, step = 0.01f, description = "Global linear output scale (~-0.32 EV at 0.80)")
    float outputExposureScale = 0.80f;

    @Tunable(title = "Headroom Scale", category = "Color & Tone", min = 0.50f, max = 1.20f, defaultValue = 0.90f, step = 0.01f, description = "Fraction of the display gain treated as scene white headroom")
    float headroomScale = 0.90f;

    @Tunable(title = "Headroom Max", category = "Color & Tone", min = 1.0f, max = 20.0f, defaultValue = 14.5f, step = 0.5f, description = "Upper clamp of the scene white headroom; keep above 0.9*Gain Max (14.4 at Gain Max 16) or high-gain scenes clip highlights flat")
    float sceneWhiteMax = 14.5f;

    private GLTexture fallbackGainMap;

    public HeadroomRender() {
        super("", "HeadroomRender");
    }

    @Override
    public void Compile() {}

    @Override
    public void AfterRun() {
        // Last consumer of the fusion map (was Initial's duty).
        if (((PostPipeline) basePipeline).FusionMap != null) {
            ((PostPipeline) basePipeline).FusionMap.close();
        }
        if (fallbackGainMap != null) {
            fallbackGainMap.close();
            fallbackGainMap = null;
        }
    }

    @Override
    public void Run() {
        PostPipeline pipeline = (PostPipeline) basePipeline;

        float displayGain = Math.max(1.0f, pipeline.linearDisplayGain);
        float sceneWhite = Math.max(1.0f,
                Math.min(sceneWhiteMax, headroomScale * displayGain));

        // Scene (pre-exposure) luma that this tonemapping maps to SDR display
        // white: the curve reaches 1.0 at exposed = sceneWhite, i.e. raw =
        // sceneWhite / displayGain. The gain-map pass anchors on this so the
        // recoverable highlight headroom is pushed exactly above scene white.
        pipeline.sceneWhiteRaw = sceneWhite / displayGain;

        // Matrix-only color: plain matrix for CUBE/CUBES modes (cubes skipped).
        float[] intermediateToSRGB = basePipeline.mParameters.CCT.matrix;
        if (basePipeline.mParameters.CCT.correctionMode
                == ColorCorrectionTransform.CorrectionMode.MATRIXES) {
            intermediateToSRGB = basePipeline.mParameters.CCT.combineMatrix(
                    basePipeline.mParameters.whitePoint);
        }

        GLTexture gainMapTex = pipeline.GainMap;
        if (gainMapTex == null) {
            if (fallbackGainMap == null) {
                fallbackGainMap = new GLTexture(new Point(1, 1),
                        new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                        BufferUtils.getFrom(new float[]{1.f, 1.f, 1.f, 1.f}),
                        GL_LINEAR, GL_CLAMP_TO_EDGE);
            }
            gainMapTex = fallbackGainMap;
        }

        boolean fusion = pipeline.FusionMap != null;
        glProg.setDefine("FUSION", fusion);
        glProg.setDefine("NEUTRALPOINT", basePipeline.mParameters.whitePoint);
        glProg.useAssetProgram("headroom/render");
        glProg.setTexture("InputBuffer", super.previousNode.WorkingTexture);
        if (fusion) glProg.setTexture("FusionMap", pipeline.FusionMap);
        glProg.setTexture("GainMap", gainMapTex);
        glProg.setVar("sensorToIntermediate", basePipeline.mParameters.sensorToProPhoto);
        glProg.setVar("intermediateToSRGB", intermediateToSRGB);
        glProg.setVar("displayGain", displayGain);
        glProg.setVar("sceneWhite", sceneWhite);
        glProg.setVar("outputExposureScale", Math.max(outputExposureScale, 1.0e-2f));
        glProg.setVar("activeSize", 2, 2,
                basePipeline.mParameters.sensorPix.right - basePipeline.mParameters.sensorPix.left - 2,
                basePipeline.mParameters.sensorPix.bottom - basePipeline.mParameters.sensorPix.top - 2);
        Log.d(Name, "displayGain:" + displayGain + " sceneWhite:" + sceneWhite
                + " outputExposureScale:" + outputExposureScale
                + " intermediateToSRGB:" + Arrays.toString(intermediateToSRGB));

        WorkingTexture = basePipeline.getMain();
    }
}
