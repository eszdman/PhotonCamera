//
// Created by eszdman on 19.09.2026.
//

#pragma once

#include <algorithm>
#include <string>
#include <vector>

#include "align_pipeline.h"

// Optional level-0 (raw/2) refinement stage: block Lucas-Kanade
// (Gauss-Newton) refinement of the hierarchical alignment, seeded by the
// level-1 field that stage 2 produces. Additive to the split pipeline:
//
//   once per burst:
//     alignburst_base               stage 1 (unchanged)
//     flow_base           -> gprod  per-tile gradient products at level 0
//
//   per alt frame:
//     alignburst                     stage-2 field (level-1 grid)
//     flow_refine                    refined field, level-0 grid
//     median3x3_2ch                  caller side, now on the level-0 field
//
// Why level 0: the finest level the hierarchical stage aligns is raw/4 -
// level 0 of the base pyramid is materialized but unused. A
// continuous-gradient refinement there doubles the vector-field density
// again (tile origins every 16 raw px, 1:1 with merge atlas cells), yields
// sub-texel-continuous vectors without the candidate raster or parabolic
// fit, and follows local motion per tile instead of per 64-raw-px block.
//
// Tile geometry is inherited from AlignParams (same tile_size/tile_stride
// as every other level); FlowParams carries only the solver knobs. The
// seed must be the stage-2 output with its default min_level = 1: the
// resample below assumes exactly one pyramid step between the seed grid
// and the level-0 grid.
//
// Output convention matches stage 2: alignments(c, tx, ty) float, in
// raw/2 texel units, alt(p + d) ~= base(p), on the level-0 overlapped
// tile grid (level_tiles(raw_dim, 0, tile_size, tile_stride)).
namespace halide_align {

struct FlowParams {
    // Gauss-Newton passes per tile. 3 typically; 4-5 for night frames.
    int iterations = 3;
    // Acceptance gate, encoded u8 units on the summed 256-texel tile cost:
    // the refined vector replaces the seed only if its residual cost beats
    // the seed's by more than this. Same philosophy as the hierarchical
    // stage's significance gate - without it, flat/saturated tiles wander
    // on noise (measured 9-17 texel drift in the raster-search version).
    // 0 disables refinement acceptance (every tile keeps its seed).
    int gate_margin = 64;
    // Per-iteration displacement clamp, raw2x texels. Keeps one bad
    // linearization from throwing a tile across the frame; convergence
    // from a one-level seed needs at most a couple of texels per step.
    float step_limit = 2.0f;
    // Total-travel clamp from the seed, raw2x texels. The refinement is a
    // REFINEMENT: it may correct up to about one coarse-texel mislock
    // (4 raw2x texels at level 2), never re-lock onto a different
    // texture period. Without it, tiles whose seed already sits in a
    // wrong cost basin (the hierarchical stage's known ~10% sub-Nyquist
    // mislocks) descend that basin further instead of staying put.
    float max_travel = 4.0f;
    // Steps below this (per axis sum) count as converged and are not
    // applied - avoids accumulating micro-jitter after convergence.
    float min_step = 0.03f;
    // Levenberg damping as a fraction of the gradient-matrix trace.
    // Inflates the diagonal so the 2x2 solve stays positive-definite on
    // near-aperture tiles; the det guard below skips fully flat ones.
    float damping = 1e-3f;
    // Reference schedule: every stage materialized at compute_root with
    // plain (non-rfactored) reductions. Value-identical, just slow.
    bool simple_schedule = false;
    // Apply NO hand schedules at all (every stage left unscheduled) so an
    // external scheduler - e.g. an autoscheduler via Pipeline::
    // apply_autoscheduler - can place the whole graph. The caller must
    // supply estimates on every input Func and the output.
    bool no_schedule = false;
};

namespace detail {

// Bilinear sample of a u8 level at float coordinates, in the STORED
// (sqrt-encoded) domain: comparing and warping in the same domain keeps
// residuals noise-normalized at every brightness, matching the matcher.
inline Halide::Expr bilinear_u8(const Halide::Func &img, const Halide::Expr &fx,
                                const Halide::Expr &fy) {
    using namespace Halide;
    Expr x0 = floor(fx), y0 = floor(fy);
    Expr xi = cast<int32_t>(x0), yi = cast<int32_t>(y0);
    Expr wx = fx - x0, wy = fy - y0;
    Expr a = cast<float>(img(xi, yi));
    Expr b = cast<float>(img(xi + 1, yi));
    Expr c = cast<float>(img(xi, yi + 1));
    Expr d = cast<float>(img(xi + 1, yi + 1));
    return (a * (1.0f - wx) + b * wx) * (1.0f - wy) +
           (c * (1.0f - wx) + d * wx) * wy;
}

}  // namespace detail

// ---- Flow stage F1: gradient products, once per burst ----------------------
// gprod(c, tx, ty) -> int32, the 2x2 Gauss-Newton normal matrix per level-0
// tile, summed over the tile: c = 0: sum ix*ix, 1: sum ix*iy (SIGNED -
// correlations are negative about as often as positive), 2: sum iy*iy.
// Central differences of the stored u8 level; ix, iy in [-255, 255], so
// every sum fits int32 with room to spare (<= 255^2 * 256 < 2^24).
// base_l0 must be the same padded, encoded level-0 buffer stage 2 consumes
// (read taps reach +-1 texel past the tile extent; the padding covers it).
//
// Only ~550 KB per burst (3 x 249 x 186 x 4 B for a 4000x3000 raw) and the
// refine stage recomputes ix/iy on the fly, so per-pixel gradients are
// never materialized.
inline Halide::Func build_flow_base(Halide::Func base_l0, const AlignParams &p,
                                    bool no_schedule = false) {
    using namespace Halide;
    Var c("fb_c"), tx("fb_tx"), ty("fb_ty");
    const int T = p.tile_size, S = p.tile_stride;

    RDom r(0, T, 0, T);
    Expr px = tx * S + r.x, py = ty * S + r.y;
    Expr gx = cast<int32_t>(base_l0(px + 1, py)) - cast<int32_t>(base_l0(px - 1, py));
    Expr gy = cast<int32_t>(base_l0(px, py + 1)) - cast<int32_t>(base_l0(px, py - 1));

    // Three single-value reductions, each with the matcher-cost rfactor
    // shape - the proven-fast form on arm64. (Both a per-channel select
    // walk and a 3-tuple partial reduction measured ~50x slower: the
    // multi-value partial defeats the vectorizer.) Three passes over an
    // L1-resident tile, once per burst.
    Func ch0("flow_gxx"), ch1("flow_gxy"), ch2("flow_gyy");
    ch0(tx, ty) = cast<int32_t>(0);
    ch1(tx, ty) = cast<int32_t>(0);
    ch2(tx, ty) = cast<int32_t>(0);
    ch0(tx, ty) += gx * gx;
    ch1(tx, ty) += gx * gy;
    ch2(tx, ty) += gy * gy;

    // ABI: (c, tx, ty) int32 buffer.
    Func out("flow_gprod_c");
    out(c, tx, ty) = select(c == 0, ch0(tx, ty),
                            select(c == 1, ch1(tx, ty), ch2(tx, ty)));

    if (!no_schedule) {
    out.compute_root().parallel(ty);
    ch0.compute_root().parallel(ty);
    ch1.compute_root().parallel(ty);
    ch2.compute_root().parallel(ty);
    if (!p.simple_schedule) {
        // Same rfactor shape as the matcher cost: per-column partials,
        // 16-wide split into two unrolled 8-wide vector chunks, two rows in
        // flight (the measured-best shape on arm64).
        Var u("fb_u"), uo("fb_uo"), ui("fb_ui");
        Func p0 = ch0.update().rfactor({{r.x, u}});
        p0.compute_at(ch0, tx);
        p0.split(u, uo, ui, 8).unroll(uo).vectorize(ui);
        p0.update().split(u, uo, ui, 8).unroll(uo).vectorize(ui).unroll(r.y, 2);
        Func p1 = ch1.update().rfactor({{r.x, u}});
        p1.compute_at(ch1, tx);
        p1.split(u, uo, ui, 8).unroll(uo).vectorize(ui);
        p1.update().split(u, uo, ui, 8).unroll(uo).vectorize(ui).unroll(r.y, 2);
        Func p2 = ch2.update().rfactor({{r.x, u}});
        p2.compute_at(ch2, tx);
        p2.split(u, uo, ui, 8).unroll(uo).vectorize(ui);
        p2.update().split(u, uo, ui, 8).unroll(uo).vectorize(ui).unroll(r.y, 2);
    }
    }
    return out;
}

// ---- Flow stage F2: per-frame Gauss-Newton refinement -----------------------
// Inputs:
//   base_l0  padded encoded level 0 of the base frame (stage-1 output)
//   gprod    flow_base output, (3, nt0x, nt0y) int32
//   alt_raw  the alternate frame's raw buffer (u16 counts or normalized
//            fp16 - anything decimate_u8 handles via p.white_level)
//   seed     the stage-2 alignment field, (2, nt1x, nt1y) float, raw2x
//            texel units (values NOT rounded - the parabolic sub-texel
//            estimate is the whole point of a continuous initialization)
// Output: alignments(c, tx, ty) float, raw2x texels, level-0 grid.
//
// Per tile: seed = bilinear resample of the level-1 field onto the level-0
// grid (tile t's center lands on level-1 tile centers for even t and
// midway between them for odd t - one formula covers both), then K
// Gauss-Newton steps of the damped 2x2 system, then the acceptance gate.
inline Halide::Func build_flow_refine(Halide::Func base_l0, Halide::Func gprod,
                                      Halide::Func alt_raw, Halide::Func seed,
                                      Halide::Expr raw_w, Halide::Expr raw_h,
                                      const AlignParams &p, const FlowParams &fp) {
    using namespace Halide;
    Var ch("fr_ch"), tx("fr_tx"), ty("fr_ty");

    const int T = p.tile_size, S = p.tile_stride;
    const int K = std::max(0, fp.iterations);
    std::vector<int> B = level_paddings(p);
    // Bilinear taps stay inside the padded level for any |d| up to this;
    // hierarchical reach is ~80 raw2x texels, so this never binds in
    // practice - it exists to keep bounds provable for wild seeds.
    const float reach = (float)(B[0] - T - 2);

    Expr lw0 = raw_w / 2, lh0 = raw_h / 2;

    // Alt level 0: the same decimate + mirror-pad as the hierarchical
    // stage builds per frame (only level 0 is needed here).
    Func dec = detail::decimate_u8(alt_raw, p.white_level, p.sqrt_encode, "flow_alt_dec");
    Func alt0 = detail::pad_level0(dec, lw0, lh0, B[0], "flow_alt_p0");
    // dec's domain is exactly the image: everything outside is handled by
    // alt0's mirror condition. Declaring it keeps tile-aligning schedulers
    // from rounding the stored region past the raw buffer's edge (an
    // autoscheduler-aligned region starting at -63 read the raw buffer at
    // -126, caught by the runtime bounds check).
    dec.bound(dec.args()[0], 0, lw0);
    dec.bound(dec.args()[1], 0, lh0);
    if (!fp.no_schedule) {
        dec.compute_root().vectorize(dec.args()[0], 16);
        alt0.compute_root().vectorize(alt0.args()[0], 16);
    }

    // Seed resample onto the level-0 grid. Level-0 tile t has its center
    // at 4t+4 level-1 texels; level-1 tile k spans [8k, 8k+16), so t>>1
    // and (t+1)>>1 are the two bracketing level-1 tiles (equal at the grid
    // edge - clamped, and the average degenerates to the single value).
    // One pyramid step assumed between the grids (min_level = 1).
    Expr lws = lw0, lhs = lh0;
    for (int i = 0; i < p.min_level; i++) { lws = lws / 2; lhs = lhs / 2; }
    Expr nsx = max((lws - T) / S + 1, 1);
    Expr nsy = max((lhs - T) / S + 1, 1);
    Func d0("flow_d0");
    {
        Expr ax = clamp(tx / 2, 0, nsx - 1), bx = clamp((tx + 1) / 2, 0, nsx - 1);
        Expr ay = clamp(ty / 2, 0, nsy - 1), by = clamp((ty + 1) / 2, 0, nsy - 1);
        d0(tx, ty) = Tuple(
            clamp(0.5f * (seed(0, ax, ay) + seed(0, bx, by)), -reach, reach),
            clamp(0.5f * (seed(1, ax, ay) + seed(1, bx, by)), -reach, reach));
    }

    // Gauss-Newton chain: K (accumulate -> solve) pairs. acc_j walks the
    // tile at d_{j-1} producing the steepest-descent vector b and the
    // residual cost there; d_j solves the precomputed normal system with
    // damping and per-step clamps. All pure stages - no update recursion
    // between iterations, trivially deterministic under parallel(ty).
    RDom r(0, T, 0, T);
    Expr px = tx * S + r.x, py = ty * S + r.y;
    Func dprev = d0;
    std::vector<Func> accs, dsolver;
    for (int j = 1; j <= K; j++) {
        std::string js = std::to_string(j);
        Func acc("flow_acc" + js);
        Expr wx = cast<float>(px) + dprev(tx, ty)[0];
        Expr wy = cast<float>(py) + dprev(tx, ty)[1];
        Expr A = detail::bilinear_u8(alt0, wx, wy);
        Expr res = A - cast<float>(base_l0(px, py));
        Expr gx = cast<int32_t>(base_l0(px + 1, py)) - cast<int32_t>(base_l0(px - 1, py));
        Expr gy = cast<int32_t>(base_l0(px, py + 1)) - cast<int32_t>(base_l0(px, py - 1));
        acc(tx, ty) = Tuple(0.0f, 0.0f, 0.0f);
        acc(tx, ty) += Tuple(cast<float>(gx) * res, cast<float>(gy) * res, abs(res));

        // Damped 2x2 solve. det is >= damping*trace for any PSD gradient
        // matrix, so the guard only fires on numerically flat tiles (trace
        // 0 => det 0), which stay at their seed. The discarded branch may
        // divide by zero to NaN - select() never takes it, so it cannot
        // leak.
        Func dn("flow_d" + js);
        Expr Gxx = cast<float>(gprod(0, tx, ty));
        Expr Gxy = cast<float>(gprod(1, tx, ty));
        Expr Gyy = cast<float>(gprod(2, tx, ty));
        Expr tr = Gxx + Gyy;
        Expr lam = fp.damping * tr;
        Expr det = (Gxx + lam) * (Gyy + lam) - Gxy * Gxy;
        Expr bx = acc(tx, ty)[0], by = acc(tx, ty)[1];
        Expr lim = fp.step_limit;
        Expr sx = clamp(-((Gyy + lam) * bx - Gxy * by) / det, -lim, lim);
        Expr sy = clamp(-((Gxx + lam) * by - Gxy * bx) / det, -lim, lim);
        Expr step = abs(sx) + abs(sy);
        Expr alive = det > 1e-6f * tr * tr;
        Expr prevx = dprev(tx, ty)[0], prevy = dprev(tx, ty)[1];
        // Travel is measured from the SEED: the chain may correct, not
        // re-lock (see max_travel).
        Expr lox = d0(tx, ty)[0] - fp.max_travel, hix = d0(tx, ty)[0] + fp.max_travel;
        Expr loy = d0(tx, ty)[1] - fp.max_travel, hiy = d0(tx, ty)[1] + fp.max_travel;
        dn(tx, ty) = Tuple(
            clamp(select(alive && step >= fp.min_step, prevx + sx, prevx), lox, hix),
            clamp(select(alive && step >= fp.min_step, prevy + sy, prevy), loy, hiy));

        accs.push_back(acc);
        dsolver.push_back(dn);
        dprev = dn;
    }

    // Final residual cost at d_K (the accumulated cost inside the loop is
    // measured at d_{K-1}); the gate must compare costs AT the vectors it
    // is choosing between. Skipped entirely when K == 0.
    Func cf("flow_cost");
    if (K > 0) {
        Expr wx = cast<float>(px) + dprev(tx, ty)[0];
        Expr wy = cast<float>(py) + dprev(tx, ty)[1];
        Expr A = detail::bilinear_u8(alt0, wx, wy);
        Expr res = A - cast<float>(base_l0(px, py));
        cf(tx, ty) = 0.0f;
        cf(tx, ty) += abs(res);
    }

    Func out("flow_alignments");
    if (K > 0) {
        Expr cost_seed = accs[0](tx, ty)[2];  // cost at d_0 = the seed
        Expr take = cf(tx, ty) + (float)fp.gate_margin < cost_seed;
        out(ch, tx, ty) = select(ch == 0,
                                 select(take, dprev(tx, ty)[0], d0(tx, ty)[0]),
                                 select(take, dprev(tx, ty)[1], d0(tx, ty)[1]));
    } else {
        out(ch, tx, ty) = select(ch == 0, d0(tx, ty)[0], d0(tx, ty)[1]);
    }

    // ---- schedule -----------------------------------------------------------
    // Fused: the whole per-tile chain (K accumulate passes, solves, final
    // cost, gate) runs inside the output's tile loop, so the tile's ~2 KB
    // working set stays in L1 across all K passes instead of streaming the
    // whole frame K+1 times at compute_root. The per-column rfactor
    // partials are the same 8-wide two-chunk shape the matcher cost uses -
    // the reduction is what must vectorize; the 16-value finalize and the
    // 2x2 solve are per-tile scalars, lost in the noise.
    if (fp.no_schedule) {
        // External scheduler places everything (see FlowParams).
    } else if (fp.simple_schedule) {
        for (size_t j = 0; j < accs.size(); j++) accs[j].compute_root().parallel(ty);
        for (size_t j = 0; j < dsolver.size(); j++)
            dsolver[j].compute_root().parallel(ty);
        if (K > 0) cf.compute_root().parallel(ty);
        d0.compute_root().parallel(ty);
        out.compute_root().parallel(ty).vectorize(tx, 4);
    } else {
        out.compute_root().parallel(ty);
        d0.compute_at(out, tx);
        for (size_t j = 0; j < dsolver.size(); j++) dsolver[j].compute_at(out, tx);
        for (size_t j = 0; j < accs.size(); j++) {
            std::string js = std::to_string(j);
            accs[j].compute_at(out, tx);
            Var u("fu" + js), uo("fuo" + js), ui("fui" + js);
            Func part = accs[j].update().rfactor({{r.x, u}});
            part.compute_at(out, tx);
            part.split(u, uo, ui, 8).unroll(uo).vectorize(ui);
            part.update().split(u, uo, ui, 8).unroll(uo).vectorize(ui).unroll(r.y, 2);
        }
        if (K > 0) {
            cf.compute_at(out, tx);
            Var u("fcu"), uo("fcuo"), ui("fcui");
            Func part = cf.update().rfactor({{r.x, u}});
            part.compute_at(out, tx);
            part.split(u, uo, ui, 8).unroll(uo).vectorize(ui);
            part.update().split(u, uo, ui, 8).unroll(uo).vectorize(ui).unroll(r.y, 2);
        }
    }
    return out;
}

}  // namespace halide_align
