#import amaze
// AMaZE pass 12: G interpolation using R+B, and Dgrb split (C++ lines 1312-1400)
uniform sampler2D u_pmrbint;   // pmwt', rbint (half-grid)
uniform sampler2D u_gd2;       // rgbgreen', dgrb0'
uniform sampler2D u_hvwt; // @post-r32f — firmed hvwt
uniform sampler2D u_grad;

// The two horizontal pixels share one R/B entry at the even column.
ivec2 rbSlot(ivec2 p) { return ivec2(p.x & ~1, p.y); }
float hv(ivec2 p)    { return texelFetch(u_hvwt, rbSlot(p), 0).r; }
float pm(ivec2 p)    { return texelFetch(u_pmrbint, rbSlot(p), 0).r; }
float rbint(ivec2 p) { return texelFetch(u_pmrbint, rbSlot(p), 0).g; }
float dw0(ivec2 p)   { return texelFetch(u_grad, p, 0).r; }
float dw1(ivec2 p)   { return texelFetch(u_grad, p, 0).g; }
float Cf(ivec2 p)    { return texelFetch(u_grad, p, 0).a; }

layout(binding = 0, rgba16f) writeonly uniform highp image2D img_out;
void emit(ivec2 p, vec4 v) { imageStore(img_out, p, v); }

float d0_new(ivec2 s) {   // re-interpolated colour difference at R/B site s
    float own_rb = rbint(s);
    // colour ratios for G interpolation
    float cru = Cf(s + ivec2(0, -1)) * 2.0 / (EPS + own_rb + rbint(s + ivec2(0, -2)));
    float crd = Cf(s + ivec2(0, 1)) * 2.0 / (EPS + own_rb + rbint(s + ivec2(0, 2)));
    float crl = Cf(s + ivec2(-1, 0)) * 2.0 / (EPS + own_rb + rbint(s + ivec2(-2, 0)));
    float crr = Cf(s + ivec2(1, 0)) * 2.0 / (EPS + own_rb + rbint(s + ivec2(2, 0)));
    float gu = abs(1.0 - cru) < ARTHRESH ? own_rb * cru : Cf(s + ivec2(0, -1)) + 0.5 * (own_rb - rbint(s + ivec2(0, -2)));
    float gd = abs(1.0 - crd) < ARTHRESH ? own_rb * crd : Cf(s + ivec2(0, 1)) + 0.5 * (own_rb - rbint(s + ivec2(0, 2)));
    float gl = abs(1.0 - crl) < ARTHRESH ? own_rb * crl : Cf(s + ivec2(-1, 0)) + 0.5 * (own_rb - rbint(s + ivec2(-2, 0)));
    float gr = abs(1.0 - crr) < ARTHRESH ? own_rb * crr : Cf(s + ivec2(1, 0)) + 0.5 * (own_rb - rbint(s + ivec2(2, 0)));
    float gintv = (dw0(s + ivec2(0, -1)) * gd + dw0(s + ivec2(0, 1)) * gu) / (dw0(s + ivec2(0, 1)) + dw0(s + ivec2(0, -1)));
    float ginth = (dw1(s + ivec2(-1, 0)) * gr + dw1(s + ivec2(1, 0)) * gl) / (dw1(s + ivec2(-1, 0)) + dw1(s + ivec2(1, 0)));
    if (gintv < own_rb) {
        if (2.0 * gintv < own_rb) {
            gintv = median3(gintv, Cf(s + ivec2(0, -1)), Cf(s + ivec2(0, 1)));
        } else {
            float vw = 2.0 * (own_rb - gintv) / (EPS + gintv + own_rb);
            gintv = vw * gintv + (1.0 - vw) * median3(gintv, Cf(s + ivec2(0, -1)), Cf(s + ivec2(0, 1)));
        }
    }
    if (ginth < own_rb) {
        if (2.0 * ginth < own_rb) {
            ginth = median3(ginth, Cf(s + ivec2(-1, 0)), Cf(s + ivec2(1, 0)));
        } else {
            float hw = 2.0 * (own_rb - ginth) / (EPS + ginth + own_rb);
            ginth = hw * ginth + (1.0 - hw) * median3(ginth, Cf(s + ivec2(-1, 0)), Cf(s + ivec2(1, 0)));
        }
    }
    if (ginth > u_clip) ginth = median3(ginth, Cf(s + ivec2(-1, 0)), Cf(s + ivec2(1, 0)));
    if (gintv > u_clip) gintv = median3(gintv, Cf(s + ivec2(0, -1)), Cf(s + ivec2(0, 1)));
    float hvf = hv(s);
    return ginth * (1.0 - hvf) + gintv * hvf - Cf(s);
}
void main() {
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (p.x >= u_size.x || p.y >= u_size.y) return;
    vec4 o = vec4(0.0);
    if ((p.x & 1) == 1) {
        // odd columns carry only the per-pixel green value
        float g0 = texelFetch(u_gd2, p, 0).r;
        if (!isG(p.y, p.x) && inside(p, 12) && !(abs(0.5 - pm(p)) < abs(0.5 - hv(p)))) {
            o.r = Cf(p) + d0_new(p);
        } else {
            o.r = g0;
        }
        emit(p, o);
        return;
    }
    ivec2 s = ivec2(site_x(p), p.y);
    float d0;
    if (inside(s, 12) && !(abs(0.5 - pm(s)) < abs(0.5 - hv(s)))) {
        d0 = d0_new(s);
    } else {
        d0 = texelFetch(u_gd2, p, 0).g;   // dgrb0' at this half-grid texel
    }
    // green at this pixel (for even columns the site s may be p or p+1)
    float green;
    if (isG(p.y, p.x)) {
        green = texelFetch(u_gd2, p, 0).r;
    } else {
        green = Cf(p) + (s == p ? d0 : d0_new(p));
    }
    // split G-B from G-R at B sites (C++ lines 1396-1400)
    if (FC(s.y, s.x) == 2) {
        o = vec4(green, 0.0, d0, 0.0);
    } else {
        o = vec4(green, d0, 0.0, 0.0);
    }
    emit(p, o);
}
