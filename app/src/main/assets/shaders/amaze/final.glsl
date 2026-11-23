#import amaze
// AMaZE pass 15: final RGB assembly (C++ lines 1520-1577), with the tile
// skirt crop folded in: the dispatch covers the tile's interior (unpadded
// coords) while all texture reads are addressed in padded window coordinates
// (output pixel o reads window texel o + u_inner = o + PAD + BORDER), so the
// demosaiced skirt never reaches the output; stores land at o + u_outoff in
// the full-size output image.
uniform sampler2D u_chroma;   // D0', D1', rgbgreen
uniform sampler2D u_hvwt; // @post-r32f — firmed hvwt
uniform ivec2 u_outsize;      // tile interior size (tw, th)
uniform ivec2 u_outoff;       // tile origin in the output image
uniform ivec2 u_inner;        // interior origin in window coords (PAD + BORDER)

const int HALO = 2;   // half-grid reads hpos(q) can land one column left of q
const int TW = LW + 2 * HALO;
const int TH = LH + 2 * HALO;
const int TN = TW * TH;

// staging origin: the workgroup's output block (interior coords) + u_inner
// - HALO, so every staged texel is addressed in padded-window coordinates
ivec2 T0() { return ivec2(gl_WorkGroupID.xy * uvec2(LW, LH)) + u_inner - HALO; }
int SIDX(ivec2 q) { ivec2 l = q - T0(); return l.y * TW + l.x; }
int SIDX_H(ivec2 q) { ivec2 l = ivec2(q.x & ~1, q.y) - T0(); return l.y * TW + l.x; }

shared vec4 s_chroma[TN];
shared float s_hvwt[TN];
float hv(ivec2 q) { return s_hvwt[SIDX_H(q)]; }
float D0(ivec2 q) { return s_chroma[SIDX_H(q)].r; }
float D1(ivec2 q) { return s_chroma[SIDX_H(q)].g; }

void load_tile() {
    ivec2 t0 = T0();
    for (int i = int(gl_LocalInvocationIndex); i < TN; i += LW * LH) {
        ivec2 q = clamp(t0 + ivec2(i % TW, i / TW), ivec2(0), u_size - 1);
        s_chroma[i] = texelFetch(u_chroma, q, 0);
        s_hvwt[i] = texelFetch(u_hvwt, q, 0).r;
    }
    memoryBarrierShared();
    barrier();
}

layout(binding = 0, rgba16f) writeonly uniform highp image2D img_out;
void main() {
    load_tile();
    ivec2 o = ivec2(gl_GlobalInvocationID.xy);
    if (o.x >= u_outsize.x || o.y >= u_outsize.y) return;
    ivec2 p = o + u_inner;
    float g = s_chroma[SIDX(p)].b;
    float r, b;
    if (isG(p.y, p.x)) {
        float wu = hv(p + ivec2(0, -1));
        float wd = hv(p + ivec2(0, 1));
        float wl = hv(p + ivec2(-1, 0));
        float wr = hv(p + ivec2(1, 0));
        float temp = 1.0 / (wu + 2.0 - wr - wl + wd);
        r = g - (wu * D0(p + ivec2(0, -1)) + (1.0 - wr) * D0(p + ivec2(1, 0))
                 + (1.0 - wl) * D0(p + ivec2(-1, 0)) + wd * D0(p + ivec2(0, 1))) * temp;
        b = g - (wu * D1(p + ivec2(0, -1)) + (1.0 - wr) * D1(p + ivec2(1, 0))
                 + (1.0 - wl) * D1(p + ivec2(-1, 0)) + wd * D1(p + ivec2(0, 1))) * temp;
    } else {
        r = g - D0(p);
        b = g - D1(p);
    }
    imageStore(img_out, o + u_outoff, vec4(max(r, 0.0), max(g, 0.0), max(b, 0.0), 1.0));
}
