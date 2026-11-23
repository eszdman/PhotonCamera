package com.particlesdevs.photoncamera.processing.opengl.scripts;

import android.graphics.Point;
import android.util.Pair;

import com.particlesdevs.photoncamera.processing.cpu.HalideAlignment;
import com.particlesdevs.photoncamera.processing.ml.KernelNetNcnnProcessor;
import com.particlesdevs.photoncamera.processing.ml.KernelNetResult;
import com.particlesdevs.photoncamera.processing.opengl.GLBuffer;
import com.particlesdevs.photoncamera.util.Log;

import com.particlesdevs.photoncamera.app.PhotonCamera;
import com.particlesdevs.photoncamera.processing.ImageFrame;
import com.particlesdevs.photoncamera.processing.GyroAlignment;
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
import com.particlesdevs.photoncamera.util.Allocator;
import com.particlesdevs.photoncamera.util.AdaptiveNoiseModel;
import com.particlesdevs.photoncamera.util.NoiseHistogramCalibration;
import com.particlesdevs.photoncamera.util.BufferUtils;
import com.particlesdevs.photoncamera.util.BurstHotPixelFilter;
import com.particlesdevs.photoncamera.util.Math2;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_MIRRORED_REPEAT;
import static android.opengl.GLES20.GL_NEAREST;

public class ESD4D extends GLOneScript {
    private final com.particlesdevs.photoncamera.settings.merge.ESD4D tuning =
            new com.particlesdevs.photoncamera.settings.merge.ESD4D();
    public Parameters parameters;
    /** Raw spatial estimate before sensor-prior limits or persistent storage. */
    public AdaptiveNoiseModel.Model measuredNoiseModel;
    ArrayList<ImageFrame> images;
    //ByteBuffer alignment;
    GLProg glProg;
    GLUtils glUtils;
    public ESD4D(Point size, ArrayList<ImageFrame> images) {
        super(size, new GLCoreBlockProcessing(size,new GLFormat(GLFormat.DataType.FLOAT_16), GLDrawParams.Allocate.Direct),"", "ESD4D", true);
        this.glProg = glOne.glProgram;
        this.images = images;
        //this.alignment = alignment;
    }

    /**
     * KernelNet runs natively on the CPU through ncnn (see
     * {@link KernelNetNcnnProcessor}).
     */

    @Override
    public void Compile(){}
    private int baseCnt = 0;

    /**
     * Alternates the combine write target between the two fixed rotation
     * slots. The caller tracks the running accumulation separately (mergeAcc
     * in Run()); reassigning base/baseAlter collapses the rotation onto one
     * texture, making combine read and write it in the same dispatch.
     */
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
    /** Sparse defect diagnostics; null when correction is disabled. */
    public BurstHotPixelFilter.Result hotPixelResult;
    /** Sensor red-site offset ((cfa%2, cfa/2)); the packed grid is rawHalf + cfaShift. */
    Point cfaShift;
    /** Packed texture size (rawSize/2 + cfaShift) shared by all quad-packed stages. */
    Point packedSize;

    private void hotPixels(float sensorNoiseS, float sensorNoiseO) {
        if (images.size() < 3 || parameters.cfaPattern < 0 || parameters.cfaPattern > 3) return;
        ImageFrame reference = images.get(0);
        for (ImageFrame frame : images) {
            if (frame.cropOriginX != reference.cropOriginX || frame.cropOriginY != reference.cropOriginY
                    || frame.binned != reference.binned) {
                Log.d(Name, "Sensor coordinate mapping changes within burst; hot pixel correction skipped");
                return;
            }
        }
        long proposalStart = System.nanoTime();
        float sigma = BurstHotPixelFilter.sigmaThreshold(tuning.detectThr);
        int[] candidates;
        try (GLBuffer proposals = new GLBuffer(tuning.MAX_HOT_PIXELS * 4 + 1,
                new GLFormat(GLFormat.DataType.UNSIGNED_32))) {
            glProg.setLayout(8, 8, 1);
            glProg.useAssetProgram("merge/hotpixeldetect", true);
            glProg.setVar("rawSize", parameters.rawSize);
            glProg.setVar("cfaShift", cfaShift);
            glProg.setVar("exposure", 1.f / images.get(0).pair.layerMpy);
            glProg.setVar("noiseS", sensorNoiseS);
            glProg.setVar("noiseO", sensorNoiseO);
            // Proposal only: a warm pixel need not cross the strong threshold
            // in frame zero. Sparse temporal verification still requires strong
            // evidence in a majority and spatial isolation in at least 80% of samples.
            glProg.setVar("detectThr", Math.max(3f, sigma * .5f));
            glProg.setVar("maxCount", tuning.MAX_HOT_PIXELS);
            glProg.setTexture("inTexture", base);
            glProg.setBufferCompute("HotPixelList", proposals);
            glProg.computeAuto(packedSize, 1);
            int[] data = proposals.readBufferIntegers(false);
            if (data == null || data[0] < 0 || data[0] > tuning.MAX_HOT_PIXELS) {
                Log.d(Name, "Hot pixel proposals unavailable or overfull; correction skipped");
                return;
            }
            int[] sites = new int[data[0] * 4];
            int count = 0;
            for (int i = 0; i < data[0]; ++i) {
                int offset = 1 + 4 * i;
                for (int c = 0; c < 4; ++c) {
                    if ((data[offset + 2] & (1 << c)) == 0) continue;
                    int x = 2 * data[offset] - cfaShift.x + c % 2;
                    int y = 2 * data[offset + 1] - cfaShift.y + c / 2;
                    sites[count++] = y * parameters.rawSize.x + x;
                }
            }
            candidates = java.util.Arrays.copyOf(sites, count);
        }
        long verifyStart = System.nanoTime();
        BurstHotPixelFilter.Frame[] frames = new BurstHotPixelFilter.Frame[images.size()];
        for (int i = 0; i < frames.length; ++i) {
            // createF16 has already normalized each native buffer. Sparse reads
            // avoid uploading ten full frames just to verify a few candidates.
            final ByteBuffer buffer = images.get(i).buffer.duplicate().order(ByteOrder.nativeOrder());
            frames[i] = new BurstHotPixelFilter.Frame() {
                @Override public float get(int index) {
                    return android.util.Half.toFloat(buffer.getShort(index * 2));
                }
                @Override public void set(int index, float value) {
                    buffer.putShort(index * 2, android.util.Half.toHalf(value));
                }
            };
        }
        hotPixelResult = BurstHotPixelFilter.correct(frames, parameters.rawSize.x,
                parameters.rawSize.y, parameters.cfaPattern, candidates,
                sensorNoiseS, sensorNoiseO, sigma, tuning.MAX_REASONABLE_HOTPIXELS);
        Log.d(Name, "Hot pixels: proposed=" + candidates.length + " confirmed="
                + hotPixelResult.sites.length + " overfull=" + hotPixelResult.rejectedOverfull
                + " proposal_ms=" + (verifyStart - proposalStart) / 1e6
                + " verify_correct_ms=" + (System.nanoTime() - verifyStart) / 1e6);
        if (hotPixelResult.sites.length == 0) return;
        // Correct every frame before any alignment/warp. Refresh the already
        // uploaded reference; alter uploads later consume the repaired buffers.
        inputBase.loadRawHalf(images.get(0).buffer);
        glProg.setLayout(8, 8, 1);
        glProg.useAssetProgram("merge/merge00", true);
        glProg.setVar("exposure", 1.f / images.get(0).pair.layerMpy);
        glProg.setVar("createDiff", 0);
        glProg.setVar("cfaShift", cfaShift);
        glProg.setTexture("inTexture", inputBase);
        glProg.setTextureCompute("outTexture", base, true);
        glProg.computeAuto(packedSize, 1);
    }

    GLTexture inputBase;
    GLTexture baseDiff;
    GLTexture mergeSupport;
    /** Optional test observer, called on the GL thread. Null during normal capture. */
    public MergeObserver mergeObserver;
    /** Optional replay hook after alignment, before any merge dispatch. */
    public AlignmentObserver alignmentObserver;
    /** Replay-only sensitivity control; normal capture uses the calibrated model unchanged. */
    public float alignmentNoiseModelScale = 1.0f;
    public interface AlignmentObserver {
        void onAlignment(GLTexture atlas, int tile, Point frameGrid, int tilesX);
    }
    public interface MergeObserver {
        void onMerge(int frame, float weight, float noiseS, float noiseO,
                     GLTexture previous, GLTexture candidate, GLTexture merged, GLTexture kernels);
    }
    /** Merge ping-pong rotation slots: fixed after allocation, only getBase()
     * picks between them; the running accumulation is mergeAcc in Run(). */
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
    /** CPU copy of the KernelNet params (RGBA16F halves: s1, s2, rho, 1 per
     * texel) for reuse in the post pipeline. Set by {@link #createKernelsMap}
     * alongside the texture upload; a view, never freed. */
    public ShortBuffer kernelsMapCPU;
    /** Size of {@link #kernelsMapCPU}. */
    public Point kernelsMapCPUSize;
    /**
     * Base direct buffer behind {@link #kernelsMapCPU} (the inference
     * result). Single owner: whoever holds it frees it exactly once via
     * {@code Allocator.free} after the GPU upload — views don't free.
     */
    public ByteBuffer kernelsMapBase;
    /** Noise sigma fed to KernelNet (captured pre-merge-inflation). */
    float kernelSigma;
    GLTexture result;
    GLTexture inputAlter;
    GLTexture alter;
    GLTexture alignmentTex;
    private HalideAlignment frameAlignment;
    /** Dense optical-flow alignment (FlowNet); non-null when useNcnnFlow ran. */
    FlowNetAlignment flowNetAlignment;

    /** Progressive noise-blend grid, must match BLEND_GRID in
     * tools/noise-blend-calibration/mc.py: center first, then edges, then
     * corners, so the first f slots give the temporal kernel shape for f
     * frames (9 -> full 3x3 Gaussian, 5 -> plus, 2 -> two-tap, 1 -> identity). */
    private static final int[][] BLEND_GRID = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
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
     * is reduced in the luma mean, and independent Bayer-channel noise has
     * luma variance (S*b + O)/4 under the shared per-channel model. The
     * symmetric kernel annihilates planes (gradients) precisely. Only frames
     * matching the reference exposure and ISO participate: HDR layers have
     * different brightness and noise weights and invalidate this calibration.
     * The fitting domain remains sensor RAW, independent of ETTR metering. Reuses
     * {@code alter} as the per-frame conversion target and ping-pongs
     * between {@code baseAlter} and one new texture (both unused until the
     * merge loop) - returns the accumulator, which may be either of the
     * two; close it only if it is not baseAlter.
     */
    private GLTexture buildNoiseBlendFrame(int tile, float[] spatialKernel, int[] frameIndices) {
        int frameCnt = frameIndices.length;
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

        // P2 (H9): borrow baseDiff as the blend accumulator instead of a
        // dedicated texture (-24 MB). Safe: noiseblend uses imageLoad/Store
        // only (filter-agnostic), geometry is identically packedSize, and
        // baseDiff is dead until the merge loop fully overwrites it. Owned by
        // the ESD4D lifecycle - never closed here (see guards below).
        GLTexture blendAcc = baseDiff;
        GLTexture tempFloat = alter;
        GLTexture tempRaw = frameCnt > 1
                ? new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, 1), null, GL_NEAREST, GL_CLAMP_TO_EDGE)
                : null;
        GLTexture blendCurrent = baseAlter;
        GLTexture blendNext = blendAcc;
        for (int k = 0; k < frameCnt; k++) {
            int idx = frameIndices[k];
            GLTexture rawSrc = (idx == 0) ? inputBase : tempRaw;
            if (idx > 0) tempRaw.loadRawHalf(images.get(idx).buffer);

            // Convert raw Bayer -> normalized rgba16f vec4 (one texel per 2x2 quad)
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("merge/merge00", true);
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
        // Borrowed baseDiff must survive (see above); nothing to free here.
        if (tempRaw != null) tempRaw.close();
        Log.d(Name, "Noise blend: " + frameCnt + " frame(s), sum(w^2)="
                + String.format(java.util.Locale.ROOT, "%.4f", java.util.stream.DoubleStream.of(weights).map(w -> w * w).sum()));
        return blendCurrent;
    }

    /**
     * Profiling aid: drains the command queue so the enclosing Stage[merge:*]
     * timer includes GPU execution, not just submit time (dispatches are
     * queued asynchronously; without this the loop's timers read ~0-2 ms
     * while the real cost surfaces later in a single drain). Off by default -
     * it serializes the pipeline and inflates the shot time.
     */
    private void gpuSyncProfile() {
        if (tuning.profileGpuSync) android.opengl.GLES30.glFinish();
    }

    @Override
    public void Run() {
        try {
            runBurst();
        } finally {
            releaseFrameAlignment();
        }
    }

    private void releaseFrameAlignment() {
        if (frameAlignment != null) {
            frameAlignment.close();
            frameAlignment = null;
        }
    }

    private void runBurst() {
        measuredNoiseModel = null;
        com.particlesdevs.photoncamera.settings.TunableInjector.inject(tuning);
        if (tuning.profileGpuSync)
            Log.d("ESD4D", "GPU sync profiling ON: Stage[merge:*] times now include GPU execution");
        Log.d("ESD4D", "Noise multiplier: " + tuning.noiseMpy);
            //Log.d("ESD4D", "Optical flow refinement: " + enableFlowRefinement + " maxShift: " + flowRefineMaxDisp);
        glUtils = new GLUtils(glOne.glProcessing);

        // Convert every frame once into white/black-level normalized fp16
        // (RawF16, NEON). The swap is size-neutral (uint16 -> half float), the
        // original raw is freed, and every later upload (merge00 / alignment
        // normalize / flowRGB inputs) feeds FLOAT_16 textures directly - the
        // shaders receive already-normalized floats and skip the
        // whitelevel/blackLevel math.
        long f16T = System.currentTimeMillis();
        for (int i = 0; i < images.size(); i++) {
            ImageFrame frame = images.get(i);
            if (frame.fp16 || frame.buffer == null) continue;
            ByteBuffer normalized;
            if (frame.packedBits > 0) {
                // Frame arrived as a packed bitstream (burst-memory saving):
                // unpack to uint16 first — createF16 reads raw sample counts.
                try (ImageFrame.Upload up = frame.upload()) {
                    normalized = Allocator.createF16(up.buffer,
                            parameters.rawSize.x, parameters.rawSize.y,
                            parameters.whiteLevel, parameters.blackLevel);
                }
            } else {
                normalized = Allocator.createF16(frame.buffer,
                        parameters.rawSize.x, parameters.rawSize.y,
                        parameters.whiteLevel, parameters.blackLevel);
            }
            if (normalized == null) {
                throw new IllegalStateException("createF16 failed for frame " + i);
            }
            Allocator.free(frame.buffer);
            frame.buffer = normalized;
            frame.fp16 = true;
            frame.packedBits = 0;
        }
        Log.d("ESD4D", "Stage[f16-convert] elapsed:" + (System.currentTimeMillis() - f16T) + " ms");

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
        long texT = System.currentTimeMillis();
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
        result = new GLTexture(raw,new GLFormat(GLFormat.DataType.FLOAT_16,1), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
        inputBase = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16,1), null, GL_NEAREST, GL_CLAMP_TO_EDGE);
        inputBase.loadRawHalf(images.get(0).buffer);
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
        // Black/white-level normalization now happens on the CPU (createF16),
        // so the shaders receive site-normalized fp16 directly; only the
        // max-black estimate for mergeAlign's minLevel noise floor remains.
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
        Log.d("ESD4D", "Stage[textures+base] elapsed:" + (System.currentTimeMillis() - texT) + " ms");
        //glUtils.convertVec4(base, "vec4(0.5)", base);
        //var buff = glUtils.GenerateGLImage(base.mSize, 4);
        //Log.d(Name, "Buffer first:" + buff.byteBuffer.get(0) + " " + buff.byteBuffer.get(1));
        //glUtils.Result(base.mSize, "noiseInput", buff.byteBuffer);

        double adaptiveNMpy = 1.0;
        final double baselineNoiseS = noiseS;
        final double baselineNoiseO = noiseO;
        if (tuning.enableAdaptiveNoise) {
            // Load independently of this capture's fit: a scene with too
            // little brightness variation must still use learned calibration.
            DynamicNoiseStore.NoiseEstimate storedNoise = tuning.enableNoiseStore
                    ? DynamicNoiseStore.dynamicNoiseStore.getEstimate(parameters.physicalID,
                            parameters.iso, modeler.AnalogueISO, parameters.binned) : null;
            if (storedNoise != null) {
                AdaptiveNoiseModel.Model prior = new AdaptiveNoiseModel.Model(storedNoise.s, storedNoise.o, 0);
                if (AdaptiveNoiseModel.isLowIso(parameters.iso, modeler.AnalogueISO)
                        && !storedNoise.higherIsoReference) {
                    prior = AdaptiveNoiseModel.limitToBaseline(prior, baselineNoiseS,
                            baselineNoiseO, tuning.lowIsoNoiseVarianceLimit);
                }
                noiseS = (float) prior.s;
                noiseO = (float) Math.max(prior.o, 0.05 * baselineNoiseO);
            }
            float[] exposureLayers = new float[images.size()];
            int[] frameIsos = new int[images.size()];
            for (int i = 0; i < images.size(); i++) {
                exposureLayers[i] = images.get(i).pair.layerMpy;
                frameIsos[i] = images.get(i).pair.iso;
            }
            int[] noiseFrames = AdaptiveNoiseModel.selectFrames(exposureLayers, frameIsos,
                    parameters.iso, Math.min(tuning.noiseBlendMaxFrames, BLEND_GRID.length));
            if (noiseFrames.length > 0) {
                // 2D histogram: (brightness_bin * NUM_VARIANCE_BINS + variance_bin) -> count
                // Model: variance = NoiseS * brightness + NoiseO  =>  sigma = sqrt(NoiseS*b + NoiseO)
                final int numBrightnessBins = 64;
                final int numVarianceBins = 64;
                final int noiseScanBins = numBrightnessBins * numVarianceBins; // 4096
                // Estimation input: progressive misaligned Gaussian blend of the
                // burst (see buildNoiseBlendFrame). The blend blurs scene detail
                // - the root cause of the old overestimation - while noise only
                // drops by the analytical residual response below.
                int blendFrames = noiseFrames.length;
                final double residualResponse = NoiseHistogramCalibration.residualStdResponse(
                        blendFrames, parameters.binned, parameters.cfaPattern) * tuning.noiseBlendCalMpy;
                float maxBlack = Math.max(Math.max(parameters.blackLevel[0], parameters.blackLevel[1]),
                        Math.max(parameters.blackLevel[2], parameters.blackLevel[3]));
                final double rawStep = 1.0 / Math.max(1.0, parameters.whiteLevel - maxBlack);
                // The old fixed 0.12-sigma axis left low-ISO noise in 1-3 bins.
                // The prior sets measurement resolution only; it does not set
                // the row estimates or their fitted coefficients.
                final float varianceScale = (float) NoiseHistogramCalibration.varianceScale(
                        numVarianceBins, residualResponse, noiseS, noiseO, rawStep, tuning.noiseHistogramRange);
                // Concentrate bins on the lower RAW signal range. Mobile RAWs
                // remain dark with the exposure metering used here; the sqrt(3)
                // factor intentionally excludes the upper range from calibration.
                final float brightnessScale = numBrightnessBins * (float) Math.sqrt(3.0);
                float[] spatialKernel = new float[9];
                long blendT = System.currentTimeMillis();
                GLTexture noiseInput = buildNoiseBlendFrame(8, spatialKernel, noiseFrames);
                Log.d("ESD4D", "Stage[noiseblend] elapsed:" + (System.currentTimeMillis() - blendT) + " ms");
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
                int scanStride = Math.max(1, tuning.noiseScanSubsample);
                // Small or strongly reduced inputs otherwise leave too few
                // observations in each lower-RAW row for a reliable median.
                while (scanStride > 1 && (long) packedSize.x * packedSize.y
                        / (scanStride * scanStride) < 16384) scanStride--;
                noiseHist.resize = scanStride;
                noiseHist.customKernel = spatialKernel;
                long histT = System.currentTimeMillis();
                int[][] noiseRes = noiseHist.Compute(noiseInput);
                // The accumulator may be the borrowed baseDiff (odd frame counts):
                // it must survive for the merge loop, which fully overwrites it.
                if (noiseInput != baseAlter && noiseInput != baseDiff) noiseInput.close();
                noiseHist.close();
                Log.d("ESD4D", "Stage[noise-histogram] elapsed:" + (System.currentTimeMillis() - histT) + " ms");
                int[] hist = noiseRes[0];
                long fitT = System.currentTimeMillis();
                AdaptiveNoiseModel.Model fitted = AdaptiveNoiseModel.fitSpatialHistogram(hist,
                        numBrightnessBins, numVarianceBins, brightnessScale, varianceScale,
                        residualResponse, tuning.noiseBrightnessTextureGate);
                measuredNoiseModel = fitted;
                if (fitted != null) {
                    Log.d("DynamicNoise", "Noise histogram fit: S=" + fitted.s + " O=" + fitted.o
                            + " rows=" + fitted.rows + " textureRows=" + fitted.textureRows);
                    // Scene texture is a positive variance bias, especially when
                    // daylight sensor noise is below the histogram resolution.
                    // Use the sensor prior until the persistent store can project
                    // a reliable model down from higher-ISO measurements.
                    if (AdaptiveNoiseModel.isLowIso(parameters.iso, modeler.AnalogueISO)
                            && (storedNoise == null || !storedNoise.higherIsoReference)) {
                        fitted = AdaptiveNoiseModel.limitToBaseline(fitted, baselineNoiseS,
                                baselineNoiseO, tuning.lowIsoNoiseVarianceLimit);
                    }
                    double fitS = Math.max(fitted.s, 1e-10);
                    double fitO = Math.max(fitted.o, 0.05 * baselineNoiseO);
                    if (tuning.enableNoiseStore) {
                        DynamicNoiseStore.NoiseEstimate blended =
                                DynamicNoiseStore.dynamicNoiseStore.commitAndGet(
                                        parameters.physicalID, parameters.iso, modeler.AnalogueISO,
                                        fitS, fitO, parameters.exposureTime, parameters.binned);
                        if (blended != null) {
                            AdaptiveNoiseModel.Model resolved = new AdaptiveNoiseModel.Model(blended.s, blended.o, 0);
                            if (AdaptiveNoiseModel.isLowIso(parameters.iso, modeler.AnalogueISO)
                                    && !blended.higherIsoReference) {
                                resolved = AdaptiveNoiseModel.limitToBaseline(resolved, baselineNoiseS,
                                        baselineNoiseO, tuning.lowIsoNoiseVarianceLimit);
                            }
                            fitS = resolved.s;
                            fitO = Math.max(resolved.o, 0.05 * baselineNoiseO);
                        }
                    }
                    if (tuning.enableFitOCorrection) fitO += fitS * fitS * 3.0 / 8.0;
                    noiseS = (float) fitS;
                    noiseO = (float) fitO;
                    Log.d("DynamicNoise", "Fitted noise model: NoiseS=" + noiseS
                            + " NoiseO=" + noiseO + " Half=" + Math.sqrt(noiseS * 0.5 + noiseO)
                            + " (points=" + fitted.points + ")");
                } else {
                    // A flat/narrow scene or clipped histogram cannot identify
                    // S and O. Keep the stored/sensor model; averaging residuals
                    // here previously promoted scene texture to extra denoising.
                    Log.d("DynamicNoise", "Insufficient noise fit coverage; retaining "
                            + (storedNoise == null ? "sensor" : "stored") + " noise model");
                }
                Log.d("ESD4D", "Stage[noise-fit] elapsed:" + (System.currentTimeMillis() - fitT) + " ms");
            } else {
                Log.d("DynamicNoise", "No matching sensor-domain calibration frames; retaining prior");
            }
            parameters.noiseModeler.baseModel = new Pair[] {
                    new Pair<>((double) noiseS, (double) noiseO),
                    new Pair<>((double) noiseS, (double) noiseO),
                    new Pair<>((double) noiseS, (double) noiseO)};
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
        if(tuning.enableHotPixelCorrection) {
            long hotT = System.currentTimeMillis();
            hotPixels(rawNoiseS, rawNoiseO);
            Log.d("ESD4D", "Stage[hotpixels] elapsed:" + (System.currentTimeMillis() - hotT) + " ms");
        }

        long brightT = System.currentTimeMillis();
        glProg.setLayout(tile,tile,1);
        glProg.useAssetProgram("merge/mergeGrayscale",true);
        glProg.setVar("inSize", packedSize);
        glProg.setTextureCompute("inTexture",base, false);
        glProg.setTextureCompute("outTexture",brightMap, true);
        glProg.computeAuto(brightMap.mSize, 1);
        exportBrightMap();
        Log.d("ESD4D", "Stage[brightmap] elapsed:" + (System.currentTimeMillis() - brightT) + " ms");
        // GPU copy consumed (the CPU copy feeds inference from here on):
        // release now instead of AfterRun so it doesn't span alignment +
        // the merge loop. Nulled; AfterRun null-guards it.
        brightMap.close();
        brightMap = null;
        // KernelNet's input derives from the reference frame only, so its
        // inference is independent of the alignment/merge loop below. Run it
        // on a worker thread concurrently with alignment (merge00 / FlowNet /
        // mergeAlign) and collect it just before the first combine pass needs
        // kernelsMap. The inference (and the model load inside it) touches no
        // GL state; the kernelsMap build/upload must rejoin the GL thread.
        final float kernelSigmaArg = kernelSigma * tuning.noiseMpy;
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
        // The merge loop uploads each alter frame into inputAlter just before
        // FlowNet consumes it, so create it up-front and share it (plus the
        // already-uploaded inputBase) instead of letting FlowNet allocate and
        // re-upload duplicates. -48 MB VRAM, -8 uploads; bit-exact (same
        // texture object sampled identically). Halide's gyro prewarp also
        // borrows this upload texture; its GPU scratch is released after matching.
        inputAlter = new GLTexture(parameters.rawSize, new GLFormat(GLFormat.DataType.FLOAT_16, 1), null, GL_NEAREST, GL_MIRRORED_REPEAT);
        if (tuning.gyroOnly) tuning.alignerSelect = "off";
        if (Objects.equals(tuning.alignerSelect, "halide") && !HalideAlignment.isAvailable()) {
            Log.w("ESD4D", "Halide unavailable; using GL alignment");
            tuning.alignerSelect = "gl";
        }
        float[][] gyroHomographies = new float[images.size()][];
        int gyroFrames = 0;
        if (tuning.gyroOnly || (tuning.gyroPrealignment && Objects.equals(tuning.alignerSelect, "halide"))) {
            for (int f = 1; f < images.size(); f++) {
                gyroHomographies[f] = GyroAlignment.between(images.get(0), images.get(f), parameters);
                if (gyroHomographies[f] != null) gyroFrames++;
                else if (tuning.gyroOnly) gyroHomographies[f] = new float[]{1,0,0,0,1,0,0,0,1};
            }
            Log.d("ESD4D", "Gyro prealignment: " + gyroFrames + "/" + (images.size()-1)
                    + " frames; local alignment=" + !tuning.gyroOnly);
        }
        // Merge tile size: parameters.tile (16 raw px) normally, doubled
        // when the Halide aligner runs without flow refinement - its plain
        // level-1 field carries one vector per 32 raw px, the alignment
        // block below resizes the atlas to that native grid, and the merge
        // then blends one vector per tile.
        int mergeTile = parameters.tile;
        Point alignmentOutputSize = new Point(parameters.alignmentSize.x * parameters.tilesX,
                parameters.alignmentSize.y * ((images.size()-1)/parameters.tilesX + 1));
        Log.d("Alignment", "alignment pipeline size: " + alignmentOutputSize.x + " " + alignmentOutputSize.y);
        // Aligner selector: 0 = GL pyramid (disables FlowNet), 1 = FlowNet,
        // 2 = Halide CPU. FlowNet keeps its init fallback to the pyramid.
        if (Objects.equals(tuning.alignerSelect, "flownet")) {
            FlowNetAlignment flowNetAlignmentTmp = new FlowNetAlignment(alignmentOutputSize, images, glProg, glUtils, this, minExpIdx);
            flowNetAlignmentTmp.parameters = parameters;
            flowNetAlignmentTmp.shareInputTextures(inputBase, inputAlter);
            long startTime = System.currentTimeMillis();
            boolean useNcnnFlow = flowNetAlignmentTmp.initFlow();
            Log.d("ESD4D", "FlowNet alignment init time: " + (System.currentTimeMillis() - startTime) + "ms");
            if (useNcnnFlow) {
                flowNetAlignment = flowNetAlignmentTmp;
                alignmentTex = flowNetAlignment.flowTex;
            } else {
                flowNetAlignmentTmp.close();
                tuning.alignerSelect = "off";
                Log.d("ESD4D", "FlowNet alignment disabled, using identity alignment");
            }
        }
        if (Objects.equals(tuning.alignerSelect, "halide")) {
            // CPU/NEON Halide path; identical Result
            // atlas format, so the merge below is unchanged.
            boolean halideRefine = tuning.halideRefineFlow;
            if (!halideRefine && tuning.alignDebugCompare == 1) {
                // The aligner debug compare indexes both aligners' atlases
                // cell-by-cell on the shared raw/16 grid; the level-1-native
                // atlas does not match it. Force the refined field for this
                // run so the comparison stays meaningful.
                Log.d("ESD4D", "alignDebugCompare: forcing Halide flow refinement on (raw/16 grid)");
                halideRefine = true;
            }
            if (!halideRefine) {
                // Plain level-1 field: one vector per 32 raw px. Resize the
                // atlas to that native density so every vector is packed 1:1
                // (no 2x2 duplication) and double the merge tile - each merge
                // tile then consumes exactly one vector and mergeAlign's
                // cosine windows cross-fade between genuinely adjacent
                // vectors instead of duplicated copies.
                parameters.alignmentSize = new Point(
                        parameters.rawSize.x / (parameters.tile * 2) + 1,
                        parameters.rawSize.y / (parameters.tile * 2) + 1);
                alignmentOutputSize = new Point(parameters.alignmentSize.x * parameters.tilesX,
                        parameters.alignmentSize.y * ((images.size() - 1) / parameters.tilesX + 1));
                mergeTile = parameters.tile * 2;
            }
            HalideAlignment halideAlignment = new HalideAlignment(alignmentOutputSize, images, glProg, inputAlter);
            halideAlignment.parameters = parameters;
            halideAlignment.refineFlow = halideRefine;
            halideAlignment.accurateSubpixel = tuning.halideAccurateSubpixel;
            halideAlignment.coarseGlobal = tuning.halideCoarseGlobal;
            halideAlignment.noiseS = rawNoiseS * alignmentNoiseModelScale;
            halideAlignment.noiseO = rawNoiseO * alignmentNoiseModelScale;
            halideAlignment.gyroHomographies = gyroHomographies;
            if (alignmentObserver != null || tuning.alignDebugCompare == 1) {
                try (HalideAlignment batch = halideAlignment) {
                    batch.Run();
                }
            } else {
                frameAlignment = halideAlignment;
                frameAlignment.startStreaming();
            }
            alignmentTex = halideAlignment.Result;
            if (tuning.alignDebugCompare == 1 && gyroFrames == 0) {
                logAlignerCompare(alignmentOutputSize, images);
            } else if (tuning.alignDebugCompare == 1) {
                Log.d("ESD4D", "Aligner comparison skipped: Halide vectors are gyro-space residuals");
            }
        } else if (Objects.equals(tuning.alignerSelect, "gl")) {
            PyramidAlignment pyramidAlignment = new PyramidAlignment(alignmentOutputSize, images, glProg, glUtils, this);
            pyramidAlignment.parameters = parameters;
            pyramidAlignment.startLevel = tuning.alignmentStartLevel;
            pyramidAlignment.shareInputTextures(inputBase, inputAlter);
            // P2 (H2): lend baseDiff as pyramid scratch. Valid only when the
            // packed grid matches rawHalf exactly (cfaShift==0); otherwise the
            // 1-px size mismatch would diverge border texels.
            if (cfaShift.x == 0 && cfaShift.y == 0) pyramidAlignment.borrowTempTexture(baseDiff);
            long startTime = System.currentTimeMillis();
            pyramidAlignment.Run();
            Log.d("ESD4D", "Alignment time: " + (System.currentTimeMillis() - startTime) + "ms");
            alignmentTex = pyramidAlignment.Result;
            pyramidAlignment.close();
        } else if (Objects.equals(tuning.alignerSelect, "off")) {
            alignmentTex = new GLTexture(alignmentOutputSize, new GLFormat(GLFormat.DataType.FLOAT_16, 4),
                    BufferUtils.getFrom(new float[alignmentOutputSize.x * alignmentOutputSize.y * 4]),
                    GL_NEAREST, GL_CLAMP_TO_EDGE);
            Log.d("ESD4D", tuning.gyroOnly ? "Gyro-only merge: zero local residuals"
                    : "Alignment disabled, using identity alignment");
        }

        //Point aSize = new Point(parameters.rawSize.x/(2*parameters.tile) + 1, parameters.rawSize.y/(2*parameters.tile) + 1);
        Point border = new Point(16,16);
        if (alignmentObserver != null)
            alignmentObserver.onAlignment(alignmentTex, mergeTile, parameters.alignmentSize, parameters.tilesX);
        // NOTE: inputAlter is created above alignment (shared with FlowNet / gyro prewarp).
        //alignmentTex = new GLTexture(aSize, new GLFormat(GLFormat.DataType.FLOAT_32, 2), alignment, GL_NEAREST, GL_MIRRORED_REPEAT);

        //counter.put(1.0f,1.0f);
        float cnt1 = 2.0f;

        float cnt2 = 1.0f;
        //Log.d("ESD4D", "alignment size: " + aSize.x + " " + aSize.y);
        Log.d("ESD4D", "alignment size: " + parameters.alignmentSize.x + " " + parameters.alignmentSize.y);
        float maxBlack = Math.max(blackLevel[0], Math.max(blackLevel[1], Math.max(blackLevel[2], blackLevel[3])));
        float minLevel = (float) (1.0/(double)(parameters.whiteLevel-maxBlack));

        // The base frame's pixels are on the GPU now (inputBase upload, shared
        // with the alignment stage; synchronous). The loop below only touches
        // its GPU texture and scalar pair metadata, and the base index is
        // never loaded there, so release the native copy up-front: it would
        // otherwise outlive the whole merge.
        images.get(0).close();

        // Running accumulation: starts at the merge00 conversion in base;
        // each combine pass writes into the other slot (getBase) and adopts
        // it here. The slot fields themselves never change - that is what
        // keeps the rotation on two distinct textures.
        GLTexture mergeAcc = base;

        long mergeLoopT = System.currentTimeMillis();
        boolean supportInitialized = false;
        for (int f = 0; f < images.size(); f++) {
            startT();
            if(f == minExpIdx) continue;
            int ind = f;
            if(ind == 0){
                ind = minExpIdx;
            }
            ImageFrame frame = images.get(ind);
            float exposure = 1.f/frame.pair.layerMpy;
            Point shift;
            if (frameAlignment != null) {
                frameAlignment.alignFrame(ind);
                shift = new Point(0, 0);
            } else {
                shift = PyramidAlignment.alignmentShift(parameters, ind);
            }
            //int f = 1;
            Log.d("ESD4D", "load:"+frame.pair.curlayer.name() + " " + frame.pair.layerMpy);
            long stageT = System.currentTimeMillis();
            inputAlter.loadRawHalf(frame.buffer);
            gpuSyncProfile();
            if (tuning.profileGpuSync)
                Log.d("ESD4D", "Stage[merge:upload] elapsed:" + (System.currentTimeMillis() - stageT) + " ms f=" + f);

            GLTexture flowTex = null;
            if(Objects.equals(tuning.alignerSelect, "flownet")) {
                // Dense FlowNet optical flow for THIS alter frame, computed just
                // in time (one pair at a time, no stored flow fields). Must run
                // before the mergeAlign program is bound below.
                flowTex = flowNetAlignment.computeFlow(ind);
            }

            // Convert inputAlter to alter (vec4 format)
            stageT = System.currentTimeMillis();
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram("merge/merge00", true);
            glProg.setVar("exposure", 1.f/images.get(0).pair.layerMpy);
            glProg.setVar("createDiff", 0);
            glProg.setVar("cfaShift", cfaShift);
            glProg.setTexture("inTexture", inputAlter);
            glProg.setTextureCompute("outTexture", alter, true);
            glProg.computeAuto(new Point(alter.mSize.x, alter.mSize.y), 1);
            gpuSyncProfile();
            if (tuning.profileGpuSync)
                Log.d("ESD4D", "Stage[merge:pack] elapsed:" + (System.currentTimeMillis() - stageT) + " ms f=" + f);

            //alignmentTex.loadData(alignment.position((ind-1)*(aSize.x*aSize.y*4*2)));
            glProg.setDefine("TILE_AL", mergeTile);
            boolean gyroWarp = gyroHomographies[ind] != null;
            glProg.setDefine("GYRO_WARP", gyroWarp ? 1 : 0);
            int halideSampling = 0;
            if (Objects.equals(tuning.alignerSelect, "halide")) {
                if (Objects.equals(tuning.halideMergeSampling, "nearest")) halideSampling = 1;
                else if (Objects.equals(tuning.halideMergeSampling, "bilinear")) halideSampling = 2;
                else if (Objects.equals(tuning.halideMergeSampling, "cubic")) halideSampling = 3;
                else if (Objects.equals(tuning.halideMergeSampling, "lanczos2")) halideSampling = 4;
            }
            glProg.setDefine("HALIDE_SAMPLING", halideSampling);
            glProg.setDefine("HALIDE_MOTION_FIELD", tuning.halideInterpolateMotion ? 1 : 0);
            boolean residualFusion = tuning.cfaResidualFusion > 0.0f && !gyroWarp
                    && Objects.equals(tuning.alignerSelect, "halide") && halideSampling > 0;
            glProg.setDefine("CFA_RESIDUAL_FUSION", residualFusion ? 1 : 0);
            stageT = System.currentTimeMillis();
            glProg.setLayout(tile, tile, 1);
            glProg.useAssetProgram(Objects.equals(tuning.alignerSelect, "flownet") ? "merge/mergeAlignFlow" : "merge/mergeAlign", true);
            if (gyroWarp) {
                // GLES requires transpose=false; Java matrices are row-major.
                glProg.setVar("gyroHomography", false, GyroAlignment.columnMajor(gyroHomographies[ind]));
            }
            glProg.setVar("rawHalf", rawHalf);
            if (residualFusion) {
                glProg.setVar("cfaResidualStrength", Math.min(tuning.cfaResidualFusion, 1.0f));
                glProg.setVar("cfaNoiseS", rawNoiseS);
                glProg.setVar("cfaNoiseO", rawNoiseO);
            }
            glProg.setVar("whitePoint", parameters.whitePoint);
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
            if(Objects.equals(tuning.alignerSelect, "flownet")) {
                glProg.setTexture("alignmentTexture", flowTex);
            } else {
                glProg.setVar("shift", shift);
                glProg.setVar("alignmentSize", parameters.alignmentSize);
                glProg.setTexture("alignmentTexture", alignmentTex);
            }
            glProg.setTexture("inTexture", inputBase);
            // Zero-strength Halide warping does not read the accumulator.
            // FlowNet still needs it for its luma guide.
            if (residualFusion || Objects.equals(tuning.alignerSelect, "flownet")) {
                glProg.setTextureCompute("baseTexture", mergeAcc, false);
            }
            glProg.setTextureCompute("alterTexture", alter, false);
            glProg.setTextureCompute("outTexture", baseDiff, true);
            glProg.computeAuto(baseDiff.mSize, 1);
            gpuSyncProfile();
            Log.d("ESD4D", "Stage[merge:mergeAlign] elapsed:" + (System.currentTimeMillis() - stageT) + " ms f=" + f);

            if (PhotonCamera.DEBUG)
                Log.d("ESD4D", "create diff");

            // First combine pass: collect the KernelNet result that has been
            // running concurrently with alignment and this frame's merge00 /
            // mergeAlign work. Waits only for any inference remainder; the
            // texture build below needs the GL thread anyway.
            stageT = System.currentTimeMillis();
            if (kernelNetThread != null) {
                try {
                    kernelNetThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                kernelNetThread = null;
                kernelsMap = createKernelsMap(kernelNetResult.get());
                // Inference joined and params uploaded: both CPU copies are
                // dead past this point (GPU textures carry on).
                brightMapCPU = null;
                brightMapCPUSize = null;
                gpuSyncProfile();
                Log.d("ESD4D", "Stage[merge:kernelnet-join] elapsed:" + (System.currentTimeMillis() - stageT) + " ms f=" + f);
            }

            glProg.setLayout(tile, tile, 1);
            stageT = System.currentTimeMillis();
            // Robustness measures frame-minus-base residuals for every sampler;
            // it does not require the optional residual reconstruction update.
            boolean robustResidual = tuning.cfaResidualRobustness;
            glProg.useAssetProgram(robustResidual ? "merge/mergeCombineWeight1Residual" : "merge/mergeCombineWeight1", true);
            if (robustResidual) {
                if (mergeSupport == null) {
                    mergeSupport = new GLTexture(packedSize,new GLFormat(GLFormat.DataType.FLOAT_32,1),null,GL_NEAREST,GL_CLAMP_TO_EDGE);
                }
                glProg.setVar("supportInitialized", supportInitialized ? 1 : 0);
                glProg.setVar("rawHalf",rawHalf);
                glProg.setVar("cfaShift",cfaShift);
                glProg.setTextureCompute("supportTexture",mergeSupport,android.opengl.GLES31.GL_READ_WRITE);
            }
            glProg.setVar("cfaPattern", parameters.cfaPattern);
            glProg.setTexture("inTex", inputBase);
            glProg.setTexture("kernelsMap", kernelsMap);
            // Optical flow refinement: brute-force diagonal candidate wins
            // only when it beats the zero offset beyond the shader's gates.
            //glProg.setVar("enableFlow", enableFlowRefinement ? 1 : 0);
            glProg.setVar("flowNoiseS", rawNoiseS);
            glProg.setVar("flowNoiseO", rawNoiseO);
            glProg.setTextureCompute("inTexture", mergeAcc, false);
            glProg.setTextureCompute("diffTexture", baseDiff, false);
            GLTexture previousMerge = mergeAcc;
            mergeAcc = getBase();
            glProg.setTextureCompute("outTexture", mergeAcc, true);
            glProg.setVar("noiseS", noiseS);
            glProg.setVar("noiseO", noiseO);
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
            glProg.computeAuto(mergeAcc.mSize, 1);
            // Submit this frame before the CPU enters Halide for the next one.
            if (frameAlignment != null) android.opengl.GLES30.glFlush();
            supportInitialized = robustResidual;
            if (mergeObserver != null) {
                float mergeWeight = exposure >= 0.95f ? 1.0f/(cnt1-1.0f) : 1.0f/(cnt2-1.0f);
                mergeObserver.onMerge(ind, mergeWeight, noiseS, noiseO,
                        previousMerge, baseDiff, mergeAcc, kernelsMap);
            }
            gpuSyncProfile();
            Log.d("ESD4D", "Stage[merge:combine] elapsed:" + (System.currentTimeMillis() - stageT) + " ms f=" + f);
            // This frame's pixels are on the GPU now: inputAlter.loadData()
            // (and FlowNet's computeFlow()) upload synchronously, and
            // everything above only touches GPU textures plus scalar pair
            // metadata afterwards. Release the native copy so peak memory no
            // longer holds the whole burst through the merge. close() is
            // idempotent, so HdrxProcessor's post-merge loop stays a safe net.
            images.get(ind).close();
            endT();
        }
        releaseFrameAlignment();
        Log.d("ESD4D", "Stage[merge-loop] elapsed:" + (System.currentTimeMillis() - mergeLoopT) + " ms");

        // The merge result stays normalized fp16 end-to-end: merge2o unpacks
        // the packed quads straight into the R16F output buffer (no uint16
        // re-encode); PostPipeline consumes it as-is and the uint16 DNG save
        // re-encodes on the CPU (Allocator.createU16FromF16).
        // Temporal temporaries are dead past this point: merge2o below reads
        // only mergeAcc + alignmentTex. Release ~530 MB (64 MP) before the output
        // readback instead of AfterRun. Fields are nulled and AfterRun
        // null-guards them, so a stale close can never delete a recycled ID.
        long mergeOutT = System.currentTimeMillis();
        baseDiff.close(); baseDiff = null;
        if (mergeSupport != null) { mergeSupport.close(); mergeSupport = null; }
        alter.close(); alter = null;
        inputAlter.close(); inputAlter = null;
        inputBase.close(); inputBase = null;

        glProg.setLayout(tile,tile,1);
        glProg.useAssetProgram("merge/merge2o");
        glProg.setVar("cfaShift", cfaShift); // uniform: GLProg clears defines after each load
        glProg.setTexture("inTexture",mergeAcc);
        glProg.setTexture("alignmentTexture", alignmentTex);
        result.BufferLoad();
        glOne.glProcessing.drawBlocksToOutput();
        Output = glOne.glProcessing.mOutBuffer;
        // The tiled glReadPixels inside drawBlocksToOutput drains every
        // compute dispatch the merge loop queued asynchronously, so this
        // stage carries the loop's real GPU execution time on top of the
        // readback transfer itself.
        Log.d("ESD4D", "Stage[merge2o+readback] elapsed:" + (System.currentTimeMillis() - mergeOutT) + " ms");
        long teardownT = System.currentTimeMillis();
        AfterRun();
        Log.d("ESD4D", "Stage[afterrun] elapsed:" + (System.currentTimeMillis() - teardownT) + " ms");
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
     * as RGBA-interleaved (s1, s2, rho, 1) floats, or null if the model isn't available.
     * Takes ~40-170ms at high res; Run() calls this on a worker thread in
     * parallel with the alignment loop and collects the result before the
     * first mergeCombineWeight0 pass. Touches no GL state, so it is safe to
     * call off the GL thread.
     */
    public KernelNetResult runKernelNetInference(float sigma) {
        if (brightMapCPU == null || brightMapCPUSize == null) return null;
        var ctx = PhotonCamera.getAppContext();
        if (ctx == null) return null;
        // Process-wide shared instance (kept warm across shots): do NOT
        // close it here. runInference blocks until the model is ready.
        KernelNetNcnnProcessor processor = KernelNetNcnnProcessor.start(ctx);
        if (!processor.isReady()) return null;
        return processor.runInference(brightMapCPU, brightMapCPUSize.x, brightMapCPUSize.y, sigma);
    }

    /**
     * Uploads the KernelNet parameter map as an RGBA16F texture for the
     * anisotropic Gaussian filter: texel = (s1, s2, rho, 1). The inference
     * result already comes back as RGBA-interleaved fp16 halves in the exact
     * GL_RGBA16F layout (see {@link KernelNetNcnnProcessor}), so it is
     * uploaded with GL_HALF_FLOAT — no repack, no driver FLOAT->HALF
     * conversion. Also publishes the same buffer as {@link #kernelsMapCPU}
     * for the post pipeline. The texture is left open for downstream use;
     * caller owns it.
     */
    public GLTexture createKernelsMap(KernelNetResult result) {
        if (result == null) return null;
        int w = result.width();
        int h = result.height();
        GLTexture map = new GLTexture(new Point(w, h), new GLFormat(GLFormat.DataType.FLOAT_16, 4), null);
        // The inference result already comes back RGBA-interleaved fp16 (see
        // {@link KernelNetNcnnProcessor}), so the buffer is uploaded as-is.
        // Also publishes the same interleaved buffer as {@link #kernelsMapCPU}
        // for the post pipeline; the malloc'd base buffer rides along via
        // kernelsMapBase so the post pipeline can free it deterministically
        // once uploaded (views don't free). The texture is left open for
        // downstream use; caller owns it.
        ByteBuffer halves = result.params();
        halves.position(0);
        try {
            map.loadRawHalf(halves);
        } catch (Throwable t) {
            // Upload failed: the malloc'd result has no other owner yet.
            com.particlesdevs.photoncamera.util.Allocator.free(result.params());
            throw t;
        }
        kernelsMapCPU = halves.asShortBuffer();
        kernelsMapCPUSize = new Point(w, h);
        kernelsMapBase = halves;
        return map;
    }

    @Override
    public void AfterRun() {
        // The unpack staging cache is only needed while frames are uploaded
        // (this phase). Release it here so post-merge MemStage baselines are
        // unchanged vs per-upload malloc/free.
        ImageFrame.releaseUploadStaging();
        // baseDiff/alter/inputAlter/inputBase/brightMap may already be
        // released post-loop (nulled there); guard so a stale close can
        // never delete a recycled texture ID.
        if (inputAlter != null) inputAlter.close();
        if (alter != null) alter.close();
        if (inputBase != null) inputBase.close();
        if (baseDiff != null) baseDiff.close();
        if (mergeSupport != null) mergeSupport.close();
        base.close();
        baseAlter.close();
        if (brightMap != null) brightMap.close();
        // The kernel-params texture is only read by the merge passes above;
        // downstream uses the fp32 CPU view. Free the GPU copy with the rest
        // instead of leaving it registered until context teardown.
        if (kernelsMap != null) {
            kernelsMap.close();
            kernelsMap = null;
        }
        result.close();
        if(Objects.equals(tuning.alignerSelect, "flownet") && flowNetAlignment != null) {
            // Closes flowTex (== alignmentTex), so drop the reference to avoid
            // a double close below.
            flowNetAlignment.close();
            flowNetAlignment = null;
            alignmentTex = null;
        } else {
            alignmentTex.close();
        }
        if(kernelsMap != null) kernelsMap.close();
        GLTexture.notClosed();
    }

    /**
     * Debug: runs the GL block-pyramid aligner in addition to the already
     * computed Halide atlas (alignmentTex) and logs per-frame differences
     * between the two vector fields. A constant offset reveals a packing or
     * sign-convention bug; scene-scaled differences reveal a grid/phase
     * mismatch; random differences are matcher noise.
     */
    private void logAlignerCompare(Point atlasSize, ArrayList<ImageFrame> images) {
        try {
            long t0 = System.currentTimeMillis();
            PyramidAlignment pyramidAlignment = new PyramidAlignment(atlasSize, images, glProg, glUtils, this);
            pyramidAlignment.parameters = parameters;
            pyramidAlignment.startLevel = tuning.alignmentStartLevel;
            pyramidAlignment.Run();
            Log.d("AlignerCompare", "GL pyramid reference computed in "
                    + (System.currentTimeMillis() - t0) + "ms");

            Point rawHalf = new Point(parameters.rawSize.x / 2, parameters.rawSize.y / 2);
            float[] halide = readAtlasFloats(alignmentTex, atlasSize);
            float[] glpyr = readAtlasFloats(pyramidAlignment.Result, atlasSize);
            pyramidAlignment.close();
            if (halide == null || glpyr == null) return;

            for (int f = 1; f < images.size(); f++) {
                Point shift = PyramidAlignment.alignmentShift(parameters, f);
                ArrayList<Float> dxs = new ArrayList<>();
                ArrayList<Float> dys = new ArrayList<>();
                float sdx = 0, sdy = 0;
                for (int ty = 0; ty < parameters.alignmentSize.y; ty++) {
                    for (int tx = 0; tx < parameters.alignmentSize.x; tx++) {
                        int o = ((shift.y + ty) * atlasSize.x + shift.x + tx) * 4;
                        if (o + 3 >= glpyr.length) continue;
                        float hdx = decodeDx(halide, o, rawHalf.x), hdy = decodeDy(halide, o, rawHalf.y);
                        float gdx = decodeDx(glpyr, o, rawHalf.x), gdy = decodeDy(glpyr, o, rawHalf.y);
                        dxs.add(hdx - gdx);
                        dys.add(hdy - gdy);
                        sdx += hdx - gdx;
                        sdy += hdy - gdy;
                    }
                }
                int n = dxs.size();
                // A few sample cells: center + four corners of the grid.
                StringBuilder samples = new StringBuilder();
                int[][] probe = {
                        {parameters.alignmentSize.x / 2, parameters.alignmentSize.y / 2},
                        {2, 2}, {parameters.alignmentSize.x - 3, 2},
                        {2, parameters.alignmentSize.y - 3},
                        {parameters.alignmentSize.x - 3, parameters.alignmentSize.y - 3}};
                for (int[] pc : probe) {
                    int o = ((shift.y + pc[1]) * atlasSize.x + shift.x + pc[0]) * 4;
                    if (o + 3 >= glpyr.length) continue;
                    samples.append(String.format(" [(%d,%d) h(%+.1f,%+.1f) g(%+.1f,%+.1f)]",
                            pc[0], pc[1],
                            decodeDx(halide, o, rawHalf.x), decodeDy(halide, o, rawHalf.y),
                            decodeDx(glpyr, o, rawHalf.x), decodeDy(glpyr, o, rawHalf.y)));
                }
                Log.d("AlignerCompare", String.format(
                        "frame %d: mean diff (%+.2f,%+.2f) rawHalf texels over %d cells%s",
                        f, sdx / n, sdy / n, n, samples));
            }
        } catch (Throwable tr) {
            Log.w("AlignerCompare", "comparison failed", tr);
        }
    }

    private static float decodeDx(float[] atlas, int o, float rawHalf) {
        return (float) Math.floor(atlas[o] * rawHalf + 0.5f) + atlas[o + 2];
    }

    private static float decodeDy(float[] atlas, int o, float rawHalf) {
        return (float) Math.floor(atlas[o + 1] * rawHalf + 0.5f) + atlas[o + 3];
    }

    private static float[] readAtlasFloats(GLTexture tex, Point size) {
        java.nio.ByteBuffer buf = tex.textureBuffer(tex.mFormat);
        buf.order(java.nio.ByteOrder.nativeOrder());
        java.nio.FloatBuffer fb = buf.asFloatBuffer();
        float[] out = new float[size.x * size.y * 4];
        fb.get(out);
        return out;
    }
}
