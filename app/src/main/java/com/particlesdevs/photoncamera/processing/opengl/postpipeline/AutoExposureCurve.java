    package com.particlesdevs.photoncamera.processing.opengl.postpipeline;

    import android.graphics.Point;
    import com.particlesdevs.photoncamera.processing.opengl.GLFormat;
    import com.particlesdevs.photoncamera.processing.opengl.GLTexture;
    import com.particlesdevs.photoncamera.processing.opengl.nodes.Node;
    import com.particlesdevs.photoncamera.processing.opengl.scripts.GLHistogram;
    import com.particlesdevs.photoncamera.settings.annotations.Tunable;
    import com.particlesdevs.photoncamera.util.BufferUtils;
    import com.particlesdevs.photoncamera.util.Log;
    import com.particlesdevs.photoncamera.util.Math2;

    import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
    import static android.opengl.GLES20.GL_LINEAR;

    /**
     * Curve-based auto exposure for the default tone pipeline.
     *
     * Runs before {@link Initial} on the linear input and estimates exposure
     * with the classic AutoExposure scheme (fill-coefficient weighted average
     * gain, noise/max clamps, Reinhard normalization, top-0.5% white point
     * search), plus an adaptive highlight shoulder: the image fraction that
     * would clip under the estimated response drives a soft knee that
     * compresses the top of the range toward 1.0 on HDR scenes instead of
     * blowing out to flat white. Instead of applying the gain in its own
     * full-res pass, it bakes the per-channel display response (gamma lift
     * -&gt; gain -&gt; extended Reinhard -&gt; gamma lift -&gt; shoulder) into a 1D
     * curve texture stored in {@link PostPipeline#exposureCurve}; Initial
     * samples that curve at the end of its shader, fusing the exposure pass
     * into Initial's draw.
     *
     * The histogram covers the linear input, so bins are mapped through the
     * sRGB OETF to keep the gain estimate in the display domain the curve
     * operates on. The input is white-balanced (Bayer2Float divides by the
     * camera neutral point), so bins are additionally scaled per channel by
     * {@link GLHistogram#exposure} to resolve the real range up to
     * 1/whitePoint[c]; without that, everything above linear 1.0 clamps into
     * the top bin and the white point search cannot see the true scene white.
     *
     * Renders nothing: passes the input texture through and marks the program
     * closed so the pipeline skips the draw for this node.
     */
    public class AutoExposureCurve extends Node {
        @Tunable(title = "Histogram size", category = "Auto Exposure", defaultValue = 256, min = 256, max = 16384, step = 16, description = "Histogram bin count")
        int histSize;

        @Tunable(title = "Target Brightness", category = "Auto Exposure", max = 255.0f, defaultValue = 128.0f)
        float target;

        @Tunable(title = "Noise Max", category = "Auto Exposure", max = 1.0f, defaultValue = 0.05f)
        float noiseMax;

        @Tunable(title = "Gain Max", category = "Auto Exposure", max = 20.0f, defaultValue = 9.0f)
        float gainMax;

        @Tunable(title = "Enable WhitePoint Search", category = "Auto Exposure", defaultValue = 1, min = 0, max = 1, step = 1, description = "Enable white point search for Reinhard tone mapping")
        boolean enableWP;

        @Tunable(title = "WhitePoint apply level", category = "Auto Exposure", min = 0.0f, max = 1.0f, step = 0.1f, defaultValue = 0.8f, description = "Lower level disables white point, higher level applies full")
        float whiteApply;

        @Tunable(title = "Fill coefficient", category = "Auto Exposure", min = 0.0f, max = 1.0f, step = 0.01f, defaultValue = 0.99f, description = "Lower fill ratio can skip right histogram value peaks for HDR scenarios")
        float fillCoefficient;

    @Tunable(title = "Apply gamma mix", category = "Auto Exposure", min = 0.0f, max = 1.0f, step = 0.01f, defaultValue = 0.05f, description = "Blend between AE color space sRGB-linear")
    float applyGammaMix;

    @Tunable(title = "Highlight Compression", category = "Auto Exposure", defaultValue = 1, min = 0, max = 1, step = 1, description = "Adaptive highlight shoulder: smoothly compress the top of the range on HDR scenes instead of clipping to flat white")
    boolean highlightCompression;

    @Tunable(title = "Highlight Knee Max", category = "Auto Exposure", min = 0.6f, max = 1.0f, step = 0.01f, defaultValue = 0.9f, description = "Shoulder start when almost nothing clips (higher = later rolloff)")
    float kneeMax;

    @Tunable(title = "Highlight Knee Min", category = "Auto Exposure", min = 0.3f, max = 1.0f, step = 0.01f, defaultValue = 0.55f, description = "Shoulder start at heavy clipping (lower = stronger highlight compression)")
    float kneeMin;

    @Tunable(title = "Highlight Clip Ref", category = "Auto Exposure", min = 0.01f, max = 0.5f, step = 0.01f, defaultValue = 0.1f, description = "Clipped image fraction at which the knee reaches its minimum")
    float kneeRef;

    @Tunable(title = "Highlight Clip Tolerance", category = "Auto Exposure", min = 0.0f, max = 0.2f, step = 0.01f, defaultValue = 0.03f, description = "Response headroom above display white before a pixel counts as clipped; absorbs bin quantization so an exactly-white highlight pile does not trigger the shoulder")
    float clipTolerance;

    @Tunable(title = "Adaptive WhitePoint", category = "Auto Exposure", defaultValue = 1, min = 0, max = 1, step = 1, description = "Measure the scene white above display white and divide the input by it in initial.glsl, folding over-range highlight detail below display white")
    boolean adaptiveWhitePointEnable;

        private static final int CURVE_SIZE = 1024;

        public AutoExposureCurve() {
            super("", "AutoExposureCurve");
        }

        @Override
        public void AfterRun() {
        }

        @Override
        public void Compile() {}

        @Override
        public void Run() {
            int bins = histSize;
            if (bins < 16) bins = 256; // guard against a failed tunable injection

            GLHistogram histogram = new GLHistogram(glProg, bins);
            histogram.Rc = true;
            histogram.Gc = true;
            histogram.Bc = true;
            histogram.Ac = false;
            // The input is white-balanced: Bayer2Float divides by the camera
            // neutral point, so a photosite that clips at sensor white holds up
            // to 1/whitePoint[c] after balancing - a range the inpaint-opposed
            // reconstruction actively fills. Scale the histogram bins per
            // channel to that real extent; with the default exposure of 1.0
            // everything above linear 1.0 clamps into the top bin and neither
            // the white point search nor the shoulder estimate can see the
            // true highlight energy.
            float[] sensorWP = basePipeline.mParameters.whitePoint;
            float[] extent = new float[3];
            for (int c = 0; c < 3; c++) {
                extent[c] = sensorWP != null && sensorWP[c] > 0.f && sensorWP[c] < 1.f
                        ? 1.f / sensorWP[c] : 1.f;
                histogram.exposure[c] = 1.f / extent[c];
            }
            // Green sites are inpainted from the R/B opposed colours, so G's
            // reconstruction ceiling is the cube-space mean of the R/B
            // extents. Extend G's histogram range accordingly - with G's
            // default extent of 1.0 its reconstructed white would clamp into
            // the top bin and read ~1.0.
            float gCeiling = (float) Math.pow((Math.cbrt(extent[0]) + Math.cbrt(extent[2])) * 0.5, 3.0);
            if (gCeiling > extent[1]) {
                extent[1] = gCeiling;
                histogram.exposure[1] = 1.f / gCeiling;
            }
            Log.d(Name, "Histogram extent:" + extent[0] + "," + extent[1] + "," + extent[2]
                    + " exposure:" + histogram.exposure[0] + "," + histogram.exposure[1] + "," + histogram.exposure[2]
                    + " whitePoint:" + (sensorWP != null ? sensorWP[0] + "," + sensorWP[1] + "," + sensorWP[2] : "null"));
            int[][] result;
            try {
                result = histogram.Compute(previousNode.WorkingTexture);
            } finally {
                histogram.close();
            }

            // Map the linear bins to the display domain (bin units) so the
            // gain math below estimates the display-referred exposure. Bin i
            // of channel c covers linear i/(bins-1)*extent[c], evaluated at
            // the bin centre so the mapping is unbiased - a floor()-quantized
            // pile at linear 1.0 must not read ~0.997 or the white point
            // search reports white just below the gain and the clipped test
            // below misfires on it. The OETF is extended past 1.0 so real
            // over-range whites keep their magnitude.
            float[][] mapped = new float[3][bins];
            for (int c = 0; c < 3; c++) {
                for (int i = 0; i < bins; i++) {
                    mapped[c][i] = srgbEncodeExtended((i + 0.5f) / (bins - 1.0f) * extent[c]) * (bins - 1.0f);
                }
            }

            int histNormR = 0;
            int histNormG = 0;
            int histNormB = 0;
            for (int i = 0; i < bins; i++) {
                histNormR += result[0][i];
                histNormG += result[1][i];
                histNormB += result[2][i];
            }
            float avg = estimateAvg(result, mapped, bins, histNormR, histNormG, histNormB);
            float mpy = clampGain((bins / 256.0f) * target / Math.max(avg, 1.0e-4f));
            float sceneWhite = searchWhite(result, mapped, bins, histNormR, histNormG, histNormB, mpy);

            // Adaptive white point: the measured scene white sits above display
            // white on HDR scenes (the over-range highlights the inpaint-opposed
            // reconstruction fills). initial.glsl divides its input by this
            // scalar, anchoring whites at 1.0 before the SDR tone chain whose
            // clamps would otherwise destroy the over-range detail. The
            // histogram bins are remapped to the same divided domain and the
            // response is re-estimated below, so the baked curve agrees with
            // the division instead of underexposing the frame.
            float adaptiveWhitePoint = 1.0f;
            if (adaptiveWhitePointEnable && enableWP && sceneWhite > 1.0f) {
                adaptiveWhitePoint = srgbDecodeExtended(sceneWhite);
                for (int c = 0; c < 3; c++) {
                    for (int i = 0; i < bins; i++) {
                        mapped[c][i] = srgbEncodeExtended((i + 0.5f) / (bins - 1.0f)
                                * extent[c] / adaptiveWhitePoint) * (bins - 1.0f);
                    }
                }
                avg = estimateAvg(result, mapped, bins, histNormR, histNormG, histNormB);
                // Re-estimate the gain on the divided domain directly: the
                // divided histogram is darker, so the AE picks the higher gain
                // that compensates the division. Holding the pass-1 gain (the
                // former avgComp multiplication) left the frame ~20% darker
                // than the same capture without reconstruction.
                mpy = clampGain((bins / 256.0f) * target / Math.max(avg, 1.0e-4f));
                sceneWhite = searchWhite(result, mapped, bins, histNormR, histNormG, histNormB, mpy);
                Log.d(Name, "Adaptive white point:" + adaptiveWhitePoint
                        + " scene white after division:" + sceneWhite);
            }
            ((PostPipeline) basePipeline).adaptiveWhitePoint = adaptiveWhitePoint;

            float normL = 0.0f;
            float normR = 0.0f;
            for (int i = 0; i < bins; i++) {
                float val = ((float) (i) / (bins - 1.0f)) * mpy;
                normL += Math.min(val, 1.0f);
                normR += (val * (1.0f + (val / (mpy * mpy)))) / (1.0f + val);
            }
            Log.d(Name, "Reinhard normalizer:" + normR + " normL:" + normL + " base Mpy:" + mpy);
            mpy *= normL / normR;

            float whiteMax = sceneWhite * mpy;
            float whiteEff = enableWP ? Math2.mix(mpy, whiteMax, whiteApply) : mpy;
            Log.d(Name, "Reinhard white max (top 0.5%): " + whiteMax + " effective:" + whiteEff);
            Log.d(Name, "Average brightness: " + avg + ", multiplier: " + mpy);

            // Adaptive highlight shoulder: measure the image fraction whose
            // response lands above 1.0 under the estimated gain/white point -
            // exactly the highlights that would hard-clip to flat white on HDR
            // scenes. The more energy sits there, the lower the knee, so the
            // top of the curve rolls off smoothly toward (never reaching) 1.0.
            float knee = 1.0f;
            if (highlightCompression) {
                float kneeLo = Math.min(kneeMin, kneeMax);
                long clipped = 0;
                long totalCnt = (long) histNormR + histNormG + histNormB;
                for (int i = 0; i < bins; i++) {
                    for (int c = 0; c < 3; c++) {
                        float x = mapped[c][i] / (bins - 1.0f);
                        float g = Math2.mix(x, (float) Math.sqrt(x), applyGammaMix);
                        float v = g * mpy;
                        float r = v * (1.0f + v / (whiteEff * whiteEff)) / (1.0f + v);
                        // Tolerance above display white: a response of 1.001
                        // still ends at ~1.0 in the baked curve, it is not
                        // lost highlight detail. Without the headroom the
                        // exactly-white pile always reads as clipped (bin
                        // quantization keeps whiteEff a fraction below mpy)
                        // and the shoulder engages on every bright scene.
                        if (r > 1.0f + clipTolerance) clipped += (long) result[c][i];
                    }
                }
                float clippedFrac = totalCnt > 0 ? clipped / (float) totalCnt : 0.0f;
                knee = Math2.mix(kneeMax, kneeLo, Math.min(clippedFrac / Math.max(kneeRef, 1.0e-4f), 1.0f));
                Log.d(Name, "Highlight shoulder: clipped:" + clippedFrac + " knee:" + knee + " tolerance:" + clipTolerance);
            }

            // Bake the per-channel exposure response into a 1D curve over the
            // display-encoded [0,1] range.
            float[] curve = new float[CURVE_SIZE];
            for (int i = 0; i < CURVE_SIZE; i++) {
                float x = i / (CURVE_SIZE - 1.0f);
                float g = Math2.mix(x, (float) Math.sqrt(x), applyGammaMix);
                float r = g * mpy;
                r = r * (1.0f + r / (whiteEff * whiteEff)) / (1.0f + r);
                float o = Math2.mix(r, r * r, applyGammaMix);
                if (knee < 1.0f) o = softShoulder(o, knee);
                curve[i] = Math.min(Math.max(o, 0.0f), 1.0f);
            }
            Log.d(Name, "Exposure curve: " + (CURVE_SIZE - 1) + " -> " + curve[CURVE_SIZE - 1]);

            ((PostPipeline) basePipeline).exposureCurve = new GLTexture(new Point(CURVE_SIZE, 1),
                    new GLFormat(GLFormat.DataType.FLOAT_16), BufferUtils.getFrom(curve),
                    GL_LINEAR, GL_CLAMP_TO_EDGE);

            WorkingTexture = previousNode.WorkingTexture;
            glProg.closed = true;
        }

        /**
         * sRGB OETF without the upper clamp: keeps growing past linear 1.0 so
         * over-range whites (white-balanced clip up to 1/whitePoint[c]) retain
         * their magnitude in the display-domain histogram math.
         */
        private static float srgbEncodeExtended(float v) {
            if (v <= 0.0f) return 0.0f;
            return v <= 0.0031308f ? v * 12.92f : 1.055f * (float) Math.pow(v, 1.0f / 2.4f) - 0.055f;
        }

        /** Inverse of {@link #srgbEncodeExtended}: display encoded -> linear, valid above 1.0. */
        private static float srgbDecodeExtended(float v) {
            return v <= 0.0031308f ? v / 12.92f : (float) Math.pow((v + 0.055f) / 1.055f, 2.4f);
        }

        /** Display-domain average over the histogram bins (bin units), fill-coefficient weighted. */
        private float estimateAvg(int[][] result, float[][] mapped, int bins, int histNormR, int histNormG, int histNormB) {
            // Cap the average's bin values at display white: the response curve
            // operates on display values clamped to [0,1] (initial.glsl clamps
            // before the curve), so no pixel can render brighter than white.
            // Counting over-range reconstructed highlights at their inflated
            // magnitude would deflate the gain estimate and underexpose the
            // frame. The white point search keeps the unclamped mapping.
            float cap = bins - 1.0f;
            float sum = 0.0f;
            int cnt = 0;
            for (int i = 0; i < bins - 1; i++) {
                if (cnt > (histNormR + histNormG + histNormB) * fillCoefficient) {
                    Log.d(Name, "Histogram already full, coefficient:" + fillCoefficient);
                    break;
                }
                sum += result[0][i] * Math.min(mapped[0][i], cap)
                        + result[1][i] * Math.min(mapped[1][i], cap)
                        + result[2][i] * Math.min(mapped[2][i], cap);
                cnt += result[0][i] + result[1][i] + result[2][i];
            }
            return cnt > 0 ? sum / cnt : (bins / 256.0f) * target;
        }

        /**
         * Scene white estimate: the per-channel top-0.5% means, taking the
         * MINIMUM - the highest display level that all three channels reach
         * with significant mass (the channel "crossing"). A reconstructed
         * neutral white has all channels present up to that level; above it
         * only the dominant channel extends, so anchoring there (per-channel
         * maximum, or any fixed channel like green) would let one channel
         * define display white and push the others down - tinted highlights.
         * The crossing anchor is white-balance agnostic: whichever channel
         * tops out lowest defines the common white and everything above it
         * folds to display white together.
         */
        private float searchWhite(int[][] result, float[][] mapped, int bins,
                                  int histNormR, int histNormG, int histNormB, float mpy) {
            float white = Float.MAX_VALUE;
            for (int c = 0; c < 3; c++) {
                int histNorm = c == 0 ? histNormR : (c == 1 ? histNormG : histNormB);
                float sum = 0.0f;
                int cnt = 0;
                for (int i = bins - 1; i > Math.max(bins * 2.0 / 3.0, bins / (mpy + 0.001)); i--) {
                    sum += result[c][i] * mapped[c][i];
                    cnt += result[c][i];
                    if (cnt > histNorm * 0.005f) break;
                }
                if (cnt == 0) {
                    sum = bins - 1;
                    cnt = 1;
                }
                white = Math.min(white, (sum / cnt) / bins);
            }
            return white;
        }

        /** Applies the noise and max gain clamps to the estimated multiplier. */
        private float clampGain(float mpy) {
            float gainNoiseMax = (float) (noiseMax / Math.sqrt(basePipeline.noiseS * 0.5 + basePipeline.noiseO));
            gainNoiseMax = Math.max(gainNoiseMax, 1.0f);
            if (mpy > gainNoiseMax) {
                Log.d(Name, "Clamping gain by noise from " + mpy + " to " + gainNoiseMax);
                mpy = gainNoiseMax;
            }
            if (mpy > gainMax) {
                Log.d(Name, "Clamping gain by max from " + mpy + " to " + gainMax);
                mpy = gainMax;
            }
            return mpy;
        }

        /**
         * Highlight shoulder, editor-style: identity below the knee, then a
         * smooth power sag (exponent 2) that pulls the upper part of the
         * curve down while still reaching exactly 1.0 at the domain top -
         * the same behaviour as a highlights slider in photo editors. The
         * old rational rolloff targeted an asymptote at infinity, which the
         * clamped [0,1] input domain never reaches, so display white was
         * crushed to (1+knee)/2 (0.78 at the default knee minimum).
         */
        private static float softShoulder(float x, float knee) {
            if (x <= knee) return x;
            float t = x - knee;
            float s = 1.0f - knee;
            float g = t / s;
            return knee + s * g * g;
        }
    }
