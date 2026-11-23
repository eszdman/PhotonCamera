// Common header of the AMaZE compute passes (amaze/*.glsl).
// GLES 3.1 port of the verified GLSL 4.30 pipeline in amazeGLSL
// (amaze_glsl/compute_shaders.py, CS_HEADER); the math is unchanged.
//
// AMaZE demosaicing: Copyright (c) 2008-2010 Emil Martinec,
// (c) Ingo Weyrich - GPL-3.0-or-later.
precision highp float;
precision highp int;
precision highp sampler2D;
precision highp image2D;

// 8x8 = 64 invocations per workgroup: the minimum local work group size
// guaranteed by every GLES 3.1 implementation (mobile target).
layout(local_size_x = 8, local_size_y = 8) in;

uniform sampler2D u_cfa; // @post-r32f — R32F, padded CFA in [0, 1]
uniform ivec2 u_size;         // (W, H) of the padded image, both even
uniform ivec4 u_fc;           // fc[0][0], fc[0][1], fc[1][0], fc[1][1]  (0=R 1=G 2=B)
uniform float u_clip;         // clip_pt (1 / initialGain in RawTherapee)

const float EPS = 1e-5;
const float EPSSQ = 1e-10;
const float ARTHRESH = 0.75;
const float CLIP8_MULT = 0.8;
// gaussian on 5x5 quincunx, sigma=1.2
const float GAUSSODD[4] = float[4](0.14659727707323927, 0.103592713382435, 0.0732036125103057, 0.0365543548389495);
// gaussian on 5x5, sigma ~1.7, premultiplied by nyquist threshold 0.5
const float GAUSSGRAD[6] = float[6](0.03692205946710551, 0.03103755984085744, 0.0260909097373903,
                                    0.01843709643366797, 0.01549866102028923, 0.009206597080729441);
const float GAUSSEVEN[2] = float[2](0.13719494435797422, 0.05640252782101291);
const float GQUINC[4] = float[4](0.169917, 0.108947, 0.069855, 0.0287182);

int FC(int y, int x) {
    return (y & 1) == 0 ? ((x & 1) == 0 ? u_fc.x : u_fc.y)
                        : ((x & 1) == 0 ? u_fc.z : u_fc.w);
}
bool isG(int y, int x) { return (FC(y, x) & 1) == 1; }

int lin(ivec2 p) { return p.y * u_size.x + p.x; }

// exact translation of the C++ half-grid: a[indx >> 1] lives at column x & ~1
ivec2 hpos(int l) {
    int h = l >> 1;
    int tsh = u_size.x >> 1;
    return ivec2((h % tsh) * 2, h / tsh);
}
// direct half-linear index arithmetic (pass 12 reads of rbint)
ivec2 hh(int h) {
    int tsh = u_size.x >> 1;
    return ivec2((h % tsh) * 2, h / tsh);
}

float median3(float a, float b, float c) { return max(min(a, b), min(max(a, b), c)); }
float sq(float x) { return x * x; }

// column of the R/B site whose value is stored at this (even-column) texel
int site_x(ivec2 p) { return isG(p.y, p.x) ? p.x + 1 : p.x; }
bool inside(ivec2 p, int m) {
    return p.x >= m && p.y >= m && p.x < u_size.x - m && p.y < u_size.y - m;
}

const int LW = 8;   // workgroup output block (keep in sync with local_size_*)
const int LH = 8;
