precision highp float;
precision highp sampler2D;

uniform sampler2D InputBuffer;
uniform sampler2D RemapLut;
out float Output;

// Pointwise remap of the coarsest Gaussian level.  This level is the base of
// the Laplacian reconstruction, so it has to pass through the same remapping
// curve as every pyramid coefficient (anchor = the texel's own intensity).
// Row k of the LUT holds the curve for anchor (k + 0.5) / rows; since
// bilinear row centers sit at (k + 0.5) / rows, the anchor coordinate is
// the intensity itself and CLAMP_TO_EDGE clamps the interpolation weights.
void main() {
    float g = clamp(texelFetch(InputBuffer, ivec2(gl_FragCoord.xy), 0).r, 0.0, 1.0);
    Output = texture(RemapLut, vec2(g, g)).r;
}
