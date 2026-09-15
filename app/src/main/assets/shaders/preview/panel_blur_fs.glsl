precision highp float;
uniform sampler2D sTexture;
uniform vec2 uViewSize;
// Panel geometry in framebuffer pixels (GL convention: origin bottom-left).
uniform vec2 uCenter;
uniform vec2 uHalfSize;
uniform float uAngle;
uniform float uRadius;
uniform float uAlpha;
// Optional clip rectangle in the panel's local frame (zero size disables it):
// shapes larger than their view (the knob wheel disc) are cut to what is drawn.
uniform vec2 uClipCenter;
uniform vec2 uClipHalfSize;
// 1 = knob wheel scrim: the circle through the clip rect's bottom corners and
// top-centre (the same arc KnobView draws), cut to the clip rect and rounded
// on its bottom corners like the manual bar pill.
uniform float uDome;
out vec4 Output;
void main() {
    vec2 d = gl_FragCoord.xy - uCenter;
    float c = cos(uAngle);
    float s = sin(uAngle);
    // Rotate into the panel's local frame (rotation follows the on-screen panel).
    vec2 lp = vec2(c * d.x + s * d.y, -s * d.x + c * d.y);
    // Clip rect centre in the panel's local frame.
    vec2 cd = uClipCenter - uCenter;
    vec2 lc = vec2(c * cd.x + s * cd.y, -s * cd.x + c * cd.y);
    float dist;
    if (uDome > 0.5) {
        float hw = uClipHalfSize.x;
        float hh = uClipHalfSize.y;
        // hh * (1 + (hw / 2hh)^2) == (hw*hw + 4*hh*hh) / (4*hh), but it never
        // squares the (large) rect half-width, so it cannot overflow mediump
        // range on GPUs that evaluate mediump in fp16.
        float domeAspect = hw / (2.0 * hh);
        float domeRadius = hh * (1.0 + domeAspect * domeAspect);
        vec2 domeCenter = lc + vec2(0.0, hh - domeRadius);
        dist = length(lp - domeCenter) - domeRadius;
        vec2 cq = abs(lp - lc) - uClipHalfSize;
        dist = max(dist, length(max(cq, vec2(0.0))) + min(max(cq.x, cq.y), 0.0));
        // Fillet the bottom corners: a small arc is fitted tangent to both the
        // disc and the bottom edge, so the scrim reads as rounded instead of
        // ending in the disc's points (mirrored via abs for both corners).
        if (uRadius > 0.0) {
            float fillet = uRadius;
            float denom = max(domeRadius - fillet, 0.001);
            float base = domeRadius - 2.0 * hh + fillet;
            float xf = sqrt(max(denom * denom - base * base, 0.0));
            float yc = (hh - domeRadius) + domeRadius * base / denom;
            vec2 p = vec2(abs(lp.x - lc.x), lp.y - lc.y);
            vec2 fc = vec2(xf, -hh + fillet);
            float bite = max(max(xf - p.x, p.y - yc), fillet - length(p - fc));
            dist = max(dist, -bite);
        }
    } else {
        vec2 q = abs(lp) - max(uHalfSize - vec2(uRadius), vec2(0.0));
        dist = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - uRadius;
        if (uClipHalfSize.x > 0.0 && uClipHalfSize.y > 0.0) {
            vec2 cq = abs(lp - lc) - uClipHalfSize;
            float clipDist = length(max(cq, vec2(0.0))) + min(max(cq.x, cq.y), 0.0);
            dist = max(dist, clipDist);
        }
    }
    float mask = 1.0 - smoothstep(-1.0, 1.0, dist);
    vec2 uv = gl_FragCoord.xy / uViewSize;
    Output = vec4(texture(sTexture, uv).rgb, mask * uAlpha);
}
