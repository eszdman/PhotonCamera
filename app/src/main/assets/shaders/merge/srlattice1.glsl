
#define LAYOUT //
LAYOUT
precision highp float;
precision highp image2D;
uniform highp usampler2D srDepV;
uniform highp usampler2D srDepW;
// Output: the fused drizzle at the OUTPUT grid, single-channel luma only
// (R16F). The post composes it with the demosaiced crop's chroma and
// reconstructs at zoom 1, so above-raw-Nyquist content is represented instead
// of folded by a raw-grid reduce. Half the memory of the RGBA16F lattice.
layout(r32f, binding = 0) writeonly uniform highp image2D fusedOut;
// CFA comb offset in accumulator texels (2 raw px).
uniform vec2 srCombOff;
#define SR_FIXED (1.0 / 65536.0)
// CFA comb weight: see srlattice.glsl. Dropped on the output-grid drizzle:
// the comb was validated on the RAW-GRID lattice (the beads are already in
// that fused luma), but the drizzle fuses sub-pixel-diverse samples across
// CFA classes, which already averages the phase (bench: the 0.25 cyc/out
// excess drops 33-42% from 1 to 9 frames), while the comb costs ~27% of the
// delivered above-raw-Nyquist band (0.52 -> 0.71 without it). The raw-grid
// fallback keeps 0.4.
#ifndef SR_CFA_COMB
#define SR_CFA_COMB 0.0
#endif

vec3 srGather(vec2 c, ivec2 cmax, int n) {
    float lumaSum = 0.0;
    float wSum = 0.0;
    float bw2 = 0.0;
    if (n >= 2 && n <= 6) {
        vec2 sc = vec2(n);
        ivec2 b0 = ivec2(floor(c - (sc - vec2(1.0)) * 0.5 + vec2(0.5)));
        for (int j = 0; j < 6; j++) {
            if (j >= n) break;
            for (int i = 0; i < 6; i++) {
                if (i >= n) break;
                ivec2 t = clamp(b0 + ivec2(i, j), ivec2(0, 0), cmax);
                lumaSum += float(texelFetch(srDepV, t, 0).x) * SR_FIXED;
                wSum += float(texelFetch(srDepW, t, 0).x) * SR_FIXED;
            }
        }
        bw2 = float(n * n);
    } else {
        ivec2 c0 = ivec2(floor(c));
        vec2 f = c - vec2(c0);
        for (int j = 0; j <= 1; j++) {
            for (int i = 0; i <= 1; i++) {
                float bw = (i == 0 ? 1.0 - f.x : f.x) * (j == 0 ? 1.0 - f.y : f.y);
                ivec2 t = clamp(c0 + ivec2(i, j), ivec2(0, 0), cmax);
                lumaSum += bw * float(texelFetch(srDepV, t, 0).x) * SR_FIXED;
                wSum += bw * float(texelFetch(srDepW, t, 0).x) * SR_FIXED;
                bw2 += bw * bw;
            }
        }
    }
    return vec3(lumaSum, wSum, bw2);
}

float srLuma(vec2 c, ivec2 cmax, int n) {
    vec3 g = srGather(c, cmax, n);
    return g.y > 1e-6 ? g.x / g.y : 0.0;
}

void main() {
    ivec2 site = ivec2(gl_GlobalInvocationID.xy);
    ivec2 grid = imageSize(fusedOut);
    if (site.x >= grid.x || site.y >= grid.y) return;
    ivec2 accSize = textureSize(srDepV, 0);
    ivec2 cmax = accSize - ivec2(1);
    vec2 scale = vec2(accSize) / vec2(grid);
    vec2 c = (vec2(site) + vec2(0.5)) * scale - vec2(0.5);
    int n = int(floor(scale.x + 0.5));
    float luma = srLuma(c, cmax, n);
    if (SR_CFA_COMB > 0.0 && srCombOff.x > 0.5) {
        vec2 d = srCombOff;
        // Coverage-weighted comb: srGather returns (sum(v*w), sum(w)); the
        // weighted mean excludes low/zero-weight neighbours instead of
        // dragging the centre toward hole zeros (the dark-dot lattice), and
        // the mix amount fades where the neighbours carry less coverage than
        // the centre, so thin areas keep their own value.
        vec3 gA = srGather(c - vec2(d.x, 0.0), cmax, n);
        vec3 gB = srGather(c + vec2(d.x, 0.0), cmax, n);
        vec3 gC = srGather(c - vec2(0.0, d.y), cmax, n);
        vec3 gD = srGather(c + vec2(0.0, d.y), cmax, n);
        float wsum = gA.y + gB.y + gC.y + gD.y;
        float comb = wsum > 1e-6 ? (gA.x + gB.x + gC.x + gD.x) / wsum : luma;
        vec3 gc = srGather(c, cmax, n);
        float cov = clamp(wsum / (4.0 * max(gc.y, 1e-6)), 0.0, 1.0);
        luma = mix(luma, comb, clamp(SR_CFA_COMB, 0.0, 1.0) * cov);
    }
    if (!(luma <= 65504.0)) luma = 0.0;
    imageStore(fusedOut, site, vec4(luma, 0.0, 0.0, 1.0));
}
