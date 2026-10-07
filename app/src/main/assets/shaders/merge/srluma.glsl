
#define LAYOUT //
LAYOUT
precision highp float;
precision highp sampler2D;
precision highp image2D;
// Packed RGBA quad frame (raw sites in CFA order).
uniform highp sampler2D alterPacked;
// Packing shift (cfaShift): packed coord = (cropRaw + cfaShift) / 2.
uniform ivec2 srCfa;
// One-hot packed-channel selectors for canonical RGB, computed in Java from
// the CFA pattern.
uniform vec4 srRw;
uniform vec4 srGw;
uniform vec4 srBw;
// Per-channel white balance (merge-packed data is black-normalized only).
uniform vec3 srWhitePoint;
// Per raw site: .r = cross-channel luma (white-balanced), .g = the site's own
// raw value (for the drizzle's clip weight).
layout(rgba16f, binding = 0) writeonly uniform highp image2D srLumaOut;

float pick4(vec4 v, int ch) {
    return ch == 0 ? v.x : (ch == 1 ? v.y : (ch == 2 ? v.z : v.w));
}

// CFA channel of a raw site and the packed texel that holds it.
int siteChannel(ivec2 site) {
    return ((site.x + srCfa.x) & 1) + ((site.y + srCfa.y) & 1) * 2;
}

void main() {
    ivec2 site = ivec2(gl_GlobalInvocationID.xy);
    ivec2 outSize = imageSize(srLumaOut);
    if (site.x >= outSize.x || site.y >= outSize.y) return;
    ivec2 texMax = textureSize(alterPacked, 0) - ivec2(1);
    int ch = siteChannel(site);
    ivec2 i0 = (site + srCfa - ivec2(ch & 1, ch >> 1)) / 2;
    float own = pick4(texelFetch(alterPacked, clamp(i0, ivec2(0), texMax), 0), ch);
    float ownR = pick4(srRw, ch);
    float ownG = pick4(srGw, ch);
    float ownB = pick4(srBw, ch);
    // Same-colour neighbours of the own channel are two raw sites away, i.e.
    // the adjacent packed texels read at the same channel. Their mean is the
    // own channel's smooth (low-frequency) part, so own minus it is its
    // high-frequency deviation - the part the missing channels cannot supply.
    vec4 sOwnV = vec4(0.0);
    for (int k = 0; k < 4; k++) {
        ivec2 d = k == 0 ? ivec2(1, 0) : k == 1 ? ivec2(-1, 0)
                : k == 2 ? ivec2(0, 1) : ivec2(0, -1);
        sOwnV[k] = pick4(texelFetch(alterPacked, clamp(i0 + d, ivec2(0), texMax), 0), ch);
    }
    float ownWP = ownR > 0.5 ? srWhitePoint.r : (ownG > 0.5 ? srWhitePoint.g : srWhitePoint.b);
    // Median of the four same-colour neighbours, not their mean: at a step the
    // mean pulls across the edge, so ownCorr = own - mean overshoots at the
    // CFA phase - the beaded/ringed edge. The median ignores the one neighbour
    // on the far side and keeps the correction local. Bench: overshoot 6.1% ->
    // 2.9% and rise10-90 3.28 -> 3.11 px with the median alone (no cap needed),
    // and below-Nyquist delivery rises 0.84x -> 0.94x.
    float sOwnMed = 0.5 * (max(min(sOwnV.x, sOwnV.y), min(sOwnV.z, sOwnV.w))
            + min(max(sOwnV.x, sOwnV.y), max(sOwnV.z, sOwnV.w)));
    float ownCorr = (own - sOwnMed) / max(ownWP, 1e-4);
    // Interpolate the two missing colours from the eight neighbours (four
    // cardinals + four diagonals): at an R/B site that is four of each other
    // colour, at a G site two of each, so one uniform formula covers every
    // site class (empty counts fall back to the own sample).
    float sR = 0.0;
    float sG = 0.0;
    float sB = 0.0;
    float nR = 0.0;
    float nG = 0.0;
    float nB = 0.0;
    for (int k = 0; k < 8; k++) {
        ivec2 d = k == 0 ? ivec2(1, 0) : k == 1 ? ivec2(-1, 0)
                : k == 2 ? ivec2(0, 1) : k == 3 ? ivec2(0, -1)
                : k == 4 ? ivec2(1, 1) : k == 5 ? ivec2(1, -1)
                : k == 6 ? ivec2(-1, 1) : ivec2(-1, -1);
        ivec2 n = site + d;
        int nch = siteChannel(n);
        ivec2 ni = (n + srCfa - ivec2(nch & 1, nch >> 1)) / 2;
        float v = pick4(texelFetch(alterPacked, clamp(ni, ivec2(0), texMax), 0), nch);
        float wr = pick4(srRw, nch);
        float wg = pick4(srGw, nch);
        float wb = pick4(srBw, nch);
        sR += wr * v;
        sG += wg * v;
        sB += wb * v;
        nR += wr;
        nG += wg;
        nB += wb;
    }
    // The site's own channel is sampled, so it keeps its own value; the other
    // two take their neighbour averages plus the own channel's high-frequency
    // deviation. Luma detail is common to all channels and chroma is smooth,
    // so that deviation is the missing channels' high-frequency content: the
    // plain +-1 neighbour average low-passes it (at 0.4 c/px the neighbours
    // are out of phase and the average cancels the very band the drizzle
    // exists to fuse), leaving a CFA-phase texture mosaic. One weighted luma
    // at every site - a green-only or class-inconsistent form leaves a
    // CFA-phase mosaic in the fused field.
    float balR = (ownR > 0.5 ? own : (nR > 0.5 ? sR / nR : own)) / max(srWhitePoint.r, 1e-4)
            + (ownR > 0.5 ? 0.0 : ownCorr);
    float balG = (ownG > 0.5 ? own : (nG > 0.5 ? sG / nG : own)) / max(srWhitePoint.g, 1e-4)
            + (ownG > 0.5 ? 0.0 : ownCorr);
    float balB = (ownB > 0.5 ? own : (nB > 0.5 ? sB / nB : own)) / max(srWhitePoint.b, 1e-4)
            + (ownB > 0.5 ? 0.0 : ownCorr);
    float y = 0.2126 * balR + 0.7152 * balG + 0.0722 * balB;
    imageStore(srLumaOut, site, vec4(y, own, 0.0, 0.0));
}
