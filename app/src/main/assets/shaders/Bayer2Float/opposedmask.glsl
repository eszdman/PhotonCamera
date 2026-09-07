// Inpaint opposed - pass 1: clipped-cell mask.
// One workgroup invocation per 3x3-photosite cell: scans the cell's pixels,
// marks per-channel clip bits (a channel counts if any of its photosites in
// the cell is clipped) and counts clipped cells. The CPU reads the counter
// to decide whether the reconstruction engages at all.
precision highp float;
precision highp int;
precision highp usampler2D;
uniform usampler2D u_raw;
uniform ivec2 u_size;     // image size in pixels
uniform ivec2 u_msize;    // mask grid size in cells
uniform vec3 u_level;     // normalized per-channel black level (greens averaged)
uniform vec3 u_clipthr;   // raw-domain clip thresholds (counts)
uniform int CfaPattern;
#define QUAD 0
#define RGBLAYOUT 0
#define LAYOUT //
LAYOUT
layout(std430, binding = 1) buffer MaskOut {
    uint mask[];
};
layout(std430, binding = 2) buffer ClipCount {
    uint count[];
};

// channel (0=R,1=G,2=B) of the raw photosite p - same anchoring as tofloat.glsl
int hlFcol(ivec2 p) {
    ivec2 ph = ivec2(CfaPattern % 2, CfaPattern / 2);
    ivec2 f = (QUAD == 1) ? (((p - ph * 2) / 2) & 1) : ((p - ph) & 1);
    return (f.x + f.y == 1) ? 1 : (f.x == 0 ? 0 : 2);
}

void main() {
    ivec2 cell = ivec2(gl_GlobalInvocationID.xy);
    if (cell.x >= u_msize.x || cell.y >= u_msize.y) return;
    uint bits = 0u;
    for (int dy = 0; dy < 3; dy++) {
        for (int dx = 0; dx < 3; dx++) {
            ivec2 p = cell * 3 + ivec2(dx, dy);
            if (p.x >= u_size.x || p.y >= u_size.y) continue;
            #if RGBLAYOUT == 1
            uvec3 v = texelFetch(u_raw, p, 0).rgb;
            if (float(v.r) >= u_clipthr.r) bits |= 1u;
            if (float(v.g) >= u_clipthr.g) bits |= 2u;
            if (float(v.b) >= u_clipthr.b) bits |= 4u;
            #else
            int c = hlFcol(p);
            if (float(texelFetch(u_raw, p, 0).r) >= u_clipthr[c]) bits |= (1u << c);
            #endif
        }
    }
    mask[cell.y * u_msize.x + cell.x] = bits;
    if (bits != 0u) atomicAdd(count[0], 1u);
}
