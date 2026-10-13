precision highp float;
precision highp sampler2D;
precision highp image2D;
uniform highp sampler2D InputBuffer;
uniform vec3 blackLevel;
out vec3 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec3 color = texelFetch(InputBuffer, xy, 0).rgb;
    // Tint-only correction: subtract each channel's deviation from the mean
    // black level instead of the full per-channel level (and no per-channel
    // gain rescale). The mean offset - brightness - is preserved, so blacks
    // turn neutral grey at the same luminance instead of getting darker;
    // neutral blacks also survive JPEG chroma subsampling far better.
    float meanLevel = (blackLevel.r + blackLevel.g + blackLevel.b) * (1.0 / 3.0);
    Output = max(color - (blackLevel - vec3(meanLevel)), vec3(0.0));
}
