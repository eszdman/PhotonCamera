
#define LAYOUT //
LAYOUT
precision highp float;
precision highp image2D;
uniform highp usampler2D srDepV;
uniform highp usampler2D srDepW;
// Raw px per output px per axis (the drizzle expansion).
uniform vec2 srPerOut;
// Pre-inflation noise model (the scatter field averages raw samples, so its
// noise does not scale with the denoise slider).
uniform float srNoiseS0;
uniform float srNoiseO0;
// Expected accumulated weight per output texel (frames / expansion^2),
// bounding the coverage gate.
uniform float srCoverageRef;
// Output: the restored band above the raw Nyquist and its confidence, packed
// as two halves into one R32UI word (.r band, .g gate). The post's band layer
// replaces the aniso reconstruction's invented content in this band.
layout(r32ui, binding = 0) writeonly uniform highp uimage2D srBandOut;
#define SR_FIXED (1.0 / 65536.0)
// Noise gate: suppress the restoration below half a sigma of the local band
// noise (the former resolve's guard, kept for the same reason).
#define SR_GATE_K 0.5
// Band edges in raw cycles/px: the raw Nyquist and the restoration top
// (1.5x of it; the pixel aperture's sinc nulls at 2x, so nothing above that
// is real anyway).
#define SR_F_RAW_NYQ 0.35
#define SR_F_RAW_TOP 0.75
// Restoration gain cap. The modeled inverse of the sampling MTF wants ~3-6x
// at these frequencies (pixel aperture + bilinear deposit + lens all roll
// off), which is noise-limited in practice; the bench shows the correlation
// with the true above-Nyquist band survives 2.2x with the amplitude nearly
// doubled, so that is the ceiling - the per-pixel noise gate below decides
// how much of it is actually applied.
#define SR_GAIN_MAX 2.2
// Lens blur assumed for the MTF model, in raw px (the pixel aperture is
// modeled exactly). A softer lens lowers the target gain, a sharper one
// raises it toward the cap.
#define SR_LENS_SIGMA_RAW 0.30

// Gaussian -3dB cutoff f (cycles per output px) -> sigma in output px.
float srSigma(float perOut, float fRaw) {
    float f = clamp(fRaw * perOut, 1e-4, 0.45);
    return clamp(0.187 / f, 0.35, 1.8);
}

// Restoration gain: the inverse of the modeled sampling MTF at the band's
// center frequency, capped. f = 0.625 cycles/raw px = the midpoint of
// [raw Nyquist, 1.5x raw Nyquist].
float srRestoreGain(float perOut) {
    const float PI = 3.14159265359;
    float fRaw = 0.5 * (SR_F_RAW_NYQ + SR_F_RAW_TOP);
    float fOut = fRaw * perOut;
    float box = fRaw < 1e-6 ? 1.0 : sin(PI * fRaw) / (PI * fRaw);
    float fSplat = fOut < 1e-6 ? 1.0 : sin(PI * fOut) / (PI * fOut);
    float splat = fSplat * fSplat;
    float lens = exp(-2.0 * PI * PI * SR_LENS_SIGMA_RAW * SR_LENS_SIGMA_RAW * fRaw * fRaw);
    float mtf = max(box * splat * lens, 1e-4);
    return clamp(1.0 / mtf, 1.0, SR_GAIN_MAX);
}

void main() {
    ivec2 o = ivec2(gl_GlobalInvocationID.xy);
    ivec2 size = imageSize(srBandOut);
    if (o.x >= size.x || o.y >= size.y) return;
    float vs = float(texelFetch(srDepV, o, 0).x) * SR_FIXED;
    float ws = float(texelFetch(srDepW, o, 0).x) * SR_FIXED;
    if (!(ws > 1e-6)) {
        imageStore(srBandOut, o, uvec4(0u));
        return;
    }
    float f = vs / ws;
    if (!(f <= 65504.0)) f = 0.0;
    float per = min(max(srPerOut.x, 1e-4), max(srPerOut.y, 1e-4));
    float sigN = srSigma(per, SR_F_RAW_NYQ);
    float sigC = srSigma(per, SR_F_RAW_TOP);
    // Gaussian-difference band-pass of the scatter field over
    // [raw Nyquist, 1.5x raw Nyquist]: exactly the range the burst's
    // sub-pixel sampling can actually resolve. Holes fall back to the
    // center's value so they don't drag the band.
    float lpN = 0.0;
    float lpC = 0.0;
    float wN = 0.0;
    float wC = 0.0;
    ivec2 imax = size - ivec2(1);
    for (int j = -3; j <= 3; j++) {
        for (int i = -3; i <= 3; i++) {
            ivec2 t = clamp(o + ivec2(i, j), ivec2(0, 0), imax);
            float v = float(texelFetch(srDepV, t, 0).x) * SR_FIXED;
            float w = float(texelFetch(srDepW, t, 0).x) * SR_FIXED;
            float fv = w > 1e-6 ? v / w : f;
            float d2 = float(i * i + j * j);
            float gN = exp(-0.5 * d2 / (sigN * sigN));
            float gC = exp(-0.5 * d2 / (sigC * sigC));
            lpN += gN * fv;
            wN += gN;
            lpC += gC * fv;
            wC += gC;
        }
    }
    // Upper-band extractor: the low-pass at the band TOP minus the one at
    // the raw Nyquist (small sigma first) - positive inside the band. The
    // reversed order is the negative of this and passes low frequencies.
    float band = lpC / max(wC, 1e-6) - lpN / max(wN, 1e-6);
    // Per-sample noise from the pre-inflation model over the accumulated
    // weight; the gate suppresses the restoration where the band is at or
    // below that noise.
    float sigma = sqrt(max(srNoiseS0 * f + srNoiseO0, 1e-12) / max(ws, 1.0));
    float t = SR_GATE_K * sigma / max(abs(band), 1e-9);
    float gate = max(0.0, 1.0 - t * t);
    // Coverage: holes and thin cells never carry restored detail, so the
    // band layer falls back to the aniso's own reconstruction there (no
    // ghosting into unsampled areas).
    gate *= clamp(ws / max(srCoverageRef, 1.0), 0.0, 1.0);
    band *= srRestoreGain(per);
    if (!(band <= 65504.0)) band = 0.0;
    if (!(gate <= 65504.0)) gate = 0.0;
    imageStore(srBandOut, o, uvec4(packHalf2x16(vec2(band, gate))));
}
