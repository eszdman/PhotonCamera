precision highp float;
precision highp sampler2D;

// Ultra HDR scene-luma plane: linear scene luminance sampled from the captured
// post-demosaic buffer, with the exact same mirror / rotate / crop transform
// the stored SDR base went through (coordinate logic mirrors
// addwatermark_rotate.glsl) so the plane is pixel-aligned with the base image.
//
// This is the pre-local-tone-map scene energy: fusion LTM compresses
// highlights below display white inside the rendering chain, so the
// recoverable highlight headroom only exists here. No clamp - scene luminance
// may legitimately reach/exceed 1.0.

uniform sampler2D InputBuffer;
uniform sampler2D GainMap;
uniform int rotate;
uniform bool mirror;
uniform ivec2 cropSize;
uniform ivec2 rawSize;
// Geometry of the final stored SDR plane and of the scene-luma grid.
// A scene-luma output texel averages its entire corresponding SDR-output
// region.
uniform ivec2 uLinFullSize;
uniform ivec2 uLinGridSize;
// Banded execution (see RunHDRGainMap): the input may be a band tile holding
// absolute image rows/columns at uInOrigin (not the frame origin). Fetches
// subtract the origin; mirror/transpose branches key off the FULL input size
// uInFull (what textureSize would report on a full input, but misreports on
// a tile). Full-input renders pass origin (0,0) and the frame size, which is
// exactly the old behavior.
uniform ivec2 uInOrigin;
uniform ivec2 uInFull;
// Display color transform shared with Initial/ModernInitial/HeadroomRender.
// P3 mode uses these to measure Yhdr in the same primaries as the stored SDR
// base; sRGB mode keeps the legacy luminance path untouched.
uniform mat3 sensorToIntermediate;
uniform mat3 intermediateToDisplay;
uniform vec3 neutralPoint;
uniform mat3 CUBE0;
uniform mat3 CUBE1;
uniform mat3 CUBE2;

out vec4 Output;

#define lum709(x) dot(x, vec3(0.2126, 0.7152, 0.0722))
#define lumP3(x) dot(x, vec3(0.2289746, 0.6917385, 0.0792869))
#define P3 0
#define CCT 0
#import interpolation

#if P3 == 1
vec3 displayLinear(vec3 c) {
    vec3 wb = max(c, vec3(0.0)) * neutralPoint;
    #if CCT == 1
    float br0 = (c.r + c.g + c.b)
            / (neutralPoint.r + neutralPoint.g + neutralPoint.b);
    mat3 corr;
    if (br0 > 0.5) {
        corr = CUBE1 * (1.0 - (br0 - 0.5) * 2.0)
                + CUBE2 * ((br0 - 0.5) * 2.0);
    } else {
        corr = CUBE0 * (1.0 - (br0 - 0.25) * 4.0)
                + CUBE1 * ((br0 - 0.25) * 4.0);
    }
    return corr * sensorToIntermediate * wb;
    #else
    return intermediateToDisplay * sensorToIntermediate * wb;
    #endif
}
#endif

float sceneLumaAt(ivec2 xy, ivec2 texSize) {
    ivec2 srcCoord;
    vec3 c;
    switch (rotate) {
        case 0:
            xy += ivec2(0, (rawSize.y - cropSize.y));
            if (mirror)
                xy.y = texSize.y - xy.y;
            srcCoord = xy;
            c = texelFetch(InputBuffer, srcCoord - uInOrigin, 0).rgb;
            break;
        case 1:
            xy += ivec2((rawSize.y - cropSize.y), 0);
            if (mirror)
                xy.x = cropSize.y - xy.x;
            srcCoord = ivec2(texSize.x - xy.y, xy.x);
            c = texelFetch(InputBuffer, srcCoord - uInOrigin, 0).rgb;
            break;
        case 2:
            if (mirror)
                xy.y = texSize.y - xy.y;
            srcCoord = ivec2(texSize.x - xy.x, texSize.y - xy.y);
            c = texelFetch(InputBuffer, srcCoord - uInOrigin, 0).rgb;
            break;
        case 3:
            if (mirror)
                xy.x = cropSize.y - xy.x;
            srcCoord = ivec2(xy.y, texSize.y - xy.x);
            c = texelFetch(InputBuffer, srcCoord - uInOrigin, 0).rgb;
            break;
        default:
            srcCoord = min(xy, texSize - 1);
            c = texelFetch(InputBuffer, srcCoord - uInOrigin, 0).rgb;
            break;
    }
    // Lens-shading vignetting (must match Initial's luminance correction):
    // tofloat.glsl normalises its GainMap fetch to avg 1 (chromatic only), so
    // the archived post-demosaic buffer still carries the achromatic falloff.
    // Initial restores it via gainsVal = dot(avg_gains) on the SDR path; we
    // must do the same here so L and SDR are on the same flat field before
    // the gainmap ratio and median anchoring.
    vec4 gains = textureBicubicHardware(GainMap, vec2(srcCoord) / vec2(rawSize));
    gains.rgb = vec3(gains.r, (gains.g + gains.b) / 2.0, gains.a);
    float gainsVal = dot(gains.rgb, vec3(1.0 / 3.0));
    #if P3 == 1
    // Yhdr must be luminance in the primary image's primaries (ISO 21496-1).
    // Apply the renderer's white balance + camera->display transform, but no
    // tone mapping: the gain map recovers the pre-tone-map scene.
    return lumP3(max(displayLinear(c), vec3(0.0))) * gainsVal;
    #else
    return lum709(max(c, vec3(0.0))) * gainsVal;
    #endif
}

void main() {
    ivec2 outXY = ivec2(gl_FragCoord.xy);

    // Integer boundaries partition [0, uLinFullSize) exactly over the final
    // grid. This handles dimensions not divisible by down without dropping
    // or stretching the final right/bottom source regions.
    ivec2 begin = ivec2(
            vec2(outXY) * vec2(uLinFullSize) / vec2(uLinGridSize));
    ivec2 end = ivec2(
            vec2(outXY + ivec2(1)) * vec2(uLinFullSize) / vec2(uLinGridSize));

    begin = clamp(begin, ivec2(0), uLinFullSize - ivec2(1));
    end = clamp(max(end, begin + ivec2(1)), begin + ivec2(1), uLinFullSize);

    ivec2 texSize = uInFull;
    float sum = 0.0;
    int count = 0;

    for (int y = begin.y; y < end.y; y++) {
        for (int x = begin.x; x < end.x; x++) {
            sum += sceneLumaAt(ivec2(x, y), texSize);
            count++;
        }
    }

    float l = sum / float(count);
    Output = vec4(l, l, l, 1.0);
}
