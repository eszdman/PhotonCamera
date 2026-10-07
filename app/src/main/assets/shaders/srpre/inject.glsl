precision highp float;
precision highp sampler2D;
// Demosaiced crop image after ABLC: the reference the multi-frame correction
// is differenced against, and the image it is applied to. The normal
// reconstruction (KernelNet anisotropic upscale + the tail) runs after this
// node, so the SR output is the Disabled render plus this correction: it can
// add detail but can never soften the reconstruction, because it never
// replaces it.
uniform sampler2D InputBuffer;
// Fused multi-frame lattice at the same grid (the merge's scatter
// accumulation reduced by merge/srlattice): .r = fused luma in the pre-ABLC
// packed domain, .g = effective accumulated weight.
uniform sampler2D FusedLuma;
// Per-channel ABLC black levels applied to InputBuffer (0 when ABLC is off).
// The fused luma arrives pre-ABLC; push it through the same transform, or
// the levels disagree.
uniform vec3 srBlack;
// Pre-inflation noise model. The fused luma averages raw per-frame data, so
// its noise does not scale with the denoise slider.
uniform float srNoiseS;
uniform float srNoiseO;
out vec4 Output;

// Selection constant matching the former post-aniso resolve's validated
// default (srResolveDetail 0.6), fixed here: the injection is a correctness
// stage, not a taste knob.
#define SR_DETAIL 0.6
// Band-edge droop restoration. The per-site luma is gathered by a Catmull-Rom
// bicubic whose response is ~0.7 at the sensor band edge, and the crop
// reduction adds a little more. Injected raw, a confident difference there
// reads as a ~30% contrast loss - the SR output measurably softer than
// Disabled exactly where the fusion is surest (bold 3x report: lower detail,
// lower noise). The tight 3x3 Gaussian's high-pass carries ~0.56 of the edge
// amplitude, so this gain restores ~0.7 -> ~0.94: deliberately
// under-correcting rather than overshooting.
#define SR_MTF_GAIN 0.43

void main() {
    ivec2 o = ivec2(gl_FragCoord.xy);
    ivec2 imax = textureSize(InputBuffer, 0) - ivec2(1);
    ivec2 fmax = textureSize(FusedLuma, 0) - ivec2(1);
    vec3 ref = texelFetch(InputBuffer, clamp(o, ivec2(0), imax), 0).rgb;
    vec2 fw = texelFetch(FusedLuma, clamp(o, ivec2(0), fmax), 0).rg;
    float yRef = dot(ref, vec3(0.2126, 0.7152, 0.0722));
    // Holes (no drizzle samples here): leave the reference untouched.
    if (!(fw.g > 1e-6)) {
        Output = vec4(ref, 1.0);
        return;
    }
    float bLuma = dot(srBlack, vec3(0.2126, 0.7152, 0.0722));
    float denom = max(1.0 - bLuma, 1e-4);
    float yFused = max((fw.r - bLuma) / denom, 0.0);
    // Wiener (soft) shrinkage against the local noise floor: one-frame sigma
    // from the noise model over the square root of the accumulated weight,
    // halved for the gather's noise gain (the former resolve's calibration).
    float sigma = 0.5 * sqrt(max(srNoiseS * yRef + srNoiseO, 1e-12) / max(fw.g, 1.0));
    // Restore the gather's band-edge droop on the fused field itself, before
    // differencing: reference-free and strictly in-band, so it cannot import
    // aliasing. The tight 3x3 Gaussian (~0.55 px) high-passes the lattice;
    // the boost is gated below the noise floor so an empty band is not
    // amplified. Weights renormalize over cells that carry samples.
    float lpF = 0.0;
    float lpW = 0.0;
    for (int j = -1; j <= 1; j++) {
        for (int i = -1; i <= 1; i++) {
            float wgt = (i == 0 ? 0.72 : 0.14) * (j == 0 ? 0.72 : 0.14);
            vec2 s = texelFetch(FusedLuma, clamp(o + ivec2(i, j), ivec2(0, 0), fmax), 0).rg;
            if (s.g > 1e-6) {
                lpF += wgt * max((s.r - bLuma) / denom, 0.0);
                lpW += wgt;
            }
        }
    }
    if (!(lpW > 1e-6)) {
        Output = vec4(ref, 1.0);
        return;
    }
    lpF /= lpW;
    float hi = yFused - lpF;
    float tB = 0.5 * sigma / max(abs(hi), 1e-9);
    yFused += SR_MTF_GAIN * max(0.0, 1.0 - tB * tB) * hi;
    // Differential-only injection: add the confident multi-frame difference,
    // never substitute the reference's band. The old low+mid-band fallback
    // existed to overwrite the KernelNet map's post-aniso mottle; here the
    // reference is the demosaiced multi-frame base, so a substitution could
    // only soften it (and read as "lower noise, less detail" against
    // Disabled). The shrinkage keeps noise-level differences out, so flats
    // and texture stay the reference's, noise and all.
    float hp = yFused - yRef;
    float t = SR_DETAIL * sigma / max(abs(hp), 1e-9);
    float m = max(0.0, 1.0 - t * t);
    vec3 rgb = ref + m * hp;
    Output = vec4(clamp(rgb, vec3(0.0), vec3(8.0)), 1.0);
}
