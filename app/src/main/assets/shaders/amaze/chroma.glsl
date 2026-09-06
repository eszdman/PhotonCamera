#import amaze
// AMaZE pass 14: fancy chroma smoothing (C++ lines 1426-1435)
uniform sampler2D u_gd3;   // rgbgreen'', D0, D1

const int HALO = 4;
const int TW = LW + 2 * HALO;
const int TH = LH + 2 * HALO;
const int TN = TW * TH;

ivec2 T0() { return ivec2(gl_WorkGroupID.xy * uvec2(LW, LH)) - HALO; }
int SIDX(ivec2 q) { ivec2 l = q - T0(); return l.y * TW + l.x; }
int SIDX_H(ivec2 q) { ivec2 l = ivec2(q.x & ~1, q.y) - T0(); return l.y * TW + l.x; }

shared vec4 s_gd3[TN];
float D0(ivec2 q) { return s_gd3[SIDX_H(q)].g; }
float D1(ivec2 q) { return s_gd3[SIDX_H(q)].b; }

void load_tile() {
    ivec2 t0 = T0();
    for (int i = int(gl_LocalInvocationIndex); i < TN; i += LW * LH) {
        ivec2 q = clamp(t0 + ivec2(i % TW, i / TW), ivec2(0), u_size - 1);
        s_gd3[i] = texelFetch(u_gd3, q, 0);
    }
    memoryBarrierShared();
    barrier();
}

layout(binding = 0, rgba16f) writeonly uniform highp image2D img_out;
void emit(ivec2 p, vec4 v) { imageStore(img_out, p, v); }
void main() {
    load_tile();
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (p.x >= u_size.x || p.y >= u_size.y) return;
    vec4 base = s_gd3[SIDX(p)];
    vec4 o = vec4(base.g, base.b, base.r, 0.0);
    if ((p.x & 1) == 1) { emit(p, o); return; }
    ivec2 s = ivec2(site_x(p), p.y);
    if (!inside(s, 14)) { emit(p, o); return; }
    bool cb = FC(s.y, s.x) == 2;   // c = 0 at B sites (smooth D0), c = 1 at R sites (smooth D1)
#define DC(q) (cb ? D0(q) : D1(q))
    float wtnw = 1.0 / (EPS + abs(DC(s + ivec2(-1, -1)) - DC(s + ivec2(1, 1))) + abs(DC(s + ivec2(-1, -1)) - DC(s + ivec2(-3, -3))) + abs(DC(s + ivec2(1, 1)) - DC(s + ivec2(-3, -3))));
    float wtne = 1.0 / (EPS + abs(DC(s + ivec2(1, -1)) - DC(s + ivec2(-1, 1))) + abs(DC(s + ivec2(1, -1)) - DC(s + ivec2(3, -3))) + abs(DC(s + ivec2(-1, 1)) - DC(s + ivec2(3, -3))));
    float wtsw = 1.0 / (EPS + abs(DC(s + ivec2(-1, 1)) - DC(s + ivec2(1, -1))) + abs(DC(s + ivec2(-1, 1)) - DC(s + ivec2(3, 3))) + abs(DC(s + ivec2(1, -1)) - DC(s + ivec2(-3, 3))));
    float wtse = 1.0 / (EPS + abs(DC(s + ivec2(1, 1)) - DC(s + ivec2(-1, -1))) + abs(DC(s + ivec2(1, 1)) - DC(s + ivec2(-3, 3))) + abs(DC(s + ivec2(-1, -1)) - DC(s + ivec2(3, 3))));
    float val = (wtnw * (1.325 * DC(s + ivec2(-1, -1)) - 0.175 * DC(s + ivec2(-3, -3)) - 0.075 * DC(s + ivec2(-3, -1)) - 0.075 * DC(s + ivec2(-1, -3)))
               + wtne * (1.325 * DC(s + ivec2(1, -1)) - 0.175 * DC(s + ivec2(3, -3)) - 0.075 * DC(s + ivec2(3, -1)) - 0.075 * DC(s + ivec2(1, 1)))
               + wtsw * (1.325 * DC(s + ivec2(-1, 1)) - 0.175 * DC(s + ivec2(-3, 3)) - 0.075 * DC(s + ivec2(-3, 1)) - 0.075 * DC(s + ivec2(-1, -1)))
               + wtse * (1.325 * DC(s + ivec2(1, 1)) - 0.175 * DC(s + ivec2(3, 3)) - 0.075 * DC(s + ivec2(3, 1)) - 0.075 * DC(s + ivec2(1, 3)))) / (wtnw + wtne + wtsw + wtse);
#undef DC
    if (cb) {
        o.r = val;   // at B sites c==0 -> D0 smoothed
    } else {
        o.g = val;   // at R sites c==1 -> D1 smoothed
    }
    emit(p, o);
}
