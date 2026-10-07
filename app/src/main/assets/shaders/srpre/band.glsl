precision highp float;
precision highp sampler2D;
// Aniso reconstruction at the output grid (this node's input).
uniform sampler2D InputBuffer;
// Restored band above the raw Nyquist + confidence, packed as two halves in
// one R32UI word (produced by merge/srrecover). Absolute output coords.
uniform highp usampler2D BandMap;
// Raw px per output px per axis (bands must match the recovery's).
uniform vec2 srPerOut;
// ABLC levels applied to the input (0 when ABLC is off): the band arrives in
// the pre-ABLC domain, so it scales by the same affine denominator.
uniform vec3 srBlack;
// Input window origin (absolute output row of the InputBuffer tile's row 0)
// and this tile's absolute output origin. (0,0) on the full-frame path.
uniform ivec2 u_inOrigin;
uniform ivec2 u_tileOrigin;
// 0 = replace (raw-grid path): the reconstruction's invented band is
// replaced by the recovered one. 1 = deconv (output-grid drizzle path): the
// drizzle already carries the real above-raw-Nyquist content, so the
// reconstruction's OWN band is deconvolved by the recovery's modeled gain
// instead of being replaced - replacing it with the raw deposit's band would
// re-inject that deposit's sampling grid.
uniform float srBandMode;
out vec4 Output;

#define SR_F_RAW_NYQ 0.35
#define SR_F_RAW_TOP 0.75
#define SR_LENS_SIGMA_RAW 0.30
#define SR_GAIN_MAX 2.2
// Above-raw-Nyquist gain as a fraction of the below-Nyquist gain: that part is
// mostly moire (the local-average drizzle cannot invert the sampling), so it
// is tapered (bench: uniform gain corr 0.47 -> 0.52 and edge overshoot 5.0%
// -> 4.4% at 0.3, while the genuine below-Nyquist amplitude stays ~0.85x).
#define SR_BAND_SPLIT 0.3

float srSigma(float perOut, float fRaw) {
    float f = clamp(fRaw * perOut, 1e-4, 0.45);
    return clamp(0.187 / f, 0.35, 1.8);
}

// Mirror of merge/srrecover's restoration gain: the modeled inverse of the
// sampling MTF (pixel aperture x bilinear deposit x lens) at the band center,
// capped (see srrecover for the calibration note).
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
    ivec2 o = ivec2(gl_FragCoord.xy) + u_tileOrigin;
    ivec2 oIn = o - u_inOrigin;
    ivec2 imax = textureSize(InputBuffer, 0) - ivec2(1);
    ivec2 bmax = textureSize(BandMap, 0) - ivec2(1);
    vec3 ref = texelFetch(InputBuffer, clamp(oIn, ivec2(0), imax), 0).rgb;
    float per = min(max(srPerOut.x, 1e-4), max(srPerOut.y, 1e-4));
    float sigN = srSigma(per, SR_F_RAW_NYQ);
    float sigC = srSigma(per, SR_F_RAW_TOP);
    // The raw Nyquist splits the band into the genuine, recoverable part
    // (below: the drizzle's multi-frame fusion resolves it) and the mostly
    // moire part (above: the local average cannot invert the sampling). Boost
    // the first at the full modeled gain, taper the second (SR_BAND_SPLIT).
    float sigM = srSigma(per, 0.5);
    // The reconstruction's own content in the recovered band: exactly the
    // quantity the restored band replaces.
    float lpN = 0.0;
    float lpC = 0.0;
    float lpM = 0.0;
    float wN = 0.0;
    float wC = 0.0;
    float wM = 0.0;
    for (int j = -3; j <= 3; j++) {
        for (int i = -3; i <= 3; i++) {
            vec3 r = texelFetch(InputBuffer, clamp(oIn + ivec2(i, j), ivec2(0, 0), imax), 0).rgb;
            float y = dot(r, vec3(0.2126, 0.7152, 0.0722));
            float d2 = float(i * i + j * j);
            float gN = exp(-0.5 * d2 / (sigN * sigN));
            float gC = exp(-0.5 * d2 / (sigC * sigC));
            float gM = exp(-0.5 * d2 / (sigM * sigM));
            lpN += gN * y;
            wN += gN;
            lpC += gC * y;
            wC += gC;
            lpM += gM * y;
            wM += gM;
        }
    }
    float lpNn = lpN / max(wN, 1e-6);
    float lpMm = lpM / max(wM, 1e-6);
    float lpCc = lpC / max(wC, 1e-6);
    float bandLo = lpMm - lpNn;
    float bandHi = lpCc - lpMm;
    float bandAniso = lpCc - lpNn;
    vec2 bg = unpackHalf2x16(texelFetch(BandMap, clamp(o, ivec2(0), bmax), 0).x);
    float bLuma = dot(srBlack, vec3(0.2126, 0.7152, 0.0722));
    float denom = max(1.0 - bLuma, 1e-4);
    // Differential, gate-weighted replacement: where the recovery is
    // confident the real band replaces the reconstruction's invented
    // content; at gate 0 the output is exactly the native reconstruction.
    // On the output-grid drizzle path (mode 1) the reconstruction's band is
    // the real thing (already de-aliased by the scatter), so the modeled
    // inverse-MTF gain sharpens it instead of replacing it.
    float target = srBandMode > 0.5
            ? bandAniso + (srRestoreGain(per) - 1.0) * bandLo
              + (SR_BAND_SPLIT * srRestoreGain(per) - 1.0) * bandHi
            : bg.x / denom;
    float corr = bg.y * (target - bandAniso);
    if (srBandMode > 0.5) {
        // Deconvolution only: Weber-cap the boost exactly like the acutance,
        // so an edge's large band-pass cannot ring. Bench: without it the edge
        // overshoot doubles (6% -> 11.5%) and the 10-90 rise collapses at high
        // frame counts; the texture boost is far below the cap and survives.
        float lvl = dot(ref, vec3(0.2126, 0.7152, 0.0722));
        float cap = max(0.01 * lvl, 1e-4);
        corr = cap * tanh(corr / cap);
    }
    if (!(corr <= 65504.0 || corr >= -65504.0)) corr = 0.0;
    Output = vec4(clamp(ref + corr, vec3(0.0), vec3(8.0)), 1.0);
}
