// Inpaint opposed - pass 3: chrominance accumulation.
// One workgroup per image row (local size 64): photosites whose channel is
// flagged by the dilated mask and whose value sits inside (0.2*clip, clip)
// contribute (value - refavg) to their channel's sum. Per-invocation partial
// sums land in shared memory, invocation 0 reduces the row and writes six
// ints: three Q16.16 sums + three counts. The CPU adds the rows and applies
// the minimum-sample rule - no float atomics needed anywhere.
precision highp float;
precision highp int;
precision highp usampler2D;
uniform usampler2D u_raw;
uniform ivec2 u_size;
uniform ivec2 u_msize;
uniform float u_whitelevel;
uniform vec3 u_level;
uniform vec3 u_whitepoint;
uniform vec3 u_loclip;
uniform float u_clip;
uniform int CfaPattern;
#define QUAD 0
#define RGBLAYOUT 0
#define LAYOUT //
LAYOUT
layout(std430, binding = 1) buffer Mask {
    uint mask[];
};
layout(std430, binding = 2) buffer RowSums {
    int rows[];
};

int hlFcol(ivec2 p) {
    ivec2 ph = ivec2(CfaPattern % 2, CfaPattern / 2);
    ivec2 f = (QUAD == 1) ? (((p - ph * 2) / 2) & 1) : ((p - ph) & 1);
    return (f.x + f.y == 1) ? 1 : (f.x == 0 ? 0 : 2);
}

// normalized white-balanced value of a raw sample - mirrors hlNorm() in tofloat.glsl
float hlNorm(uint rv, int c) {
    return max(0.0, (float(rv) / u_whitelevel - u_level[c]) / (1.0 - u_level[c]) / u_whitepoint[c]);
}

float opp3(float a, float b) {
    float m = 0.5 * (a + b);
    return m * m * m;
}

// opposed-colour estimate for channel c from the 3x3 photosite neighbourhood
// centred on p, averaged in cube-root space - mirrors hlRefavg() in tofloat.glsl
float hlRefavg(ivec2 p, int c) {
    ivec2 lo = max(p - ivec2(1), ivec2(0));
    ivec2 hi = min(p + ivec2(1), u_size - ivec2(1));
    float s0 = 0.0, s1 = 0.0, s2 = 0.0;
    float n0 = 0.0, n1 = 0.0, n2 = 0.0;
    for (int dy = lo.y; dy <= hi.y; dy++) {
        for (int dx = lo.x; dx <= hi.x; dx++) {
            int cc = hlFcol(ivec2(dx, dy));
            float v = hlNorm(texelFetch(u_raw, ivec2(dx, dy), 0).r, cc);
            if (cc == 0) { s0 += v; n0 += 1.0; }
            else if (cc == 1) { s1 += v; n1 += 1.0; }
            else { s2 += v; n2 += 1.0; }
        }
    }
    float m0 = n0 > 0.0 ? pow(s0 / n0, 1.0 / 3.0) : 0.0;
    float m1 = n1 > 0.0 ? pow(s1 / n1, 1.0 / 3.0) : 0.0;
    float m2 = n2 > 0.0 ? pow(s2 / n2, 1.0 / 3.0) : 0.0;
    return c == 0 ? opp3(m1, m2) : (c == 1 ? opp3(m0, m2) : opp3(m0, m1));
}

// must match setLayout(64, 1, 1) in OpposedGL
shared float shSum[192];
shared int shCnt[192];

void main() {
    int y = int(gl_WorkGroupID.y);
    if (y >= u_size.y) return;
    int li = int(gl_LocalInvocationIndex);
    int cellRow = min(y / 3, u_msize.y - 1) * u_msize.x;
    float p0 = 0.0, p1 = 0.0, p2 = 0.0;
    int c0 = 0, c1 = 0, c2 = 0;
    for (int x = li; x < u_size.x; x += 64) {
        int cell = cellRow + min(x / 3, u_msize.x - 1);
        uint bits = mask[cell];
        if (bits == 0u) continue;
        #if RGBLAYOUT == 1
        uvec3 rv = texelFetch(u_raw, ivec2(x, y), 0).rgb;
        vec3 u = max((vec3(rv) / u_whitelevel - u_level) / (vec3(1.0) - u_level), vec3(0.0));
        vec3 roots = pow(u, vec3(1.0 / 3.0));
        if ((bits & 1u) != 0u && u.r > u_loclip.r && u.r < u_clip) { p0 += u.r - opp3(roots.g, roots.b); c0++; }
        if ((bits & 2u) != 0u && u.g > u_loclip.g && u.g < u_clip) { p1 += u.g - opp3(roots.r, roots.b); c1++; }
        if ((bits & 4u) != 0u && u.b > u_loclip.b && u.b < u_clip) { p2 += u.b - opp3(roots.r, roots.g); c2++; }
        #else
        int c = hlFcol(ivec2(x, y));
        if ((bits & (1u << c)) == 0u) continue;
        float v = hlNorm(texelFetch(u_raw, ivec2(x, y), 0).r, c);
        if (v <= u_loclip[c] || v >= u_clip) continue;
        float ref = hlRefavg(ivec2(x, y), c);
        if (c == 0) { p0 += v - ref; c0++; }
        else if (c == 1) { p1 += v - ref; c1++; }
        else { p2 += v - ref; c2++; }
        #endif
    }
    shSum[li * 3 + 0] = p0;
    shSum[li * 3 + 1] = p1;
    shSum[li * 3 + 2] = p2;
    shCnt[li * 3 + 0] = c0;
    shCnt[li * 3 + 1] = c1;
    shCnt[li * 3 + 2] = c2;
    barrier();
    if (li == 0) {
        float s0 = 0.0, s1 = 0.0, s2 = 0.0;
        int n0 = 0, n1 = 0, n2 = 0;
        for (int k = 0; k < 64; k++) {
            s0 += shSum[k * 3 + 0];
            s1 += shSum[k * 3 + 1];
            s2 += shSum[k * 3 + 2];
            n0 += shCnt[k * 3 + 0];
            n1 += shCnt[k * 3 + 1];
            n2 += shCnt[k * 3 + 2];
        }
        rows[y * 6 + 0] = int(round(s0 * 65536.0));
        rows[y * 6 + 1] = int(round(s1 * 65536.0));
        rows[y * 6 + 2] = int(round(s2 * 65536.0));
        rows[y * 6 + 3] = n0;
        rows[y * 6 + 4] = n1;
        rows[y * 6 + 5] = n2;
    }
}
