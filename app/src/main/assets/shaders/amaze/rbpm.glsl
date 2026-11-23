#import amaze
// AMaZE pass 9+10 (fused): diagonal gradients delp/delm + R-B variances (C++
// lines 1044-1060) and the diagonal interpolation of the other chroma (C++
// lines 1139-1218).  delpm has no consumer besides this math, so it is
// computed straight into shared memory instead of round-tripping through a
// texture; only the half-grid slots this workgroup's rbpm math can reach
// (site taps within s +/- 2 of the 8x8 block) are computed.
uniform sampler2D u_grad;    // cfa rides in .a (written by gradcd)

const int HALO = 4;
const int TW = LW + 2 * HALO;
const int TH = LH + 2 * HALO;
const int TN = TW * TH;

ivec2 T0() { return ivec2(gl_WorkGroupID.xy * uvec2(LW, LH)) - HALO; }
int SIDX(ivec2 q) { ivec2 l = q - T0(); return l.y * TW + l.x; }
// Only even columns in the block plus a two-pixel halo carry diagonal gradients.
const int DIAG_W = (LW + 4) / 2;
const int DIAG_H = LH + 4;
ivec2 diagonalOrigin() { return ivec2(gl_WorkGroupID.xy * uvec2(LW, LH)) - 2; }
int SIDX_H(ivec2 q) {
    ivec2 local = q - diagonalOrigin();
    return local.y * DIAG_W + (local.x >> 1);
}

shared float s_cfa[TN];
shared vec4 s_delpm[DIAG_W * DIAG_H];   // half-grid slots (even columns)
float delp(ivec2 q) { return s_delpm[SIDX_H(q)].r; }
float delm(ivec2 q) { return s_delpm[SIDX_H(q)].g; }
float sq1p(ivec2 q) { return s_delpm[SIDX_H(q)].b; }
float sq1m(ivec2 q) { return s_delpm[SIDX_H(q)].a; }
float Cf(ivec2 q) { return s_cfa[SIDX(q)]; }

void load_tile() {
    ivec2 t0 = T0();
    for (int i = int(gl_LocalInvocationIndex); i < TN; i += LW * LH) {
        ivec2 q = clamp(t0 + ivec2(i % TW, i / TW), ivec2(0), u_size - 1);
        s_cfa[i] = texelFetch(u_grad, q, 0).a;
    }
    memoryBarrierShared();
    barrier();
}

layout(binding = 0, rgba16f) writeonly uniform highp image2D img_out;
void emit(ivec2 p, vec4 v) { imageStore(img_out, p, v); }

// delp/delm + Dgrbsq1p/1m at half-grid slot q (even column); both pattern
// branches share the Dgrbsq1p/1m formulas evaluated around column q.x+1
vec4 delpm_val(ivec2 q) {
    if (q.x < 6 || q.y < 6 || q.x > u_size.x - 8 || q.y > u_size.y - 8) return vec4(0.0);
    float c1 = Cf(q + ivec2(1, 0));
    float sq1p = sq(c1 - Cf(q + ivec2(0, 1))) + sq(c1 - Cf(q + ivec2(2, -1)));
    float sq1m = sq(c1 - Cf(q + ivec2(0, -1))) + sq(c1 - Cf(q + ivec2(2, 1)));
    float delp, delm;
    if ((FC(q.y, 2) & 1) == 0) {
        delp = abs(Cf(q + ivec2(1, -1)) - Cf(q + ivec2(-1, 1)));
        delm = abs(Cf(q + ivec2(1, 1)) - Cf(q + ivec2(-1, -1)));
    } else {
        delp = abs(Cf(q + ivec2(2, -1)) - Cf(q + ivec2(0, 1)));
        delm = abs(Cf(q + ivec2(2, 1)) - Cf(q + ivec2(0, -1)));
    }
    return vec4(delp, delm, sq1p, sq1m);
}

void compute_delpm() {
    for (int i = int(gl_LocalInvocationIndex); i < DIAG_W * DIAG_H; i += LW * LH) {
        ivec2 q = diagonalOrigin() + ivec2(2 * (i % DIAG_W), i / DIAG_W);
        s_delpm[i] = delpm_val(q);
    }
    memoryBarrierShared();
    barrier();
}

void main() {
    load_tile();
    compute_delpm();
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (p.x >= u_size.x || p.y >= u_size.y) return;
    emit(p, vec4(0.0));
    if ((p.x & 1) == 1) return;
    ivec2 s = ivec2(site_x(p), p.y);
    if (!inside(s, 8)) return;
    float c = Cf(s);
    // diagonal colour ratios (m = SE/NW diag, p = NE/SW diag)
    float crse = 2.0 * Cf(s + ivec2(1, 1)) / (EPS + c + Cf(s + ivec2(2, 2)));
    float crnw = 2.0 * Cf(s + ivec2(-1, -1)) / (EPS + c + Cf(s + ivec2(-2, -2)));
    float crne = 2.0 * Cf(s + ivec2(1, -1)) / (EPS + c + Cf(s + ivec2(2, -2)));
    float crsw = 2.0 * Cf(s + ivec2(-1, 1)) / (EPS + c + Cf(s + ivec2(-2, 2)));
    float rbse = abs(1.0 - crse) < ARTHRESH ? c * crse : Cf(s + ivec2(1, 1)) + 0.5 * (c - Cf(s + ivec2(2, 2)));
    float rbnw = abs(1.0 - crnw) < ARTHRESH ? c * crnw : Cf(s + ivec2(-1, -1)) + 0.5 * (c - Cf(s + ivec2(-2, -2)));
    float rbne = abs(1.0 - crne) < ARTHRESH ? c * crne : Cf(s + ivec2(1, -1)) + 0.5 * (c - Cf(s + ivec2(2, -2)));
    float rbsw = abs(1.0 - crsw) < ARTHRESH ? c * crsw : Cf(s + ivec2(-1, 1)) + 0.5 * (c - Cf(s + ivec2(-2, 2)));
    float wtse = EPS + delm(s) + delm(s + ivec2(1, 1)) + delm(s + ivec2(2, 2));
    float wtnw = EPS + delm(s) + delm(s + ivec2(-1, -1)) + delm(s + ivec2(-2, -2));
    float wtne = EPS + delp(s) + delp(s + ivec2(1, -1)) + delp(s + ivec2(2, -2));
    float wtsw = EPS + delp(s) + delp(s + ivec2(-1, 1)) + delp(s + ivec2(-2, 2));
    float rbm = (wtse * rbnw + wtnw * rbse) / (wtse + wtnw);
    float rbp = (wtne * rbsw + wtsw * rbne) / (wtne + wtsw);
    float rbvarm = EPSSQ + GAUSSEVEN[0] * (sq1m(s + ivec2(0, -1)) + sq1m(s + ivec2(-1, 0)) + sq1m(s + ivec2(1, 0)) + sq1m(s + ivec2(0, 1)))
        + GAUSSEVEN[1] * (sq1m(s + ivec2(-1, -2)) + sq1m(s + ivec2(1, -2)) + sq1m(s + ivec2(-2, -1)) + sq1m(s + ivec2(2, -1))
                          + sq1m(s + ivec2(-2, 1)) + sq1m(s + ivec2(2, 1)) + sq1m(s + ivec2(-1, 2)) + sq1m(s + ivec2(1, 2)));
    float rbvarp = EPSSQ + GAUSSEVEN[0] * (sq1p(s + ivec2(0, -1)) + sq1p(s + ivec2(-1, 0)) + sq1p(s + ivec2(1, 0)) + sq1p(s + ivec2(0, 1)))
        + GAUSSEVEN[1] * (sq1p(s + ivec2(-1, -2)) + sq1p(s + ivec2(1, -2)) + sq1p(s + ivec2(-2, -1)) + sq1p(s + ivec2(2, -1))
                          + sq1p(s + ivec2(-2, 1)) + sq1p(s + ivec2(2, 1)) + sq1p(s + ivec2(-1, 2)) + sq1p(s + ivec2(1, 2)));
    float pmwt = rbvarm / (rbvarp + rbvarm);
    // bound the interpolation in regions of high saturation
    if (rbp < c) {
        if (2.0 * rbp < c) {
            rbp = median3(rbp, Cf(s + ivec2(1, -1)), Cf(s + ivec2(-1, 1)));
        } else {
            float pw = 2.0 * (c - rbp) / (EPS + rbp + c);
            rbp = pw * rbp + (1.0 - pw) * median3(rbp, Cf(s + ivec2(1, -1)), Cf(s + ivec2(-1, 1)));
        }
    }
    if (rbm < c) {
        if (2.0 * rbm < c) {
            rbm = median3(rbm, Cf(s + ivec2(1, 1)), Cf(s + ivec2(-1, -1)));
        } else {
            float mw = 2.0 * (c - rbm) / (EPS + rbm + c);
            rbm = mw * rbm + (1.0 - mw) * median3(rbm, Cf(s + ivec2(1, 1)), Cf(s + ivec2(-1, -1)));
        }
    }
    if (rbp > u_clip) rbp = median3(rbp, Cf(s + ivec2(1, -1)), Cf(s + ivec2(-1, 1)));
    if (rbm > u_clip) rbm = median3(rbm, Cf(s + ivec2(1, 1)), Cf(s + ivec2(-1, -1)));
    emit(p, vec4(rbm, rbp, pmwt, 0.0));
}
