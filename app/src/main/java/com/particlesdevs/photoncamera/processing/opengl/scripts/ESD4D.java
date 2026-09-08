package com.particlesdevs.photoncamera.processing.opengl.scripts;

import android.graphics.Point;
import android.util.Pair;

import com.particlesdevs.photoncamera.processing.ml.KernelNetNcnnProcessor;
import com.particlesdevs.photoncamera.processing.ml.KernelNetResult;
import com.particlesdevs.photoncamera.processing.ml.KernelParams;
import com.particlesdevs.photoncamera.processing.opengl.GLBuffer;
import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.opengl.GLCoreBlockProcessing;
import com.particlesdevs.photoncamera.processing.opengl.GLDrawParams;
import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
import com.particlesdevs.photoncamera.processing.opengl.GLOneScript;
import com.particlesdevs.photoncamera.processing.opengl.GLProg;
import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
import com.particlesdevs.photoncamera.processing.opengl.GLUtils;
import com.particlesdevs.photoncamera.processing.render.NoiseModeler;
import com.particlesdevs.photoncamera.processing.render.Parameters;
import com.particlesdevs.photoncamera.settings.DynamicNoiseStore;
import com.particlesdevs.photoncamera.util.BufferUtils;
import com.particlesdevs.photoncamera.util.Math2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_MIRRORED_REPEAT;
import static android.opengl.GLES20.GL_NEAREST;
import static com.particlesdevs.photoncamera.processing.processor.ProcessorBase.FAKE_WL;

public class ESD4D extends GLOneScript {
    public Parameters parameters;
    ArrayList<ImageFrame> images;
    //ByteBuffer alignment;
    GLProg glProg;
    GLUtils glUtils;
    public ESD4D(Point size, ArrayList<ImageFrame> images) {
        super(size, new GLCoreBlockProcessing(size,new GLFormat(GLFormat.DataType.UNSIGNED_16), GLDrawParams.Allocate.Direct),"", "ESD4D", true);
        this.glProg = glOne.glProgram;
        this.images = images;
        //this.alignment = alignment;
    }

    /**
     * KernelNet runs natively through ncnn on the Vulkan backend (see
     * {@link KernelNetNcnnProcessor}).
     */

    @Override
    public void Compile(){}
    private int baseCnt = 0;

    private GLTexture getBase(){
        if(baseCnt == 0){
            baseCnt++;
            return baseAlter;
        } else {
            baseCnt = 0;
            return base;
        }
    }
    float noiseS;
    float noiseO;
    GLBuffer hotPixelBuffer;
    int hotPixelCount;
    /** Black levels permuted to the normalized internal RGGB packed-channel order (merge00 shifts quad origins to the red site). */
    float[] blNorm;
    /** Sensor red-site offset ((cfa%2, cfa/2)); the packed grid is rawHalf + cfaShift. */
    Point cfaShift;
    /** Packed texture size (rawSize/2 + cfaShift) shared by all quad-packed stages. */
    Point packedSize;
    @Tunable(title = "Max hotPixels", category = "Merge", description = "Statistical cpu filtering count threshold", min = 16384, max = 262144, step = 1000, defaultValue = 65535)
    int MAX_HOT_PIXELS;
    @Tunable(title = "Max reasonable hotPixels", category = "Merge", description = "Statistical cpu filtering count threshold", min = 1000, max = 10000, step = 100, defaultValue = 2000)
    int MAX_REASONABLE_HOTPIXELS;

    @Tunable(title = "Enable hotPixel correction", category = "Merge", min = 0, max = 1, step = 1, defaultValue = 0)
    boolean enableHotPixelCorrection;

    /**
     * Averages up to 10 frames (or fewer if not available) into a single rgba16f texture
     * at rawHalf resolution. Uses incremental mix: mix(current, new, 1/(i+1)) which yields
     * a proper running average without overflow.
     */
    private GLTexture buildAveragedFrame(float[] blackLevel, int tile) {
        int maxFrames = Math.min(10, images.size());

        GLTexture avgA     = new GLTexture(packedSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
        GLTexture avgB     = new GLTexture(packedSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
        GLTexture tempFloat = new GLTexture(packedSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
        GLTexture tempRaw  = maxFrames > 1
                ? new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16, 1), null, GL_NEAREST, GL_CLAMP_TO_EDGE)
                : null;

        GLTexture avgCurrent = avgA;
        GLTexture avgNext    = avgB;

        for (int i = 0; i < maxFrames; i++) {
            GLTexture rawSrc = (i == 0) ? inputBase : tempRaw;
            if (i > 0) {
                tempRaw.loadData(images.get(i).buffer);
            }

            // Convert raw Bayer -> normalized rgba16f vec4 (one texel per 2x2 Bayer quad)
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("merge/merge00", true);
            //glProg.setVar("whiteLevel", (float) parameters.whiteLevel);
            glProg.setVarU("whitelevel", (int) parameters.whiteLevel);
            glProg.setVar("blackLevel", blackLevel);
            glProg.setVar("exposure", 1.0f / images.get(0).pair.layerMpy);
            glProg.setVar("createDiff", 0);
            glProg.setVar("cfaShift", cfaShift);
            glProg.setTexture("inTexture", rawSrc);
            glProg.setTextureCompute("outTexture", tempFloat, true);
            glProg.computeAuto(packedSize, 1);

            // Incremental mix: mix(currentAvg, newFrame, 1/(i+1))
            // i=0 → weight=1.0 copies newFrame wholesale (currentAvg is uninitialised zeros)
            float weight = 1.0f / (i + 1);
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("merge/avermix", true);
            glProg.setTextureCompute("currentTexture", avgCurrent, false);
            glProg.setTextureCompute("newTexture",     tempFloat,  false);
            glProg.setTextureCompute("outTexture",     avgNext,    true);
            glProg.setVar("weight", weight);
            glProg.computeAuto(packedSize, 1);

            // Ping-pong: avgNext becomes the new accumulator
            GLTexture swap = avgCurrent;
            avgCurrent = avgNext;
            avgNext    = swap;
        }

        avgNext.close();
        tempFloat.close();
        if (tempRaw != null) tempRaw.close();
        Log.d(Name, "Averaged " + maxFrames + " frame(s) for hot pixel detection");
        return avgCurrent; // caller must close
    }

    private GLBuffer detectHotPixels(GLTexture avgTex) {
        GLBuffer res = new GLBuffer(MAX_HOT_PIXELS*4+1, new GLFormat(GLFormat.DataType.UNSIGNED_32));
        glProg.setLayout(8,8,1);
        glProg.useAssetProgram("merge/hotpixeldetect", true);
        glProg.setVar("noiseS", noiseS);
        glProg.setVar("noiseO", noiseO);
        glProg.setVar("detectThr", (float) detectThr);
        glProg.setVar("maxCount", MAX_HOT_PIXELS);
        glProg.setTexture("inTexture", avgTex);
        glProg.setBufferCompute("HotPixelList",res);
        glProg.computeAuto(base.mSize, 1);
        int[] outputArr = res.readBufferIntegers(false);
        int rawCount = Math.min(outputArr[0], MAX_HOT_PIXELS);
        Log.d(Name, "Hot pixels detected (raw):" + rawCount);
        
        hotPixelCount = filterHotPixels(outputArr, rawCount, res);
        Log.d(Name, "Hot pixels after filtering:" + hotPixelCount);
        return res;
    }
    
    private int filterHotPixels(int[] data, int count, GLBuffer buffer) {
        if (count <= 0) return 0;
        
        // Structure: data[0] = count, then for each pixel: x, y, channels, strength
        ArrayList<int[]> candidates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = 1 + i * 4;
            int x = data[idx];
            int y = data[idx + 1];
            int ch = data[idx + 2];
            int strength = data[idx + 3];
            candidates.add(new int[]{x, y, ch, strength, i});
        }
        
        // If too many detections, likely false positives - filter by strength
        if (count > MAX_REASONABLE_HOTPIXELS) {
            Log.d(Name, "Too many hot pixels, filtering by strength");
            // Sort by strength (descending)
            candidates.sort((a, b) -> Integer.compare(b[3], a[3]));
            // Keep only the strongest
            while (candidates.size() > MAX_REASONABLE_HOTPIXELS) {
                candidates.remove(candidates.size() - 1);
            }
        }
        
        ArrayList<int[]> filtered = candidates;
        
        // Statistical outlier removal based on strength distribution
        if (filtered.size() > 50) {
            // Calculate mean and stddev of strength
            double sum = 0, sumSq = 0;
            for (int[] c : filtered) {
                sum += c[3];
                sumSq += (double)c[3] * c[3];
            }
            double mean = sum / filtered.size();
            double variance = sumSq / filtered.size() - mean * mean;
            double stddev = Math.sqrt(Math.max(variance, 1));
            
            // Remove weak outliers (strength < mean - 1.5*stddev)
            double threshold = mean - 1.5 * stddev;
            ArrayList<int[]> statistical = new ArrayList<>();
            for (int[] c : filtered) {
                if (c[3] >= threshold) {
                    statistical.add(c);
                }
            }
            Log.d(Name, "Statistical filtering: mean=" + (int)mean + " stddev=" + (int)stddev + " thr=" + (int)threshold);
            Log.d(Name, "Removed " + (filtered.size() - statistical.size()) + " weak detections");
            filtered = statistical;
        }
        
        // Repack filtered results back into buffer
        int finalCount = filtered.size();
        data[0] = finalCount;
        for (int i = 0; i < finalCount; i++) {
            int[] c = filtered.get(i);
            int idx = 1 + i * 4;
            data[idx] = c[0];
            data[idx + 1] = c[1];
            data[idx + 2] = c[2];
            data[idx + 3] = c[3];
        }
        buffer.uploadBuffer(data, finalCount * 4 + 1);
        
        return finalCount;
    }

    private void correctHotPixelsBase(GLBuffer buffer, int count){
        if (count > 0) {
            glProg.setLayout(64, 1, 1);
            glProg.useAssetProgram("merge/hotpixelcorrect", true);
            glProg.setBufferCompute("HotPixelList", buffer);
            glProg.setTextureCompute("inTexture", base, false);
            glProg.setTextureCompute("outTexture", base, true);
            glProg.computeManual((count + 63) / 64, 1, 1);
            Log.d(Name, "Hot pixels corrected in base:" + count);
        }
    }

    private void correctHotPixelsInAlter(GLBuffer buffer, int count){
        if (count > 0) {
            glProg.setLayout(64, 1, 1);
            glProg.useAssetProgram("merge/hotpixelcorrect", true);
            glProg.setBufferCompute("HotPixelList", buffer);
            glProg.setTextureCompute("inTexture", alter, false);
            glProg.setTextureCompute("outTexture", alter, true);
            glProg.computeManual((count + 63) / 64, 1, 1);
            Log.d(Name, "Hot pixels corrected in alter:" + count);
        }
    }

    private void hotPixels(){
        GLTexture avgTex = buildAveragedFrame(blNorm, 8);
        hotPixelBuffer = detectHotPixels(avgTex);
        avgTex.close();
        correctHotPixelsBase(hotPixelBuffer, hotPixelCount);
    }

    GLTexture inputBase;
    GLTexture baseDiff;
    GLTexture base;
    GLTexture baseAlter;
    //GLTexture;
    GLTexture brightMap;
    /** CPU copy of brightMap (float32 grayscale luma in [0,1]) set by {@link #exportBrightMap()}. */
    public FloatBuffer brightMapCPU;
    /** Unpacked size of {@link #brightMapCPU} (row-major, width*height floats). */
    public Point brightMapCPUSize;
    /** KernelNet half-res parameter texture (s1, s2, rho in RGBA16F) for the anisotropic filter. */
    public GLTexture kernelsMap;
    /** CPU copy of the unpacked KernelNet params (RGBA floats: s1, s2, rho, 1 per
     * texel, full fp32 — not the fp16 texture values) for reuse in the post
     * pipeline. Set by {@link #createKernelsMap} alongside the texture upload. */
    public FloatBuffer kernelsMapCPU;
    /** Size of {@link #kernelsMapCPU}. */
    public Point kernelsMapCPUSize;
    /** Noise sigma fed to KernelNet (captured pre-merge-inflation). */
    float kernelSigma;
    GLTexture result;
    GLTexture inputAlter;
    GLTexture alter;
    GLTexture alignmentTex;
    /** Dense optical-flow alignment (FlowNet); non-null when useNcnnFlow ran. */
    FlowNetAlignment flowNetAlignment;
    @Tunable(title = "HotPixels detect threshold", category = "Merge", description = "Higher multiplier detects less hotpixels", min = 0.5f, max = 5.0f, step = 0.1f, defaultValue = 1.5f)
    double detectThr;

    @Tunable(title = "Enable Adaptive Noise Model", category = "Merge", description = "Creates noise multiplier based on stdev", min = 0, max = 1, step = 1, defaultValue = 1)
    boolean enableAdaptiveNoise;

    @Tunable(title = "Enable Alignment", category = "Merge", description = "Disable to test merging motion filtering without alignment", min = 0, max = 1, step = 1, defaultValue = 1)
    boolean enableAlignment;

    @Tunable(title = "FlowNet optical flow alignment", category = "Merge", description = "Align burst frames with the FlowNet dense optical flow model (ncnn) instead of the block pyramid", min = 0, max = 1, step = 1, defaultValue = 0)
    boolean useNcnnFlow;

    //@Tunable(title = "Optical flow refinement", category = "Merge", description = "Brute-force half-texel diagonal refinement on the green quincunx in the merge combine pass (exact sample pairs, no interpolation, immune to brightness offsets between frames); the winning sub-texel offset warps the final mix tap - greens exact on the quincunx, R/B phase-dithered so the accumulator averages their chroma alias (moire) away across frames; comb weights stay full vec4 over exact whole-texel taps so the dither never modulates them (no temporal blink, chroma excess still steers the weight for demosaicing)", min = 0, max = 1, step = 1, defaultValue = 1)
    //boolean enableFlowRefinement;

    //@Tunable(title = "Flow refinement max shift", category = "Merge", description = "Unused by the brute-force diagonal refinement (candidates are fixed at half a texel); kept for settings compatibility", min = 1.0f, max = 4.0f, step = 1.0f, defaultValue = 2.0f)
    //float flowRefineMaxDisp;

    @Tunable(title = "Enable Adaptive Noise Storage", category = "Merge", description = "Persist fitted noise model into the dynamic multisample store", min = 0, max = 1, step = 1, defaultValue = 1)
    boolean enableNoiseStore;

    @Tunable(title = "Network merge noise multiplier", category = "Merge", description = "Scales the noise model fed to the kernel network", min = 0.1f, max = 20.0f, step = 0.05f, defaultValue = 1.0f)
    float noiseMpy;

    @Tunable(title = "Noise blend max frames", category = "Merge", description = "Frames combined into the deliberately misaligned progressive Gaussian blend used for noise estimation (blurs scene detail while noise only drops by a known factor)", min = 1, max = 9, step = 1, defaultValue = 9)
    int noiseBlendMaxFrames;

    @Tunable(title = "Noise blend calibration", category = "Merge", description = "Trim multiplier on the Monte-Carlo noise blend calibration table (1.0 = table value)", min = 0.5f, max = 2.0f, step = 0.05f, defaultValue = 1.0f)
    float noiseBlendCalMpy;

    @Tunable(title = "Noise scan subsample", category = "Merge", description = "Stride between texels evaluated by the noise histogram; the cheap difference operator supports a dense stride (was fixed 3 in the median-chain era)", min = 1, max = 8, step = 1, defaultValue = 3)
    int noiseScanSubsample;

    @Tunable(title = "Noise fit variance bins", category = "Merge", description = "Per-brightness-row cutoff on occupied variance bins kept by the noise fit pass 1 (lower rejects texture harder but undershoots on texture-free scenes; was fixed 45)", min = 8, max = 45, step = 1, defaultValue = 45)
    int noiseFitVarBins;

    @Tunable(title = "Noise fit gate", category = "Merge", description = "Adaptive per-brightness gate: pass 2 keeps only histogram bins whose implied variance is within this multiple of the pass-1 fitted noise (the per-brightness lower part; rejects texture and saturated bins). 0 disables", min = 0.0f, max = 5.0f, step = 0.25f, defaultValue = 2.0f)
    float noiseFitGateMpy;

    @Tunable(title = "Read noise floor multiplier", category = "Merge", description = "Multiplier on the analytic OPlace read-noise floor applied to fitted O; the legacy 3.0 compensated texture leakage that the noise blend now removes", min = 0.5f, max = 4.0f, step = 0.25f, defaultValue = 1.0f)
    float noiseOFloorMpy;

    @Tunable(title = "Fit O correction", category = "Merge", description = "Legacy fitO += 3/8*fitS^2 correction that compensated the under-rescaled fit; keep off with the calibrated blend", min = 0, max = 1, step = 1, defaultValue = 0)
    boolean enableFitOCorrection;

    @Tunable(title = "Adaptive fallback min", category = "Merge", description = "Lower clamp of the fallback adaptive multiplier (was 1.0, up-only)", min = 0.25f, max = 2.0f, step = 0.25f, defaultValue = 0.5f)
    float adaptiveFallbackMin;

    @Tunable(title = "Adaptive fallback max", category = "Merge", description = "Upper clamp of the fallback adaptive multiplier (was 4.0)", min = 1.0f, max = 4.0f, step = 0.25f, defaultValue = 2.0f)
    float adaptiveFallbackMax;

    /** Progressive noise-blend grid, must match BLEND_GRID in
     * tools/noise-blend-calibration/mc.py: center first, then edges, then
     * corners, so the first f slots give the temporal kernel shape for f
     * frames (9 -> full 3x3 Gaussian, 5 -> plus, 2 -> two-tap, 1 -> identity). */
    private static final int[][] BLEND_GRID = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    /** End-to-end calibration for the luma difference operator
     * |quad luma - kernel mean| (fixed full 3x3 Gaussian, sigma_g = 1) on
     * the progressive temporal blend, folded for the two-pass fit with the
     * default gate (noiseFitGateMpy = 2.0): E[noisehist "var"] =
     * VAR_STAT[f-1] * sigma for white per-frame noise through the blend,
     * the luma operator, the histogram binning and the gated weighted fit
     * (frame count f = 1..9). Measured by
     * tools/noise-blend-calibration/fixed_mc.py; trim with the
     * noiseBlendCalMpy tunable if device measurements disagree. */
    private static final float[] NOISE_BLEND_VAR_STAT = {
            0.23862f, 0.17435f, 0.14193f, 0.12277f, 0.10907f, 0.10082f, 0.09468f, 0.08916f, 0.08433f};
    /** Variance-axis anchor: bin 63 maps to sigma = SIGMA_REF for every frame
     * count (varScale = 63 / (VAR_STAT[f-1] * SIGMA_REF)), keeping bin
     * resolution in sigma terms constant and 2.4x-31x finer than the old
     * fixed 384 scale. */
    private static final float NOISE_BLEND_SIGMA_REF = 0.12f;

    /**
     * Builds the noise-estimation input: up to {@code noiseBlendMaxFrames}
     * frames (spaced across the burst) each sampled at its own slot of the
     * progressive 3x3 grid (one packed texel = one 2x2 Bayer quad = 2 raw px,
     * CFA-periodic so channels stay aligned) with normalized Gaussian
     * weights (sigma_g = 1 texel). Scene detail is correlated across frames,
     * so the fixed offsets convolve it with the kernel (fine texture
     * suppressed), while frame-independent noise only drops by the known
     * factor sum(w_i^2). noisehist.glsl then applies the luma difference
     * operator |quad luma - kernel mean| with a FIXED full 3x3 Gaussian
     * (sigma_g = 1, filled into spatialKernel, independent of frame count -
     * the temporal kernel is the only f-adaptive part): chroma structure
     * cancels exactly in the luma mean, the luma noise variance equals
     * S*b + O in quad-mean brightness for any white point, and the
     * symmetric kernel annihilates planes (gradients) precisely. Exposure
     * differences are
     * harmless: the conversion is linear, so every frame's variance obeys
     * the same variance = S*brightness + O in normalized units. Reuses
     * {@code alter} as the per-frame conversion target and ping-pongs
     * between {@code baseAlter} and one new texture (both unused until the
     * merge loop) - returns the accumulator, which may be either of the
     * two; close it only if it is not baseAlter.
     */
    private GLTexture buildNoiseBlendFrame(float[] blackLevel, int tile, float[] spatialKernel) {
        int frameCnt = Math.min(Math.min(noiseBlendMaxFrames, BLEND_GRID.length), images.size());
        double[] weights = new double[frameCnt];
        double wSum = 0;
        for (int k = 0; k < frameCnt; k++) {
            weights[k] = Math.exp(-(BLEND_GRID[k][0] * BLEND_GRID[k][0]
                    + BLEND_GRID[k][1] * BLEND_GRID[k][1]) / 2.0);
            wSum += weights[k];
        }
        java.util.Arrays.fill(spatialKernel, 0.0f);
        double opSum = 0;
        double[] opW = new double[BLEND_GRID.length];
        for (int k = 0; k < BLEND_GRID.length; k++) {
            opW[k] = Math.exp(-(BLEND_GRID[k][0] * BLEND_GRID[k][0]
                    + BLEND_GRID[k][1] * BLEND_GRID[k][1]) / 2.0);
            opSum += opW[k];
        }
        for (int k = 0; k < frameCnt; k++) {
            weights[k] /= wSum;
        }
        for (int k = 0; k < BLEND_GRID.length; k++) {
            int dx = BLEND_GRID[k][0], dy = BLEND_GRID[k][1];
            spatialKernel[(dy + 1) * 3 + (dx + 1)] = (float) (opW[k] / opSum);
        }

        GLTexture blendAcc = new GLTexture(packedSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
        GLTexture tempFloat = alter;
        GLTexture tempRaw = frameCnt > 1
                ? new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16, 1), null, GL_NEAREST, GL_CLAMP_TO_EDGE)
                : null;
        GLTexture blendCurrent = baseAlter;
        GLTexture blendNext = blendAcc;
        for (int k = 0; k < frameCnt; k++) {
            int idx = frameCnt == 1 ? 0
                    : (int) Math.round((double) k * (images.size() - 1) / (frameCnt - 1));
            GLTexture rawSrc = (idx == 0) ? inputBase : tempRaw;
            if (idx > 0) tempRaw.loadData(images.get(idx).buffer);

            // Convert raw Bayer -> normalized rgba16f vec4 (one texel per 2x2 quad)
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("merge/merge00", true);
            //glProg.setVar("whiteLevel", (float) parameters.whiteLevel);
            glProg.setVarU("whitelevel", (int) parameters.whiteLevel);
            glProg.setVar("blackLevel", blackLevel);
            glProg.setVar("exposure", 1.0f / images.get(0).pair.layerMpy);
            glProg.setVar("createDiff", 0);
            glProg.setVar("cfaShift", cfaShift);
            glProg.setTexture("inTexture", rawSrc);
            glProg.setTextureCompute("outTexture", tempFloat, true);
            glProg.computeAuto(packedSize, 1);

            // Progressive temporal blend accumulate at this frame's grid slot
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("merge/noiseblend", true);
            glProg.setTextureCompute("currentTexture", blendCurrent, false);
            glProg.setTextureCompute("newTexture", tempFloat, false);
            glProg.setTextureCompute("outTexture", blendNext, true);
            glProg.setVar("weight", (float) weights[k]);
            glProg.setVar("offset", BLEND_GRID[k][0], BLEND_GRID[k][1]);
            glProg.setVar("firstPass", k == 0 ? 1 : 0);
            glProg.computeAuto(packedSize, 1);

            GLTexture swap = blendCurrent;
            blendCurrent = blendNext;
            blendNext = swap;
        }
        if (blendCurrent != blendAcc) blendAcc.close();
        if (tempRaw != null) tempRaw.close();
        Log.d(Name, "Noise blend: " + frameCnt + " frame(s), sum(w^2)="
                + String.format(java.util.Locale.ROOT, "%.4f", java.util.stream.DoubleStream.of(weights).map(w -> w * w).sum()));
        return blendCurrent;
    }

    @Override
    public void Run() {
        com.particlesdevs.photoncamera.settings.TunableInjector.inject(this);
        Log.d("ESD4D", "Noise multiplier: " + noiseMpy);
        //Log.d("ESD4D", "Optical flow refinement: " + enableFlowRefinement + " maxShift: " + flowRefineMaxDisp);
        glUtils = new GLUtils(glOne.glProcessing);

        float minExp = 1.f;
        int minExpIdx = 0;
        int lowCnt = 0;
        for (int i = 1; i < images.size(); i++) {
            ImageFrame frame = images.get(i);
            float exposure = 1.f/frame.pair.layerMpy;
            Log.d("ESD4D", "exposure: " + exposure);
            if(exposure < 0.95f) {
                lowCnt++;
            }
            if(exposure < minExp) {
                minExpIdx = i;
                minExp = exposure;
            }
        }

        if (parameters.tile != 16) {
            // Custom tile sizes (set upstream) keep their own alignmentSize.
            Log.d("ESD4D", "Alignment tile size: " + parameters.tile
                    + " alignmentSize: " + parameters.alignmentSize.x + "x" + parameters.alignmentSize.y);
        }
        Point raw = parameters.rawSize;
        Point rawHalf = new Point(parameters.rawSize.x/2,parameters.rawSize.y/2);
        // merge00 green-normalizes all packed quads for any CFA: the quincunx
        // sub-texel sampler needs the two greens on the anti-diagonal g/b
        // slots. Only GRBG/GBRG carry their greens on the main diagonal - for
        // those, quad origins are shifted back by the red-site offset and the
        // packed grid grows by it. RGGB/BGGR already have greens on the
        // anti-diagonal (R/B merely sit swapped for BGGR, which every merge
        // stage treats channel-agnostically), so they get no shift, no filler
        // and an unchanged grid. Real raw site X lives at packed rel = X +
        // cfaShift; shifted out-of-range sites are edge duplicates, never
        // read back on unpack.
        int cfa = (int) parameters.cfaPattern;
        if (cfa < 0 || cfa > 3) cfa = 0; // quad/monochrome modes: no normalization
        cfaShift = (cfa == 1 || cfa == 2) ? new Point(cfa % 2, cfa / 2) : new Point(0, 0);
        packedSize = new Point(rawHalf.x + cfaShift.x, rawHalf.y + cfaShift.y);
        result = new GLTexture(raw,new GLFormat(GLFormat.DataType.UNSIGNED_16,1), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
        inputBase = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16,1),images.get(0).buffer, GL_NEAREST, GL_CLAMP_TO_EDGE);
        // Pyramid diff
        baseDiff = new GLTexture(packedSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        // Temporal result
        base = new GLTexture(packedSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        baseAlter = new GLTexture(packedSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        alter = new GLTexture(packedSize,new GLFormat(GLFormat.DataType.FLOAT_16,4),null,GL_LINEAR,GL_CLAMP_TO_EDGE);
        // Pack 4 horizontal luma samples per rgba16f texel (r16f image formats are
        // rejected by some drivers) -> texture is 4x smaller in x.
        Point brightMapSize = new Point((packedSize.x + 3) / 4, packedSize.y);
        brightMap = new GLTexture(brightMapSize,new GLFormat(GLFormat.DataType.FLOAT_16,4));
        brightMapCPUSize = new Point(brightMapSize.x * 4, brightMapSize.y);
        float[] blackLevel = parameters.blackLevel;
        //float[] blackLevel = new float[]{parameters.blackLevel[0]*0.5f, parameters.blackLevel[1]*0.5f, parameters.blackLevel[2]*0.5f, parameters.blackLevel[3]*0.5f};
        //float bl = Math.max(Math.max(parameters.blackLevel[0], parameters.blackLevel[1]), Math.max(parameters.blackLevel[2], parameters.blackLevel[3]));
        // Per-channel vectors for packed textures must use the shifted
        // (R, Gr, Gb, B) level order; unshifted CFAs keep the natural site
        // order. blackLevel[i] is the sensor site (i%2, i/2) level.
        blNorm = blackLevel.clone();
        switch (cfa) {
            case 1: blNorm = new float[]{blackLevel[1], blackLevel[0], blackLevel[3], blackLevel[2]}; break; // GRBG
            case 2: blNorm = new float[]{blackLevel[2], blackLevel[3], blackLevel[0], blackLevel[1]}; break; // GBRG
        }
        glOne.glProgram.setDefine("RAWSIZE",parameters.rawSize);
        glOne.glProgram.setDefine("CFAPATTERN",(int)parameters.cfaPattern);

        float[] analogBalance = new float[4];
        switch (parameters.cfaPattern){
            case 0: // RGGB
                analogBalance[0] = 1.0f/parameters.whitePoint[0];
                analogBalance[1] = 1.0f/parameters.whitePoint[1];
                analogBalance[2] = 1.0f/parameters.whitePoint[1];
                analogBalance[3] = 1.0f/parameters.whitePoint[2];
                break;
            case 1: // GRBG
                analogBalance[0] = 1.0f/parameters.whitePoint[1];
                analogBalance[1] = 1.0f/parameters.whitePoint[0];
                analogBalance[2] = 1.0f/parameters.whitePoint[2];
                analogBalance[3] = 1.0f/parameters.whitePoint[1];
                break;
            case 2: // GBRG
                analogBalance[0] = 1.0f/parameters.whitePoint[1];
                analogBalance[1] = 1.0f/parameters.whitePoint[2];
                analogBalance[2] = 1.0f/parameters.whitePoint[0];
                analogBalance[3] = 1.0f/parameters.whitePoint[1];
                break;
            case 3: // BGGR
                analogBalance[0] = 1.0f/parameters.whitePoint[2];
                analogBalance[1] = 1.0f/parameters.whitePoint[1];
                analogBalance[2] = 1.0f/parameters.whitePoint[1];
                analogBalance[3] = 1.0f/parameters.whitePoint[0];
                break;
        }
        NoiseModeler modeler = parameters.noiseModeler;
        noiseS = modeler.baseModel[0].first.floatValue() +
                modeler.baseModel[1].first.floatValue() +
                modeler.baseModel[2].first.floatValue();
        noiseO = modeler.baseModel[0].second.floatValue() +
                modeler.baseModel[1].second.floatValue() +
                modeler.baseModel[2].second.floatValue();
        noiseS /= 3.f;
        noiseO /= 3.f;
        //GLUtils glUtils = new GLUtils(glOne.glProcessing);
        int tile = 8;
        glProg.setLayout(tile,tile,1);
        glProg.useAssetProgram("merge/merge00",true);
        //glProg.setVar("whiteLevel",(float)(parameters.whiteLevel));
        glProg.setVarU("whitelevel", (int) parameters.whiteLevel);
        glProg.setVar("blackLevel", blNorm);
        glProg.setVar("exposure", 1.f/images.get(0).pair.layerMpy);
        glProg.setVar("createDiff", 0);
        glProg.setVar("cfaShift", cfaShift);
        glProg.setVar("analogBalance", analogBalance);
        glProg.setVar("randF", (float)Math.random(), (float)Math.random());
        // Test value if enabled in shader
        //glProg.setVar("noiseS", 0.0013796629f);
        //glProg.setVar("noiseO", 8.3751265E-6f);
        //glProg.setVar("noiseS", 0.05f);
        //glProg.setVar("noiseO", 0.0f);
        glProg.setTexture("inTexture",inputBase);
        glProg.setTextureCompute("outTexture",base, true);
        glProg.computeAuto(new Point(base.mSize.x, base.mSize.y), 1);
        //glUtils.convertVec4(base, "vec4(0.5)", base);
        //var buff = glUtils.GenerateGLImage(base.mSize, 4);
        //Log.d(Name, "Buffer first:" + buff.byteBuffer.get(0) + " " + buff.byteBuffer.get(1));
        //glUtils.Result(base.mSize, "noiseInput", buff.byteBuffer);

        double adaptiveNMpy = 1.0;
        if (enableAdaptiveNoise) {
            // 2D histogram: (brightness_bin * NUM_VARIANCE_BINS + variance_bin) -> count
            // Model: variance = NoiseS * brightness + NoiseO  =>  sigma = sqrt(NoiseS*b + NoiseO)
            final int numBrightnessBins = 64;
            final int numVarianceBins = 64;
            final int noiseScanBins = numBrightnessBins * numVarianceBins; // 4096
            // Estimation input: progressive misaligned Gaussian blend of the
            // burst (see buildNoiseBlendFrame). The blend blurs scene detail
            // - the root cause of the old overestimation - while noise only
            // drops by the calibrated factor varStat below.
            int blendFrames = Math.min(Math.min(noiseBlendMaxFrames, BLEND_GRID.length), images.size());
            final float varStat = NOISE_BLEND_VAR_STAT[blendFrames - 1] * noiseBlendCalMpy;
            // Variance axis anchored so bin 63 = SIGMA_REF for every frame
            // count (the old fixed 64*6 scale wasted most of the range at
            // typical noise levels, quantizing low-ISO fits into 1-3 bins).
            final float varianceScale = (numVarianceBins - 1) / (varStat * NOISE_BLEND_SIGMA_REF);
            final float brightnessScale = 64.0f * (float)Math.sqrt(3.0f);
            float[] spatialKernel = new float[9];
            GLTexture noiseInput = buildNoiseBlendFrame(blNorm, 8, spatialKernel);
            GLHistogram noiseHist = new GLHistogram(glProg, noiseScanBins);
            noiseHist.Custom = true;
            noiseHist.Rc = true;
            noiseHist.Gc = false;
            noiseHist.Bc = false;
            noiseHist.Ac = false;
            noiseHist.exposure[0] = 1.0f;
            noiseHist.exposure[1] = 1.0f;
            noiseHist.exposure[2] = 1.0f;
            noiseHist.exposure[3] = 1.0f;
            noiseHist.CustomShader = "merge/noisehist";
            noiseHist.input1 = brightnessScale;
            noiseHist.input2 = varianceScale;
            noiseHist.resize = noiseScanSubsample;
            noiseHist.customKernel = spatialKernel;
            int[][] noiseRes = noiseHist.Compute(noiseInput);
            if (noiseInput != baseAlter) noiseInput.close();
            noiseHist.close();
            int[] hist = noiseRes[0];
            // Weighted linear regression: variance = NoiseS * brightness + NoiseO,
            // run in two passes. Pass 1 fits all bins kept by the per-row
            // filter; pass 2 (adaptive gate, noiseFitGateMpy) keeps only bins
            // whose implied variance is consistent with the pass-1 noise
            // model - the per-brightness "lower part" that rejects texture
            // and saturation-capped bins without a per-threshold
            // calibration (see tools/noise-blend-calibration pct/gate runs).
            double sumW = 0, sumWb = 0, sumWv = 0, sumWb2 = 0, sumWbv = 0;
            int points = 0;
            int varCnt = 0;
            for (int i = 0; i < noiseScanBins; i++) {
                int count = hist[i];
                var bin = i / numVarianceBins;
                var vin = i % numVarianceBins;
                if(vin == 0) {
                    varCnt = 0;
                }
                if (count <= 0 || bin == numBrightnessBins-1 || (varCnt >= 30 && vin == 63) || varCnt > noiseFitVarBins) continue;
                varCnt++;
                // Fit in the absolute quad-mean brightness domain the
                // consumers use. (The old (b-minBr)/(1-minBr) rescale fit in
                // scene-relative brightness and inflated S by 1/(1-minBr) on
                // any scene without near-black content - snow, sky, low-key.)
                double brightness = ((double)(bin) + 0.5) / ((double)brightnessScale);
                brightness = Math.pow(brightness, 2.0);
                double variance = (vin + 0.5) / varianceScale;
                // The shader's "var" statistic (|center - kernel mean| after
                // the temporal blend) is calibrated end-to-end - see
                // NOISE_BLEND_VAR_STAT and tools/noise-blend-calibration -
                // to varStat * sigma, so squaring and dividing by varStat^2
                // recovers the per-frame variance.
                variance = variance * variance / ((double) varStat * varStat);
                double w = count * 1.0f;
                sumW += w;
                sumWb += w * brightness;
                sumWv += w * variance;
                sumWb2 += w * brightness * brightness;
                sumWbv += w * brightness * variance;
                points++;
            }
            //points = 9;
            if (points >= 1) {
                double denom = sumW * sumWb2 - sumWb * sumWb;
                if (denom > 1e-20) {
                    double passS = (sumW * sumWbv - sumWb * sumWv) / denom;
                    double passO = (sumWv - passS * sumWb) / sumW;
                    double fitS = passS;
                    double fitO = passO;
                    if (noiseFitGateMpy > 0.0f) {
                        // Pass 2: keep only bins whose implied variance is
                        // within the gate multiple of the pass-1 model.
                        double gW = 0, gWb = 0, gWv = 0, gWb2 = 0, gWbv = 0;
                        int gPoints = 0;
                        varCnt = 0;
                        for (int i = 0; i < noiseScanBins; i++) {
                            int count = hist[i];
                            var bin = i / numVarianceBins;
                            var vin = i % numVarianceBins;
                            if(vin == 0) {
                                varCnt = 0;
                            }
                            if (count <= 0 || bin == numBrightnessBins-1 || (varCnt >= 30 && vin == 63) || varCnt > noiseFitVarBins) continue;
                            varCnt++;
                            double brightness = ((double)(bin) + 0.5) / ((double)brightnessScale);
                            brightness = Math.pow(brightness, 2.0);
                            double variance = (vin + 0.5) / varianceScale;
                            variance = variance * variance / ((double) varStat * varStat);
                            double gateVar = noiseFitGateMpy
                                    * (Math.max(passS, 1e-12) * brightness + Math.max(passO, 0.0));
                            if (variance > gateVar) continue;
                            double w = count * 1.0f;
                            gW += w;
                            gWb += w * brightness;
                            gWv += w * variance;
                            gWb2 += w * brightness * brightness;
                            gWbv += w * brightness * variance;
                            gPoints++;
                        }
                        double gDenom = gW * gWb2 - gWb * gWb;
                        if (gPoints >= 1 && gDenom > 1e-20) {
                            fitS = (gW * gWbv - gWb * gWv) / gDenom;
                            fitO = (gWv - fitS * gWb) / gW;
                            Log.d("DynamicNoise", "Gate pass: " + gPoints + " bins kept of " + points);
                        }
                    }
                    fitS = Math.max(fitS, 1e-10);
                    Log.d("DynamicNoise",  "Fit S:" + fitS + " O:" + fitO);
                    // Keep at least 5% of original read noise so we don't collapse to zero on noisy sensors
                    double minO = 0.05 * noiseO;
                    fitO = Math.max(fitO, minO);
                    // Read-noise floor: O=S/7 overstates read noise now that the variance
                    // estimator is unbiased (previously S carried a ~2.2x bias that made
                    // S/7 a sane proxy). S/20 keeps a guard against O collapsing while no
                    // longer dominating realistic sensors (O/S is typically < 0.05).
                    //fitO = Math.max(fitO, fitS/20);
                    //fitS = Math.max(fitS, parameters.noiseModeler.SPlace(parameters.iso));
                    //fitO = Math.max(fitO, parameters.noiseModeler.OPlace(parameters.iso) * noiseOFloorMpy);
                    // Commit the fitted S/O to the multisample noise map, then read
                    // back the blended (moving-average) value. Committing before
                    // reading makes the current estimation participate in the
                    // average, while the store's measurement-list guard skips
                    // duplicate scenes (same exposure/iso) to avoid bias. Using the
                    // blended output smooths per-capture estimator fluctuations.
                    double commitS = fitS;
                    double commitO = fitO;
                    DynamicNoiseStore.NoiseEstimate blended = null;
                    if (enableNoiseStore) {
                        blended = DynamicNoiseStore.dynamicNoiseStore.commitAndGet(
                                parameters.physicalID, parameters.iso,
                                parameters.noiseModeler.AnalogueISO,
                                commitS, commitO, parameters.exposureTime);
                    }
                    if (blended != null) {
                        fitS = blended.s;
                        fitO = blended.o;
                        // Re-apply floors defensively on the blended result.
                        //fitS = Math.max(fitS, parameters.noiseModeler.SPlace(parameters.iso));
                        //fitO = Math.max(fitO, parameters.noiseModeler.OPlace(parameters.iso));
                        Log.d("DynamicNoise", "Blended noise model from store: S=" + fitS
                                + " O=" + fitO + " for iso=" + parameters.iso);
                    }
                    // Legacy correction that compensated the old under-rescaled
                    // fit; off by default now that the blend is calibrated.
                    if (enableFitOCorrection) fitO += fitS*fitS * 3.0/8.0;
                    noiseS = (float) fitS;
                    noiseO = (float) fitO;
                    Log.d("DynamicNoise",  "Fitted noise model: NoiseS=" + noiseS + " NoiseO=" + noiseO + " Half=" + Math.sqrt(noiseS * 0.5 + noiseO) + " (points=" + points + ")");
                    parameters.noiseModeler.baseModel = new Pair[] {
                            new Pair<>((double) noiseS, (double) noiseO),
                            new Pair<>((double) noiseS, (double) noiseO),
                            new Pair<>((double) noiseS, (double) noiseO)};
                }
                adaptiveNMpy = 1.0;
            } else {
                // Fallback: scale original model to match observed at mid-gray (same as before)
                double modelSigmaMid = Math.sqrt(noiseS * 0.5 + noiseO);
                if (modelSigmaMid > 1e-10) {
                    double sumWeightedSigma = 0, sumWeightedCount = 0;
                    for (int i = 0; i < noiseScanBins; i++) {
                        int count = hist[i];
                        if (count <= 0) continue;
                        double sigma = ((i % numVarianceBins + 0.5) / varianceScale) / varStat;
                        sumWeightedSigma += sigma * count;
                        sumWeightedCount += count;
                    }
                    if (sumWeightedCount > 0) {
                        double observedSigma = sumWeightedSigma / sumWeightedCount;
                        adaptiveNMpy = observedSigma / modelSigmaMid;
                        adaptiveNMpy = Math2.clamp(adaptiveNMpy, adaptiveFallbackMin, adaptiveFallbackMax);
                    }
                }
                Log.d("DynamicNoise", "Adaptive Mpy (fallback): " + adaptiveNMpy + " (insufficient points=" + points + ")");
            }
        }
        parameters.noiseModeler.setAdaptiveMpy(adaptiveNMpy);
        double noisempy = Math.pow(2.0, PhotonCamera.getSettings().mergeStrength);
        //double noiseMin = 1.0/(double)parameters.whiteLevel;
        double noiseMin = 1e-10;
        kernelSigma = (float) Math.sqrt(noiseS * 0.5 + noiseO);
        // Pre-inflation noise model for the optical-flow significance gate
        // (noiseS/noiseO below are merge-strength inflated).
        float rawNoiseS = noiseS;
        float rawNoiseO = noiseO;
        noiseS = (float)Math.max(noiseS * noisempy * adaptiveNMpy * adaptiveNMpy,noiseMin);
        noiseO = (float)Math.max(noiseO * noisempy * adaptiveNMpy * adaptiveNMpy,noiseMin);
        if(enableHotPixelCorrection)
            hotPixels();

        glProg.setLayout(tile,tile,1);
        glProg.useAssetProgram("merge/mergeGrayscale",true);
        glProg.setVar("inSize", packedSize);
        glProg.setTextureCompute("inTexture",base, false);
        glProg.setTextureCompute("outTexture",brightMap, true);
        glProg.computeAuto(brightMap.mSize, 1);
        exportBrightMap();
        // KernelNet's input derives from the reference frame only, so its
        // inference is independent of the alignment/merge loop below. Run it
        // on a worker thread concurrently with alignment (merge00 / FlowNet /
        // mergeAlign) and collect it just before the first combine pass needs
        // kernelsMap. The inference (and the model load inside it) touches no
        // GL state; the kernelsMap build/upload must rejoin the GL thread.
        final float kernelSigmaArg = kernelSigma * noiseMpy;
        final AtomicReference<KernelNetResult> kernelNetResult = new AtomicReference<>();
        Thread kernelNetThread = new Thread(() -> {
            try {
                kernelNetResult.set(runKernelNetInference(kernelSigmaArg));
            } catch (Throwable t) {
                Log.e("ESD4D", "KernelNet worker failed", t);
            }
        }, "KernelNet-inference");
        kernelNetThread.start();

        // Alignment runs after the KernelNet worker is launched so the CPU
        // ncnn inference overlaps the whole alignment pass (pyramid or FlowNet)
        // instead of following it. Nothing between the worker start and the
        // merge loop consumes alignmentTex, and this block only needs the
        // reference-frame inputs already prepared above.
        Point alignmentOutputSize = new Point(parameters.alignmentSize.x * parameters.tilesX,
                parameters.alignmentSize.y * ((images.size()-1)/parameters.tilesX + 1));
        Log.d("Alignment", "alignment pipeline size: " + alignmentOutputSize.x + " " + alignmentOutputSize.y);
        useNcnnFlow = enableAlignment && useNcnnFlow;
        if (enableAlignment && useNcnnFlow) {
            FlowNetAlignment flowNetAlignmentTmp = new FlowNetAlignment(alignmentOutputSize, images, glProg, glUtils, this, minExpIdx);
            flowNetAlignmentTmp.parameters = parameters;
            long startTime = System.currentTimeMillis();
            useNcnnFlow = flowNetAlignmentTmp.initFlow();
            Log.d("ESD4D", "FlowNet alignment init time: " + (System.currentTimeMillis() - startTime) + "ms");
            if (useNcnnFlow) {
                flowNetAlignment = flowNetAlignmentTmp;
                alignmentTex = flowNetAlignment.flowTex;
            } else {
                flowNetAlignmentTmp.close();
            }
        }
        if (enableAlignment && !useNcnnFlow) {
            PyramidAlignment pyramidAlignment = new PyramidAlignment(alignmentOutputSize, images, glProg, glUtils, this);
            pyramidAlignment.parameters = parameters;
            long startTime = System.currentTimeMillis();
            pyramidAlignment.Run();
            Log.d("ESD4D", "Alignment time: " + (System.currentTimeMillis() - startTime) + "ms");
            alignmentTex = pyramidAlignment.Result;
            pyramidAlignment.close();
        } else if (!enableAlignment) {
            alignmentTex = new GLTexture(alignmentOutputSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                    BufferUtils.getFrom(new float[alignmentOutputSize.x * alignmentOutputSize.y * 4]),
                    GL_NEAREST, GL_CLAMP_TO_EDGE);
            Log.d("ESD4D", "Alignment disabled, using identity alignment");
        }

        //Point aSize = new Point(parameters.rawSize.x/(2*parameters.tile) + 1, parameters.rawSize.y/(2*parameters.tile) + 1);
        Point border = new Point(16,16);
        inputAlter = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.UNSIGNED_16, 1), null, GL_NEAREST, GL_MIRRORED_REPEAT);
        //alignmentTex = new GLTexture(aSize, new GLFormat(GLFormat.DataType.FLOAT_32, 2), alignment, GL_NEAREST, GL_MIRRORED_REPEAT);

        //counter.put(1.0f,1.0f);
        float cnt1 = 2.0f;

        float cnt2 = 1.0f;
        //Log.d("ESD4D", "alignment size: " + aSize.x + " " + aSize.y);
        Log.d("ESD4D", "alignment size: " + parameters.alignmentSize.x + " " + parameters.alignmentSize.y);
        float maxBlack = Math.max(blackLevel[0], Math.max(blackLevel[1], Math.max(blackLevel[2], blackLevel[3])));
        float minLevel = (float) (1.0/(double)(parameters.whiteLevel-maxBlack));

        // The base frame's pixels are on the GPU now (inputBase upload plus
        // the FlowNet/Pyramid init uploads above, all synchronous). The loop
        // below only touches its GPU texture and scalar pair metadata, and
        // the base index is never loaded there, so release the native copy
        // up-front: it would otherwise outlive the whole merge.
        images.get(0).close();

        for (int f = 0; f < images.size(); f++) {
            startT();
            if(f == minExpIdx) continue;
            int ind = f;
            if(ind == 0){
                ind = minExpIdx;
            }
            ImageFrame frame = images.get(ind);
            float exposure = 1.f/frame.pair.layerMpy;
            Point shift = PyramidAlignment.alignmentShift(parameters, ind);
            //int f = 1;
            Log.d("ESD4D", "load:"+frame.pair.curlayer.name() + " " + frame.pair.layerMpy);
            inputAlter.loadData(frame.buffer);

            GLTexture flowTex = null;
            if(useNcnnFlow) {
                // Dense FlowNet optical flow for THIS alter frame, computed just
                // in time (one pair at a time, no stored flow fields). Must run
                // before the mergeAlign program is bound below.
                flowTex = flowNetAlignment.computeFlow(ind);
            }

            // Convert inputAlter to alter (vec4 format)
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("merge/merge00", true);
            //glProg.setVar("whiteLevel", (float)(parameters.whiteLevel));
            glProg.setVarU("whitelevel", (int) parameters.whiteLevel);
            glProg.setVar("blackLevel", blNorm);
            glProg.setVar("exposure", 1.f/images.get(0).pair.layerMpy);
            glProg.setVar("createDiff", 0);
            glProg.setVar("cfaShift", cfaShift);
            glProg.setTexture("inTexture", inputAlter);
            glProg.setTextureCompute("outTexture", alter, true);
            glProg.computeAuto(new Point(alter.mSize.x, alter.mSize.y), 1);
            
            correctHotPixelsInAlter(hotPixelBuffer, hotPixelCount);
            //alignmentTex.loadData(alignment.position((ind-1)*(aSize.x*aSize.y*4*2)));
            glProg.setDefine("TILE_AL", parameters.tile);
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram(useNcnnFlow ? "merge/mergeAlignFlow" : "merge/mergeAlign", true);
            glProg.setVar("rawHalf", rawHalf);
            glProg.setVarU("whitelevel", (int) parameters.whiteLevel);
            glProg.setVar("whitePoint", parameters.whitePoint);
            glProg.setVar("blackLevel", blNorm);
            // Red-site origin shift for mergeAlign's noise-model repack
            // (harmless no-op uniform for mergeAlignFlow).
            glProg.setVar("cfaShift", cfaShift);
            glProg.setVar("minLevel",minLevel);
            glProg.setVar("exposure", exposure);
            glProg.setVar("analogBalance", analogBalance);
            if(exposure >= 0.95f) {
                if(lowCnt > 1)
                    glProg.setVar("exposureLow", minExp - 0.05f);
                else {
                    glProg.setVar("exposureLow", 0.0f);
                }
            } else {
                glProg.setVar("exposureLow", 0.0f);
            }
            glProg.setVar("createDiff", 1);
            glProg.setVar("noiseS", noiseS);
            glProg.setVar("noiseO", noiseO);
            glProg.setVar("border", border);
            if(useNcnnFlow) {
                glProg.setTexture("alignmentTexture", flowTex);
            } else {
                glProg.setVar("shift", shift);
                glProg.setVar("alignmentSize", parameters.alignmentSize);
                glProg.setTexture("alignmentTexture", alignmentTex);
            }
            glProg.setTexture("inTexture", inputBase);
            glProg.setTextureCompute("baseTexture",base, false);
            glProg.setTextureCompute("alterTexture", alter, false);
            glProg.setTextureCompute("outTexture", baseDiff, true);
            glProg.computeAuto(baseDiff.mSize, 1);

            Log.d("ESD4D", "create diff");

            // First combine pass: collect the KernelNet result that has been
            // running concurrently with alignment and this frame's merge00 /
            // mergeAlign work. Waits only for any inference remainder; the
            // texture build below needs the GL thread anyway.
            if (kernelNetThread != null) {
                try {
                    kernelNetThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                kernelNetThread = null;
                kernelsMap = createKernelsMap(kernelNetResult.get());
            }

            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("merge/mergeCombineWeight1", true);
            glProg.setVar("cfaPattern", parameters.cfaPattern);
            glProg.setTexture("inTex", inputBase);
            glProg.setTexture("kernelsMap", kernelsMap);
            // Optical flow refinement: brute-force diagonal candidate wins
            // only when it beats the zero offset beyond the shader's gates.
            //glProg.setVar("enableFlow", enableFlowRefinement ? 1 : 0);
            glProg.setVar("flowNoiseS", rawNoiseS);
            glProg.setVar("flowNoiseO", rawNoiseO);
            glProg.setTextureCompute("inTexture", base, false);
            glProg.setTextureCompute("diffTexture", baseDiff, false);
            base = getBase();
            glProg.setTextureCompute("outTexture", base, true);
            glProg.setVar("noiseS", noiseS);
            glProg.setVar("noiseO", noiseO);
            glProg.setVarU("whitelevel", (int) parameters.whiteLevel);
            glProg.setVar("blackLevel", blNorm);
            glProg.setVar("analogBalance", analogBalance);
            glProg.setVar("exposure", exposure);
            if(exposure >= 0.95f){
                glProg.setVar("weight", 1.0f/cnt1);
                //glProg.setVar("exposure", minExp);
                cnt1+=1.0f;
            } else {
                glProg.setVar("weight", 1.0f/cnt2);
                //glProg.setVar("exposure", 1.0f);
                cnt2+=1.0f;
            }
            //glProg.setVar("exposure", exposure);
            //glProg.setVar("weight",  1.0f);
            glProg.computeAuto(base.mSize, 1);
            // This frame's pixels are on the GPU now: inputAlter.loadData()
            // (and FlowNet's computeFlow()) upload synchronously, and
            // everything above only touches GPU textures plus scalar pair
            // metadata afterwards. Release the native copy so peak memory no
            // longer holds the whole burst through the merge. close() is
            // idempotent, so HdrxProcessor's post-merge loop stays a safe net.
            images.get(ind).close();
            endT();
        }

        float[] bl2 = new float[4];
        for (int i = 0; i < 4; i++) {
            bl2[i] = blNorm[i]*(FAKE_WL / parameters.whiteLevel);
        }
        glProg.setDefine("WHITE_LEVEL", FAKE_WL);
        glProg.setDefine("BLACK_LEVEL", new float[]{0,0,0,0});
        glProg.setLayout(tile,tile,1);
        glProg.useAssetProgram("merge/merge2o");
        glProg.setVar("cfaShift", cfaShift); // uniform: GLProg clears defines after each load
        glProg.setTexture("inTexture",base);
        glProg.setTexture("alignmentTexture", alignmentTex);
        result.BufferLoad();
        glOne.glProcessing.drawBlocksToOutput();
        Output = glOne.glProcessing.mOutBuffer;
        AfterRun();
        com.particlesdevs.photoncamera.util.Allocator.logStage("ESD4D", "post-merge");
    }

    /**
     * Reads brightMap back to CPU. The packed rgba16f texels decode directly to
     * row-major grayscale luma (4 x-samples per texel), so reading the RGBA floats
     * in order already yields the full-width buffer. Must be called while the GL
     * context is current and before AfterRun() closes brightMap.
     */
    public FloatBuffer exportBrightMap() {
        if (brightMap == null) return null;
        brightMap.BufferLoad();
        ByteBuffer raw = brightMap.textureBuffer(new GLFormat(GLFormat.DataType.FLOAT_32, 4), true);
        raw.order(ByteOrder.nativeOrder());
        brightMapCPU = raw.asFloatBuffer();
        return brightMapCPU;
    }

    /**
     * Runs the KernelNet parameter model on the previously exported {@link #brightMapCPU}
     * (call {@link #exportBrightMap()} first). Returns half-resolution kernel params
     * (s1, s2, rho) as channel-major floats, or null if the model isn't available.
     * Takes ~40-170ms at high res; Run() calls this on a worker thread in
     * parallel with the alignment loop and collects the result before the
     * first mergeCombineWeight0 pass. Touches no GL state, so it is safe to
     * call off the GL thread.
     */
    public KernelNetResult runKernelNetInference(float sigma) {
        if (brightMapCPU == null || brightMapCPUSize == null) return null;
        var ctx = PhotonCamera.getAppContext();
        if (ctx == null) return null;
        KernelNetNcnnProcessor processor = new KernelNetNcnnProcessor(ctx);
        try {
            if (!processor.isReady()) return null;
            return processor.runInference(brightMapCPU, brightMapCPUSize.x, brightMapCPUSize.y, sigma);
        } finally {
            processor.close();
        }
    }

    /**
     * Converts a KernelNet parameter map (channel-major s1, s2, rho floats at half-res)
     * into an RGBA16F texture for the anisotropic Gaussian filter: texel = (s1, s2, rho, 1).
     * Also publishes the unpacked fp32 params as {@link #kernelsMapCPU} for the
     * post pipeline, so no GPU readback of the texture is needed.
     * The texture is left open for downstream use; caller owns it.
     */
    public GLTexture createKernelsMap(KernelNetResult result) {
        if (result == null) return null;
        int w = result.width();
        int h = result.height();
        GLTexture map = new GLTexture(new Point(w, h), new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);
        // Band the interleave+upload: the old path built a full float[4*w*h]
        // (~245 MB at 64 MP) just to ferry params across GL contexts. Sub-rect
        // uploads convert identically (driver FLOAT->HALF is per-texel), so
        // keep the compact channel-major result as the CPU copy instead.
        FloatBuffer src = result.asFloatBuffer();
        int plane = w * h;
        ByteBuffer bandBytes = ByteBuffer.allocateDirect(
                w * KernelParams.BAND_ROWS * 4 * 4).order(ByteOrder.nativeOrder());
        FloatBuffer band = bandBytes.asFloatBuffer();
        for (int y0 = 0; y0 < h; y0 += KernelParams.BAND_ROWS) {
            int rows = Math.min(KernelParams.BAND_ROWS, h - y0);
            KernelParams.interleaveBand(src, w, plane, y0, rows, band);
            band.position(0);
            band.limit(w * rows * 4);
            map.loadDataOffset(0, y0, w, rows, band);
        }
        // The unpacked fp32 params are exactly what the post pipeline needs;
        // keep them as the CPU copy instead of reading the fp16 texture back.
        // A view is enough: no copy, and HdrxProcessor nulls it after handoff.
        kernelsMapCPU = src;
        kernelsMapCPUSize = new Point(w, h);
        return map;
    }

    @Override
    public void AfterRun() {
        if(hotPixelBuffer != null) hotPixelBuffer.close();
        inputAlter.close();
        alter.close();
        inputBase.close();
        baseDiff.close();
        base.close();
        baseAlter.close();
        brightMap.close();
        result.close();
        if(useNcnnFlow && flowNetAlignment != null) {
            // Closes flowTex (== alignmentTex), so drop the reference to avoid
            // a double close below.
            flowNetAlignment.close();
            flowNetAlignment = null;
            alignmentTex = null;
        } else {
            alignmentTex.close();
        }
        GLTexture.notClosed();
    }
}
