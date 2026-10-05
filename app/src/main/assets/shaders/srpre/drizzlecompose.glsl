precision highp float;
precision highp sampler2D;
#import interpolation
// Demosaiced crop (raw grid, post-ABLC RGB): the chroma source.
uniform sampler2D InputBuffer;
// Fused drizzle at the OUTPUT grid (R32F luma, pre-ABLC packed domain).
uniform sampler2D FusedLuma;
// ABLC black levels: FusedLuma arrives pre-ABLC, InputBuffer post.
uniform vec3 srBlack;
// Raw px per output px per axis (the drizzle's expansion).
uniform vec2 srPerOut;
out vec4 Output;

// Compose the output-grid RGB the reconstruction consumes: luma from the
// drizzle (which represents above-raw-Nyquist content instead of folding it),
// chroma from the demosaiced crop (smooth, and the drizzle has none). The
// post then runs its aniso at zoom 1; splitChroma keeps this chroma.
void main() {
    ivec2 o = ivec2(gl_FragCoord.xy);
    ivec2 fmax = textureSize(FusedLuma, 0) - ivec2(1);
    ivec2 imax = textureSize(InputBuffer, 0) - ivec2(1);
    float yd = texelFetch(FusedLuma, clamp(o, ivec2(0), fmax), 0).r;
    vec2 c = (vec2(o) + vec2(0.5)) * srPerOut;
    vec2 uv = c / vec2(textureSize(InputBuffer, 0));
    // Bicubic chroma: a bilinear crop upsample would leave a block grid at
    // chromatic edges (which the aniso's splitChroma then carries).
    vec3 ref = textureBicubicHardware(InputBuffer, uv).rgb;
    float bLuma = dot(srBlack, vec3(0.2126, 0.7152, 0.0722));
    float denom = max(1.0 - bLuma, 1e-4);
    float y = max((yd - bLuma) / denom, 0.0);
    float yc = dot(ref, vec3(0.2126, 0.7152, 0.0722));
    // Holes (no drizzle samples): keep the reference's luma there.
    if (!(y > 1e-6)) y = yc;
    vec3 co = ref - vec3(yc);
    Output = vec4(clamp(y + co, vec3(0.0), vec3(8.0)), 1.0);
}
