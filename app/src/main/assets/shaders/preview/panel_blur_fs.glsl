precision highp float;
uniform sampler2D sTexture;
uniform vec2 uViewSize;
// Sampling is confined to the sharp (viewfinder) rect, so a panel over a
// letterbox band continues the same blurred edge content as the backdrop.
uniform vec2 uSampleMin;
uniform vec2 uSampleMax;
// Panel geometry in framebuffer pixels (GL convention: origin bottom-left).
uniform vec2 uCenter;
uniform vec2 uHalfSize;
uniform float uAngle;
uniform float uRadius;
uniform float uAlpha;
// Manual-palette blob mode (uPillTop > 0): the bubble's rounded rect with the
// wheel dome grown out of its top, blended in through shoulder arcs of
// uShoulderRadius. uPillTop is the FIXED height of the reserved dome zone
// above the bubble (the bubble's top line never moves); uDomeHeight is the
// CURRENTLY inflated cap (0 = collapsed, bubble only). Mirrors
// ManualPaletteBackground exactly — keep the two constructions in sync.
uniform float uPillTop;
uniform float uDomeHeight;
uniform float uShoulderRadius;
out vec4 Output;
void main() {
    vec2 d = gl_FragCoord.xy - uCenter;
    float c = cos(uAngle);
    float s = sin(uAngle);
    // Rotate into the panel's local frame (rotation follows the on-screen panel).
    vec2 lp = vec2(c * d.x + s * d.y, -s * d.x + c * d.y);
    float dist;
    if (uPillTop > 0.5) {
        if (uDomeHeight > 0.5) {
            // Switch to the drawable's frame: x from the centre, y down from
            // the panel's top edge, so the maths reads like the Java path walk.
            vec2 p = vec2(lp.x, uHalfSize.y - lp.y);
            float a = uHalfSize.x;
            float h = 2.0 * uHalfSize.y;
            float pillTop = uPillTop;
            // Shoulder circle tangent to the side line x = ±a and internally
            // tangent to the dome circle; (domeR - rb) ≥ (a - rb) always holds.
            float rb = uShoulderRadius;
            float aspect = a / uDomeHeight;
            float domeR = (uDomeHeight / 2.0) * (1.0 + aspect * aspect);
            float domeCy = pillTop - uDomeHeight + domeR;
            float sq = sqrt(max((domeR - rb) * (domeR - rb) - (a - rb) * (a - rb), 0.0));
            float sy = domeCy - sq;
            // Dome/shoulder tangency height; the pocket between the shoulder
            // arcs and the side lines exists only between tRy and sy.
            float tRy = domeCy - domeR * sq / max(domeR - rb, 0.001);
            float sdUncut = length(vec2(abs(p.x) - (a - rb), p.y - sy)) - rb;
            // Pill with sharp top corners (the sides continue into the
            // shoulders) and rounded bottom corners: an exact union of the
            // upper box, the inner rectangle and the two bottom corner discs.
            // In the shoulder band (tRy..sy) the upper box is bounded by the
            // shoulder arcs, not the side lines — cut that pocket out.
            float upperBox = max(max(max(abs(p.x) - a, pillTop - p.y), p.y - (h - uRadius)),
                    min(min(abs(p.x) - (a - rb), sdUncut), min(p.y - tRy, sy - p.y)));
            float innerRect = max(max(max(abs(p.x) - (a - uRadius), pillTop - p.y), p.y - h),
                    min(min(abs(p.x) - (a - rb), sdUncut), min(p.y - tRy, sy - p.y)));
            vec2 cq = vec2(abs(p.x) - (a - uRadius), p.y - (h - uRadius));
            float cornerDiscs = length(cq) - uRadius;
            dist = min(upperBox, min(innerRect, cornerDiscs));
            // Dome disc, cut below the shoulder tangent line and inside the
            // sides, and cut again by the shoulder arcs in their band.
            vec2 dq = p - vec2(0.0, domeCy);
            float dd = max(length(dq) - domeR, max(p.y - sy, abs(p.x) - a));
            float sd = max(sdUncut, p.y - sy);
            dd = max(dd, min(min(abs(p.x) - (a - rb), sd), p.y - tRy));
            dist = min(dist, min(dd, sd));
        } else {
            // Collapsed: plain rounded bubble over the bar's rect only —
            // NOT the full panel rect, whose dome zone stays unblurred.
            vec2 pillCentre = vec2(0.0, -uPillTop / 2.0);
            vec2 pillHalf = max(vec2(uHalfSize.x - uRadius,
                    uHalfSize.y - uPillTop / 2.0 - uRadius), vec2(0.0));
            vec2 q = abs(lp - pillCentre) - pillHalf;
            dist = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - uRadius;
        }
    } else {
        vec2 q = abs(lp) - max(uHalfSize - vec2(uRadius), vec2(0.0));
        dist = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - uRadius;
    }
    float mask = 1.0 - smoothstep(-1.0, 1.0, dist);
    // The blur passes place the camera image exactly like the sharp pass, so
    // the panel samples its own screen position (clamped to the sharp rect).
    vec2 uv = clamp(gl_FragCoord.xy / uViewSize, uSampleMin, uSampleMax);
    Output = vec4(texture(sTexture, uv).rgb, mask * uAlpha);
}
