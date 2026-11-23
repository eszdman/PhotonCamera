//
// Created by eszdman on 05.08.2026.
//

#ifndef ESD4D_BUFFER_BACKEND
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
#define HALIDE_SAMPLING 0 // 0 legacy floor, 1 nearest, 2 CFA bilinear, 3 bounded CFA cubic, 4 bounded CFA Lanczos-2
#define HALIDE_MOTION_FIELD 0
#define CFA_RESIDUAL_FUSION 0
#if GYRO_WARP
uniform mat3 gyroHomography;
#endif
#if CFA_RESIDUAL_FUSION
layout(rgba16f, binding = 2) uniform highp readonly image2D baseTexture;
uniform float cfaResidualStrength;
uniform float cfaNoiseS;
uniform float cfaNoiseO;
#endif

#endif

#if !GYRO_WARP && HALIDE_SAMPLING > 0
// RGBA stores Bayer sites. R/B retain their own color lattices; the two
// greens are sampled together on their physical quincunx lattice.
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

vec4 projectionWeights(float f) {
#if HALIDE_SAMPLING == 4
    // Normalized Lanczos-2. Its common sinc factor cancels on normalization.
    // Two trigonometric evaluations cover all four taps; endpoints are exact.
    if (f<=1e-6) return vec4(0,1,0,0);
    if (f>=1.0-1e-6) return vec4(0,0,1,0);
    float sn=sin(0.5*M_PI*f), cs=cos(0.5*M_PI*f);
    vec4 d=vec4(1.0+f,f,1.0-f,2.0-f);
    vec4 w=vec4(-cs,sn,cs,-sn)/(d*d);
    return w/dot(w,vec4(1.0));
#else
    float f2 = f*f, f3 = f2*f;
    // Interpolating Catmull-Rom: exact at integer shifts, sharper than a tent.
    return vec4(-0.5*f + f2 - 0.5*f3, 1.0 - 2.5*f2 + 1.5*f3,
                 0.5*f + 2.0*f2 - 1.5*f3, -0.5*f2 + 0.5*f3);
#endif
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

// Packed greens form one rotated lattice, not two independent color filters.
// u=(rawX+rawY-1)/2, v=(rawX-rawY-1)/2; both coordinates are integral at green.
float greenLatticeTexel(ivec2 uv) {
    ivec2 raw = ivec2(uv.x+uv.y+1,uv.x-uv.y);
    ivec2 quad = ivec2(floor(vec2(raw)*0.5));
    bool first = (raw.x & 1) == 1;
    // Only the selected green's real CFA bounds are needed. Loading all four
    // separately clamped planes here wastes reads at the border.
    ivec2 low = first ? ivec2(0,cfaShift.y) : ivec2(cfaShift.x,0);
    vec4 tap = imageLoad(alterTexture,clamp(quad,low,rawHalf-ivec2(1)+low));
    return first ? tap.g : tap.b;
}

vec2 bilinearGreenObservation(vec2 quad) {
    vec2 p=vec2(quad.x+quad.y,quad.x-quad.y);
    ivec2 lo = ivec2(floor(p));
    vec2 f = fract(p);
    // The second green's v coordinate is one less, so two taps are shared.
    float row0 = mix(greenLatticeTexel(lo+ivec2(0,-1)),greenLatticeTexel(lo+ivec2(1,-1)),f.x);
    float row1 = mix(greenLatticeTexel(lo),greenLatticeTexel(lo+ivec2(1,0)),f.x);
    float row2 = mix(greenLatticeTexel(lo+ivec2(0,1)),greenLatticeTexel(lo+ivec2(1,1)),f.x);
    return vec2(mix(row1,row2,f.y),mix(row0,row1,f.y));
}
// A packed quad contains two consecutive v sites on one green-lattice column.
vec2 greenPairTexel(ivec2 p, bool interior) {
    if (interior) return imageLoad(alterTexture,p).gb;
    ivec2 lowG=ivec2(0,cfaShift.y), lowB=ivec2(cfaShift.x,0);
    return vec2(imageLoad(alterTexture,clamp(p,lowG,rawHalf-ivec2(1)+lowG)).g,
                imageLoad(alterTexture,clamp(p,lowB,rawHalf-ivec2(1)+lowB)).b);
}

vec2 fourTapGreenObservation(vec2 quad, bool bounded) {
    vec2 p=vec2(quad.x+quad.y,quad.x-quad.y);
    ivec2 lo=ivec2(floor(p));
    vec4 wu=projectionWeights(fract(p.x)), wv=projectionWeights(fract(p.y));
    ivec2 center=ivec2(floor(quad));
    bool interior=all(greaterThanEqual(center-ivec2(3),cfaShift))
               && all(lessThan(center+ivec2(3),rawHalf));
    // G2 is one lattice row below G1. Their 20-site footprint occupies just
    // 12 packed quads: each fetch supplies both phases at their actual sites.
    vec4 rows=vec4(0.0);
    float lastRow=0.0;
    vec2 lower=vec2(1e30), upper=vec2(-1e30);
    for (int i=0;i<4;++i) {
        ivec2 uv=lo+ivec2(i-1,-2);
        ivec2 raw=ivec2(uv.x+uv.y+1,uv.x-uv.y);
        ivec2 at=ivec2(floor(vec2(raw)*0.5));
        vec2 a=greenPairTexel(at,interior);
        vec2 b=greenPairTexel(at+ivec2(1,-1),interior);
        vec2 c=greenPairTexel(at+ivec2(2,-2),interior);
        bool first=(raw.x & 1)==1;
        vec4 column=first ? vec4(a.x,b.y,b.x,c.y) : vec4(a.y,a.x,b.y,b.x);
        float last=first ? c.x : c.y;
        rows+=column*wu[i];
        lastRow+=last*wu[i];
        if (bounded && (i==1 || i==2)) {
            lower=min(lower,vec2(min(column.z,column.w),min(column.y,column.z)));
            upper=max(upper,vec2(max(column.z,column.w),max(column.y,column.z)));
        }
    }
    vec2 value=vec2(dot(vec4(rows.yzw,lastRow),wv),dot(rows,wv));
    return bounded ? clamp(value,lower,upper) : value;
}

vec4 sampleCfa(vec2 p) {
#if HALIDE_SAMPLING == 4
    // At a whole-quad shift both Bayer lattices land on their original sites.
    if (all(equal(p, floor(p)))) return cfaTexel(ivec2(p));
#endif
#if HALIDE_SAMPLING == 1
    vec4 value=cfaTexel(ivec2(floor(p+vec2(0.5))));
    ivec2 green=ivec2(floor(vec2(p.x+p.y,p.x-p.y)+vec2(0.5)));
    value.gb=vec2(greenLatticeTexel(green),greenLatticeTexel(green+ivec2(0,-1)));
    return value;
#else
    ivec2 lo=ivec2(floor(p));
    vec2 f=fract(p);
#if HALIDE_SAMPLING == 2
    vec4 value=mix(mix(cfaTexel(lo),cfaTexel(lo+ivec2(1,0)),f.x),
                   mix(cfaTexel(lo+ivec2(0,1)),cfaTexel(lo+ivec2(1)),f.x),f.y);
    value.gb=bilinearGreenObservation(p);
    return value;
#else
    vec4 wx=projectionWeights(f.x), wy=projectionWeights(f.y);
    vec4 value;
    if (all(greaterThanEqual(lo-ivec2(1),cfaShift))
            && all(lessThan(lo+ivec2(2),rawHalf))) {
        value=cubicInterior(lo,wx,wy);
    } else {
        value=vec4(0.0);
        for (int j=0;j<4;++j) for (int i=0;i<4;++i)
            value+=cfaTexel(lo+ivec2(i-1,j-1))*wx[i]*wy[j];
        vec4 a=cfaTexel(lo),b=cfaTexel(lo+ivec2(1,0));
        vec4 c=cfaTexel(lo+ivec2(0,1)),d=cfaTexel(lo+ivec2(1));
        value=clamp(value,min(min(a,b),min(c,d)),max(max(a,b),max(c,d)));
    }
    // Bound the two green outputs by enclosing sites on their actual lattice.
    value.gb=fourTapGreenObservation(p,true);
    return value;
#endif
#endif
}

#if CFA_RESIDUAL_FUSION
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

#if HALIDE_SAMPLING >= 3
// Autocorrelation of the four interpolation taps: the matching local A^T A kernel.
vec4 fourTapNormalWeights(vec4 w) {
    return vec4(dot(w,w),dot(w.xyz,w.yzw),dot(w.xy,w.zw),w.x*w.w);
}

float greenNormalWeight(vec4 nu, vec4 nv, int u, int v) {
    if (abs(u)>3 || abs(v)>3) return 0.0;
    return nu[abs(u)]*nv[abs(v)];
}

vec4 fourTapResidualCandidate(ivec2 xy, vec2 displacement, vec4 base) {
    vec2 p=vec2(xy)+displacement;
    ivec2 lo=ivec2(floor(p));
    vec4 wx=projectionWeights(fract(p.x)), wy=projectionWeights(fract(p.y));
    vec4 observation=vec4(0.0);
    // Keep the interpolation linear so its residual model remains matched.
    // The caller clamps the final reconstructed CFA to the sensor range.
    for (int j=0;j<4;++j) {
        vec4 row=vec4(0.0);
        for (int i=0;i<4;++i) row+=cfaTexel(lo+ivec2(i-1,j-1))*wx[i];
        observation+=row*wy[j];
    }
    observation.gb=fourTapGreenObservation(p,false);
    vec4 nx=fourTapNormalWeights(wx), ny=fourTapNormalWeights(wy);
    vec2 gf=fract(vec2(displacement.x+displacement.y,displacement.x-displacement.y));
    vec4 nu=fourTapNormalWeights(projectionWeights(gf.x));
    vec4 nv=fourTapNormalWeights(projectionWeights(gf.y));
    vec4 sum=vec4(0.0), squares=vec4(0.0), predicted=vec4(0.0);
    // Reuse the confidence taps for the center of the normal operator.
    for (int j=-1;j<=1;++j) for (int i=-1;i<=1;++i) {
        vec4 tap=baseCfaTexel(xy+ivec2(i,j));
        sum+=tap; squares+=tap*tap;
        predicted.ra+=tap.ra*nx[abs(i)]*ny[abs(j)];
        float same=greenNormalWeight(nu,nv,i+j,i-j);
        predicted.g+=tap.g*same+tap.b*greenNormalWeight(nu,nv,i+j,i-j-1);
        predicted.b+=tap.b*same+tap.g*greenNormalWeight(nu,nv,i+j,i-j+1);
    }
    vec4 mean=sum/9.0, variance=max(squares/9.0-mean*mean,vec4(0.0));
    vec4 noiseVariance=max(mean*cfaNoiseS+cfaNoiseO,vec4(0.0));
    float confidence=dot(max(variance-noiseVariance,vec4(0.0)),vec4(0.25))
                     /max(dot(variance,vec4(0.25)),1e-10);
    if (confidence<=0.0) return observation*exposure;
    for (int j=-3;j<=3;++j) for (int i=-3;i<=3;++i) {
        if (abs(i)<=1 && abs(j)<=1) continue;
        vec4 tap=baseCfaTexel(xy+ivec2(i,j));
        predicted.ra+=tap.ra*nx[abs(i)]*ny[abs(j)];
        float same=greenNormalWeight(nu,nv,i+j,i-j);
        predicted.g+=tap.g*same+tap.b*greenNormalWeight(nu,nv,i+j,i-j-1);
        predicted.b+=tap.b*same+tap.g*greenNormalWeight(nu,nv,i+j,i-j+1);
    }
    return observation*exposure+cfaResidualStrength*confidence*(base-predicted);
}
#endif

// Local-translation approximation to a CFA forward/adjoint residual update.
// A bilinear forward warp followed by its adjoint has separable weights
// [f*(1-f), 1-2*f*(1-f), f*(1-f)]. Evaluate this normal operator directly:
// Four source taps + nine base taps, no intermediate warp or extra dispatch.
// Green quincunx sampling uses six source taps and the same nine base taps.
// Strength below one regularizes the inverse: a full update over-amplifies
// near-Nyquist detail/noise when the bilinear observation model is imperfect.
vec4 residualCandidate(ivec2 xy, vec2 displacement, vec4 base) {
#if HALIDE_SAMPLING == 1
    // Nearest-neighbor warp has an identity local normal operator.
    return sampleCfa(vec2(xy)+displacement)*exposure;
#elif HALIDE_SAMPLING >= 3
    return fourTapResidualCandidate(xy,displacement,base);
#else
    vec2 p = vec2(xy) + displacement;
    ivec2 lo = ivec2(floor(p));
    vec2 f = fract(p);
    vec4 observation = mix(mix(cfaTexel(lo), cfaTexel(lo+ivec2(1,0)), f.x),
                           mix(cfaTexel(lo+ivec2(0,1)), cfaTexel(lo+ivec2(1)), f.x), f.y);
    observation.gb = bilinearGreenObservation(p);
    vec2 gf = fract(vec2(displacement.x+displacement.y,displacement.x-displacement.y));
    vec2 gs = gf*(1.0-gf), gc = 1.0-2.0*gs;
    float gCenter = gc.x*gc.y, gAxial = gs.x*gs.y;
    float gU = gs.x*gc.y, gV = gc.x*gs.y;
    vec2 greenPredicted = vec2(0.0);
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
        sum += left+center+right;
        squareSum += left*left+center*center+right*right;
    }
    predicted.gb = greenPredicted;
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
#endif
}
#endif
#endif

#if GYRO_WARP

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
    vec4 w[4];
    w[3] = windowxy4((TILE*xy)%TILE_AL);
    w[2] = windowxy4((TILE*xy)%TILE_AL + ivec2(TILE_AL,0));
    w[1] = windowxy4((TILE*xy)%TILE_AL + ivec2(0,TILE_AL));
    w[0] = windowxy4((TILE*xy)%TILE_AL + ivec2(TILE_AL));
    vec4 alignedSum = vec4(0.0);
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
    // The residual uses the estimated warp; the following combine pass
    // performs shared-quad robust rejection.
    vec4 bayerBase = imageLoad(baseTexture,xy);
    vec4 candidate = residualCandidate(xy, displacement, bayerBase);
    imageStore(outTexture, xy, clamp(candidate, vec4(0.0), vec4(min(exposure,1.0))));
    return;
#else
    alignedSum = sampleCfa(vec2(xy)+displacement);
#endif
#else
#if !GYRO_WARP && HALIDE_SAMPLING >= 3
    vec4 previousAlignment = vec4(0.0), previousSample = vec4(0.0);
#endif
    for (int i = 0; i < 4; i++) {
        ivec2 xyT = clamp(ivec2((TILE*xy)/TILE_AL + ivec2(i % 2, i / 2)),ivec2(0),alignmentSize-1);
        vec4 alignLoad = texelFetch(alignmentTexture, xyT + shift, 0);
#if GYRO_WARP
        vec2 residual = vec4ToAlignment(alignLoad);
        vec4 bayerAlter = warpedBayer(vec2(xy)+residual);
#elif HALIDE_SAMPLING >= 3
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
        alignedSum += bayerAlter * w[i];
    }
#endif

    alignedSum = clamp(alignedSum, vec4(0.0), vec4(1.0));
    alignedSum *= vec4(exposure);

    imageStore(outTexture, xy, clamp(alignedSum, vec4(0.0), vec4(1.0)));
}
