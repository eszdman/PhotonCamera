precision mediump float;
uniform sampler2D sTexture;
uniform vec2 uFboSize;
// Per-tap maximum offset in FBO pixels (screen space).
uniform vec2 uOffsetPx;
out vec4 Output;
void main() {
    vec2 uv0 = gl_FragCoord.xy / uFboSize;
    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int i = -8; i <= 8; i++) {
        float w = exp(-0.5 * float(i * i) / 18.0);
        vec2 uv = uv0 + uOffsetPx * (float(i) / 8.0) / uFboSize;
        sum += texture(sTexture, clamp(uv, vec2(0.0), vec2(1.0))).rgb * w;
        wsum += w;
    }
    Output = vec4(sum / wsum, 1.0);
}
