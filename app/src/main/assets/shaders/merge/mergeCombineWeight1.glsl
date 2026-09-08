#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
//uniform highp usampler2D alterTexture;
uniform highp usampler2D inTex;
uniform highp sampler2D kernelsMap;
uniform highp sampler2D inTexture;
layout(rgba16f, binding = 1) uniform highp readonly image2D diffTexture;
layout(rgba16f, binding = 2) uniform highp writeonly image2D outTexture;
#define NIGHT_WEIGHTED 0
#if NIGHT_WEIGHTED
// GLSL ES 3.10 permits read/write float images only with r32f.
layout(r32f, binding = 3) uniform highp image2D temporalWeights;
uniform int firstWeightedMerge;
uniform highp sampler2D sourceConfidence;
uniform float referenceNoiseS;
uniform float referenceNoiseO;
uniform float candidateNoiseS;
uniform float candidateNoiseO;
uniform float nightGateScale;
layout(r32f, binding = 0) uniform highp image2D posteriorVariance;
#endif
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

uint getBayer(ivec2 coords, highp usampler2D tex){
    return texelFetch(tex,coords,0).r;
}

vec4 getBayerVec(ivec2 coords, highp usampler2D tex){
    vec4 c0 = vec4(getBayer(coords,tex),getBayer(coords+ivec2(1,0),tex),getBayer(coords+ivec2(0,1),tex),getBayer(coords+ivec2(1,1),tex));
    return clamp((c0 - blackLevel)/(vec4(float(whitelevel))-blackLevel), 0.0, 1.0);
}

vec4 robustWeight(vec4 w){
    return vec4(min(w.r, min(w.g, min(w.b, w.a))));
}

#define EPS 1e-6
#define EPS2 1e-5
#define NOISE_EPS 1e-10
#if NIGHT_WEIGHTED
float quadMaximum(vec4 value) { return max(max(value.r, value.g), max(value.b, value.a)); }
float quadVariance(vec4 signalValue, float shot, float readout) {
    return max(quadMaximum(max(signalValue, vec4(0.0)) * shot + readout), NOISE_EPS);
}
float referenceDefectConfidence(ivec2 position, vec4 center) {
    ivec2 limit = textureSize(inTexture, 0) - 1;
    vec4 lowValue = vec4(1.0), highValue = vec4(0.0), meanValue = vec4(0.0);
    for (int direction = 0; direction < 4; direction++) {
        ivec2 delta = direction == 0 ? ivec2(-1, 0) : direction == 1 ? ivec2(1, 0)
                : direction == 2 ? ivec2(0, -1) : ivec2(0, 1);
        vec4 value = texelFetch(inTexture, clamp(position + delta, ivec2(0), limit), 0);
        lowValue = min(lowValue, value); highValue = max(highValue, value); meanValue += value * .25;
    }
    vec4 sigma = sqrt(max(meanValue * referenceNoiseS + referenceNoiseO, vec4(NOISE_EPS)));
    vec4 excursion = max(max(center - highValue, lowValue - center), vec4(0.0));
    return 1.0 - quadMaximum(smoothstep(8.0 * sigma + .002, 12.0 * sigma + .004, excursion));
}
// Compare small same-channel patches at four alternate whole-quad offsets.
// No resampling is performed here: this is match confidence, not a second warp.
float patchCost(ivec2 position, ivec2 delta) {
    ivec2 limit = imageSize(outTexture) - 1;
    float cost = 0.0;
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        ivec2 q = position + ivec2(x, y);
        vec4 residual = texelFetch(inTexture, clamp(q, ivec2(0), limit), 0)
                - imageLoad(diffTexture, clamp(q + delta, ivec2(0), limit));
        cost += dot(residual * residual, vec4(.25));
    }
    return cost / 9.0;
}
// Local contrast above the noise floor is a conservative blur proxy.
vec2 detailEnergy(ivec2 position, vec4 centerBase, vec4 centerCandidate) {
    vec2 energy = vec2(0.0);
    ivec2 limit = imageSize(outTexture) - 1;
    for (int direction = 0; direction < 4; direction++) {
        ivec2 delta = direction == 0 ? ivec2(-1, 0) : direction == 1 ? ivec2(1, 0)
                : direction == 2 ? ivec2(0, -1) : ivec2(0, 1);
        ivec2 q = clamp(position + delta, ivec2(0), limit);
        vec4 rb = centerBase - texelFetch(inTexture, q, 0);
        vec4 rc = centerCandidate - imageLoad(diffTexture, q);
        energy += vec2(dot(rb * rb, vec4(.25)), dot(rc * rc, vec4(.25)));
    }
    return energy * .25;
}
#endif
void main() {
    ivec2 xy = ivec2(gl_GlobalInvocationID.xy);
    if (any(greaterThanEqual(xy, imageSize(outTexture)))) return;
    vec4 kernelParams = texture(kernelsMap, vec2(xy) / vec2(2.0 * vec2(textureSize(kernelsMap, 0)))).rgba;
    float s1 = max(kernelParams.x, EPS);
    float s2 = max(kernelParams.y, EPS);
    float rho = clamp(kernelParams.z, -1.0 + EPS, 1.0 - EPS);
    float det = max(1.0 - rho * rho, EPS);
    float a = 1.0 / (s1 * s1 * det);   // dy² (j) coefficient
    float b = -rho / (s1 * s2 * det);  // dy*dx (i*j) coefficient
    float c = 1.0 / (s2 * s2 * det);   // dx² (i) coefficient
    vec4 base = texelFetch(inTexture, xy, 0);
    ivec2 flow = ivec2(0);
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
            vec4 neighborBayer = texelFetch(inTexture, clamp(xy + offset, ivec2(0), textureSize(inTexture, 0) - 1), 0);
            //exposure1 += neighborDiff;
            exposure2 += neighborBayer;
        }
    }
    //exposure1 /= 121.0;
    exposure2 /= 121.0;
    vec4 meanMain = exposure2;
    for(float i = -5.0; i <= 5.0; i+=1.0) {
        float qi = c * i * i;
        for(float j = -5.0; j <= 5.0; j+=1.0) {
            ivec2 offset = ivec2(i, j);
            // Local-translation assumption: the block selected at the center
            // applies to the whole combine window.
            vec4 neighborDiff = imageLoad(diffTexture, clamp(xy + offset + flow, ivec2(0), imageSize(diffTexture) - 1));
            vec4 neighborBayer = texelFetch(inTexture, clamp(xy + offset, ivec2(0), textureSize(inTexture, 0) - 1), 0);
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
    localDiff /= Z;
    vec4 localSigned = abs(localDiffSigned) / Z;
    //float br = dot(base, vec4(0.25));
    // Residual noise from the noise model.
#if NIGHT_WEIGHTED
    float referenceVariance = quadVariance(meanMain, referenceNoiseS, referenceNoiseO);
    float previousWeight = firstWeightedMerge != 0 ? 1.0 / referenceVariance
            : max(imageLoad(temporalWeights, xy).r, 0.0);
    float candidateVariance = quadVariance(meanMain, candidateNoiseS, candidateNoiseO);
    float baseVariance = firstWeightedMerge != 0 || previousWeight <= 0.0
            ? referenceVariance : max(imageLoad(posteriorVariance, xy).r, NOISE_EPS);
    float residualVariance = max(baseVariance + candidateVariance, NOISE_EPS);
    vec4 sigmaR = vec4(sqrt(residualVariance * nightGateScale));
#else
    vec4 sigmaR = sqrt(max(meanMain * noiseS + noiseO, NOISE_EPS));
#endif
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
#if !NIGHT_WEIGHTED
    if(any(greaterThan(diff, vec4(exposure*0.80))) && exposure < 0.95) {
        comb = vec4(0.0); // skip overexposed pixels
    }
#endif
#if NIGHT_WEIGHTED
    vec3 sourceQuality = clamp(texelFetch(sourceConfidence, xy, 0).rgb, 0.0, 1.0);
    float centerCost = patchCost(xy, ivec2(0));
    float alternativeCost = min(min(patchCost(xy, ivec2(-1, 0)), patchCost(xy, ivec2(1, 0))),
                                min(patchCost(xy, ivec2(0, -1)), patchCost(xy, ivec2(0, 1))));
    vec2 detail = detailEnergy(xy, base, diff);
    vec2 signalDetail = max(detail - 2.0 * vec2(baseVariance, candidateVariance), vec2(0.0));
    float textureEvidence = smoothstep(4.0 * residualVariance, 16.0 * residualVariance, signalDetail.x);
    // Flat/noisy regions have no unique match, but can still contribute useful photons.
    float uniqueness = mix(1.0, smoothstep(-residualVariance, 3.0 * residualVariance,
            alternativeCost - centerCost), textureEvidence);
    float sharpness = mix(1.0, smoothstep(.2, .8,
            signalDetail.y / max(signalDetail.x, NOISE_EPS)), textureEvidence);
    // A center change should not disappear in the 11x11 spatial average.
    float centerDisagreement = quadMaximum((diff - base) * (diff - base));
    float changeConfidence = 1.0 - smoothstep(9.0 * residualVariance,
            36.0 * residualVariance, centerDisagreement);
    float headroom = min(sourceQuality.x, 1.0 - smoothstep(.90, .995,
            quadMaximum(diff) / max(exposure, EPS)));
    if (firstWeightedMerge != 0) {
        float referenceHeadroom = 1.0 - smoothstep(.90, .995, quadMaximum(base));
        previousWeight *= referenceHeadroom * referenceDefectConfidence(xy, base);
    }
    // A masked reference must not veto its own replacement by a valid observation.
    if (previousWeight <= 0.0) {
        comb = vec4(1.0); uniqueness = 1.0; sharpness = 1.0; changeConfidence = 1.0;
    }
    float confidence = clamp(comb.r * headroom * sourceQuality.y * sourceQuality.z
            * uniqueness * sharpness * changeConfidence, 0.0, 1.0);
    // Physical inverse variance in reference-exposure units. Long frames can
    // outweigh short frames, but only where all spatial confidence tests agree.
    float acceptedWeight = confidence / candidateVariance;
    float totalWeight = previousWeight + acceptedWeight;
    float fraction = totalWeight > 0.0 ? acceptedWeight / totalWeight : 0.0;
    imageStore(outTexture, xy, mix(base, diff, fraction));
    imageStore(temporalWeights, xy, vec4(totalWeight));
    float updatedVariance = (1.0 - fraction) * (1.0 - fraction) * baseVariance
            + fraction * fraction * candidateVariance;
    imageStore(posteriorVariance, xy, vec4(updatedVariance));
#else
    imageStore(outTexture, xy, mix(base, diff, weight * comb));
#endif
    //imageStore(outTexture, xy, localDiff2/Z); // blur test(check kernels)
}
