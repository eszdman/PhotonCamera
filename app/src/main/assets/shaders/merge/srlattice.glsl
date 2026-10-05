
#define LAYOUT //
LAYOUT
precision highp float;
precision highp image2D;
uniform highp usampler2D srDepV;
uniform highp usampler2D srDepW;
// Output: the fused lattice at the crop's raw grid - or, when the post
// reconstructs at the output grid, the drizzle at the target grid - with
// .r = fused luma (pre-ABLC packed domain), .g = effective accumulated
// weight. The pre-aniso injection differences this against the demosaiced
// image; the effective weight drives its noise gate.
layout(rgba16f, binding = 0) writeonly uniform highp image2D fusedOut;
// CFA comb offset in accumulator texels: 2 raw px (= 2*expansion). Tied to the
// raw CFA period, so it is passed rather than derived from the grid.
uniform vec2 srCombOff;
#define SR_FIXED (1.0 / 65536.0)
// CFA comb: the fused luma carries the raw CFA phase (the site's own colour
// value dominates), which the SR renders as dotted/beaded highlight edges.
// The CFA period is 2 raw px, i.e. 0.25 cyc/raw; blending with the same-colour
// sites two raw px away cancels it. 0 = off (old behaviour).
#ifndef SR_CFA_COMB
#define SR_CFA_COMB 0.4
#endif

// Box/bilinear gather of (lumaSum, wSum, bw2) at accumulator coord c.
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
    // This site's center in accumulator texel-index space: the grid covers
    // the same crop the accumulator does, scaled by the expansion.
    vec2 scale = vec2(accSize) / vec2(grid);
    vec2 c = (vec2(site) + vec2(0.5)) * scale - vec2(0.5);
    int n = int(floor(scale.x + 0.5));
    // Anti-aliased area reduce (integer expansion): average the n x n output
    // texels covering this raw pixel. `c` is an integer for integer expansion,
    // so a bilinear gather would collapse to a point sample and fold the
    // above-Nyquist drizzle content into the below-Nyquist band.
    vec3 g = srGather(c, cmax, n);
    float wSum = g.y;
    float bw2 = g.z;
    float luma = wSum > 1e-6 ? g.x / wSum : 0.0;
    if (SR_CFA_COMB > 0.0 && srCombOff.x > 0.5) {
        vec2 d = srCombOff;
        float comb = 0.25 * (srLuma(c - vec2(d.x, 0.0), cmax, n)
                + srLuma(c + vec2(d.x, 0.0), cmax, n)
                + srLuma(c - vec2(0.0, d.y), cmax, n)
                + srLuma(c + vec2(0.0, d.y), cmax, n));
        luma = mix(luma, comb, clamp(SR_CFA_COMB, 0.0, 1.0));
    }
    // NaN/Inf guard (the shared handoff skips any CPU scan).
    if (!(luma <= 65504.0)) luma = 0.0;
    // Effective sample count: the gather averages ~1/sum(bw^2) output samples,
    // so the injection's noise threshold tracks the lattice's actual,
    // phase-dependent noise instead of one output texel's.
    float wEff = wSum > 1e-6 ? wSum / max(bw2, 1e-6) : 0.0;
    imageStore(fusedOut, site, vec4(luma, wEff, 0.0, 1.0));
}
