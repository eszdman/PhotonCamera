precision mediump float;
uniform sampler2D sTexture;
uniform vec2 uFboSize;
// Per-tap maximum offset in FBO pixels (screen space).
uniform vec2 uOffsetPx;
// Sampling is clamped to the sharp (viewfinder) rect so the separable blur
// edge-extends inside the preview instead of bleeding the letterbox black in.
uniform vec2 uClampMin;
uniform vec2 uClampMax;
out vec4 Output;
void main() {
    vec2 uv0 = gl_FragCoord.xy / uFboSize;
    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int i = -8; i <= 8; i++) {
        float w = exp(-0.5 * float(i * i) / 18.0);
        vec2 uv = uv0 + uOffsetPx * (float(i) / 8.0) / uFboSize;
        uv = clamp(uv, uClampMin, uClampMax);
        sum += texture(sTexture, uv).rgb * w;
        wsum += w;
    }
    Output = vec4(sum / wsum, 1.0);
}
