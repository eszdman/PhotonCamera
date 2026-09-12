precision highp float;
precision highp sampler2D;

// Ultra HDR gain-map comparison shader (final stage of the scene-anchored
// pass).
//
// InputBuffer: stored SDR base rendition (display-encoded sRGB, RGBA8)
// LBuffer:     scene-luma plane - linear luminance of the pre-local-tone-map
//              scene buffer (ultrahdr/sceneluma.glsl), pixel-aligned with the
//              base image. Fusion LTM compresses clipped highlights below
//              display white inside the rendering chain, so this plane is the
//              only place the recoverable highlight headroom still exists.
//
// The anchor uAnchor = medianLinearSDR / medianL declares the midtones of the
// scene equal to the midtones of the rendering (matched medians). Because the
// SDR signal saturates while the scene plane keeps climbing, the ratio grows
// monotonically toward clipped highlights: pixels at the midtone anchor get
// zero gain, brighter scene content gets progressively more, and everything
// below is floored to identity by the clamp.

uniform sampler2D InputBuffer;
uniform sampler2D LBuffer;
// Scene-to-render anchor: linear median of the base divided by the linear
// median of the scene plane (midtone alignment).
uniform float uAnchor;
// Downsample factor per axis (e.g. 4 -> gain map is 1/16 the pixels)
uniform int uDown;
// Total log2 range the gain map covers: logBoost in [0, uScale]
uniform float uScale;
// OffsetSDR/OffsetHDR from the hdrgm XMP metadata (1/64)
uniform float uEps;
// Banded draws (see RunHDRGainMap): InputBuffer may be a band tile holding
// image rows [origin, origin+rows) while LBuffer stays full-frame. Output and
// LBuffer stay absolute (viewport-offset convention); only the SDR fetch
// subtracts the origin (same pattern as sceneluma's uInOrigin).
uniform ivec2 uInOrigin;
// Output-row offset for the full-frame LBuffer fetch: band draws render
// output rows [yOffset, yOffset+rows) into a band-sized target, so LBuffer
// (full grid) needs the offset while the tile input does not.
uniform int yOffset;

out vec4 Output;

#define GAINSHADOWFLOOR_LO 0.03
#define GAINSHADOWFLOOR_HI 0.50
#define lum709(x) dot(x, vec3(0.2126, 0.7152, 0.0722))

float srgbToLinear(float c) {
    c = clamp(c, 0.0, 1.0);
    if (c <= 0.04045) return c / 12.92;
    return pow((c + 0.055) / 1.055, 2.4);
}
vec3 srgbToLinear(vec3 c) {
    return vec3(srgbToLinear(c.r), srgbToLinear(c.g), srgbToLinear(c.b));
}

void main() {
    ivec2 outXY = ivec2(gl_FragCoord.xy);
    int sx = outXY.x * uDown;
    int sy = outXY.y * uDown;

    float sdrSum = 0.0;
    float hdrSum = 0.0;
    int count = 0;

    for (int dy = 0; dy < uDown; dy++) {
        for (int dx = 0; dx < uDown; dx++) {
            ivec2 p = ivec2(sx + dx, sy + dy);
            vec3 s = texelFetch(InputBuffer, p - uInOrigin, 0).rgb;
            // Scene plane is scalar gray (vec4(l,l,l,1)) whether stored RGBA16F
            // or single-channel R16F: .r is exactly lum709 either way.
            // Band draws address output rows [yOffset, yOffset+rows) in the
            // full grid while rendering into a band target.
            float hL = max(texelFetch(LBuffer, ivec2(p.x, p.y + yOffset), 0).r, 0.0) * uAnchor;

            // SDR luminance in linear light.
            float sL = lum709(srgbToLinear(s));
            // Scene luminance is already linear; anchored to the render's top.

            // Decode applies (SDR + OffsetSDR) * 2^gain - OffsetHDR, so the
            // encode-side ratio must add the offset, not clamp to it.
            sdrSum += sL + uEps;
            hdrSum += hL + uEps;
            count++;
        }
    }

    float sdrL = sdrSum / float(count);
    float hdrL = hdrSum / float(count);

    // Scene above the anchor with a saturated SDR signal yields positive
    // boost; anything below is clamped to identity so shadows/midtones are
    // never darkened.
    float logBoost = log2(hdrL / sdrL);

    // Fade the boost in with SDR brightness: deep shadows stay identity
    // (their quotients encode tone-curve toe and local-tonemap darkening,
    // not real headroom), midtones keep partial gain, highlights get full
    // gain. Sole tuning constants in this pass.
    logBoost *= smoothstep(GAINSHADOWFLOOR_LO, GAINSHADOWFLOOR_HI, sdrL - uEps);

    // Map into [0,1] across the fixed log2 range [0, uScale], then store as 8-bit.
    float v = clamp(logBoost / uScale, 0.0, 1.0);
    int byteVal = int(v * 255.0 + 0.5);

    // Single-channel gain map, packed as R=G=B=value, A=255 (ARGB_8888).
    float chan = float(byteVal) / 255.0;
    Output = vec4(chan, chan, chan, 1.0);
}
