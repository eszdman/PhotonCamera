#import amaze
// AMaZE pass 8: populate G at R/B sites + hvwt firming (C++ lines 971-988)
uniform sampler2D u_hvwt; // @post-r32f — pre-firming hvwt (with area updates)
uniform sampler2D u_cd2;     // vcd', hcd'
uniform sampler2D u_nyq2; // @post-r32f
uniform sampler2D u_grad;    // cfa rides in .a (written by gradcd)

const int HALO = 2;
const int TW = LW + 2 * HALO;
const int TH = LH + 2 * HALO;
const int TN = TW * TH;

ivec2 T0() { return ivec2(gl_WorkGroupID.xy * uvec2(LW, LH)) - HALO; }
int SIDX(ivec2 q) { ivec2 l = q - T0(); return l.y * TW + l.x; }
int SIDX_H(ivec2 q) { ivec2 l = ivec2(q.x & ~1, q.y) - T0(); return l.y * TW + l.x; }

shared float s_hvwt[TN];
shared float s_nyq2[TN];
shared vec4 s_cd2[TN];
shared float s_cfa[TN];
float hv(ivec2 q) { return s_hvwt[SIDX_H(q)]; }
float n2(ivec2 q) { return s_nyq2[SIDX_H(q)]; }
float V(ivec2 p) { return s_cd2[SIDX(p)].r; }
float H(ivec2 p) { return s_cd2[SIDX(p)].g; }
float Cf(ivec2 q) { return s_cfa[SIDX(q)]; }

void load_tile() {
    ivec2 t0 = T0();
    for (int i = int(gl_LocalInvocationIndex); i < TN; i += LW * LH) {
        ivec2 q = clamp(t0 + ivec2(i % TW, i / TW), ivec2(0), u_size - 1);
        s_hvwt[i] = texelFetch(u_hvwt, q, 0).r;
        s_nyq2[i] = texelFetch(u_nyq2, q, 0).r;
        s_cd2[i] = texelFetch(u_cd2, q, 0);
        s_cfa[i] = texelFetch(u_grad, q, 0).a;
    }
    memoryBarrierShared();
    barrier();
}

layout(binding = 0, rgba16f) writeonly uniform highp image2D img_gd;
layout(binding = 1, r32f) writeonly uniform highp image2D img_hv;
void emit_gd(ivec2 p, vec4 v) { imageStore(img_gd, p, v); }
void emit_hv(ivec2 p, float v) { imageStore(img_hv, p, vec4(v)); }

float firm(ivec2 s) {   // firming of hvwt at R/B site s
    float own = hv(s);
    float alt = 0.25 * (hv(s + ivec2(1, 1)) + hv(s + ivec2(1, -1)) + hv(s + ivec2(-1, -1)) + hv(s + ivec2(-1, 1)));
    return abs(0.5 - own) < abs(0.5 - alt) ? alt : own;
}
float d0_at(ivec2 s) {  // colour difference G-R/G-B at R/B site s
    float hvf = firm(s);
    return hvf * V(s) + (1.0 - hvf) * H(s);
}
void main() {
    load_tile();
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (p.x >= u_size.x || p.y >= u_size.y) return;
    float ohv = 0.0;
    if ((p.x & 1) == 1) {
        // odd column: only the per-pixel green channel is meaningful
        vec4 ogd = (!isG(p.y, p.x) && inside(p, 8)) ? vec4(Cf(p) + d0_at(p), 0.0, 0.0, 0.0)
                                                    : vec4(Cf(p), 0.0, 0.0, 0.0);
        emit_hv(p, ohv);
        emit_gd(p, ogd);
        return;
    }
    ivec2 s = ivec2(site_x(p), p.y);   // R/B site anchored at this even texel
    vec4 ogd;
    if (inside(s, 8)) {
        float d0 = d0_at(s);
        float green = Cf(s) + d0;
        float d2h = 0.0, d2v = 0.0;
        if (n2(s) > 0.5) {
            // neighbouring G values are the cfa samples
            d2h = sq(green - 0.5 * (Cf(s + ivec2(-1, 0)) + Cf(s + ivec2(1, 0))));
            d2v = sq(green - 0.5 * (Cf(s + ivec2(0, -1)) + Cf(s + ivec2(0, 1))));
        }
        ohv = firm(s);
        // .r is the green of THIS pixel; site values go to the half-grid channels
        ogd = (s == p) ? vec4(green, d0, d2h, d2v) : vec4(Cf(p), d0, d2h, d2v);
    } else {
        ogd = vec4(Cf(p), 0.0, 0.0, 0.0);
    }
    emit_hv(p, ohv);
    emit_gd(p, ogd);
}
