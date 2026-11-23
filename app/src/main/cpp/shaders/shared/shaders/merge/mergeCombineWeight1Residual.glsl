//
// Created by eszdman on 20.11.2026.
//

#ifndef ESD4D_BUFFER_BACKEND
#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
//uniform highp usampler2D alterTexture;
uniform highp usampler2D inTex;
uniform highp sampler2D kernelsMap;
layout(rgba16f, binding = 0) uniform highp readonly image2D inTexture;
layout(rgba16f, binding = 1) uniform highp readonly image2D diffTexture;
layout(rgba16f, binding = 2) uniform highp writeonly image2D outTexture;
// Each invocation owns one quad's state; no neighboring state is read.
layout(r32f, binding = 3) uniform highp image2D supportTexture;
uniform bool supportInitialized;
uniform ivec2 rawHalf;
uniform ivec2 cfaShift;
#define TILE 2
#define CONCAT 1
uniform float weight;
uniform float weight2;
uniform float exposure;
uniform float noiseS;
uniform float noiseO;
uniform uint whitelevel;
uniform vec4 blackLevel;
uniform vec4 analogBalance;
uniform int cfaPattern;
// Optical flow refinement: per-pixel correction of the coarse alignment.
// This optional refinement selects whole packed-quad offsets. Component-wise
// interpolation of packed RGBA would preserve CFA colors, but would add a
// second resampling after mergeAlign and soften its subpixel reconstruction.
uniform int enableFlow;
uniform float flowMaxDisp;
// Pre-inflation noise model (noiseS/noiseO are merge-strength inflated and
// would scale the significance gate with user settings).
uniform float flowNoiseS;
uniform float flowNoiseO;

uint getBayer(ivec2 coords, highp usampler2D tex){
    return texelFetch(tex,coords,0).r;
}

vec4 getBayerVec(ivec2 coords, highp usampler2D tex){
    vec4 c0 = vec4(getBayer(coords,tex),getBayer(coords+ivec2(1,0),tex),getBayer(coords+ivec2(0,1),tex),getBayer(coords+ivec2(1,1),tex));
    return clamp((c0 - blackLevel)/(vec4(float(whitelevel))-blackLevel), 0.0, 1.0);
}

#endif

vec4 validCfaSites(ivec2 p) {
    vec2 raw = vec2(2*p-cfaShift);
    vec4 x = raw.x+vec4(0,1,0,1), y = raw.y+vec4(0,0,1,1);
    return step(vec4(0.0),x)*step(x,vec4(2*rawHalf.x-1))
         * step(vec4(0.0),y)*step(y,vec4(2*rawHalf.y-1));
}

vec4 robustWeight(vec4 w){
    return vec4(min(w.r, min(w.g, min(w.b, w.a))));
}
vec4 residualRobustWeight(vec4 penalty, float scale){
    // min(scale / penalty) = scale / max(penalty), for positive penalties.
    return vec4(scale / max(penalty.r, max(penalty.g, max(penalty.b, penalty.a))));
}

#define EPS 1e-6
#define EPS2 1e-5
#define NOISE_EPS 1e-10
#define E_ABS   0.7978845608 // sqrt(2/pi): E|X| for X ~ N(0, sigma)
#define SIG_ABS 0.6027980    // sqrt(1 - 2/pi): std|X|
#define SQRT2   1.41421356   // Var(r^2) = 2*sigma^4 for Gaussian r
#define GATE_SENSITIVITY 2.5 // reject at this many statistic-sigmas (higher = merge more)
// Called with a constant mask flag so the interior variant can eliminate
// per-tap validity, masked weight sums, and exposure-count accumulation.
void combine(bool masked) {
    ivec2 xy = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(outTexture);
    if (any(greaterThanEqual(xy,size))) return;
    vec4 kernelParams = texture(kernelsMap, vec2(xy) / vec2(2.0 * vec2(textureSize(kernelsMap, 0)))).rgba;
    float s1 = max(kernelParams.x, EPS);
    float s2 = max(kernelParams.y, EPS);
    float rho = clamp(kernelParams.z, -1.0 + EPS, 1.0 - EPS);
    float det = max(1.0 - rho * rho, EPS);
    float a = 1.0 / (s1 * s1 * det);   // dy² (j) coefficient
    float b = -rho / (s1 * s2 * det);  // dy*dx (i*j) coefficient
    float c = 1.0 / (s2 * s2 * det);   // dx² (i) coefficient
    vec4 base = imageLoad(inTexture, xy);
    ivec2 flow = ivec2(0);
    //ivec2 flow = ivec2(0);
    vec4 diff = imageLoad(diffTexture, xy + flow);
    //vec4 bayer = getBayerVec(xy*2, inTex);
    vec4 Z = vec4(0.001);
    vec4 Z2 = vec4(0.001);
    vec4 localDiff = vec4(0.0);
    vec4 localDiffSigned = vec4(0.0);
    vec4 localEnergy = vec4(0.0);
    vec4 localDiff2 = vec4(0.001 * diff);
    //vec4 exposure1 = vec4(0.0);
    vec4 exposure2 = vec4(0.0);
    vec4 exposureZ = vec4(0.0);
    // Accumulate the unweighted noise-model mean from the same base taps as
    // the robustness statistics. This removes a second 11x11 image scan while
    // keeping the original i-major/j-minor accumulation order.
    for(float i = -5.0; i <= 5.0; i+=1.0) {
        float qi = c * i * i;
        for(float j = -5.0; j <= 5.0; j+=1.0) {
            ivec2 offset = ivec2(i, j);
            vec4 valid = vec4(1.0);
            if (masked) {
                ivec2 at = xy+offset;
                if (any(lessThan(at,ivec2(0))) || any(greaterThanEqual(at,size))) continue;
                valid = validCfaSites(at);
            }
            // Local-translation assumption: the block selected at the center
            // applies to the whole combine window.
            vec4 neighborDiff = imageLoad(diffTexture, xy + offset + flow);
            vec4 neighborBayer = imageLoad(inTexture, xy + offset);
            exposure2 += neighborBayer*valid;
            exposureZ += valid;
            //if(any(greaterThan(neighborDiff, vec4(exposure*0.99)))) {
            //    continue; // skip overexposed pixels
            //}
            float q = qi + 2.0 * b * i * j + a * j * j;
            float w = exp(-1.0 * q);
            vec4 r = neighborDiff - neighborBayer;
            r *= valid;
            vec4 rAbs = abs(r);
            // Green quincunx convolution: each tap contributes BOTH of its
            // greens to both green outputs - [1] weighted by the kernel
            // around the [1] sites (integer offsets, same as R/B), [2] by
            // the kernel around the [2] sites, which sit half a texel
            // anti-diagonal from [1] so their taps land shifted.
            float u = i - 0.5, v = j + 0.5;
            float wg = exp(-(c * u * u + 2.0 * b * u * v + a * v * v));
            u = i + 0.5; v = j - 0.5;
            float wb = exp(-(c * u * u + 2.0 * b * u * v + a * v * v));
            localDiff2 += vec4(
            neighborDiff.r * w,
            neighborDiff.g * w + neighborDiff.b * wg,
            neighborDiff.b * w + neighborDiff.g * wb,
            neighborDiff.a * w);
            localDiff += vec4(rAbs.r * w, rAbs.g * w + rAbs.b * wg, rAbs.b * w + rAbs.g * wb, rAbs.a * w);
            localDiffSigned += vec4(r.r * w, r.g * w + r.b * wg, r.b * w + r.g * wb, r.a * w);
            localEnergy += vec4(r.r * r.r * w, r.g * r.g * w + r.b * r.b * wg,
            r.b * r.b * w + r.g * r.g * wb, r.a * r.a * w);
            Z += vec4(w*valid.r, w*valid.g+wg*valid.b, w*valid.b+wb*valid.g, w*valid.a);
            Z2 += vec4(w*w*valid.r, w*w*valid.g+wg*wg*valid.b,
                       w*w*valid.b+wb*wb*valid.g, w*w*valid.a);
        }
    }
    vec4 meanMain = exposure2 / max(exposureZ,vec4(1.0));
    // Kernel-weighted statistics, normalized by the per-channel weight sum.
    // invEff = sqrt(Z2)/Z ~ 1/sqrt(effective tap count) is each statistic's
    // noise-reduction factor, so the standardized t-values below keep the
    // gate's false-reject rate identical for every KernelNet kernel size.
    vec4 localMean = localDiff / Z;              // kernel mean of |r|
    vec4 localSigned = abs(localDiffSigned) / Z; // |kernel mean of r|
    vec4 localEnergyMean = localEnergy / Z;      // kernel mean of r^2
    vec4 invEff = sqrt(Z2) / Z;
    //float br = dot(base, vec4(0.25));
    // Residual noise from the noise model. r = frame - running base, so its
    // variance carries both noises: sigma^2 * (1 + 1/n) with n = frames
    // already averaged into the base (n = 1/weight - 1 from the running
    // mix). Without this factor the floors sit below the true residual
    // variance and the 1/invEff standardization amplifies the mismatch into
    // systematic false rejection at large kernels.
    float nFrames = max(1.0 / weight - 1.0, 1.0);
    // Support is only needed after the window statistics.
    float accepted = supportInitialized ? max(imageLoad(supportTexture,xy).r,1.0) : 1.0;
    // Rejected frames did not reduce the base noise. The accepted mass is a
    // conservative effective-count approximation for weights in [0,1],
    // unlike the scheduled frame index. Residual-fusion correlations mean
    // it is not an exact propagated noise covariance.
    nFrames = accepted;
    vec4 sigmaR = sqrt(max(meanMain * noiseS + noiseO, NOISE_EPS) * (1.0 + 1.0 / nFrames));
    // Standardized detection statistics (mean 0, std 1 under pure noise).
    // tAbs/tSigned detect window-uniform offsets with the full 1/invEff
    // leverage of the extra samples; tEnergy detects spatially CONCENTRATED
    // residuals (thin texture under slight misregistration) that a plain
    // mean dilutes away (f*A) as the kernel grows while energy accumulates
    // (f*A^2). max() of all three: detection can only improve with size.
    vec4 tAbs = (localMean - E_ABS * sigmaR) / (SIG_ABS * sigmaR * invEff);
    vec4 tSigned = (localSigned - E_ABS * sigmaR * invEff) / (SIG_ABS * sigmaR * invEff);
    vec4 tEnergy = (localEnergyMean - sigmaR * sigmaR) / (SQRT2 * sigmaR * sigmaR * invEff);
    vec4 t = max(tAbs, max(tSigned, tEnergy));
    // Less residual than predicted noise is evidence for acceptance, not
    // an outlier of the opposite sign. Only excess disagreement is rejected.
    t = max(t,vec4(0.0));
    // The shared minimum weight comes from the largest positive denominator.
    // Reduce first, then divide once, keeping each squared statistic intact.
    vec4 penalty = t * t + GATE_SENSITIVITY * GATE_SENSITIVITY;
    vec4 comb = residualRobustWeight(penalty, GATE_SENSITIVITY * GATE_SENSITIVITY);
    //vec4 comb = exp(-0.5 * (localDiff * localDiff) / (N * N));
    // CFA statistics use one shared weight per quad.
    if(any(greaterThan(diff, vec4(exposure*0.80))) && exposure < 0.95) {
        comb = vec4(0.0); // skip overexposed pixels
    }
    // Kernels gate the merge statistics ONLY - the output must point-sample
    // diff at the center tap. Convolving the output with the kernel
    // (localDiff2/Z) low-passes texture in direct proportion to the kernel
    // size, which is blur by construction, not a robustness gain.
    float updated = accepted+comb.r;
    imageStore(supportTexture,xy,vec4(updated));
    imageStore(outTexture,xy,mix(base,diff,comb/updated));
    //imageStore(outTexture, xy, localDiff2 / Z); // blur test(check kernels)
}

void main() {
    ivec2 xy = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(outTexture);
    if (any(greaterThanEqual(xy,size))) return;
    bool fullWindow = all(greaterThanEqual(xy-ivec2(5),cfaShift))
                   && all(lessThan(xy+ivec2(5),rawHalf));
    // Select once for the whole window. Both paths keep the same tap order,
    // green phases, rejection statistics, and accepted-frame update.
    if (fullWindow) combine(false);
    else combine(true);
}
