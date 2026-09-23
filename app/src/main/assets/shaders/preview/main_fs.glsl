#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform samplerExternalOES sTexture;
uniform vec2 resolution;
uniform bool enablePeak;
uniform bool mirror;
// Rounded viewfinder corners (px, 0 = square). Fragments outside the rounded
// rect are dropped so the blurred backdrop beneath the preview shows through.
uniform float uCornerRadius;
// Sharp rect origin in GL surface pixels, so the mask is built in screen space
// (texCoord is rotated by the vertex shader and would stretch the arcs).
uniform vec2 uSharpOrigin;
// Snapshot of the previous camera's rendered frame (the mode/aspect switch
// crossfade): uSnapshotAlpha 1 shows the snapshot, 0 the live preview. It holds
// exactly the sharp rect the capture was taken with, so it is sampled by the
// fragment's position inside the current sharp rect and stays valid across
// surface resizes.
uniform sampler2D sSnapshot;
uniform float uSnapshotAlpha;
out vec4 Output;
in vec2 texCoord;
void main() {
    if (uCornerRadius > 0.0) {
        vec2 halfSize = resolution * 0.5;
        vec2 local = gl_FragCoord.xy - uSharpOrigin;
        vec2 q = abs(local - halfSize) - (halfSize - vec2(uCornerRadius));
        float cornerDist = length(max(q, vec2(0.0)))
                + min(max(q.x, q.y), 0.0) - uCornerRadius;
        if (cornerDist > 0.0)
            discard;
    }
    vec2 uv = texCoord.xy;
    if(mirror)
        uv.y = 1.0 - uv.y;
    vec4 color = texture(sTexture, uv);
    if (uSnapshotAlpha > 0.001) {
        // Map the current sharp rect onto the snapshot: the previous capture is
        // stretched into the animating rect while it crossfades away.
        vec2 sharpSize = max(resolution, vec2(1.0));
        vec2 localUv = (gl_FragCoord.xy - uSharpOrigin) / sharpSize;
        color = mix(color, texture(sSnapshot, clamp(localUv, 0.0, 1.0)), uSnapshotAlpha);
    }
    vec2 size = resolution;
    // focus peaking
    vec4 avg = vec4(0.0);
    for (int i = -1; i <= 1; i++) {
        for (int j = -1; j <= 1; j++) {
            avg += texture(sTexture, uv + vec2(i*2, j*2) / size);
        }
    }
    avg /= 9.0;
    float diff = dot(abs(color - avg), vec4(0.299, 0.587, 0.114, 0.0));
    float denoiseK = 0.05;
    // denoise
    float w = (diff * diff) /(denoiseK + (diff * diff));
    vec4 dc = vec4(1.0,0.0,1.0,0.0);
    if(enablePeak)
        color = color + dc*32.0*diff*w;
    Output = color;
}