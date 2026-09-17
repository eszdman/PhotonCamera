precision highp float;
precision highp sampler2D;

#define ANCHORS 24

uniform sampler2D InputBuffer; // downscaled luma Gaussian (level S)
uniform sampler2D RemapLut;
uniform ivec2 columnSize; // one column's (w, h)

out float Output;

// One packed column per remap anchor: column k holds r(g; (k+0.5)/ANCHORS)
// evaluated on the downscaled Gaussian.  The per-anchor pyramids must be
// built FROM these remapped images (reduces the remapped image,
// not the other way round): the tone compression has to be averaged into
// the pyramid reduction, applying the curve to the reduced values instead
// compresses nothing and pushes blacks down.
void main() {
    ivec2 p = ivec2(gl_FragCoord.xy);
    int k = p.x / columnSize.x;
    ivec2 q = ivec2(p.x - k * columnSize.x, p.y);
    float g = clamp(texelFetch(InputBuffer, q, 0).r, 0.0, 1.0);
    // the anchor sits exactly on a LUT row centre, so bilinear filtering
    // returns that row's curve
    Output = texture(RemapLut, vec2(g, (float(k) + 0.5) / float(ANCHORS))).r;
}
