
precision highp float;
precision highp usampler2D;
precision mediump sampler2D;
uniform usampler2D InputBuffer;
uniform sampler2D GainMap;
uniform sampler2D Kodak;
uniform ivec2 RawSize;
uniform vec2 RawInvSize;
uniform vec4 blackLevel;
uniform vec3 whitePoint;
uniform int CfaPattern;
uniform uint whitelevel;
uniform int MinimalInd;
#define BLR (0.0)
#define BLG (0.0)
#define BLB (0.0)
#define QUAD 0
#define RGBLAYOUT 0
#define TESTPATTERN 0
// smooth test pattern selector when TESTPATTERN == 1
// All patterns mix a white component into the hue so that every channel
// reaches the clip point -> saturated color transitions to pure white.
//   0 = gray ramp along x, 0..3 (neutral clipping check)
//   1 = hue ramp along x, luminance 0..3 along y (all hues -> white)
//   2 = constant-hue luminance ramps (hue varies smoothly with y, ramp 0..3 along x)
//   3 = radial hue sweep / cone (hue from angle, luminance from radius, clipped rim)
#define TP 2
// white component mixed into each hue [0..1]; 1/3 is the min that reaches white at l=3
#define WHITE 0.7
#define OFFSET 0,0
#define USEGAIN 1
// inpaint-opposed highlight reconstruction (port of darktable's
// _process_opposed): clipped photosites are inpainted from their opposed
// colours; Chrominance is the global per-channel offset measured on the
// unclipped ring around clipped areas (see OpposedChroma.java)
#define HLRECON 0
#define HLCLIP 0.987
#import interpolation

vec3 hue2rgb(float h) {
    h = fract(h);
    h *= 6.0;
    if (h < 1.0) return vec3(1.0, h, 0.0);
    if (h < 2.0) return vec3(2.0 - h, 1.0, 0.0);
    if (h < 3.0) return vec3(0.0, 1.0, h - 2.0);
    if (h < 4.0) return vec3(0.0, 4.0 - h, 1.0);
    if (h < 5.0) return vec3(h - 4.0, 0.0, 1.0);
    return vec3(1.0, 0.0, 6.0 - h);
}
#if RGBLAYOUT == 1
out vec3 Output;
#else
out float Output;
#endif

#if HLRECON == 1
uniform vec3 Chrominance;

// channel (0=R,1=G,2=B) of the raw photosite p, same anchoring as main()
int hlFcol(ivec2 p) {
    ivec2 ph = ivec2(CfaPattern % 2, CfaPattern / 2);
    ivec2 f = (QUAD == 1) ? (((p - ph * 2) / 2) & 1) : ((p - ph) & 1);
    return (f.x + f.y == 1) ? 1 : (f.x == 0 ? 0 : 2);
}

// normalized white-balanced value of a raw sample, same space as OpposedChroma
float hlNorm(uint rv, int c) {
    vec3 lvl = vec3(blackLevel.r, (blackLevel.g + blackLevel.b) / 2.0, blackLevel.a);
    return max(0.0, (float(rv) / float(whitelevel) - lvl[c]) / (1.0 - lvl[c]) / whitePoint[c]);
}

// opposed-colour estimate for channel c from the 3x3 photosite neighbourhood
// centred on p, averaged in cube-root space
float hlRefavg(ivec2 p, int c) {
    ivec2 lo = max(p - ivec2(1), ivec2(0));
    ivec2 hi = min(p + ivec2(1), RawSize - ivec2(1));
    float sum[3];
    float cnt[3];
    sum[0] = sum[1] = sum[2] = 0.0;
    cnt[0] = cnt[1] = cnt[2] = 0.0;
    for (int dy = lo.y; dy <= hi.y; dy++) {
        for (int dx = lo.x; dx <= hi.x; dx++) {
            int cc = hlFcol(ivec2(dx, dy));
            float v = hlNorm(texelFetch(InputBuffer, ivec2(dx, dy), 0).x, cc);
            sum[cc] += v;
            cnt[cc] += 1.0;
        }
    }
    float m0 = cnt[0] > 0.0 ? pow(sum[0] / cnt[0], 1.0 / 3.0) : 0.0;
    float m1 = cnt[1] > 0.0 ? pow(sum[1] / cnt[1], 1.0 / 3.0) : 0.0;
    float m2 = cnt[2] > 0.0 ? pow(sum[2] / cnt[2], 1.0 / 3.0) : 0.0;
    float opp = c == 0 ? 0.5 * (m1 + m2) : (c == 1 ? 0.5 * (m0 + m2) : 0.5 * (m0 + m1));
    return opp * opp * opp;
}
#endif


void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) - ivec2(OFFSET);
    ivec2 fact = (xy)%2;
    xy+=ivec2(CfaPattern%2,CfaPattern/2);
    #if QUAD == 1
        fact = (xy/2)%2;
        xy+=ivec2(CfaPattern%2,CfaPattern/2)*2;
    #endif
    float balance;
    #if USEGAIN == 1
    vec4 gains = texture(GainMap, vec2(xy)*vec2(RawInvSize));
    gains.rgb = vec3(gains.r,(gains.g+gains.b)/2.0,gains.a);
    gains.rgb /= dot(gains.rgb,vec3(1.0/3.0));
    #else
    vec3 gains = vec3(1.0);
    #endif
    //gains.rgb = vec3(1.f);
    vec3 level = vec3(blackLevel.r,(blackLevel.g+blackLevel.b)/2.0,blackLevel.a);
    #if RGBLAYOUT == 1
    //Output = vec3(texelFetch(InputBuffer, (xy+ivec2(0,0)), 0).rgb)/(float(whitelevel));
    vec3 hlRGB = vec3(texelFetch(InputBuffer, (xy), 0).rgb)/(float(whitelevel));
    hlRGB = (hlRGB - level.rgb)/(vec3(1.0)-level.rgb);
    #if HLRECON == 1
    {
        vec3 u = max(hlRGB, vec3(0.0));
        vec3 roots = pow(u, vec3(1.0/3.0));
        vec3 opp = vec3(0.5*(roots.g+roots.b), 0.5*(roots.r+roots.b), 0.5*(roots.r+roots.g));
        vec3 rec = max(u, opp*opp*opp + Chrominance);
        hlRGB = mix(u, rec, step(vec3(HLCLIP), u));
    }
    #endif
    Output = gains.rgb*hlRGB;
    #else
    vec3 col = vec3(0.0);
    float levelC;
    float gainC;
    int ci;
    if(fact.x+fact.y == 1){
            col.g = 1.0;
            balance = whitePoint.g;
            ci = 1; levelC = level.g; gainC = gains.g;
        } else {
            if(fact.x == 0){
                col.r = 1.0;
                balance = whitePoint.r;
                ci = 0; levelC = level.r; gainC = gains.r;
            } else {
                col.b = 1.0;
                balance = whitePoint.b;
                ci = 2; levelC = level.b; gainC = gains.b;
            }
        }
    Output = float(texelFetch(InputBuffer, (xy), 0).x)/(float(whitelevel));
    float hlVal = (Output - levelC)/(1.0-levelC)/balance;
    #if HLRECON == 1
    if (hlVal >= HLCLIP) {
        // inpaint the clipped photosite from its opposed colours; the value
        // stays scene-referred and may exceed 1.0 instead of clipping
        Output = gainC * max(hlVal, hlRefavg(xy, ci) + Chrominance[ci]);
    } else
    #endif
    {
        Output = clamp(gainC * hlVal, 0.0, 1.0);
    }
    #endif
    #if TESTPATTERN == 1
        ivec2 diag = ivec2(xy.x+xy.y,xy.x-xy.y);
        float t = float(xy.x)*RawInvSize.x;
        float u = float(xy.y)*RawInvSize.y;
        vec3 col2;
        float l;
        vec3 base;
        #if TP == 0
            // gray smooth ramp along x, 0..3
            l = t*3.0;
            col2 = vec3(l);
        #elif TP == 1
            // hue ramp along x, luminance 0..3 along y
            base = mix(vec3(1.0), hue2rgb(t), 1.0-WHITE);
            l = u*3.0;
            col2 = base*l;
        #elif TP == 2
            // constant-hue luminance ramps: hue smooth with y, ramp 0..3 along x
            base = mix(vec3(1.0), hue2rgb(u), 1.0-WHITE);
            l = t*3.0;
            col2 = base*l;
        #elif TP == 3
            // radial hue sweep: hue from angle, luminance from radius, clipped rim
            vec2 c = (vec2(xy)+vec2(0.5))*vec2(RawInvSize);
            float ang = atan(c.y-0.5, c.x-0.5)/6.28318530718 + 0.5;
            base = mix(vec3(1.0), hue2rgb(ang), 1.0-WHITE);
            l = min(length(c-vec2(0.5))*2.2, 3.0);
            col2 = base*l;
        #endif
        Output = length(col*col2)*balance;
    #endif
}
