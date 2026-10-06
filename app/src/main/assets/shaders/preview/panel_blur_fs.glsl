precision highp float;
uniform sampler2D sTexture;
uniform vec2 uViewSize;
// Sampling is confined to the sharp (viewfinder) rect, so a panel over a
// letterbox band continues the same blurred edge content as the backdrop.
uniform vec2 uSampleMin;
uniform vec2 uSampleMax;
// Panel geometry in framebuffer pixels (GL convention: origin bottom-left).
// The manual palette is a plain rounded rect covering the option bar plus the
// slider row(s); the bar's top padding already includes the slider zone so the
// region extends with the same rounded edges. Mirrors
// ManualPaletteBackground — keep the two in sync.
uniform vec2 uCenter;
uniform vec2 uHalfSize;
uniform float uAngle;
uniform float uRadius;
uniform float uAlpha;
out vec4 Output;
void main() {
    vec2 d = gl_FragCoord.xy - uCenter;
    float c = cos(uAngle);
    float s = sin(uAngle);
    // Rotate into the panel's local frame (rotation follows the on-screen panel).
    vec2 lp = vec2(c * d.x + s * d.y, -s * d.x + c * d.y);
    vec2 q = abs(lp) - max(uHalfSize - vec2(uRadius), vec2(0.0));
    float dist = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - uRadius;
    float mask = 1.0 - smoothstep(-1.0, 1.0, dist);
    // The blur passes place the camera image exactly like the sharp pass, so
    // the panel samples its own screen position (clamped to the sharp rect).
    vec2 uv = clamp(gl_FragCoord.xy / uViewSize, uSampleMin, uSampleMax);
    Output = vec4(texture(sTexture, uv).rgb, mask * uAlpha);
}
