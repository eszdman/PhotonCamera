//
// Created by eszdman on 18.09.2026.
//

#pragma once

#include <algorithm>
#include <cmath>
#include <string>
#include <vector>

#include "Halide.h"
#include "align_pads.h"

// Hierarchical tile alignment for raw bursts, CPU/Halide.
//
// The pipeline is split in two stages for burst use (the reference pyramid
// is built once per burst):
//
//   Stage 1 (ONCE per burst), build_base_pyramid:
//     raw (u16 Bayer)
//       -> decimate: 2x2 quad average, normalized to u8 ("raw2x", level 0)
//       -> Gaussian pyramid [1 2 1]/4 stride 2, levels 1..max_level,
//          each level MATERIALIZED with a mirror-padded border
//     The precise fp16 recipe uses Q3 u16 encoded samples and a five-tap
//     [1 4 6 4 1]/16 pyramid instead. This never filters the merged CFA data.
//
//   Stage 2 (per alt frame), build_alignments(base_levels, alt_raw):
//       -> same decimate+pyramid for the alt frame (necessarily per frame)
//       -> per level, coarsest -> finest:
//            COARSEST level: exhaustive argmin over (2r+1)^2 integer
//              candidates of the saturating-L1 tile distance
//            FINER levels: default 3x3 search around the previous level's
//              alignment upsampled x2; optional wider recovery search at
//              coarse intermediate levels. The finest search stays 3x3.
//       -> bounded coupled squared-error subpixel fit at the finest level,
//          using the existing tile grid (legacy SAD fit remains selectable)
//     The precise recipe additionally searches half-texel coarse candidates,
//     refines levels 4..2, and carries their fractional shifts between levels.
//
// The padded levels make every matcher read in-bounds by construction, so
// its loads are contiguous and vectorize (see README for the measured cost
// of getting this wrong).
//
// Output: alignments(c, tx, ty) -> float32, c = 0 -> dx, 1 -> dy, expressed
// in level-0 (raw2x) texel units with the convention
//
//     alt(p + d) ~= base(p)      (add d to base coordinates when sampling alt)
//
// The tile grid is that of the finest aligned level, OVERLAPPED
// (tile_size-texel tiles every tile_stride texels; the last tile
// ends at the image edge):
//     tx in [0, level_tiles(raw_w, min_level, tile_size, tile_stride)).
//
// Legacy reach: the coarsest level contributes +-search_radius texels at its own
// scale; each finer level adds +-1 at its own scale (the 3x3 refinement), so
// the maximum recoverable displacement in raw2x units is
//     (search_radius + 1) * 2^max_level - 2^min_level
// (78 raw2x texels = 156 raw pixels for the defaults).
namespace halide_align {

struct AlignParams {
    int tile_size = 16;       // texels per tile side, at every level
    // Texels between tile ORIGINS. Overlapping the tiles 50% (16-texel
    // tiles, origins every 8 texels, last tile ending at the image edge)
    // doubles the vector-field density per axis and lets the merge blend a
    // 2x2 tile neighborhood per pixel instead of stepping between block
    // edges. tile_stride == tile_size recovers the non-overlapping grid.
    int tile_stride = 8;
    int search_radius = 4;    // +- texels exhaustive search at the coarsest level
    // Optional recovery searches below the coarsest level; finest stays +/-1.
    int coarse_refine_radius = 1;
    int middle_refine_radius = 1;
    int min_level = 1;        // finest pyramid level to align (0 = raw2x)
    int max_level = 4;        // coarsest pyramid level
    // sqrt (gamma-2.0) encode the u8 pyramid: the effective linear
    // quantization step becomes 2*sqrt(v*white)/255, proportional to the
    // shot-noise sigma, so the quantization floor stays a fixed fraction of
    // photon noise in the shadows (linear u8 makes it 15-58% of sigma near
    // black for 10-bit input). The matcher compares stored sqrt values
    // directly with L1; it does not decode them back to linear values.
    // Stage 1 and stage 2 must be baked with the same value.
    bool sqrt_encode = true;
    // Precision recipe: Q3 encoded u16 values, a five-tap anti-alias filter,
    // half-texel coarse search and bounded refinement on the existing grid.
    // Zero/default values preserve the legacy u8 buffer ABI and algorithm.
    int pyramid_precision = 0;
    bool pyramid_five = false;
    int coarse_iterations = 0;
    int coarse_refine_minlevel = 2;
    int coarse_substeps = 1;
    bool fractional_seed = false;
    // Preserve the coarse fraction where the finest tile cannot support a fit.
    bool inherit_subpixel = false;
    Halide::Expr white_level = Halide::Expr(1023.0f);  // runtime scalar ok
    // Refinement significance gate in encoded u8 units (0 disables the
    // gate: plain argmin, ties to candidate 0).
    int gate_margin = 64;
    // Plain init/update cost schedule (no rfactor partials) - slower but
    // schedule-bug-free reference.
    bool simple_schedule = false;
    // Accuracy experiments keep the output grid and pyramid sizes fixed.
    // Experimental propagation: 0 original; 1 nearest centers; 2 bilinear;
    // 3 image-guided three-parent choice; 4/5 non-local 5x5 (16/64 samples).
    int seed_mode = 0;
    // Gaussian displacement prior: additive squared-distance cost in the
    // encoded SAD domain, replacing the discontinuous significance gate.
    int motion_prior = 0;
    // Coarsest-level global hypothesis; 0 preserves the existing matcher.
    int coarse_prior = 0;
    Halide::Expr coarse_global_enabled = true;
    // Experimental Gaussian displacement prior in negative-log score space,
    // centered on the selected seed and retaining the flat/noise gate.
    int score_prior = 0;
    bool score_prior_coarse = false;
    // Coarsest displacement-score smoothing: 0 off, 1..4 blend with a
    // separable [1 2 1] Gaussian. Never filters the source image samples.
    int score_blur = 0;
    bool score_blur_fine = false;
    // Bounded Gaussian score preference around a per-level global shift,
    // estimated from existing costs of tiles sharing the global seed.
    int score_guide = 0;
    // 0 original per-tile gate, 1 averaged-noise gate, 2 finer levels only.
    int score_global_noise_gate = 0;
    bool score_noise = false;
    // Weak, bounded Gaussian around the inherited seed, enabled only when
    // modeled noise exceeds the encoded pyramid's quantization floor.
    int score_center = 0;
    // Only relax the seed gate where measured texture exceeds modelled noise.
    bool score_texture = false;
    // Sum of reference/source shot and read variances after exposure scaling:
    // Var(reference-source) = noise_s * signal + noise_o in normalized raw.
    Halide::Expr noise_s = 0.0f, noise_o = 0.0f;
    // 0: original in-raster parabola; 1: five-point L2 fit at every winner;
    // 2: symmetric-gradient solve; 3: coupled squared-error fit (default).
    int subpixel_mode = 3;
    bool spatial_weight = false;
};

// Number of tiles along one dimension of the alignment grid at `level`:
// (dim - tile) / stride + 1, at least 1. With the overlapping convention
// (stride = tile/2) the last tile ends exactly at the image edge and the
// grid is 2x denser per axis than the non-overlapping ceil(dim/tile).
inline int level_tiles(int raw_dim, int level, int tile_size, int tile_stride) {
    int d = raw_dim / 2;
    for (int i = 0; i < level; i++) d /= 2;
    return std::max(1, (d - tile_size) / tile_stride + 1);
}

// Mirror-pad amount per level: large enough that every matcher read (tile
// extent + upsampled seed + search radius) and every gauss read of the
// level below (2x-1 .. 2x+1) is inside the stored level. Delegates to
// align_pads.h - the single source shared with the AOT harness and the JNI
// wrappers that allocate the base level buffers (see align_pads.h).
inline std::vector<int> level_paddings(const AlignParams &p) {
    PadParams pads;
    pads.tile_size = p.tile_size;
    pads.search_radius = p.search_radius;
    pads.max_level = p.max_level;
    return level_paddings(pads);
}

namespace detail {

using namespace Halide;

inline Func decimate_u8(Func raw, const Expr &white_level, bool sqrt_encode,
                        const std::string &n, int precision = 0) {
    Var x("dx"), y("dy");
    Func f(n);
    // Float-native quad average: correct for BOTH input flavors - u16 raw
    // counts (white_level = sensor white) and fp16-normalized frames
    // (white_level = 1.0 / layerMpy, the PhotonCamera path). An integer
    // cast here truncates normalized 0..1 values to zero and turns the
    // whole pyramid black - every candidate then ties and the aligner
    // outputs a constant diagonal offset.
    Expr quadf = (cast<float>(raw(2 * x, 2 * y)) +
                  cast<float>(raw(2 * x + 1, 2 * y)) +
                  cast<float>(raw(2 * x, 2 * y + 1)) +
                  cast<float>(raw(2 * x + 1, 2 * y + 1)));
    Expr vn = quadf / (4.0f * white_level);  // 0..1 linear
    Expr v = sqrt_encode ? sqrt(vn) * 255.0f : vn * 255.0f;
    const int scale = 1 << precision;
    f(x, y) = cast(precision ? UInt(16) : UInt(8), clamp(v * scale + 0.5f, 0.0f, 255.0f * scale));
    return f;
}

// Mirrored, materialized level 0, defined over [-B0, lw0 + B0) x [-B0, lh0 + B0).
inline Func pad_level0(Func dec, const Expr &lw0, const Expr &lh0, int B0,
                       const std::string &n) {
    Func b = BoundaryConditions::mirror_interior(dec, {{0, lw0}, {0, lh0}});
    Func out(n);
    Var x("px"), y("py");
    out(x, y) = b(x, y);
    return out;
}

struct GaussLevel {
    Func hx, out;
};

// One padded downsample step: [1 2 1]/4, or [1 4 6 4 1]/16 for the precise
// recipe. in: padded level l-1; out: padded level l (negative-min domains).
inline GaussLevel gauss_down(const Func &in, const std::string &n, int precision = 0, bool five = false) {
    GaussLevel g;
    g.hx = Func(n + "_hx");
    g.out = Func(n);
    Var x("gx"), y("gy");
    if (five) {
        // Q3: horizontal sum <=32640; the vertical sum uses u32.
        const int weights[5]={1,4,6,4,1};
        Expr h=cast<uint32_t>(0),v=cast<uint32_t>(0);
        for(int k=-2;k<=2;k++) h+=weights[k+2]*cast<uint32_t>(in(2*x+k,y));
        g.hx(x,y)=cast<uint16_t>(h);
        for(int k=-2;k<=2;k++) v+=weights[k+2]*cast<uint32_t>(g.hx(x,2*y+k));
        g.out(x,y)=cast(precision?UInt(16):UInt(8),(v+128)/256);
    } else if (!precision) {
    Expr h = (cast<uint16_t>(in(2 * x - 1, y)) +
              2 * cast<uint16_t>(in(2 * x, y)) +
              cast<uint16_t>(in(2 * x + 1, y)) + 2) / 4;
    g.hx(x, y) = cast<uint8_t>(h);
    Expr v = (cast<uint16_t>(g.hx(x, 2 * y - 1)) +
              2 * cast<uint16_t>(g.hx(x, 2 * y)) +
              cast<uint16_t>(g.hx(x, 2 * y + 1)) + 2) / 4;
    g.out(x, y) = cast<uint8_t>(v);
    } else {
        // Retain fractional encoded values and round once per 2D filter.
        // Q3: input <=2040, horizontal sum <=8160, vertical sum <=32640.
        g.hx(x, y) = cast<uint16_t>(in(2*x-1,y) + 2*in(2*x,y) + in(2*x+1,y));
        g.out(x, y) = cast<uint16_t>((g.hx(x,2*y-1) + 2*g.hx(x,2*y)
                                  + g.hx(x,2*y+1) + 8) / 16);
    }
    return g;
}

// Full decimate+pad+gauss chain for one raw frame. levels[l] is defined over
// [-B_l, lw_l + B_l) x [-B_l, lh_l + B_l) and materialized at compute_root.
inline void build_pyramid_levels(Func raw, const Expr &raw_w, const Expr &raw_h,
                                 const AlignParams &p, const std::vector<int> &B,
                                 const std::vector<Expr> &lw, const std::vector<Expr> &lh,
                                 const std::string &prefix, std::vector<Func> &levels) {
    Var x("sx"), y("sy");
    Func dec = decimate_u8(raw, p.white_level, p.sqrt_encode, prefix + "_dec", p.pyramid_precision);
    levels[0] = pad_level0(dec, lw[0], lh[0], B[0], prefix + "_p0");
    dec.compute_root().vectorize(dec.args()[0], 16);
    levels[0].compute_root().vectorize(levels[0].args()[0], 16);
    for (int l = 1; l <= p.max_level; l++) {
        Func input=levels[l-1];
        if (p.pyramid_five) {
            // Only the outermost padded halo needs one extra filter tap.
            Func bounded(prefix+"_filter_input_"+std::to_string(l));
            bounded(x,y)=input(clamp(x,-B[l-1],lw[l-1]+B[l-1]-1),
                               clamp(y,-B[l-1],lh[l-1]+B[l-1]-1));
            input=bounded;
        }
        GaussLevel g = gauss_down(input, prefix + "_l" + std::to_string(l), p.pyramid_precision, p.pyramid_five);
        levels[l] = g.out;
        g.hx.compute_at(g.out, g.out.args()[1]).vectorize(g.hx.args()[0], 16);
        g.out.compute_root().vectorize(g.out.args()[0], 16);
    }
}

}  // namespace detail

// ---- Stage 1: base pyramid, once per burst --------------------------------
// levels[l] is a Func over [-B_l, lw_l + B_l) x [-B_l, lh_l + B_l) (see
// level_paddings). For JIT use, realize the returned Pipeline into Buffers
// with min = -B_l (Buffer::translate); pass those Buffers to Stage 2 wrapped
// in trivial Funcs. For AOT use, this maps to the alignburst_base outputs.
inline Halide::Pipeline build_base_pyramid(Halide::Func base_raw,
                                           Halide::Expr raw_w, Halide::Expr raw_h,
                                           const AlignParams &p,
                                           std::vector<Halide::Func> &levels) {
    using namespace Halide;
    levels.resize(p.max_level + 1);
    std::vector<Expr> lw(p.max_level + 1), lh(p.max_level + 1);
    lw[0] = raw_w / 2;
    lh[0] = raw_h / 2;
    for (int l = 1; l <= p.max_level; l++) {
        lw[l] = lw[l - 1] / 2;
        lh[l] = lh[l - 1] / 2;
    }
    detail::build_pyramid_levels(base_raw, raw_w, raw_h, p, level_paddings(p),
                                 lw, lh, "base", levels);
    return Halide::Pipeline(std::vector<Halide::Func>(levels.begin(), levels.end()));
}

// ---- Stage 2: alignment of one alt frame against precomputed base levels --
// base_levels[l] must be callable over the padded domain of level l (e.g. a
// Func wrapped around a Buffer realized by Stage 1 - see app/main.cpp).
inline Halide::Func build_alignments(const std::vector<Halide::Func> &base_levels,
                                     Halide::Func alt_raw,
                                     Halide::Expr raw_w, Halide::Expr raw_h,
                                     const AlignParams &p,
                                     std::vector<Halide::Func> *debug_level_aligns = nullptr,
                                     std::vector<Halide::Func> *debug_level_costs = nullptr) {
    using namespace Halide;
    Var x("x"), y("y"), c("c"), tx("tx"), ty("ty"), ch("ch");

    const int T = p.tile_size;
    const int S = p.tile_stride;    // texels between tile origins (50% overlap)
    const int R = p.search_radius;  // exhaustive raster at the coarsest level

    std::vector<int> B = level_paddings(p);
    std::vector<Expr> lw(p.max_level + 1), lh(p.max_level + 1);
    lw[0] = raw_w / 2;
    lh[0] = raw_h / 2;
    for (int l = 1; l <= p.max_level; l++) {
        lw[l] = lw[l - 1] / 2;
        lh[l] = lh[l - 1] / 2;
    }

    // Alt pyramid: necessarily per frame.
    std::vector<Func> alt_levels(p.max_level + 1);
    detail::build_pyramid_levels(alt_raw, raw_w, raw_h, p, B, lw, lh, "alt", alt_levels);

    // ---- hierarchical alignment -------------------------------------------
    // aligns[l](ch, tx, ty): integer alignment of tile (tx, ty) at level l,
    // in level-l texel units.
    struct AlignLevel {
        Func cost, best, align;
    };
    std::vector<AlignLevel> lv(p.max_level + 1);
    std::vector<Func> global_shift(p.max_level + 1);
    Func subpix("subpix");
    Expr finest_fraction=0.f;

    for (int l = p.max_level; l >= p.min_level; l--) {
        AlignLevel &st = lv[l];
        const std::string ln = "l" + std::to_string(l);

        // Default finer-level search is 3x3. Optional wider recovery searches
        // are confined to the two coarse intermediate levels. Existing pads
        // and the seed cap below cover the supported radii (up to four).
        const int substeps = l == p.max_level ? p.coarse_substeps : 1;
        const int cost_scale = (1 << p.pyramid_precision) * substeps * substeps;
        const int radius = l == p.max_level ? R : l == p.min_level ? 1
            : l == p.max_level - 1 ? p.coarse_refine_radius
            : l == p.max_level - 2 ? p.middle_refine_radius : 1;
        const int Rl = radius * substeps;
        const int Sl = 2 * Rl + 1;
        const int NCl = Sl * Sl;

        // Previous (coarser) level's alignment, upsampled x2; zero seed at
        // the coarsest level. Clamped into the padded border so that all
        // matcher reads are in-bounds by construction.
        //
        // Overlapped grids: fine tile t (origin S*t texels) has its center
        // at coarse texel S*t/2 + T/4 = 4t+4 for S=8, T=16 - inside coarse
        // tile (t+1)/2 (origin 8k <= 4t+4), so the seed comes from
        // k = (t+1)/2, clamped to the coarse grid extent (also keeps bounds
        // inference from evaluating tiles past the coarse grid).
        Expr prev_dx = cast<int32_t>(0), prev_dy = cast<int32_t>(0);
        Expr fraction_x=0.f,fraction_y=0.f;
        if (l < p.max_level) {
            const Expr cap = cast<int32_t>(B[l] - radius - 1);
            Expr ntcx = max((lw[l + 1] - T) / S + 1, 1);
            Expr ntcy = max((lh[l + 1] - T) / S + 1, 1);
            Expr kx = clamp((tx + 1) / 2, 0, ntcx - 1);
            Expr ky = clamp((ty + 1) / 2, 0, ntcy - 1);
            auto parent = [&](int ch) {
                Expr d = 2 * lv[l + 1].align(ch, kx, ky);
                return (p.coarse_iterations || p.coarse_substeps > 1) ? cast<int>(floor(d + 0.5f)) : d;
            };
            prev_dx = clamp(parent(0), -cap, cap);
            prev_dy = clamp(parent(1), -cap, cap);
            if (p.seed_mode > 0) {
                // Tile centers, expressed in the coarser tile-index grid.
                Expr qx = tx * 0.5f - float(T - 1) / (4.0f * S);
                Expr qy = ty * 0.5f - float(T - 1) / (4.0f * S);
                if (p.seed_mode == 1) {
                    Expr ix = clamp(cast<int>(floor(qx + 0.5f)), 0, ntcx - 1);
                    Expr iy = clamp(cast<int>(floor(qy + 0.5f)), 0, ntcy - 1);
                    prev_dx = clamp(cast<int>(floor(2.f * lv[l + 1].align(0, ix, iy) + .5f)), -cap, cap);
                    prev_dy = clamp(cast<int>(floor(2.f * lv[l + 1].align(1, ix, iy) + .5f)), -cap, cap);
                } else {
                    Expr ix = cast<int>(floor(qx)), iy = cast<int>(floor(qy));
                    Expr fx = qx-floor(qx), fy = qy-floor(qy);
                    auto interp = [&](int component) {
                        auto tap = [&](int ox, int oy) {
                            return cast<float>(lv[l+1].align(component,
                                clamp(ix+ox,0,ntcx-1),clamp(iy+oy,0,ntcy-1)));
                        };
                        return cast<int>(floor(2.0f * lerp(lerp(tap(0,0),tap(1,0),fx),
                            lerp(tap(0,1),tap(1,1),fx),fy) + 0.5f));
                    };
                    prev_dx = clamp(interp(0), -cap, cap);
                    prev_dy = clamp(interp(1), -cap, cap);
                }
            }
        }

        if (p.fractional_seed && l < p.max_level && (l > p.min_level || p.inherit_subpixel)) {
            Expr nx=max((lw[l+1]-T)/S+1,1),ny=max((lh[l+1]-T)/S+1,1);
            Expr ix=clamp(p.seed_mode==1 ? tx/2 : (tx+1)/2,0,nx-1);
            Expr iy=clamp(p.seed_mode==1 ? ty/2 : (ty+1)/2,0,ny-1);
            Expr dx=clamp(2.f*lv[l+1].align(0,ix,iy),float(-B[l]+radius+1),float(B[l]-radius-1));
            Expr dy=clamp(2.f*lv[l+1].align(1,ix,iy),float(-B[l]+radius+1),float(B[l]-radius-1));
            prev_dx=cast<int>(floor(dx+(l==p.min_level?.5f:0.f)));
            prev_dy=cast<int>(floor(dy+(l==p.min_level?.5f:0.f)));
            fraction_x=dx-cast<float>(prev_dx);fraction_y=dy-cast<float>(prev_dy);
        }

        if (l < p.max_level && p.seed_mode >= 3) {
            // Image-guided parent selection, including a non-local 5x5
            // coarse neighborhood. Output density remains unchanged.
            const bool nonlocal=p.seed_mode>=4;
            const int count=nonlocal?25:3, center=nonlocal?12:0;
            const int sample_step=p.seed_mode==4?4:2;
            Expr ntcx=max((lw[l+1]-T)/S+1,1), ntcy=max((lh[l+1]-T)/S+1,1);
            Func hypothesis("hypothesis_"+ln), score("seed_score_"+ln);
            Expr kx=tx/2+(nonlocal ? c%5-2 : select(c==1,select(tx%2==0,-1,1),0));
            Expr ky=ty/2+(nonlocal ? c/5-2 : select(c==2,select(ty%2==0,-1,1),0));
            hypothesis(ch,tx,ty,c)=2*lv[l+1].align(ch,clamp(kx,0,ntcx-1),clamp(ky,0,ntcy-1));
            Expr same=hypothesis(0,tx,ty,c)==hypothesis(0,tx,ty,center)
                   && hypothesis(1,tx,ty,c)==hypothesis(1,tx,ty,center);
            RDom r(0,T/sample_step,0,T/sample_step);
            if(nonlocal)r.where(!same || c==center);
            Expr px=tx*S+sample_step*r.x,py=ty*S+sample_step*r.y;
            score(tx,ty,c)=cast<uint32_t>(0);
            score(tx,ty,c)+=cast<uint32_t>(absd(base_levels[l](px,py),
                alt_levels[l](px+hypothesis(0,tx,ty,c),py+hypothesis(1,tx,ty,c))));
            score.compute_root().reorder(c,tx,ty).parallel(ty);
            score.update().reorder(c,tx,ty).parallel(ty);
            Var u("seed_u");Func partial=score.update().rfactor({{r.x,u}});
            partial.compute_at(score,tx).vectorize(u,T/sample_step);
            partial.update().vectorize(u,T/sample_step).unroll(r.y,2);
            RDom candidate(0,count);Func winner("seed_winner_"+ln);
            Expr cost=score(tx,ty,candidate);
            if(nonlocal) {
                Expr equal_seed=hypothesis(0,tx,ty,candidate)==hypothesis(0,tx,ty,center)
                            && hypothesis(1,tx,ty,candidate)==hypothesis(1,tx,ty,center);
                cost=select(equal_seed,score(tx,ty,center),cost);
                Expr distance=abs(candidate%5-2)+abs(candidate/5-2);
                // Same significance per sampled pixel as the local matcher.
                Expr improves=cost+64/(sample_step*sample_step)<score(tx,ty,center);
                cost=select(improves,cost*32+cast<uint32_t>(distance),
                            cast<uint32_t>((1<<30))+cast<uint32_t>(distance));
            } else cost=cost*4+cast<uint32_t>(candidate);
            winner(tx,ty)=argmin(cost);
            winner.compute_root().parallel(ty);
            Expr index=clamp(winner(tx,ty)[0],0,count-1), cap=B[l]-radius-1;
            prev_dx=clamp(hypothesis(0,tx,ty,index),-cap,cap);
            prev_dy=clamp(hypothesis(1,tx,ty,index),-cap,cap);
        }

        // Saturating L1 tile distance for all candidate displacements at
        // once: cost(tx, ty, c) = sum over the tile of |base - alt|, with
        // candidate c = (ox + Rl) + (oy + Rl) * Sl relative to the seed.
        // rfactor turns the sum into per-column partials that vectorize
        // race-free with contiguous loads (the straightforward init+update
        // or pure sum() forms lower to scalar code on arm64).
        st.cost = Func("cost_" + ln);
        RDom r(0, T, 0, T);
        st.cost(tx, ty, c) = cast<uint32_t>(0);
        Expr base_px = base_levels[l](tx * S + r.x, ty * S + r.y);
        Expr alt_px = alt_levels[l](tx * S + r.x + prev_dx + (c % Sl - Rl),
                                    ty * S + r.y + prev_dy + (c / Sl - Rl));
        if (substeps > 1) {
            Func dense("coarse_samples");
            Expr ix=x/substeps,iy=y/substeps,fx=x%substeps,fy=y%substeps;
            auto tap=[&](int dx,int dy){return cast<uint32_t>(alt_levels[l](ix+dx,iy+dy));};
            dense(x,y)=cast<uint16_t>((substeps-fx)*(substeps-fy)*tap(0,0)
                +fx*(substeps-fy)*tap(1,0)+(substeps-fx)*fy*tap(0,1)+fx*fy*tap(1,1));
            dense.compute_root().vectorize(x,16).parallel(y);
            alt_px=dense((tx*S+r.x+prev_dx)*substeps+c%Sl-Rl,
                         (ty*S+r.y+prev_dy)*substeps+c/Sl-Rl);
            base_px=cast<uint16_t>(base_px)*substeps*substeps;
        }
        if (p.fractional_seed && l < p.max_level && l > p.min_level) {
            Func warped("fractional_seed_tile_"+ln);
            auto tap=[&](int dx,int dy){return cast<float>(alt_levels[l](tx*S+x+prev_dx+dx,ty*S+y+prev_dy+dy));};
            warped(x,y,tx,ty)=cast<uint16_t>(lerp(lerp(tap(0,0),tap(1,0),fraction_x),
                lerp(tap(0,1),tap(1,1),fraction_x),fraction_y)+.5f);
            warped.compute_at(st.cost,tx).vectorize(x,8);
            alt_px=warped(r.x+c%Sl-Rl,r.y+c/Sl-Rl,tx,ty);
        }
        // Compare directly in the STORED (sqrt-encoded) domain. This makes
        // the metric approximately noise-normalized (|sqrt(a)-sqrt(b)| ~
        // |a-b| / (2 sqrt(v)) ~ L1 / shot-noise sigma) AND makes the
        // u8 quantization jitter a uniform +-1 unit regardless of
        // brightness - so an absolute flat penalty below has the same
        // statistical meaning in shadows and highlights. (Decoding to
        // linear on load was tried: quantization-degenerate flat tiles
        // then contain lucky near-zero-cost candidates at wrong shifts
        // that no relative penalty can suppress.)
        Expr pixel_cost = cast<uint32_t>(absd(base_px, alt_px));
        if (p.spatial_weight) {
            Expr xx = (cast<float>(r.x) - (T-1)*0.5f)/(T*0.35f);
            Expr yy = (cast<float>(r.y) - (T-1)*0.5f)/(T*0.35f);
            Expr weight = cast<uint32_t>(floor(16.0f*exp(-0.5f*(xx*xx+yy*yy))+0.5f));
            pixel_cost = (pixel_cost * weight + 4) / 8;
        }
        st.cost(tx, ty, c) += pixel_cost;
        st.cost.compute_root();
        // ty outermost and parallel (not nested under the serial c loop,
        // which would launch NCl parallel regions per level), c inside.
        st.cost.reorder(c, tx, ty).parallel(ty);
        st.cost.update().reorder(c, tx, ty).parallel(ty);

        // Per-column partials: 16 u32 = 64 B per (c, tx). Vectorize 8-wide
        // and fully unroll the two chunks so the accumulator accesses get
        // constant offsets - the backend then keeps the chains in vector
        // registers across the row loop instead of round-tripping L1.
        // Two rows unrolled: two independent accumulator chains in flight
        // (x4 / x8 measured slower - register pressure).
        Var u("u_partial"), uo("u_outer"), ui("u_inner");
        if (p.simple_schedule) {
            // Reference schedule: no rfactor, no vectorization of the
            // reduction - value-identical, just slow.
            st.cost.update().reorder(c, tx, ty);
        } else {
            Func cost_partial = st.cost.update().rfactor({{r.x, u}});
            cost_partial.compute_at(st.cost, tx);
            cost_partial.split(u, uo, ui, 8).unroll(uo).vectorize(ui);
            cost_partial.update()
                .split(u, uo, ui, 8).unroll(uo).vectorize(ui)
                .unroll(r.y, 2);
        }

        Func score = st.cost;
        if (cost_scale > 1) {
            score = Func("encoded_score_" + ln);
            score(tx, ty, c) = (st.cost(tx, ty, c) + cost_scale / 2) / cost_scale;
        }
        if (p.score_blur > 0 && (l == p.max_level || p.score_blur_fine)) {
            score = Func("score_" + ln);
            Expr sum = cast<uint32_t>(0);
            for (int yy = -1; yy <= 1; yy++) for (int xx = -1; xx <= 1; xx++) {
                Expr candidate = clamp(c % Sl + xx, 0, Sl - 1)
                               + Sl * clamp(c / Sl + yy, 0, Sl - 1);
                sum += st.cost(tx, ty, candidate) * ((xx == 0 ? 2 : 1) * (yy == 0 ? 2 : 1));
            }
            score(tx, ty, c) = (st.cost(tx, ty, c) * (4 - p.score_blur)
                            + ((sum + 8) / 16) * p.score_blur + 2) / 4;
            score.compute_root().reorder(c, tx, ty).parallel(ty);
        }
        Func noise_variance("score_noise_variance_" + ln);
        Func texture_variance("score_texture_variance_" + ln);
        Func center_strength("score_center_strength_" + ln);
        if (p.score_noise || p.score_center > 0 || p.coarse_iterations) {
            // Tile brightness is sampled from the existing reference pyramid;
            // no extra image filtering or per-candidate image reads.
            Func brightness("score_brightness_" + ln), scale("score_noise_scale_" + ln);
            RDom sample(0, 4, 0, 4);
            Expr v = cast<float>(base_levels[l](tx * S + sample.x * (T / 4) + T / 8,
                                              ty * S + sample.y * (T / 4) + T / 8)) / (255.0f * (1 << p.pyramid_precision));
            brightness(tx, ty) = Tuple(0.0f, 0.0f);
            brightness(tx, ty) = Tuple(brightness(tx, ty)[0] + v / 16.0f,
                                      brightness(tx, ty)[1] + v * v / 16.0f);
            if (p.score_center > 0 && !p.coarse_iterations) {
                // Fuse tile statistics within one parallel row job. Separate
                // tiny parallel stages are costly on heterogeneous phone CPUs.
                brightness.compute_at(center_strength, ty);
            } else {
                brightness.compute_root().parallel(ty);
                brightness.update().parallel(ty);
            }
            Expr ns = max(p.noise_s, 0.0f), no = max(p.noise_o, 0.0f);
            // Delta-method variance after a 2x2 CFA average and sqrt encoding.
            // The noise floor avoids the sqrt derivative singularity at black.
            Expr signal = max(brightness(tx, ty)[p.sqrt_encode ? 1 : 0],
                              max(1.0f / 65025.0f, 0.5f * sqrt(no) + ns * 0.25f));
            texture_variance(tx, ty) = 65025.0f * max(brightness(tx, ty)[1]
                    - brightness(tx, ty)[0] * brightness(tx, ty)[0], 0.0f);
            Expr variance0 = p.sqrt_encode ? 65025.0f * (ns + no / signal) / 16.0f
                                          : 65025.0f * (ns * signal + no) / 4.0f;
            // Repeated [1 2 1]/4 filtering has an effective triangular kernel.
            // Its squared L2 norm propagates independent input noise variance.
            const float n = float(1 << l);
            float gain = (2 * n * n + 1) / (3 * n * n * n);
            if (p.pyramid_five) {
                std::vector<double> kernel(1,1.0);
                for(int stage=0;stage<l;stage++) {
                    const int step=1<<stage; const int weights[5]={1,4,6,4,1};
                    std::vector<double> next(kernel.size()+4*step,0);
                    for(size_t k=0;k<kernel.size();k++)for(int tap=0;tap<5;tap++)
                        next[k+tap*step]+=kernel[k]*weights[tap]/16.;
                    kernel.swap(next);
                }
                gain=0;for(double w:kernel)gain+=float(w*w);
            }
            // One encoded squared unit conservatively covers integer-pyramid
            // quantization; the covariance/noise approximation is not exact.
            noise_variance(tx, ty) = variance0 * (gain * gain);
            if (p.score_center > 0 && !p.coarse_iterations) noise_variance.compute_at(center_strength, ty);
            else noise_variance.compute_root().parallel(ty);
            scale(tx, ty) = sqrt(1.0f + noise_variance(tx, ty));
            scale.compute_root().parallel(ty);
            Func normalized("noise_normalized_score_" + ln);
            normalized(tx, ty, c) = cast<uint32_t>(cast<float>(score(tx, ty, c)) / scale(tx, ty) + 0.5f);
            normalized.compute_root().reorder(c, tx, ty).parallel(ty);
            // The centered prior leaves raw scores and the original gate intact.
            if (p.score_center == 0) score = normalized;
        }
        st.best = Func("best_" + ln);
        RDom rc(0, NCl);
        // Refinement significance gate + seed tie-break. The raster center
        // (candidate Rl*Sl + Rl) IS the upsampled previous alignment; a
        // finer level may only move a tile when a candidate beats the
        // seed's cost by more than SIGNIFICANCE_MARGIN encoded units -
        // several sigma of a matched tile's noise jitter (sigma ~ sqrt(256)
        // * 0.85 * sqrt2 ~ 17 day / ~36 night per candidate; the min over
        // NCl draws dips ~2.4 sigma). Without the gate, flat and saturated
        // tiles pick whichever candidate drew the luckiest noise, wandering
        // up to the raster corner at EVERY level and compounding across the
        // pyramid (measured: 9-17 raw2x texels of pure-noise drift); with
        // it, a plain argmin returning candidate 0 = (-Rl,-Rl) on exact
        // cost ties can never happen either - every non-improving candidate
        // is pushed behind the seed, ordered by distance from it, so ties
        // resolve to zero additional displacement (ties keep the previous
        // alignment - the classic previous-alignment bias).
        // Overflow: fallback = 2^30 + dist < 2^31, real costs <=
        // 65025*256 << 2^30, no u32 collisions.
        const int SEED_CAND = Rl * Sl + Rl;
        // 96 over-blocked genuine +-1 refinements of smooth content (their
        // cost advantage can be ~60); 64 = ~3 sigma of the candidate-cost
        // jitter while still clearing the ~2-sigma noise dip of ties.
        const int SIGNIFICANCE_MARGIN = p.gate_margin;  // encoded u8 units
        Expr seed_candidate = SEED_CAND;
        Expr significance_margin = SIGNIFICANCE_MARGIN;
        Expr guide_x = 0, guide_y = 0;
        if (p.score_guide > 0) {
            Expr gx = l == p.max_level ? Expr(0) : 2 * global_shift[l + 1]()[0];
            Expr gy = l == p.max_level ? Expr(0) : 2 * global_shift[l + 1]()[1];
            Expr nx = max((lw[l] - T) / S + 1, 1);
            Expr ny = max((lh[l] - T) / S + 1, 1);
            Func eligible("global_eligible_" + ln), total("global_total_" + ln), count("global_count_" + ln);
            eligible(tx, ty) = prev_dx == gx && prev_dy == gy;
            RDom tiles(0, nx, 0, ny);
            count() = 0;
            count() += select(eligible(tiles.x, tiles.y), 1, 0);
            count.compute_root();
            total(c) = cast<uint64_t>(0);
            total(c) += select(eligible(tiles.x, tiles.y),
                               cast<uint64_t>(score(tiles.x, tiles.y, c)), cast<uint64_t>(0));
            total.compute_root().vectorize(c, 8);
            total.update().reorder(c, tiles.x, tiles.y).vectorize(c, 8);
            RDom candidate(0, NCl);
            Expr distance = abs(candidate % Sl - Rl) + abs(candidate / Sl - Rl);
            Expr global_margin = (p.score_global_noise_gate == 1 ||
                                  (p.score_global_noise_gate == 2 && l < p.max_level))
                ? cast<uint64_t>(ceil(2.0f * SIGNIFICANCE_MARGIN * sqrt(cast<float>(max(count(), 1)))))
                : cast<uint64_t>(SIGNIFICANCE_MARGIN) * count();
            Expr improves = count() >= max(4, nx * ny / 8)
                         && total(candidate) + global_margin < total(SEED_CAND);
            Func best_global("global_best_" + ln);
            Expr cost = select(improves, total(candidate), cast<uint64_t>(1) << 55);
            best_global() = argmin(cost * 16 + cast<uint64_t>(distance));
            best_global.compute_root();
            Expr selected = clamp(best_global()[0], 0, NCl - 1);
            global_shift[l] = Func("global_shift_" + ln);
            global_shift[l]() = Tuple(gx + selected % Sl - Rl, gy + selected / Sl - Rl);
            global_shift[l].compute_root();
            guide_x = global_shift[l]()[0];
            guide_y = global_shift[l]()[1];
            if (l == p.max_level) {
                seed_candidate = selected;
                significance_margin = p.coarse_prior > 0 ? p.coarse_prior : SIGNIFICANCE_MARGIN;
            }
        } else if (l == p.max_level && p.coarse_prior > 0) {
            // Reuse the already computed coarsest costs: ambiguous tiles can
            // inherit the scene's dominant shift instead of a distant alias.
            Expr nx = max((lw[l] - T) / S + 1, 1);
            Expr ny = max((lh[l] - T) / S + 1, 1);
            RDom tiles(0, nx, 0, ny);
            Func total("coarse_total"), global("coarse_global");
            total(c) = cast<uint64_t>(0);
            total(c) += cast<uint64_t>(score(tiles.x, tiles.y, c));
            total.compute_root().vectorize(c, 8);
            total.update().reorder(c, tiles.x, tiles.y).vectorize(c, 8);
            RDom candidate(0, NCl);
            Expr distance = abs(candidate % Sl - Rl) + abs(candidate / Sl - Rl);
            // A noisy/flat whole image must keep the zero-motion seed.
            Expr improves = total(candidate) + cast<uint64_t>(SIGNIFICANCE_MARGIN) * nx * ny
                          < total(SEED_CAND);
            Expr global_cost = select(improves, total(candidate), cast<uint64_t>(1) << 55);
            global() = argmin(global_cost * 16 + cast<uint64_t>(distance));
            global.compute_root();
            seed_candidate = select(p.coarse_global_enabled, clamp(global()[0], 0, NCl - 1), SEED_CAND);
            significance_margin = select(p.coarse_global_enabled, p.coarse_prior, SIGNIFICANCE_MARGIN);
        }
        Expr cost_c = score(tx, ty, rc);
        Expr cost_seed = score(tx, ty, seed_candidate);
        Expr dist_from_seed = abs(rc % Sl - seed_candidate % Sl) + abs(rc / Sl - seed_candidate / Sl);
        Expr fallback = cast<uint32_t>(1 << 30) + cast<uint32_t>(dist_from_seed);
        Expr effective = select(cost_c + significance_margin < cost_seed,
                                cost_c, fallback);
        if (p.score_prior > 0 && (l == p.max_level || !p.score_prior_coarse)) {
            Expr dx = rc % Sl - seed_candidate % Sl;
            Expr dy = rc / Sl - seed_candidate / Sl;
            // -log(exp(-||d-seed||^2/(2 sigma^2))) is quadratic. Applying
            // it to a match likelihood gives an additive penalty, not SAD*G
            // (which would accidentally reward distant, low-weight shifts).
            Expr penalized = cost_c + cast<uint32_t>(p.score_prior * (dx * dx + dy * dy));
            effective = select(penalized + significance_margin < cost_seed,
                               penalized, fallback);
        }
        if (p.score_guide > 0) {
            auto penalty = [&](Expr candidate) {
                Expr dx = prev_dx + candidate % Sl - Rl - guide_x;
                Expr dy = prev_dy + candidate / Sl - Rl - guide_y;
                // Bounded preference: far-away object motion receives nearly
                // equal penalties for all candidates, not an unbounded pull.
                // Integer displacements allow exact rounded Gaussian weights
                // to be baked into selects; no per-candidate runtime exp().
                Expr distance2 = dx * dx + dy * dy, reward = 0;
                for (int d2 = 64; d2 >= 0; --d2) {
                    int weight = int(std::floor(p.score_guide * std::exp(-0.5 * d2) + 0.5));
                    if (weight > 0) reward = select(distance2 == d2, weight, reward);
                }
                return cast<uint32_t>(p.score_guide - reward);
            };
            Expr guided = cost_c + penalty(rc);
            effective = select(guided + significance_margin < cost_seed + penalty(seed_candidate),
                               guided, fallback);
        }
        if (p.score_center > 0) {
            Func minimum_score("score_minimum_" + ln);
            RDom candidate(0, NCl);
            minimum_score(tx, ty) = minimum(score(tx, ty, candidate));
            minimum_score.compute_at(center_strength, ty);
            // One encoded squared unit is a conservative quantization floor.
            // 3*T*sigma is a heuristic allowance for SAD score uncertainty,
            // not a calibrated probability: filtering correlates the samples.
            Expr excess = max(noise_variance(tx, ty) - 1.0f, 0.0f);
            Expr allowance = max(3.0f * T * sqrt(excess), 1.0f);
            Expr improvement = max(cast<float>(cost_seed) - cast<float>(minimum_score(tx, ty))
                                   - cast<float>(significance_margin), 0.0f);
            // The paired variance is approximately twice the reference noise
            // variance. Require spatial variation above that floor before
            // softening the old gate: an untextured noisy tile has no motion
            // evidence, regardless of which candidate drew the luckiest noise.
            Expr texture_weight = p.score_texture
                    ? clamp(texture_variance(tx, ty) / max(noise_variance(tx, ty), 1.0f) - 1.0f, 0.0f, 1.0f)
                    : Expr(1.0f);
            center_strength(tx, ty) = p.score_center * min(excess, 1.0f)
                            * clamp(2.0f - improvement / allowance, 0.0f, 1.0f) * texture_weight;
            center_strength.compute_root().parallel(ty);
            Expr dx = rc % Sl - seed_candidate % Sl, dy = rc / Sl - seed_candidate / Sl;
            Expr distance2 = dx * dx + dy * dy, gaussian = 0.0f;
            // Integer candidate positions: bake the small Gaussian into a LUT.
            // Truncating beyond d^2=16 changes <0.022 score units at strength 64.
            for (int d2 = 16; d2 >= 0; --d2)
                gaussian = select(distance2 == d2, float(std::exp(-0.5 * d2)), gaussian);
            Expr penalty = cast<uint32_t>(center_strength(tx, ty) * (1.0f - gaussian) + 0.5f);
            Expr centered = cost_c + penalty;
            // Substitute the original hard seed gate only in noisy/ambiguous
            // tiles; do not stack two priors. Near-center corrections then
            // need less evidence than far shifts. At zero weight this is
            // exactly the existing gate, including its integer tie behavior.
            Expr remaining_margin = cast<uint32_t>(cast<float>(significance_margin)
                * (1.0f - center_strength(tx, ty) / float(p.score_center)) + 0.5f);
            effective = select(centered + remaining_margin < cost_seed, centered, fallback);
        }
        if (p.motion_prior > 0) {
            Expr dx = rc % Sl - Rl, dy = rc / Sl - Rl;
            // Distance in the tie-break also makes constant images stationary.
            effective = (cost_c + cast<uint32_t>(p.motion_prior*(dx*dx+dy*dy))) * 256
                      + cast<uint32_t>(dist_from_seed);
        }
        st.best(tx, ty) = argmin(effective);
        st.best.compute_root().parallel(ty);

        Expr bwin = clamp(st.best(tx, ty)[0], 0, NCl - 1);
        if(p.inherit_subpixel && l==p.min_level)
            finest_fraction=select(bwin==SEED_CAND,select(ch==0,fraction_x,fraction_y),0.f);

        st.align = Func("align_" + ln);
        st.align(ch, tx, ty) = select(ch == 0,
                                      prev_dx * substeps + bwin % Sl - Rl,
                                      prev_dy * substeps + bwin / Sl - Rl);
        if (substeps > 1) {
            Func fractional("fractional_coarse");
            fractional(ch,tx,ty)=cast<float>(st.align(ch,tx,ty))/substeps;
            st.align=fractional;
        }
        if (p.fractional_seed && l < p.max_level && l > p.min_level) {
            Func continuous("continuous_seed_"+ln);
            continuous(ch,tx,ty)=cast<float>(st.align(ch,tx,ty))+select(ch==0,fraction_x,fraction_y);
            st.align=continuous;
        }
        st.align.compute_root().parallel(ty);

        if (p.coarse_iterations && l >= p.coarse_refine_minlevel && l > p.min_level) {
            Func initial_align = st.align;
            Func state = initial_align;
            const float encoded_scale = float(1 << p.pyramid_precision);
            auto sample_alt = [&](Expr px, Expr py, Expr sx, Expr sy) {
                Expr ix = cast<int>(floor(sx)), iy = cast<int>(floor(sy));
                Expr fx = sx - floor(sx), fy = sy - floor(sy);
                // Bound the shift before indexing, including the bilinear +1.
                ix = clamp(ix, -B[l] + 2, B[l] - 2);
                iy = clamp(iy, -B[l] + 2, B[l] - 2);
                return lerp(lerp(cast<float>(alt_levels[l](px+ix,py+iy)),
                                 cast<float>(alt_levels[l](px+ix+1,py+iy)),fx),
                            lerp(cast<float>(alt_levels[l](px+ix,py+iy+1)),
                                 cast<float>(alt_levels[l](px+ix+1,py+iy+1)),fx),fy) / encoded_scale;
            };
            for (int iteration = 0; iteration < p.coarse_iterations; ++iteration) {
                Func terms("coarse_terms_" + ln + "_" + std::to_string(iteration));
                RDom r(0,T,0,T);
                Expr px=tx*S+r.x, py=ty*S+r.y;
                auto ref = [&](int dx,int dy) { return cast<float>(base_levels[l](px+dx,py+dy))/encoded_scale; };
                Expr gx=.5f*(ref(1,0)-ref(-1,0)), gy=.5f*(ref(0,1)-ref(0,-1));
                Expr residual=sample_alt(px,py,state(0,tx,ty),state(1,tx,ty))-ref(0,0);
                terms(tx,ty)=Tuple(0.f,0.f,0.f,0.f,0.f);
                terms(tx,ty)=Tuple(terms(tx,ty)[0]+gx*gx, terms(tx,ty)[1]+gx*gy,
                    terms(tx,ty)[2]+gy*gy,terms(tx,ty)[3]+gx*residual,terms(tx,ty)[4]+gy*residual);
                terms.compute_root().parallel(ty);
                terms.update().reorder(tx,ty).parallel(ty);
                Var u("coarse_u"),uo("coarse_uo"),ui("coarse_ui");
                Func partial=terms.update().rfactor({{r.x,u}});
                partial.compute_at(terms,tx).split(u,uo,ui,8).unroll(uo).vectorize(ui);
                partial.update().split(u,uo,ui,8).unroll(uo).vectorize(ui).unroll(r.y,2);
                Expr xx=terms(tx,ty)[0],xy=terms(tx,ty)[1],yy=terms(tx,ty)[2];
                Expr bx=terms(tx,ty)[3],by=terms(tx,ty)[4],trace=xx+yy;
                Expr lambda=1e-4f*(1.f+trace);
                Expr det=(xx+lambda)*(yy+lambda)-xy*xy;
                Expr valid=trace>1.f && xx*yy-xy*xy>1e-5f*trace*trace
                    && texture_variance(tx,ty)>max(noise_variance(tx,ty),1.f/(encoded_scale*encoded_scale));
                Expr dx=-( (yy+lambda)*bx-xy*by)/max(det,1e-12f);
                Expr dy=-( (xx+lambda)*by-xy*bx)/max(det,1e-12f);
                Func next("coarse_refine_"+ln+"_"+std::to_string(iteration));
                Expr step=select(valid,clamp(select(ch==0,dx,dy),-1.f,1.f),0.f);
                next(ch,tx,ty)=clamp(cast<float>(state(ch,tx,ty))+step,
                    max(cast<float>(initial_align(ch,tx,ty))-4.f,float(-B[l]+2)),
                    min(cast<float>(initial_align(ch,tx,ty))+4.f,float(B[l]-2)));
                next.compute_root().parallel(ty);
                state=next;
            }
            Func residual_cost("coarse_refined_cost_"+ln);
            RDom r(0,T,0,T);
            Expr px=tx*S+r.x,py=ty*S+r.y;
            residual_cost(tx,ty)=0.f;
            residual_cost(tx,ty)+=abs(sample_alt(px,py,state(0,tx,ty),state(1,tx,ty))
                                      -cast<float>(base_levels[l](px,py))/encoded_scale);
            residual_cost.compute_root().parallel(ty);
            residual_cost.update().reorder(tx,ty).parallel(ty);
            Var u("accept_u");Func partial=residual_cost.update().rfactor({{r.x,u}});
            partial.compute_at(residual_cost,tx).vectorize(u,16);
            partial.update().vectorize(u,16).unroll(r.y,2);
            Func accepted("coarse_accepted_"+ln);
            accepted(ch,tx,ty)=select(residual_cost(tx,ty)+16.f<cast<float>(score(tx,ty,bwin)),
                state(ch,tx,ty),cast<float>(initial_align(ch,tx,ty)));
            accepted.compute_root().parallel(ty);
            st.align=accepted;
        }

        if (l == p.min_level) {
            // Parabolic sub-texel fit of the cost surface at the integer
            // minimum. Runs for EVERY tile (a tile that stayed at the seed
            // still carries sub-search-step motion), guarded by:
            //  - curvature den > 32: noise-flat surfaces have curvature
            //    ~ +-5, a true V has hundreds. The first version guarded
            //    with den > 1e-3 only, which let the fit explode to
            //    +-13 texels of pure noise on flat tiles - the single
            //    largest source of "impossible" alignment outliers;
            //  - the offset is clamped to +-0.75 texel regardless.
            Expr b = clamp(st.best(tx, ty)[0], 0, NCl - 1);
            Expr bxl = clamp(b - 1, 0, NCl - 1);
            Expr bxr = clamp(b + 1, 0, NCl - 1);
            Expr byl = clamp(b - Sl, 0, NCl - 1);
            Expr byr = clamp(b + Sl, 0, NCl - 1);
            Expr ox = b % Sl, oy = b / Sl;
            Expr cc = cast<float>(st.cost(tx, ty, b));
            Expr xl = cast<float>(st.cost(tx, ty, bxl));
            Expr xr = cast<float>(st.cost(tx, ty, bxr));
            Expr yl = cast<float>(st.cost(tx, ty, byl));
            Expr yr = cast<float>(st.cost(tx, ty, byr));
            Expr denx = xl - 2 * cc + xr;
            Expr deny = yl - 2 * cc + yr;
            subpix(ch, tx, ty) = select(
                ch == 0,
                select(ox > 0 && ox < Sl - 1 && denx > 32.0f,
                       clamp(0.5f * (xl - xr) / denx, -0.75f, 0.75f), 0.0f),
                select(oy > 0 && oy < Sl - 1 && deny > 32.0f,
                       clamp(0.5f * (yl - yr) / deny, -0.75f, 0.75f), 0.0f));
        }
    }

    const Func &al_min = lv[p.min_level].align;
    if (debug_level_aligns) {
        debug_level_aligns->clear();
        for (int l = p.min_level; l <= p.max_level; l++)
            debug_level_aligns->push_back(lv[l].align);
    }
    if (debug_level_costs) {
        debug_level_costs->clear();
        for (int l = p.min_level; l <= p.max_level; l++)
            debug_level_costs->push_back(lv[l].cost);
    }
    const float scale = (float)(1 << p.min_level);  // min-level texels -> raw2x

    if (p.subpixel_mode == 1 || p.subpixel_mode == 3) {
        // Recenter the fit at the selected winner, including raster edges.
        // A squared residual has a quadratic minimum; SAD generally does not.
        const Func &base = base_levels[p.min_level];
        const Func &alt = alt_levels[p.min_level];
        // Fused cross: share the reference/center loads and accumulate all
        // five squared residuals in a single vectorized tile pass.
        Func fit_cost("fit_cost");
        RDom r(0,T,0,T);
        Expr px=tx*S+r.x, py=ty*S+r.y;
        Expr ax=px+al_min(0,tx,ty), ay=py+al_min(1,tx,ty);
        Expr ref=cast<int32_t>(base(px,py));
        auto squared = [&](int dx,int dy) {
            Expr d=ref-cast<int32_t>(alt(ax+dx,ay+dy));return d*d;
        };
        fit_cost(tx,ty)=Tuple(0,0,0,0,0,0);
        fit_cost(tx,ty)=Tuple(fit_cost(tx,ty)[0]+squared(0,0),
            fit_cost(tx,ty)[1]+squared(-1,0),fit_cost(tx,ty)[2]+squared(1,0),
            fit_cost(tx,ty)[3]+squared(0,-1),fit_cost(tx,ty)[4]+squared(0,1),
            fit_cost(tx,ty)[5]+(cast<int32_t>(alt(ax+1,ay))-cast<int32_t>(alt(ax-1,ay)))
                *(cast<int32_t>(alt(ax,ay+1))-cast<int32_t>(alt(ax,ay-1))));
        fit_cost.compute_root().parallel(ty);
        fit_cost.update().reorder(tx,ty).parallel(ty);
        if (!p.simple_schedule) {
            Var u("fit_u"),uo("fit_uo"),ui("fit_ui");
            Func partial=fit_cost.update().rfactor({{r.x,u}});
            partial.compute_at(fit_cost,tx).split(u,uo,ui,8).unroll(uo).vectorize(ui);
            partial.update().split(u,uo,ui,8).unroll(uo).vectorize(ui).unroll(r.y,2);
        }
        const float fit_scale = float(1 << (2 * p.pyramid_precision));
        Expr cc=cast<float>(fit_cost(tx,ty)[0]) / fit_scale;
        Expr left=cast<float>(select(ch==0,fit_cost(tx,ty)[1],fit_cost(tx,ty)[3])) / fit_scale;
        Expr right=cast<float>(select(ch==0,fit_cost(tx,ty)[2],fit_cost(tx,ty)[4])) / fit_scale;
        Expr den=left-2*cc+right;
        Func fit("subpixel_l2");
        fit(ch,tx,ty)=select(den>64.0f,
            clamp(0.5f*(left-right)/max(den,1.0f),-0.75f,0.75f),finest_fraction);
        if (p.subpixel_mode == 3) {
            // Diagonal Hessian terms come from the actual squared costs;
            // the cross term is the Gauss-Newton gradient product, sharing
            // the same four side loads. Solve both axes together when the
            // Hessian is positive and well conditioned; otherwise retain
            // the independently guarded axis fits. No new search tiles.
            Expr xx=cast<float>(fit_cost(tx,ty)[1]+fit_cost(tx,ty)[2])/fit_scale-2*cc;
            Expr yy=cast<float>(fit_cost(tx,ty)[3]+fit_cost(tx,ty)[4])/fit_scale-2*cc;
            Expr xy=0.5f*cast<float>(fit_cost(tx,ty)[5])/fit_scale;
            Expr bx=0.5f*cast<float>(fit_cost(tx,ty)[1]-fit_cost(tx,ty)[2])/fit_scale;
            Expr by=0.5f*cast<float>(fit_cost(tx,ty)[3]-fit_cost(tx,ty)[4])/fit_scale;
            Expr det=xx*yy-xy*xy;
            Expr valid=xx>64.0f && yy>64.0f && det>0.01f*(xx+yy)*(xx+yy);
            Func coupled("subpixel_coupled");
            Expr delta=select(ch==0,yy*bx-xy*by,xx*by-xy*bx)/max(det,1.0f);
            coupled(ch,tx,ty)=select(valid,clamp(delta,-0.75f,0.75f),fit(ch,tx,ty));
            subpix=coupled;
        } else {
            subpix=fit;
        }
    } else if (p.subpixel_mode == 2) {
        // One symmetric-gradient solve per existing tile. Both frames
        // contribute to the gradient to reduce directional/noise bias.
        const Func &base = base_levels[p.min_level];
        const Func &alt = alt_levels[p.min_level];
        RDom r(0,T,0,T);
        Expr px=tx*S+r.x, py=ty*S+r.y;
        Expr ax=px+al_min(0,tx,ty), ay=py+al_min(1,tx,ty);
        auto b = [&](int x,int y) {return cast<int32_t>(base(px+x,py+y));};
        auto a = [&](int x,int y) {return cast<int32_t>(alt(ax+x,ay+y));};
        Expr gx=b(1,0)-b(-1,0)+a(1,0)-a(-1,0);
        Expr gy=b(0,1)-b(0,-1)+a(0,1)-a(0,-1);
        Expr residual=a(0,0)-b(0,0);
        Func products("subpixel_products");
        products(tx,ty)=Tuple(0,0,0,0,0,0);
        products(tx,ty)=Tuple(products(tx,ty)[0]+gx*gx,
            products(tx,ty)[1]+gx*gy,products(tx,ty)[2]+gy*gy,
            products(tx,ty)[3]+gx*residual,products(tx,ty)[4]+gy*residual,
            products(tx,ty)[5]+residual*residual);
        products.compute_root().parallel(ty);
        products.update().reorder(tx,ty).parallel(ty);
        Var u("lk_u"),uo("lk_uo"),ui("lk_ui");
        Func partial=products.update().rfactor({{r.x,u}});
        partial.compute_at(products,tx).split(u,uo,ui,8).unroll(uo).vectorize(ui);
        partial.update().split(u,uo,ui,8).unroll(uo).vectorize(ui).unroll(r.y,2);
        Expr xx=cast<float>(products(tx,ty)[0]),xy=cast<float>(products(tx,ty)[1]);
        Expr yy=cast<float>(products(tx,ty)[2]),bx=cast<float>(products(tx,ty)[3]);
        Expr by=cast<float>(products(tx,ty)[4]),err=cast<float>(products(tx,ty)[5]);
        Expr trace=xx+yy,lambda=512.0f+0.01f*trace;
        Expr det=(xx+lambda)*(yy+lambda)-xy*xy;
        Expr dx=-4.0f*((yy+lambda)*bx-xy*by)/max(det,1.0f);
        Expr dy=-4.0f*((xx+lambda)*by-xy*bx)/max(det,1.0f);
        Expr valid=trace>512.0f+5.6f*err && xx*yy-xy*xy>0.001f*trace*trace;
        Func fit("subpixel_gradient");
        fit(ch,tx,ty)=select(valid,clamp(select(ch==0,dx,dy),-0.75f,0.75f),0.0f);
        subpix=fit;
    }

    Func alignments("alignments");
    alignments(ch, tx, ty) =
        (cast<float>(al_min(ch, tx, ty)) + subpix(ch, tx, ty)) * scale;
    subpix.compute_root().parallel(ty);
    alignments.compute_root().parallel(ty).vectorize(tx, 4);

    return alignments;
}

// Convenience single-pipeline variant: builds the base pyramid inside the
// same pipeline (recomputed on every realize - fine for one-shot use, NOT
// for bursts; use the two-stage API there).
inline Halide::Func build_alignments(Halide::Func base_raw, Halide::Func alt_raw,
                                     Halide::Expr raw_w, Halide::Expr raw_h,
                                     const AlignParams &p,
                                     std::vector<Halide::Func> *debug_level_aligns = nullptr,
                                     std::vector<Halide::Func> *debug_level_costs = nullptr) {
    std::vector<Halide::Func> levels;
    build_base_pyramid(base_raw, raw_w, raw_h, p, levels);
    return build_alignments(levels, alt_raw, raw_w, raw_h, p, debug_level_aligns,
                            debug_level_costs);
}

}  // namespace halide_align
