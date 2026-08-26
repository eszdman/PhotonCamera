precision highp float;
precision highp sampler2D;

// SDR reduction for the adaptive Ultra HDR gain-map path.
//
// This shader must remain geometrically identical to sceneluma.glsl:
// each output texel covers the same full-resolution stored-SDR rectangle.
// It averages in linear light, then converts the result back to sRGB because
// gainmap.glsl expects InputBuffer to contain display-encoded sRGB.

uniform sampler2D InputBuffer;
uniform ivec2 uGridSize;

out vec4 Output;

float srgbToLinear(float c) {
    c = clamp(c, 0.0, 1.0);
    if (c <= 0.04045) return c / 12.92;
    return pow((c + 0.055) / 1.055, 2.4);
}

vec3 srgbToLinear(vec3 c) {
    return vec3(srgbToLinear(c.r), srgbToLinear(c.g), srgbToLinear(c.b));
}

float linearToSrgb(float c) {
    c = max(c, 0.0);
    if (c <= 0.0031308) return c * 12.92;
    return 1.055 * pow(c, 1.0 / 2.4) - 0.055;
}

vec3 linearToSrgb(vec3 c) {
    return vec3(linearToSrgb(c.r), linearToSrgb(c.g), linearToSrgb(c.b));
}

void main() {
    ivec2 outXY = ivec2(gl_FragCoord.xy);
    ivec2 fullSize = ivec2(textureSize(InputBuffer, 0));

    // Keep this calculation exactly in sync with sceneluma.glsl.
    ivec2 begin = ivec2(
            vec2(outXY) * vec2(fullSize) / vec2(uGridSize));
    ivec2 end = ivec2(
            vec2(outXY + ivec2(1)) * vec2(fullSize) / vec2(uGridSize));

    begin = clamp(begin, ivec2(0), fullSize - ivec2(1));
    end = clamp(max(end, begin + ivec2(1)), begin + ivec2(1), fullSize);

    vec3 sum = vec3(0.0);
    int count = 0;

    for (int y = begin.y; y < end.y; y++) {
        for (int x = begin.x; x < end.x; x++) {
            sum += srgbToLinear(texelFetch(InputBuffer, ivec2(x, y), 0).rgb);
            count++;
        }
    }

    vec3 averageLinear = sum / float(count);
    Output = vec4(linearToSrgb(averageLinear), 1.0);
}
