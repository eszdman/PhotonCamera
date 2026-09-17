#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
//uniform highp usampler2D alterTexture;
// Normalized fp16 raw input (Allocator.createF16 pre-normalizes on the CPU).
uniform highp sampler2D inTex;
uniform highp sampler2D kernelsMap;
layout(rgba16f, binding = 0) uniform highp readonly image2D inTexture;
layout(rgba16f, binding = 1) uniform highp readonly image2D diffTexture;
layout(rgba16f, binding = 2) uniform highp writeonly image2D outTexture;
#define TILE 2
#define CONCAT 1
uniform float weight;
uniform float weight2;
uniform float exposure;
uniform float noiseS;
uniform float noiseO;
uniform vec4 analogBalance;
uniform int cfaPattern;
// Optical flow refinement: per-pixel correction of the coarse alignment.
// The diff texture packs whole 2x2 Bayer quads per texel, so fractional
// resampling (bilinear) is illegal here - it would blend different color
// channels across quads. The refinement therefore only selects whole
// texel-block offsets via imageLoad.
uniform int enableFlow;
uniform float flowMaxDisp;
// Pre-inflation noise model (noiseS/noiseO are merge-strength inflated and
// would scale the significance gate with user settings).
uniform float flowNoiseS;
uniform float flowNoiseO;

float getBayer(ivec2 coords, highp sampler2D tex){
    return texelFetch(tex,coords,0).r;
}

vec4 getBayerVec(ivec2 coords, highp sampler2D tex){
    vec4 c0 = vec4(getBayer(coords,tex),getBayer(coords+ivec2(1,0),tex),getBayer(coords+ivec2(0,1),tex),getBayer(coords+ivec2(1,1),tex));
    return clamp(c0, 0.0, 1.0);
}

vec4 robustWeight(vec4 w){
    return vec4(min(w.r, min(w.g, min(w.b, w.a))));
}

// Optical flow refinement window / iteration count / gates.
#define FLOW_R 2          // 5x5 estimation window
#define FLOW_W (2 * FLOW_R + 1) // estimation taps per axis
#define FLOW_S (2 * FLOW_R + 3) // stencil span incl. the gradient halo
#define FLOW_ITERS 2      // discrete Gauss-Newton iterations
#define FLOW_DET_EPS 1e-8 // numerical floor: reject only truly textureless windows
#define FLOW_ACCEPT 0.95  // selected block's SAD must drop below this fraction of the zero-offset SAD

// Per-pixel Lucas-Kanade refinement of the coarse (tile / FlowNet) alignment.
// Template = base (accumulated reference), image = diff (coarsely warped alter).
// The solved flow is rounded to a whole texel-block offset (packed Bayer quads
// cannot be sampled fractionally) and that block is kept only when direct
// block matching beats the zero-offset candidate, so occlusions, flat areas
// and noise fall back to the coarse warp instead of drifting.
ivec2 refineFlow(ivec2 xy) {
    const float n = float((2 * FLOW_R + 1) * (2 * FLOW_R + 1));
    vec2 flow = vec2(0.0);
    float sad0 = 0.0;
    for (int it = 0; it < FLOW_ITERS; it++) {
        float h00 = 0.0, h01 = 0.0, h11 = 0.0;
        vec2 rhs = vec2(0.0);
        float sad = 0.0;
        ivec2 block = ivec2(round(flow)); // integer sampling keeps quads intact
        for (int j = -FLOW_R; j <= FLOW_R; j++) {
            for (int i = -FLOW_R; i <= FLOW_R; i++) {
                ivec2 o = ivec2(i, j);
                vec4 b = imageLoad(inTexture, xy + o);
                vec4 gx = (imageLoad(inTexture, xy + o + ivec2(1, 0))
                - imageLoad(inTexture, xy + o - ivec2(1, 0))) * 0.5;
                vec4 gy = (imageLoad(inTexture, xy + o + ivec2(0, 1))
                - imageLoad(inTexture, xy + o - ivec2(0, 1))) * 0.5;
                vec4 e = b - imageLoad(diffTexture, xy + o + block);
                sad += dot(abs(e), vec4(0.25));
                h00 += dot(gx, gx);
                h01 += dot(gx, gy);
                h11 += dot(gy, gy);
                rhs += vec2(dot(gx, e), dot(gy, e));
            }
        }
        if (it == 0) sad0 = sad;
        float det = h00 * h11 - h01 * h01;
        if (det < FLOW_DET_EPS * n * n) return ivec2(0); // nothing to solve on
        mat2 hinv = mat2(h11, -h01, -h01, h00) / det;
        vec2 stp = hinv * rhs;
        float len = length(stp);
        if (len > 1.0) stp /= len; // stay inside the linearization radius
        flow += stp;
    }
    int m = max(1, int(flowMaxDisp + 0.5));
    ivec2 block = clamp(ivec2(round(flow)), ivec2(-m), ivec2(m));
    if (block == ivec2(0)) return ivec2(0);
    // Block-match validation: the selected block must match the base window
    // better than the unrefined position both relatively and statistically.
    // On pure noise a shifted block can luck into a few percent lower SAD, so
    // the gain must also exceed k standard deviations of the window SAD
    // (SAD = 0.25*sum|e| over 4*(2R+1)^2 ~half-normal samples -> std ~
    // 0.151*sqrt(sum sigma^2); *1.25 folds in the accumulated base's noise).
    float sadB = 0.0;
    float sig2 = 0.0;
    for (int j = -FLOW_R; j <= FLOW_R; j++) {
        for (int i = -FLOW_R; i <= FLOW_R; i++) {
            ivec2 o = ivec2(i, j);
            vec4 b = imageLoad(inTexture, xy + o);
            vec4 e = b - imageLoad(diffTexture, xy + o + block);
            sadB += dot(abs(e), vec4(0.25));
            sig2 += dot(b * flowNoiseS + vec4(flowNoiseO), vec4(1.0));
        }
    }
    if (sadB > sad0 * FLOW_ACCEPT) return ivec2(0);
    if (sad0 - sadB < 2.0 * 0.19 * sqrt(sig2)) return ivec2(0);
    return block;
}

#define EPS 1e-6
#define EPS2 1e-5
void main() {
    ivec2 xy = ivec2(gl_GlobalInvocationID.xy);
    vec4 kernelParams = texture(kernelsMap, vec2(xy) / vec2(2.0 * vec2(textureSize(kernelsMap, 0)))).rgba;
    float s1 = max(kernelParams.x, EPS);
    float s2 = max(kernelParams.y, EPS);
    float rho = clamp(kernelParams.z, -1.0 + EPS, 1.0 - EPS);
    float det = max(1.0 - rho * rho, EPS);
    float a = 1.0 / (s1 * s1 * det);   // dy² (j) coefficient
    float b = -rho / (s1 * s2 * det);  // dy*dx (i*j) coefficient
    float c = 1.0 / (s2 * s2 * det);   // dx² (i) coefficient
    vec4 base = imageLoad(inTexture, xy);
    ivec2 flow = (enableFlow == 1) ? refineFlow(xy) : ivec2(0);
    //ivec2 flow = ivec2(0);
    vec4 diff = imageLoad(diffTexture, xy + flow);
    //vec4 bayer = getBayerVec(xy*2, inTex);
    float Z = 0.0001;
    float Z2 = 0.0;
    vec4 localDiff = vec4(0.0001);
    vec4 localDiffSigned = vec4(0.0);
    vec4 localDiff2 = vec4(0.0);
    //vec4 exposure1 = vec4(0.0);
    vec4 exposure2 = vec4(0.0);
    for(int i = -5; i <= 5; i++) {
        for(int j = -5; j <= 5; j++) {
            ivec2 offset = ivec2(i, j);
            ///vec4 neighborDiff = imageLoad(diffTexture, xy + offset);
            //vec4 neighborBayer = getBayerVec((xy + offset) * 2, inTex);
            vec4 neighborBayer = imageLoad(inTexture, xy + offset);
            //exposure1 += neighborDiff;
            exposure2 += neighborBayer;
        }
    }
    //exposure1 /= 121.0;
    exposure2 /= 121.0;
    vec4 meanMain = exposure2;
    vec4 variance = vec4(0.0001);
    for(float i = -5.0; i <= 5.0; i+=1.0) {
        float qi = c * i * i;
        for(float j = -5.0; j <= 5.0; j+=1.0) {
            ivec2 offset = ivec2(i, j);
            // Local-translation assumption: the block selected at the center
            // applies to the whole combine window.
            vec4 neighborDiff = imageLoad(diffTexture, xy + offset + flow);
            //vec4 neighborBayer = getBayerVec((xy + offset) * 2, inTex);
            vec4 neighborBayer = imageLoad(inTexture, xy + offset);
            //variance = max(((neighborBayer-meanMain)*(neighborBayer-meanMain)), variance);

            variance += ((neighborBayer-meanMain)*(neighborBayer-meanMain));
            //if(any(greaterThan(neighborDiff, vec4(exposure*0.99)))) {
            //    continue; // skip overexposed pixels
            //}
            float q = qi + 2.0 * b * i * j + a * j * j;
            float w = exp(-1.0 * q);
            vec4 r = neighborDiff - neighborBayer;
            localDiff += abs(r) * w;
            localDiffSigned += r * w;
            //localDiff2 += neighborBayer * w;
            Z += w;
            Z2 += w * w;
        }
    }
    variance /= 120.0;
    localDiff /= Z;
    vec4 localSigned = abs(localDiffSigned) / Z;
    //float br = dot(base, vec4(0.25));
    // Residual noise: alter noise from the model (reined in by the measured
    // accumulated-base std, as before) plus the accumulated base's own std
    // estimated from the window variance.
    vec4 sigmaA = sqrt(max(meanMain * noiseS + noiseO, EPS));
    vec4 sigmaB = min(sqrt(variance), sigmaA); // averaging can only reduce noise
    sigmaA = min(sigmaA, sqrt(variance) * 2.0);
    vec4 sigmaR = sqrt(sigmaA * sigmaA + sigmaB * sigmaB);
    // Expected noise floors (E|X| = sqrt(2/pi)*sigma); the signed kernel mean's
    // floor shrinks by sqrt(Z2)/Z, the inverse effective tap count.
    vec4 absFloor = 0.7979 * sigmaR;
    vec4 signedFloor = absFloor * sqrt(Z2) / Z;
    // Excess disagreement above the noise floor; max() of both statistics so
    // cancelling residuals around edges still raise the excess (anti-ghost).
    vec4 excess = max(max(localDiff - absFloor, localSigned - signedFloor), vec4(0.0));
    vec4 N = sigmaR;
    vec4 comb = (N * N) / (excess * excess + N * N);
    //vec4 comb = exp(-0.5 * (localDiff * localDiff) / (N * N));
    // One weight per Bayer quad: per-subpixel weights blend the color filters
    // by different amounts when their excess diverges in motion, shifting chroma.
    comb = robustWeight(comb);
    if(any(greaterThan(diff, vec4(exposure*0.80))) && exposure < 0.95) {
        comb = vec4(0.0); // skip overexposed pixels
    }
    imageStore(outTexture, xy, mix(base, diff, weight * comb));
    //imageStore(outTexture, xy, localDiff2/Z); // blur test(check kernels)
}
