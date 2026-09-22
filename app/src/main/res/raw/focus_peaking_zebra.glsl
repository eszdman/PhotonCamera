#version 300 es
precision mediump float;

in vec2 vTextureCoord;
out vec4 fragColor;

uniform sampler2D uTexture;
uniform vec2 uTexelSize;
uniform bool uFocusPeakingEnabled;
uniform bool uZebraEnabled;

void main() {
    vec4 color = texture(uTexture, vTextureCoord);
    vec3 finalRgb = color.rgb;

    // 1. Focus Peaking (Sobel filter for edge detection)
    if (uFocusPeakingEnabled) {
        vec3 tTopLeft     = texture(uTexture, vTextureCoord + vec2(-uTexelSize.x,  uTexelSize.y)).rgb;
        vec3 tTop         = texture(uTexture, vTextureCoord + vec2( 0.0,           uTexelSize.y)).rgb;
        vec3 tTopRight    = texture(uTexture, vTextureCoord + vec2( uTexelSize.x,  uTexelSize.y)).rgb;
        vec3 tLeft        = texture(uTexture, vTextureCoord + vec2(-uTexelSize.x,  0.0)).rgb;
        vec3 tRight       = texture(uTexture, vTextureCoord + vec2( uTexelSize.x,  0.0)).rgb;
        vec3 tBottomLeft  = texture(uTexture, vTextureCoord + vec2(-uTexelSize.x, -uTexelSize.y)).rgb;
        vec3 tBottom      = texture(uTexture, vTextureCoord + vec2( 0.0,          -uTexelSize.y)).rgb;
        vec3 tBottomRight = texture(uTexture, vTextureCoord + vec2( uTexelSize.x, -uTexelSize.y)).rgb;

        vec3 gx = (tTopRight + 2.0 * tRight + tBottomRight) - (tTopLeft + 2.0 * tLeft + tBottomLeft);
        vec3 gy = (tBottomLeft + 2.0 * tBottom + tBottomRight) - (tTopLeft + 2.0 * tTop + tTopRight);

        float edgeMagnitude = length(gx) + length(gy);
        if (edgeMagnitude > 0.45) {
            finalRgb = vec3(0.0, 1.0, 0.0); // Bright green focus peaking outline
        }
    }

    // 2. Zebra Highlight Clipping (Diagonal stripes over exposed areas > 0.95 luminance)
    if (uZebraEnabled) {
        float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        if (luma > 0.95) {
            float stripe = sin((gl_FragCoord.x + gl_FragCoord.y) * 0.25);
            if (stripe > 0.0) {
                finalRgb = vec3(0.0); // Draw black zebra stripes over highlights
            }
        }
    }

    fragColor = vec4(finalRgb, color.a);
}
