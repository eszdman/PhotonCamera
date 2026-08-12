precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
// When > 0 the shader outputs the 8-bit quantised gain map directly,
// otherwise it outputs the raw log2 gain.
uniform float GAIN_SCALE;
// Log2 of the minimum content boost: the gain value quantised to 0.
uniform float GAIN_MIN;
uniform float GAIN_CLAMP_MIN;
uniform float GAIN_CLAMP_MAX;
uniform float GAIN_OFFSET;
uniform float GAIN_DEADBAND;
uniform int BLOCK_OFFSET;
uniform int ROTATE;
uniform int MIRROR;
uniform ivec2 RAW_SIZE;
uniform int CROP_H;
out vec4 Output;

// Computes the Ultra HDR gain map from the intermediate pipeline texture.
// The alpha channel holds the pre-tonemap linear HDR luminance written by
// Initial. The gain map is the log2 ratio between that linear HDR luminance
// and the displayed SDR base linearized with the standard sRGB transfer.
// Positive gains recover compressed highlights. Shadows and neutral regions
// remain exactly the SDR base so texture and noise cannot become dark spots.
float srgbToLinear(float value) {
    value = max(value, 0.0);
    return value <= 0.04045
            ? value / 12.92
            : pow((value + 0.055) / 1.055, 2.4);
}
ivec2 mapOutputToSource(ivec2 outCoord, ivec2 texSize) {
    ivec2 src;
    switch (ROTATE) {
        case 0:
            src = ivec2(outCoord.x, outCoord.y + texSize.y - CROP_H);
            if (MIRROR == 1) src.y = texSize.y - 1 - src.y;
            break;
        case 1:
            src = ivec2(texSize.x - 1 - outCoord.y,
                    outCoord.x + texSize.y - CROP_H);
            if (MIRROR == 1) src.y = texSize.y - 1 - src.y;
            break;
        case 2:
            src = ivec2(texSize.x - 1 - outCoord.x,
                    texSize.y - 1 - outCoord.y);
            if (MIRROR == 1) src.y = outCoord.y;
            break;
        default:
            src = ivec2(outCoord.y, texSize.y - 1 - outCoord.x);
            if (MIRROR == 1) src.y = outCoord.x;
            break;
    }
    return src;
}

void main() {
    ivec2 outCoord = ivec2(gl_FragCoord.xy) + ivec2(0, BLOCK_OFFSET);
    ivec2 texSize = RAW_SIZE;
    ivec2 src = mapOutputToSource(outCoord, texSize);
    if (src.x < 0 || src.x >= texSize.x || src.y < 0 || src.y >= texSize.y) {
        Output = vec4(0.0);
        return;
    }
    vec4 t = texelFetch(InputBuffer, src, 0);
    float sdrLin = dot(vec3(srgbToLinear(t.r), srgbToLinear(t.g),
            srgbToLinear(t.b)), vec3(0.299, 0.587, 0.114));
    float hdrLum = max(t.a, 0.0);
    float L = log2((hdrLum + GAIN_OFFSET) / (sdrLin + GAIN_OFFSET));
    if (sdrLin < 0.007843137) {
        L = min(L, 2.3);
    }
    L = clamp(L, GAIN_CLAMP_MIN, GAIN_CLAMP_MAX);
    if (L < GAIN_DEADBAND) {
        L = 0.0;
    }
    if (GAIN_SCALE > 0.0) {
        Output = vec4(clamp((L - GAIN_MIN) * GAIN_SCALE, 0.0, 255.0), 0.0, 0.0, 0.0);
    } else {
        Output = vec4(L, 0.0, 0.0, 0.0);
    }
}
