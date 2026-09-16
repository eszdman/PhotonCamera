precision highp float;
uniform sampler2D sTexture;
uniform vec2 uViewSize;
// Sharp (viewfinder) rect in GL surface pixels.
uniform vec2 uSharpOrigin;
uniform vec2 uSharpSize;
// Sharp corner radius in px (0 = square); the rounded-corner cutouts inside the
// rect continue the adjacent band so the whole blur surround stays uniform.
uniform float uCornerRadius;
uniform vec3 uScrimColor;
uniform float uScrimAlpha;
out vec4 Output;
void main() {
    vec2 size = max(uViewSize, vec2(1.0));
    vec2 rectMin = uSharpOrigin;
    vec2 rectMax = uSharpOrigin + uSharpSize;
    vec2 p = gl_FragCoord.xy;
    vec2 samplePos = p;
    // Every letterbox band shows the strip of the frame adjacent to it (the
    // same region the mirrored bands sampled) with the frame's own orientation:
    // translating the fragment inward by the band depth lands it on that strip
    // unflipped. The rect's corner squares are treated as part of the top or
    // bottom band, so the rounded-corner cutouts (the only backdrop pixels
    // visible inside the rect) continue that strip as well.
    float r = uCornerRadius;
    bool nearTop = p.y > rectMax.y - r;
    bool nearBottom = p.y < rectMin.y + r;
    bool nearLeft = p.x < rectMin.x + r;
    bool nearRight = p.x > rectMax.x - r;
    if (p.y > rectMax.y || (nearTop && (nearLeft || nearRight))) {
        samplePos.y = p.y + rectMax.y - size.y;
    } else if (p.y < rectMin.y || (nearBottom && (nearLeft || nearRight))) {
        samplePos.y = p.y + rectMin.y;
    } else if (p.x < rectMin.x) {
        samplePos.x = p.x + rectMin.x;
    } else if (p.x > rectMax.x) {
        samplePos.x = p.x + rectMax.x - size.x;
    }
    vec2 uv = clamp(samplePos / size, rectMin / size, rectMax / size);
    vec3 blurred = texture(sTexture, uv).rgb;
    // Same 40% scrim treatment as the floating panels.
    Output = vec4(mix(blurred, uScrimColor, uScrimAlpha), 1.0);
}
