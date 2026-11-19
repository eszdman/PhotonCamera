#define LAYOUT //
LAYOUT
precision highp float;
precision highp int;
precision highp sampler2D;
precision highp image2D;
// Normalized fp16 raw input (Allocator.createF16 pre-normalizes on the CPU).
uniform highp sampler2D inTexture;
uniform highp sampler2D alignmentTexture;
//layout(r16ui, binding = 0) uniform highp readonly uimage2D inTexture;
layout(rgba16f, binding = 0) uniform highp readonly image2D avrTexture;
layout(rgba8, binding = 1) uniform highp readonly image2D hotPixTexture;
layout(rgba16f, binding = 2) uniform highp readonly image2D baseTexture;
layout(rgba16f, binding = 3) uniform highp writeonly image2D outTexture;
layout(rgba16f, binding = 4) uniform highp readonly image2D alterTexture;

uniform float minLevel;
uniform float exposure;
uniform float exposureLow;
uniform bool createDiff;
uniform float noiseS;
uniform float noiseO;
uniform ivec2 border;
uniform ivec2 shift;
uniform ivec2 alignmentSize;
uniform ivec2 rawHalf;
uniform vec4 analogBalance;
uniform ivec2 cfaShift; // sensor red-site offset (cfa%2, cfa/2), 0..1 per axis
#define TILE 2
#define CONCAT 1
#define M_PI 3.1415926535897932384626433832795
#define TILE_AL 16
#define GYRO_WARP 0
#define HALIDE_SAMPLING 0 // 0 legacy floor, 1 nearest, 2 CFA bilinear, 3 bounded CFA cubic
#define HALIDE_MOTION_FIELD 0
#define HALIDE_PIXEL_FALLBACK 1
#define CFA_RESIDUAL_FUSION 0
#define CFA_GREEN_QUINCUNX 0
uniform float cfaResidualStrength;
uniform float cfaNoiseS;
uniform float cfaNoiseO;

#if !GYRO_WARP && HALIDE_SAMPLING > 0
// RGBA stores four separate Bayer parity planes. Filtering each component
// is CFA-safe; filtering the single-channel raw mosaic would mix colors.
vec4 cfaTexel(ivec2 p) {
    if (all(greaterThanEqual(p, cfaShift)) && all(lessThan(p, rawHalf)))
        return imageLoad(alterTexture, p);
    // merge00 duplicates raw edge pixels into its padding. Clamp each plane
    // to its own real sites so filtering cannot pick up another CFA color.
    vec4 value;
    for (int c = 0; c < 4; c++) {
        ivec2 site = ivec2(c % 2, c / 2);
        ivec2 low = (cfaShift - site + ivec2(1)) / 2;
        ivec2 high = (rawHalf * 2 - ivec2(1) + cfaShift - site) / 2;
        value[c] = imageLoad(alterTexture, clamp(p, low, high))[c];
    }
    return value;
}

vec4 cubicWeights(float f) {
    float f2 = f*f, f3 = f2*f;
    // Interpolating Catmull-Rom: exact at integer shifts, sharper than a tent.
    return vec4(-0.5*f + f2 - 0.5*f3, 1.0 - 2.5*f2 + 1.5*f3,
                 0.5*f + 2.0*f2 - 1.5*f3, -0.5*f2 + 0.5*f3);
}

// A complete footprint inside the real CFA planes needs no per-tap bounds
// checks. Explicit rows also avoid dynamic loop/channel indexing on mobile.
vec4 cubicRow(ivec2 p, vec4 w) {
    return imageLoad(alterTexture, p) * w.x
         + imageLoad(alterTexture, p + ivec2(1,0)) * w.y
         + imageLoad(alterTexture, p + ivec2(2,0)) * w.z
         + imageLoad(alterTexture, p + ivec2(3,0)) * w.w;
}

vec4 cubicInterior(ivec2 lo, vec4 wx, vec4 wy) {
    vec4 a = imageLoad(alterTexture, lo);
    vec4 b = imageLoad(alterTexture, lo + ivec2(1,0));
    vec4 c = imageLoad(alterTexture, lo + ivec2(0,1));
    vec4 d = imageLoad(alterTexture, lo + ivec2(1));
    vec4 row1 = imageLoad(alterTexture, lo + ivec2(-1,0)) * wx.x
              + a * wx.y + b * wx.z
              + imageLoad(alterTexture, lo + ivec2(2,0)) * wx.w;
    vec4 row2 = imageLoad(alterTexture, lo + ivec2(-1,1)) * wx.x
              + c * wx.y + d * wx.z
              + imageLoad(alterTexture, lo + ivec2(2,1)) * wx.w;
    vec4 value = cubicRow(lo + ivec2(-1,-1), wx) * wy.x
               + row1 * wy.y + row2 * wy.z
               + cubicRow(lo + ivec2(-1,2), wx) * wy.w;
    return clamp(value, min(min(a,b),min(c,d)), max(max(a,b),max(c,d)));
}

vec4 sampleCfa(vec2 p) {
#if HALIDE_SAMPLING == 1
    return cfaTexel(ivec2(floor(p + vec2(0.5))));
#else
    ivec2 lo = ivec2(floor(p));
    vec2 f = fract(p);
#if HALIDE_SAMPLING == 2
    return mix(mix(cfaTexel(lo), cfaTexel(lo + ivec2(1,0)), f.x),
               mix(cfaTexel(lo + ivec2(0,1)), cfaTexel(lo + ivec2(1)), f.x), f.y);
#else
    vec4 wx = cubicWeights(f.x), wy = cubicWeights(f.y);
    if (all(greaterThanEqual(lo - ivec2(1), cfaShift))
            && all(lessThan(lo + ivec2(2), rawHalf))) {
        return cubicInterior(lo, wx, wy);
    }
    // Only edge footprints need per-plane clamping of individual taps.
    vec4 value = vec4(0.0), lower = vec4(1e20), upper = vec4(-1e20);
    for (int y = 0; y < 4; y++) {
        vec4 row = vec4(0.0);
        for (int x = 0; x < 4; x++) {
            vec4 tap = cfaTexel(lo + ivec2(x-1,y-1));
            row += tap * wx[x];
            if (x >= 1 && x <= 2 && y >= 1 && y <= 2) {
                lower = min(lower, tap);
                upper = max(upper, tap);
            }
        }
        value += row * wy[y];
    }
    // Negative cubic lobes preserve detail but can overshoot a sharp edge.
    // Bound each color independently by the enclosing four same-color samples.
    return clamp(value, lower, upper);
#endif
#endif
}

#if CFA_RESIDUAL_FUSION
#if CFA_GREEN_QUINCUNX
// Packed greens form one rotated lattice, not two independent color filters.
// u=(rawX+rawY-1)/2, v=(rawX-rawY-1)/2; both coordinates are integral at green.
float greenLatticeTexel(ivec2 uv) {
    ivec2 raw = ivec2(uv.x+uv.y+1,uv.x-uv.y);
    ivec2 quad = ivec2(floor(vec2(raw)*0.5));
    return cfaTexel(quad)[(raw.x & 1) == 1 ? 1 : 2];
}

vec2 jointGreenObservation(ivec2 xy, vec2 displacement) {
    vec2 p = vec2(xy.x+xy.y,xy.x-xy.y)
           + vec2(displacement.x+displacement.y,displacement.x-displacement.y);
    ivec2 lo = ivec2(floor(p));
    vec2 f = fract(p);
    // The second green's v coordinate is one less, so two taps are shared.
    float row0 = mix(greenLatticeTexel(lo+ivec2(0,-1)),greenLatticeTexel(lo+ivec2(1,-1)),f.x);
    float row1 = mix(greenLatticeTexel(lo),greenLatticeTexel(lo+ivec2(1,0)),f.x);
    float row2 = mix(greenLatticeTexel(lo+ivec2(0,1)),greenLatticeTexel(lo+ivec2(1,1)),f.x);
    return vec2(mix(row1,row2,f.y),mix(row0,row1,f.y));
}
#endif
vec4 baseCfaTexel(ivec2 p) {
    if (all(greaterThanEqual(p, cfaShift)) && all(lessThan(p, rawHalf)))
        return imageLoad(baseTexture, p);
    vec4 value;
    for (int c = 0; c < 4; c++) {
        ivec2 site = ivec2(c % 2, c / 2);
        ivec2 low = (cfaShift - site + ivec2(1)) / 2;
        ivec2 high = (rawHalf * 2 - ivec2(1) + cfaShift - site) / 2;
        value[c] = imageLoad(baseTexture, clamp(p, low, high))[c];
    }
    return value;
}

// Local-translation approximation to a CFA forward/adjoint residual update.
// A bilinear forward warp followed by its adjoint has separable weights
// [f*(1-f), 1-2*f*(1-f), f*(1-f)]. Evaluate this normal operator directly:
// Four source taps + nine base taps, no intermediate warp or extra dispatch.
// Optional joint-green sampling adds six source taps, reusing the base taps.
// Strength below one regularizes the inverse: a full update over-amplifies
// near-Nyquist detail/noise when the bilinear observation model is imperfect.
vec4 residualCandidate(ivec2 xy, vec2 displacement, vec4 base) {
    vec2 p = vec2(xy) + displacement;
    ivec2 lo = ivec2(floor(p));
    vec2 f = fract(p);
    vec4 observation = mix(mix(cfaTexel(lo), cfaTexel(lo+ivec2(1,0)), f.x),
                           mix(cfaTexel(lo+ivec2(0,1)), cfaTexel(lo+ivec2(1)), f.x), f.y);
#if CFA_GREEN_QUINCUNX
    observation.gb = jointGreenObservation(xy,displacement);
    vec2 gf = fract(vec2(displacement.x+displacement.y,displacement.x-displacement.y));
    vec2 gs = gf*(1.0-gf), gc = 1.0-2.0*gs;
    float gCenter = gc.x*gc.y, gAxial = gs.x*gs.y;
    float gU = gs.x*gc.y, gV = gc.x*gs.y;
    vec2 greenPredicted = vec2(0.0);
#endif
    vec2 side = f*(1.0-f);
    vec3 wx = vec3(side.x, 1.0-2.0*side.x, side.x);
    vec3 wy = vec3(side.y, 1.0-2.0*side.y, side.y);
    vec4 predicted = vec4(0.0);
    vec4 sum = vec4(0.0), squareSum = vec4(0.0);
    for (int j=-1; j<=1; ++j) {
        vec4 left = baseCfaTexel(xy+ivec2(-1,j));
        vec4 center = baseCfaTexel(xy+ivec2(0,j));
        vec4 right = baseCfaTexel(xy+ivec2(1,j));
        vec4 row = left*wx.x + center*wx.y + right*wx.z;
        predicted += row*wy[j+1];
#if CFA_GREEN_QUINCUNX
        // The green-lattice A^T A stencil fits within these existing nine
        // quad reads. Its diagonal neighbors are the opposite green phase.
        if (j == -1) {
            greenPredicted += gAxial*center.gb;
            greenPredicted.x += gU*center.b+gV*right.b;
        } else if (j == 0) {
            greenPredicted += gCenter*center.gb+gAxial*(left.gb+right.gb);
            greenPredicted.x += gU*right.b+gV*center.b;
            greenPredicted.y += gU*left.g+gV*center.g;
        } else {
            greenPredicted += gAxial*center.gb;
            greenPredicted.y += gU*center.g+gV*left.g;
        }
#endif
        sum += left+center+right;
        squareSum += left*left+center*center+right*right;
    }
#if CFA_GREEN_QUINCUNX
    predicted.gb = greenPredicted;
#endif
    vec4 mean = sum/9.0;
    vec4 variance = max(squareSum/9.0-mean*mean, vec4(0.0));
    // Conservative single-frame noise floor: accepted frame count is not
    // available here and scheduled 1/n would underestimate rejected regions.
    // One shared confidence avoids independently changing Bayer color gains.
    vec4 noiseVariance = max(mean*cfaNoiseS+cfaNoiseO, vec4(0.0));
    float signal = dot(max(variance-noiseVariance,vec4(0.0)),vec4(0.25));
    float total = dot(variance,vec4(0.25));
    float confidence = signal/max(total,1e-10);
    return observation*exposure + cfaResidualStrength*confidence*(base-predicted);
}
#endif
#endif

#if GYRO_WARP
uniform mat3 gyroHomography;

// Match the GPU Bayer-plane prewarp, but sample the original only once.
// Residuals are in the prewarped raw/2 grid: source = H(2*(p+d)+site).
float warpedChannel(vec2 p, ivec2 site, int channel) {
    vec2 raw = 2.0*p - vec2(cfaShift) + vec2(site);
    vec3 projected = gyroHomography * vec3(raw, 1.0);
    vec2 packedCoord = (projected.xy/projected.z + vec2(cfaShift-site))*0.5;
    ivec2 low = (cfaShift-site+ivec2(1))/2;
    ivec2 high = (rawHalf*2-ivec2(1)+cfaShift-site)/2;
    packedCoord = clamp(packedCoord, vec2(low), vec2(high));
    ivec2 a = ivec2(floor(packedCoord));
    ivec2 b = min(a+ivec2(1), high);
    vec2 f = fract(packedCoord);
    float upper = mix(imageLoad(alterTexture,a)[channel],
                      imageLoad(alterTexture,ivec2(b.x,a.y))[channel],f.x);
    float lower = mix(imageLoad(alterTexture,ivec2(a.x,b.y))[channel],
                      imageLoad(alterTexture,b)[channel],f.x);
    return mix(upper,lower,f.y);
}

vec4 warpedBayer(vec2 p) {
    return vec4(warpedChannel(p,ivec2(0,0),0), warpedChannel(p,ivec2(1,0),1),
                warpedChannel(p,ivec2(0,1),2), warpedChannel(p,ivec2(1,1),3));
}
#endif

float getBayer(ivec2 coords, highp sampler2D tex){
    return texelFetch(tex,coords,0).r;
}

// Repack raw with the same red-site origin shift as merge00 (negative shift,
// edge-duplicated fetches), so the normalized quad layout (R, Gr, Gb, B)
// matches the packed textures.
vec4 getBayerVec(ivec2 coords, highp sampler2D tex){
    ivec2 sz = textureSize(tex, 0);
    ivec2 org = coords - cfaShift;
    vec4 c0 = vec4(getBayer(clamp(org, ivec2(0), sz - ivec2(1)),tex),
                   getBayer(clamp(org + ivec2(1,0), ivec2(0), sz - ivec2(1)),tex),
                   getBayer(clamp(org + ivec2(0,1), ivec2(0), sz - ivec2(1)),tex),
                   getBayer(clamp(org + ivec2(1,1), ivec2(0), sz - ivec2(1)),tex));
    return clamp(c0, 0.0, 1.0);
}

float window(float x){
    return 0.5f - 0.5f * cos(2.f * M_PI * ((0.5f * (x + 0.5f) / float(TILE_AL))));
}

float windowxy(ivec2 xy){
    return window(float(xy.x)) * window(float(xy.y));
}

vec4 windowxy4(ivec2 xy){
    return vec4(window(float(xy.x)) * window(float(xy.y)),
                window(float(xy.x+1)) * window(float(xy.y)),
                window(float(xy.x)) * window(float(xy.y+1)),
                window(float(xy.x+1)) * window(float(xy.y+1)));
}

vec2 vec4ToAlignment(vec4 alignment) {
    // Round the integer part: rgba16f precision reconstructs floor(v)/rawHalf
    // as e.g. 1.9998 and truncation would bias offsets by -1px. The fract
    // part is retained for gyro composition and Halide subpixel sampling.
    return floor(alignment.xy * vec2(rawHalf) + vec2(0.5)) + alignment.zw;
}
vec2 hash22(vec2 p)
{
    vec3 p3 = fract(vec3(p.xyx) * vec3(.1031, .1030, .0973));
    p3 += dot(p3, p3.yzx+33.33);
    return fract((p3.xx+p3.yz)*p3.zy);
}
void main() {
    ivec2 xy = ivec2(gl_GlobalInvocationID.xy);
    ivec2 outSize = imageSize(outTexture);
    if (any(greaterThanEqual(xy, outSize))) return;
    vec2 uvScale = vec2(outSize-border);
    vec2 uv = vec2(xy)/uvScale + vec2(0.5)/uvScale;
    vec4 bayerBase = imageLoad(baseTexture,xy);
    vec4 bayer = getBayerVec(xy*TILE, inTexture);
    //vec4 hp = imageLoad(hotPixTexture, xy);
    //bayer = bayer * vec4(1.0-hp) + imageLoad(avrTexture, xy) * hp;
    vec4 noise = vec4(max(sqrt(max(bayer * noiseS + noiseO, 1e-6)), vec4(minLevel)));
    vec4 w[4];
    w[3] = windowxy4((TILE*xy)%TILE_AL);
    w[2] = windowxy4((TILE*xy)%TILE_AL + ivec2(TILE_AL,0));
    w[1] = windowxy4((TILE*xy)%TILE_AL + ivec2(0,TILE_AL));
    w[0] = windowxy4((TILE*xy)%TILE_AL + ivec2(TILE_AL));
    vec4 alignedSum = vec4(0.0);
#if GYRO_WARP
    vec4 bayerNone = warpedBayer(vec2(xy));
#else
    vec4 bayerNone = imageLoad(alterTexture, xy);
#endif
#if !GYRO_WARP && HALIDE_SAMPLING > 0 && (HALIDE_MOTION_FIELD || CFA_RESIDUAL_FUSION)
    // Halide's overlapped patch spans 2*TILE_AL raw pixels. Its first
    // vector belongs at TILE_AL-0.5, not the patch origin. Interpolate the
    // motion at this Bayer quad's center, then reconstruct the CFA once.
    // Mixing four separately warped images otherwise widens edges whenever
    // neighboring estimates differ, even for a smooth physical motion field.
    vec2 grid = (2.0*vec2(xy) - vec2(cfaShift) + 1.0) / float(TILE_AL) - 1.0;
    ivec2 cell = ivec2(floor(grid));
    vec2 fraction = fract(grid);
    vec2 motion[4];
    for (int i=0; i<4; ++i) {
        ivec2 at = clamp(cell + ivec2(i%2,i/2),ivec2(0),alignmentSize-1);
        motion[i] = vec4ToAlignment(texelFetch(alignmentTexture,at+shift,0));
    }
    vec2 displacement = mix(mix(motion[0],motion[1],fraction.x),
                            mix(motion[2],motion[3],fraction.x),fraction.y);
#if CFA_RESIDUAL_FUSION
    // The residual is defined against the estimated warp; choosing an
    // unwarped pixel independently would invalidate its observation model.
    // The following combine pass still performs shared-quad robust rejection.
    vec4 candidate = residualCandidate(xy, displacement, bayerBase);
    imageStore(outTexture, xy, clamp(candidate, vec4(0.0), vec4(min(exposure,1.0))));
    return;
#else
    vec4 candidate = sampleCfa(vec2(xy)+displacement);
#if HALIDE_PIXEL_FALLBACK
    vec4 alignedError = abs(candidate*exposure-bayerBase);
    vec4 unalignedError = abs(bayerNone*exposure-bayerBase);
    alignedSum = mix(bayerNone,candidate,smoothstep(vec4(0.48),vec4(0.51),
                     unalignedError/max(alignedError+unalignedError,vec4(1e-8))));
#else
    alignedSum = candidate;
#endif
#endif
#else
#if !GYRO_WARP && HALIDE_SAMPLING == 3
    vec4 previousAlignment = vec4(0.0), previousSample = vec4(0.0);
#endif
    for (int i = 0; i < 4; i++) {
        ivec2 xyT = clamp(ivec2((TILE*xy)/TILE_AL + ivec2(i % 2, i / 2)),ivec2(0),alignmentSize-1);
        vec4 alignLoad = texelFetch(alignmentTexture, xyT + shift, 0);
#if GYRO_WARP
        vec2 residual = vec4ToAlignment(alignLoad);
        vec4 bayerAlter = warpedBayer(vec2(xy)+residual);
#elif HALIDE_SAMPLING == 3
        // Median-filtered Halide fields often repeat neighboring vectors.
        // Exact equality permits reuse without smoothing or rounding motion.
        vec4 bayerAlter;
        if (i > 0 && all(equal(alignLoad, previousAlignment))) {
            bayerAlter = previousSample;
        } else {
            bayerAlter = sampleCfa(vec2(xy) + vec4ToAlignment(alignLoad));
        }
        previousAlignment = alignLoad;
        previousSample = bayerAlter;
#elif HALIDE_SAMPLING > 0
        vec4 bayerAlter = sampleCfa(vec2(xy) + vec4ToAlignment(alignLoad));
#else
        ivec2 align = ivec2(floor(vec4ToAlignment(alignLoad)));
        ivec2 aligned = clamp(xy + align, ivec2(0), outSize - ivec2(1));
        vec4 bayerAlter = imageLoad(alterTexture, aligned);
#endif
        vec4 w1 = (abs(bayerAlter*vec4(exposure) - bayerBase));
        vec4 w2 = (abs(bayerNone*vec4(exposure) - bayerBase));
        // Correct smoothstep(edge0, edge1, x) order, including equal/black
        // samples. The former non-gyro call used the ratio as edge0 and
        // could reverse the edges (undefined), or divide zero by zero.
#if HALIDE_PIXEL_FALLBACK || HALIDE_SAMPLING == 0 || GYRO_WARP
        bayerAlter = mix(bayerNone, bayerAlter, smoothstep(vec4(0.48),vec4(0.51),w2/max(w1+w2,vec4(1e-8))));
#endif
        alignedSum += bayerAlter * w[i];
    }
#endif

    alignedSum = clamp(alignedSum, vec4(0.0), vec4(1.0));
    alignedSum *= vec4(exposure);

    imageStore(outTexture, xy, clamp(alignedSum, vec4(0.0), vec4(1.0)));
}
