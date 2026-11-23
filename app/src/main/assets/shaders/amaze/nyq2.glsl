#import amaze
// AMaZE pass 6: nyquist flag dilate/erode (C++ lines 893-927).
// The bounding-box test is disabled: every tile processes all of its sites
// (the per-site flags themselves still gate the downstream math).
uniform sampler2D u_nyqtest; // @post-r32f

const int HALO = 3;
const int TW = LW + 2 * HALO;
const int TH = LH + 2 * HALO;
const int TN = TW * TH;

ivec2 T0() { return ivec2(gl_WorkGroupID.xy * uvec2(LW, LH)) - HALO; }
int SIDX(ivec2 q) { ivec2 l = q - T0(); return l.y * TW + l.x; }
int SIDX_H(ivec2 q) { ivec2 l = ivec2(q.x & ~1, q.y) - T0(); return l.y * TW + l.x; }

shared float s_nyqtest[TN];   // staged at natural texel positions
bool flag(ivec2 q) { return s_nyqtest[SIDX_H(q)] > 0.0; }

void load_tile() {
    ivec2 t0 = T0();
    for (int i = int(gl_LocalInvocationIndex); i < TN; i += LW * LH) {
        ivec2 q = clamp(t0 + ivec2(i % TW, i / TW), ivec2(0), u_size - 1);
        s_nyqtest[i] = texelFetch(u_nyqtest, q, 0).r;
    }
    memoryBarrierShared();
    barrier();
}

layout(binding = 0, r32f) writeonly uniform highp image2D img_out;
void emit(ivec2 p, float v) { imageStore(img_out, p, vec4(v)); }
void main() {
    load_tile();
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (p.x >= u_size.x || p.y >= u_size.y) return;
    emit(p, 0.0);
    if ((p.x & 1) == 1) return;
    ivec2 s = ivec2(site_x(p), p.y);
    float nb = (float(flag(s + ivec2(0, -2))) + float(flag(s + ivec2(-1, -1))) + float(flag(s + ivec2(1, -1)))
                + float(flag(s + ivec2(-2, 0))) + float(flag(s + ivec2(2, 0)))
                + float(flag(s + ivec2(-1, 1))) + float(flag(s + ivec2(1, 1))) + float(flag(s + ivec2(0, 2))));
    emit(p, nb > 4.0 ? 1.0 : (nb < 4.0 ? 0.0 : (flag(s) ? 1.0 : 0.0)));
}
