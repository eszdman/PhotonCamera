#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform samplerExternalOES sTexture;
uniform vec2 uViewSize;
uniform vec2 uFboSize;
// Per-tap maximum offset in VIEW pixels (screen space).
uniform vec2 uOffsetPx;
uniform float uCos;
uniform float uSin;
uniform bool mirror;
out vec4 Output;
void main() {
    vec2 scale = uViewSize / uFboSize;
    vec3 sum = vec3(0.0);
    float wsum = 0.0;
    for (int i = -8; i <= 8; i++) {
        // Screen-space tap, converted to the camera texture's sample coordinate.
        vec2 viewPx = gl_FragCoord.xy * scale + uOffsetPx * (float(i) / 8.0);
        vec2 ndc = viewPx / uViewSize * 2.0 - 1.0;
        vec2 q = vec2(ndc.x * uCos + ndc.y * uSin, -ndc.x * uSin + ndc.y * uCos);
        vec2 uv = vec2((1.0 + q.y) * 0.5, (q.x + 1.0) * 0.5);
        if (mirror)
            uv.y = 1.0 - uv.y;
        float w = exp(-0.5 * float(i * i) / 18.0);
        sum += texture(sTexture, clamp(uv, vec2(0.0), vec2(1.0))).rgb * w;
        wsum += w;
    }
    Output = vec4(sum / wsum, 1.0);
}
