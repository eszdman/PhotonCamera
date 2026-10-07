package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

import android.graphics.Point;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
import com.particlesdevs.photoncamera.processing.render.ColorCorrectionTransform;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.BufferUtils;

import java.util.Arrays;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;

/**
 * Modern replacement for {@link Initial}. One fixed path, no selector-driven
 * variants inside the node:
 * linear input -&gt; adaptive white point division -&gt; color transform
 * (sensor -&gt; wide gamut -&gt; sRGB matrices) -&gt; lens-shading gain map
 * Reinhard highlight compression -&gt; sRGB encode -&gt; OEM-style saturation
 * -&gt; contrast/shadows S-curve -&gt; AE exposure curve.
 * No fusion maps, correction cubes, HSV/Look maps, LUTs or fitted
 * gamma/tonemap polynomials.
 */
public class ModernInitial extends Node {
    public ModernInitial() {
        super("", "ModernInitial");
    }

    @Tunable(title = "Highlight Range", category = "Color & Tone", min = 1.0f, max = 4.0f, defaultValue = 1.0f,
            description = "Reinhard white point scale over the lens-shading gain map; higher = later highlight rolloff")
    float highlightRange = 1.0f;

    @Tunable(title = "Base Contrast", category = "Color & Tone", min = 0.0f, max = 1.0f, defaultValue = 0.0f,
            description = "Extra S-curve on top of the contrast slider; 0 (default) keeps the raw-editor linear match")
    float baseContrast = 0.0f;

    GLTexture HSVTexture;
    GLTexture LookupTexture;

    @Override
    public void Compile() {}

    @Override
    public void AfterRun() {
        if (HSVTexture != null) HSVTexture.close();
        if (LookupTexture != null) LookupTexture.close();
        if(((PostPipeline)basePipeline).exposureCurve != null) {
            ((PostPipeline)basePipeline).exposureCurve.close();
            ((PostPipeline)basePipeline).exposureCurve = null;
        }
    }

    @Override
    public int halo() {
        return 0; // pointwise LUT render; curves frozen by pre-pass
    }

    public void Run() {
        // Cheap-pass support: keep the linear buffer (post
        // demosaic/denoise/ABLC/resize) so the Ultra HDR gain-map pass can
        // measure the pre-local-tone-map scene at output size.
        if (((PostPipeline) basePipeline).captureDemosaic) {
            ((PostPipeline) basePipeline).captureDemosaicLinear(super.previousNode.WorkingTexture);
        }

        float sat = (float) basePipeline.mSettings.saturation;
        if(basePipeline.mSettings.cfaPattern == 4) {
            sat = 0.f; //MONO
        }
        glProg.setDefine("SATURATION",sat);
        glProg.setDefine("CONTRAST", (float) basePipeline.mSettings.contrastMpy);
        glProg.setDefine("SHADOWS", (float) basePipeline.mSettings.shadows);
        glProg.setDefine("BASECONTRAST",baseContrast);
        glProg.setDefine("HIGHLIGHTRANGE",highlightRange);
        glProg.setDefine("NEUTRALPOINT",basePipeline.mParameters.whitePoint);

        boolean aeCurve = ((PostPipeline)basePipeline).exposureCurve != null;
        if(aeCurve) glProg.setDefine("EXPOCURVE", 1);
        //DCP profile tables
        if (basePipeline.mParameters.HSVMap != null)
            glProg.setDefine("USE_HSV", 1);
        if (basePipeline.mParameters.LookMap != null)
            glProg.setDefine("LOOKUP", 1);
        glProg.useAssetProgram("Initial/modern");

        float[] cct = basePipeline.mParameters.CCT.matrix;
        if(basePipeline.mParameters.CCT.correctionMode == ColorCorrectionTransform.CorrectionMode.MATRIXES){
            cct = basePipeline.mParameters.CCT.combineMatrix(basePipeline.mParameters.whitePoint);
        }
        Log.d(Name,"sensorToIntermediate: "+ Arrays.toString(basePipeline.mParameters.sensorToProPhoto));
        glProg.setVar("sensorToIntermediate",basePipeline.mParameters.sensorToProPhoto);
        Log.d(Name,"intermediateToSRGB: "+ Arrays.toString(cct));
        glProg.setVar("intermediateToSRGB",cct);
        // Bounds rescaled into the input (draw-target) domain (see
        // PostPipeline.activeSizeForDomain): sensorPix stays base-domain
        // while this node now runs at target size after a resize.
        com.particlesdevs.photoncamera.processing.opengl.GLTexture modInput =
                super.previousNode.WorkingTexture;
        int[] modActive = ((PostPipeline)basePipeline).activeSizeForDomain(
                modInput != null ? modInput.mSize : null);
        glProg.setVar("activeSize",modActive[0],modActive[1],modActive[2],modActive[3]);
        float[] modGainFp = ((PostPipeline)basePipeline).gainFootprint();
        glProg.setVar("u_gainMin", modGainFp[0], modGainFp[1]);
        glProg.setVar("u_gainMax", modGainFp[2], modGainFp[3]);
        glProg.setTexture("InputBuffer",super.previousNode.WorkingTexture);
        glProg.setTexture("GainMap", ((PostPipeline)basePipeline).GainMap);
        //setVar resolves locations on the active program: only valid after
        //useAssetProgram above.
        if(aeCurve) {
            glProg.setVar("adaptiveWhitePoint", ((PostPipeline)basePipeline).adaptiveWhitePoint);
            glProg.setTexture("ExposureCurve",((PostPipeline)basePipeline).exposureCurve);
        }
        if (basePipeline.mParameters.HSVMap != null) {
            HSVTexture = new GLTexture(new Point(basePipeline.mParameters.HSVMapSize[1], basePipeline.mParameters.HSVMapSize[0]), new GLFormat(GLFormat.DataType.FLOAT_32, 3), BufferUtils.getFrom(basePipeline.mParameters.HSVMap), GL_LINEAR, GL_CLAMP_TO_EDGE);
            glProg.setTexture("HSVMap", HSVTexture);
        }
        if (basePipeline.mParameters.LookMap != null) {
            LookupTexture = new GLTexture(new Point(basePipeline.mParameters.LookMapSize[2] * basePipeline.mParameters.LookMapSize[1], basePipeline.mParameters.LookMapSize[0]), new GLFormat(GLFormat.DataType.FLOAT_32, 3), BufferUtils.getFrom(basePipeline.mParameters.LookMap), GL_LINEAR, GL_CLAMP_TO_EDGE);
            glProg.setTexture("LookMap", LookupTexture);
        }
        // No oracle: the Initial/modern asset is absent from this tree, so the
        // "new" tone path cannot run to compare against. Branch kept so a
        // future driver treats it uniformly (LUT render, halo 0).
        WorkingTexture = tileActive() ? tileOut : basePipeline.getMain();
    }

    @Override
    public void postDrawOracle() {
        // Deferred draw, no oracle: the draw consumed the input, and nothing
        // downstream reads it (see TileDriver.releaseConsumedInput; ~1.16 GB
        // at a 144.5 MP output).
        if (!((PostPipeline) basePipeline).debugTiledCompare) {
            TileDriver.releaseConsumedInput(basePipeline, super.previousNode.WorkingTexture, WorkingTexture);
        }
    }
}
