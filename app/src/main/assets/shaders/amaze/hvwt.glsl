#import amaze
// AMaZE pass 4+5 (fused): adaptive weights hvwt at R/B sites (C++ lines
// 741-783) and the nyquist texture test (C++ lines 836-857).  Both evaluate
// at the same sites under the same inside(s, 6) guard.
//
// The nyquist test value is still computed and stored per site - the
// per-pixel flags gate the correction math in nyq2/area/nyqref - but the
// flagged-site bounding box reduction is disabled: the correction passes
// run unconditionally on every tile (measured: the bbox almost always
// covered the whole window on real content anyway).
uniform sampler2D u_cd2;   // vcd', hcd', cddiffsq
uniform sampler2D u_cdb;   // dgintv, dginth
uniform sampler2D u_grad;

// All taps are inside the six-pixel guard in main.
float V(ivec2 p)   { return texelFetch(u_cd2, p, 0).r; }
float H(ivec2 p)   { return texelFetch(u_cd2, p, 0).g; }
float dgi(ivec2 p) { return texelFetch(u_cdb, p, 0).r; }
float dgh(ivec2 p) { return texelFetch(u_cdb, p, 0).g; }
float dw0(ivec2 p) { return texelFetch(u_grad, p, 0).r; }
float dw1(ivec2 p) { return texelFetch(u_grad, p, 0).g; }
float cds(ivec2 p) { return texelFetch(u_cd2, p, 0).b; }
float dhs(ivec2 p) { return texelFetch(u_grad, p, 0).b; }

layout(binding = 0, r32f) writeonly uniform highp image2D img_hvwt;
layout(binding = 1, r32f) writeonly uniform highp image2D img_nyq;

// hvwt at R/B site s, guarded by inside(s, 6)
float hvwt_val(ivec2 s) {
    float uave = V(s) + V(s + ivec2(0, -1)) + V(s + ivec2(0, -2)) + V(s + ivec2(0, -3));
    float dave = V(s) + V(s + ivec2(0, 1)) + V(s + ivec2(0, 2)) + V(s + ivec2(0, 3));
    float lave = H(s) + H(s + ivec2(-1, 0)) + H(s + ivec2(-2, 0)) + H(s + ivec2(-3, 0));
    float rave = H(s) + H(s + ivec2(1, 0)) + H(s + ivec2(2, 0)) + H(s + ivec2(3, 0));
    float varu = sq(V(s) - uave) + sq(V(s + ivec2(0, -1)) - uave) + sq(V(s + ivec2(0, -2)) - uave) + sq(V(s + ivec2(0, -3)) - uave);
    float vard = sq(V(s) - dave) + sq(V(s + ivec2(0, 1)) - dave) + sq(V(s + ivec2(0, 2)) - dave) + sq(V(s + ivec2(0, 3)) - dave);
    float varl = sq(H(s) - lave) + sq(H(s + ivec2(-1, 0)) - lave) + sq(H(s + ivec2(-2, 0)) - lave) + sq(H(s + ivec2(-3, 0)) - lave);
    float varr = sq(H(s) - rave) + sq(H(s + ivec2(1, 0)) - rave) + sq(H(s + ivec2(2, 0)) - rave) + sq(H(s + ivec2(3, 0)) - rave);
    float hwt = dw1(s + ivec2(-1, 0)) / (dw1(s + ivec2(-1, 0)) + dw1(s + ivec2(1, 0)));
    float vwt = dw0(s + ivec2(0, -1)) / (dw0(s + ivec2(0, 1)) + dw0(s + ivec2(0, -1)));
    float vcdvar = EPSSQ + vwt * vard + (1.0 - vwt) * varu;
    float hcdvar = EPSSQ + hwt * varr + (1.0 - hwt) * varl;
    float varu1 = dgi(s) + dgi(s + ivec2(0, -1)) + dgi(s + ivec2(0, -2));
    float vard1 = dgi(s) + dgi(s + ivec2(0, 1)) + dgi(s + ivec2(0, 2));
    float varl1 = dgh(s) + dgh(s + ivec2(-1, 0)) + dgh(s + ivec2(-2, 0));
    float varr1 = dgh(s) + dgh(s + ivec2(1, 0)) + dgh(s + ivec2(2, 0));
    float vcdvar1 = EPSSQ + vwt * vard1 + (1.0 - vwt) * varu1;
    float hcdvar1 = EPSSQ + hwt * varr1 + (1.0 - hwt) * varl1;
    float varwt = hcdvar / (vcdvar + hcdvar);
    float diffwt = hcdvar1 / (vcdvar1 + hcdvar1);
    return ((0.5 - varwt) * (0.5 - diffwt) > 0.0 && abs(0.5 - diffwt) < abs(0.5 - varwt)) ? varwt : diffwt;
}

// nyquist test value at R/B site s, guarded by inside(s, 6)
float nyq_val(ivec2 s) {
    return GAUSSODD[0] * cds(s)
        + GAUSSODD[1] * (cds(s + ivec2(1, 1)) + cds(s + ivec2(1, -1)) + cds(s + ivec2(-1, -1)) + cds(s + ivec2(-1, 1)))
        + GAUSSODD[2] * (cds(s + ivec2(0, -2)) + cds(s + ivec2(-2, 0)) + cds(s + ivec2(2, 0)) + cds(s + ivec2(0, 2)))
        + GAUSSODD[3] * (cds(s + ivec2(2, 2)) + cds(s + ivec2(2, -2)) + cds(s + ivec2(-2, -2)) + cds(s + ivec2(-2, 2)))
        - GAUSSGRAD[0] * dhs(s)
        - GAUSSGRAD[1] * (dhs(s + ivec2(0, -1)) + dhs(s + ivec2(1, 0)) + dhs(s + ivec2(-1, 0)) + dhs(s + ivec2(0, 1)))
        - GAUSSGRAD[2] * (dhs(s + ivec2(1, 1)) + dhs(s + ivec2(1, -1)) + dhs(s + ivec2(-1, -1)) + dhs(s + ivec2(-1, 1)))
        - GAUSSGRAD[3] * (dhs(s + ivec2(0, -2)) + dhs(s + ivec2(-2, 0)) + dhs(s + ivec2(2, 0)) + dhs(s + ivec2(0, 2)))
        - GAUSSGRAD[4] * (dhs(s + ivec2(-1, -2)) + dhs(s + ivec2(1, -2)) + dhs(s + ivec2(-2, -1)) + dhs(s + ivec2(2, -1))
                          + dhs(s + ivec2(-2, 1)) + dhs(s + ivec2(2, 1)) + dhs(s + ivec2(-1, 2)) + dhs(s + ivec2(1, 2)))
        - GAUSSGRAD[5] * (dhs(s + ivec2(2, 2)) + dhs(s + ivec2(2, -2)) + dhs(s + ivec2(-2, -2)) + dhs(s + ivec2(-2, 2)));
}

void main() {
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    float hv = 0.0, val = 0.0;
    if (p.x < u_size.x && p.y < u_size.y) {
        if ((p.x & 1) == 0) {
            ivec2 s = ivec2(site_x(p), p.y);
            if (inside(s, 6)) {
                hv = hvwt_val(s);
                val = nyq_val(s);
            }
        }
        imageStore(img_hvwt, p, vec4(hv));
        imageStore(img_nyq, p, vec4(val));
    }
}
