//
// Created by eszdman on 19.09.2026.
//

// AOT generators for the split (burst-friendly) alignment pipeline:
//
//   alignburst_base(base_raw, white_level) -> base_l0 .. base_l4
//       Stage 1, called ONCE per burst. Padded u8 pyramid levels; output
//       buffers must cover [-B_l, lw_l + B_l) x [-B_l, lh_l + B_l), i.e.
//       allocate (lw_l + 2 B_l) x (lh_l + 2 B_l) with min = -B_l
//       (B_l from halide_align::level_paddings).
//
//   alignburst(base_l0..base_l4, alt_raw, white_level) -> alignments
//       Stage 2, called per alt frame. Consumes the buffers produced by
//       alignburst_base; the raw size is derived from base_l0's extent.

#include <cstdio>
#include <cstdlib>

#include "Halide.h"
#include "align_flow.h"
#include "align_pipeline.h"

namespace {

halide_align::AlignParams make_params(int tile, int radius, int min_l, int max_l,
                                      int sqrt_enc, Halide::Expr white) {
    halide_align::AlignParams p;
    p.tile_size = tile;
    p.search_radius = radius;
    p.min_level = min_l;
    p.max_level = max_l;
    p.sqrt_encode = sqrt_enc != 0;
    p.white_level = white;
    return p;
}

}  // namespace

// fp16-input variants for PhotonCamera: frames arrive as tightly packed
// half-float buffers already black/white normalized per Bayer site by
// Allocator.createF16. white_level is the EFFECTIVE white in normalized
// units: 1.0 for the base frame, pair.layerMpy for an alt frame (its
// normalized values are layerMpy times larger; highlights clamp at the
// base's range exactly like the GLSL alignment's exposure clamp).
// ---------------------------------------------------------------------------

class AlignBurstBaseF16 : public Halide::Generator<AlignBurstBaseF16> {
public:
    GeneratorParam<int> tile_size{"tile_size", 16};
    GeneratorParam<int> search_radius{"search_radius", 4};
    GeneratorParam<int> sqrt_encode{"sqrt_encode", 1};  // must match stage 2
    GeneratorParam<int> max_level{"max_level", 4};  // fixed: 5 level outputs

    Input<Buffer<Halide::float16_t>> base_raw{"base_raw", 2};
    Input<float> white_level{"white_level"};

    Output<Buffer<uint8_t>> base_l0{"base_l0", 2};
    Output<Buffer<uint8_t>> base_l1{"base_l1", 2};
    Output<Buffer<uint8_t>> base_l2{"base_l2", 2};
    Output<Buffer<uint8_t>> base_l3{"base_l3", 2};
    Output<Buffer<uint8_t>> base_l4{"base_l4", 2};

    void generate() {
        if ((int)max_level != 4) {
            std::fprintf(stderr, "alignburst_base_f16: exactly 5 levels required\n");
            std::abort();
        }
        Var x("x"), y("y");
        Func f("base_raw_f16");
        f(x, y) = base_raw(x, y);
        halide_align::AlignParams p = make_params(
            tile_size, search_radius, 1, max_level, sqrt_encode, white_level);
        std::vector<Func> levels;
        build_base_pyramid(f, base_raw.width(), base_raw.height(), p, levels);
        base_l0 = levels[0];
        base_l1 = levels[1];
        base_l2 = levels[2];
        base_l3 = levels[3];
        base_l4 = levels[4];
    }
};

class AlignBurstF16 : public Halide::Generator<AlignBurstF16> {
public:
    GeneratorParam<int> seed_mode{"seed_mode", 0};
    GeneratorParam<int> motion_prior{"motion_prior", 0};
    GeneratorParam<int> coarse_prior{"coarse_prior", 0};
    GeneratorParam<int> score_guide{"score_guide", 0};
    GeneratorParam<int> score_global_noise_gate{"score_global_noise_gate", 0};
    GeneratorParam<int> subpixel_mode{"subpixel_mode", 3};
    GeneratorParam<int> tile_size{"tile_size", 16};
    GeneratorParam<int> search_radius{"search_radius", 4};
    GeneratorParam<int> min_level{"min_level", 1};
    GeneratorParam<int> sqrt_encode{"sqrt_encode", 1};  // must match stage 1
    GeneratorParam<int> max_level{"max_level", 4};  // fixed: 5 level inputs

    Input<Buffer<uint8_t>> base_l0{"base_l0", 2};
    Input<Buffer<uint8_t>> base_l1{"base_l1", 2};
    Input<Buffer<uint8_t>> base_l2{"base_l2", 2};
    Input<Buffer<uint8_t>> base_l3{"base_l3", 2};
    Input<Buffer<uint8_t>> base_l4{"base_l4", 2};
    Input<Buffer<Halide::float16_t>> alt_raw{"alt_raw", 2};
    Input<float> white_level{"white_level"};

    Output<Buffer<float>> alignments{"alignments", 3};

    void generate() {
        if ((int)max_level != 4) {
            std::fprintf(stderr, "alignburst_f16: exactly 5 levels required\n");
            std::abort();
        }
        Var x("x"), y("y");
        halide_align::AlignParams p = make_params(
            tile_size, search_radius, min_level, max_level, sqrt_encode, white_level);
        p.seed_mode = seed_mode;
        p.motion_prior = motion_prior;
        p.coarse_prior = coarse_prior;
        p.score_guide = score_guide;
        p.score_global_noise_gate = score_global_noise_gate;
        p.subpixel_mode = subpixel_mode;
        std::vector<int> B = halide_align::level_paddings(p);
        Expr raw_w = 2 * (base_l0.width() - 2 * B[0]);
        Expr raw_h = 2 * (base_l0.height() - 2 * B[0]);

        std::vector<Func> levels;
        auto wrap = [&](Input<Buffer<uint8_t>> &in, const char *name) {
            Func w(name);
            w(x, y) = in(x, y);
            return w;
        };
        levels.push_back(wrap(base_l0, "bl0f"));
        levels.push_back(wrap(base_l1, "bl1f"));
        levels.push_back(wrap(base_l2, "bl2f"));
        levels.push_back(wrap(base_l3, "bl3f"));
        levels.push_back(wrap(base_l4, "bl4f"));
        Func alt_f("alt_raw_f16");
        alt_f(x, y) = alt_raw(x, y);
        alignments = build_alignments(levels, alt_f, raw_w, raw_h, p);
    }
};

// Accurate noise-aware recipe, using the same output grid and raw CFA input.
class AlignBurstBasePreciseF16 : public Halide::Generator<AlignBurstBasePreciseF16> {
public:
    GeneratorParam<int> tile_size{"tile_size", 16};
    GeneratorParam<int> search_radius{"search_radius", 4};
    GeneratorParam<int> sqrt_encode{"sqrt_encode", 1};  // must match stage 2
    GeneratorParam<int> max_level{"max_level", 4};  // fixed: 5 level outputs

    Input<Buffer<Halide::float16_t>> base_raw{"base_raw", 2};
    Input<float> white_level{"white_level"};

    Output<Buffer<uint16_t>> base_l0{"base_l0", 2};
    Output<Buffer<uint16_t>> base_l1{"base_l1", 2};
    Output<Buffer<uint16_t>> base_l2{"base_l2", 2};
    Output<Buffer<uint16_t>> base_l3{"base_l3", 2};
    Output<Buffer<uint16_t>> base_l4{"base_l4", 2};

    void generate() {
        if ((int)max_level != 4) {
            std::fprintf(stderr, "alignburst_base_f16: exactly 5 levels required\n");
            std::abort();
        }
        Var x("x"), y("y");
        Func f("base_raw_f16");
        f(x, y) = base_raw(x, y);
        halide_align::AlignParams p = make_params(
            tile_size, search_radius, 1, max_level, sqrt_encode, white_level);
        p.pyramid_precision = 3;
        p.pyramid_five = true;
        std::vector<Func> levels;
        build_base_pyramid(f, base_raw.width(), base_raw.height(), p, levels);
        base_l0 = levels[0];
        base_l1 = levels[1];
        base_l2 = levels[2];
        base_l3 = levels[3];
        base_l4 = levels[4];
    }
};

class AlignBurstF16Precise : public Halide::Generator<AlignBurstF16Precise> {
public:
    GeneratorParam<int> seed_mode{"seed_mode", 0};
    GeneratorParam<int> motion_prior{"motion_prior", 0};
    GeneratorParam<int> coarse_prior{"coarse_prior", 0};
    GeneratorParam<int> coarse_refine_radius{"coarse_refine_radius", 1};
    GeneratorParam<int> middle_refine_radius{"middle_refine_radius", 1};
    GeneratorParam<int> score_center{"score_center", 64};
    GeneratorParam<int> subpixel_mode{"subpixel_mode", 3};
    GeneratorParam<int> tile_size{"tile_size", 16};
    GeneratorParam<int> search_radius{"search_radius", 4};
    GeneratorParam<int> min_level{"min_level", 1};
    GeneratorParam<int> sqrt_encode{"sqrt_encode", 1};  // must match stage 1
    GeneratorParam<int> max_level{"max_level", 4};  // fixed: 5 level inputs

    Input<Buffer<uint16_t>> base_l0{"base_l0", 2};
    Input<Buffer<uint16_t>> base_l1{"base_l1", 2};
    Input<Buffer<uint16_t>> base_l2{"base_l2", 2};
    Input<Buffer<uint16_t>> base_l3{"base_l3", 2};
    Input<Buffer<uint16_t>> base_l4{"base_l4", 2};
    Input<Buffer<Halide::float16_t>> alt_raw{"alt_raw", 2};
    Input<float> white_level{"white_level"};
    Input<float> noise_s{"noise_s"};
    Input<float> noise_o{"noise_o"};
    Input<int32_t> coarse_global{"coarse_global"};

    Output<Buffer<float>> alignments{"alignments", 3};

    void generate() {
        if ((int)max_level != 4) {
            std::fprintf(stderr, "alignburst_f16: exactly 5 levels required\n");
            std::abort();
        }
        Var x("x"), y("y");
        halide_align::AlignParams p = make_params(
            tile_size, search_radius, min_level, max_level, sqrt_encode, white_level);
        p.pyramid_precision = 3;
        p.pyramid_five = true;
        p.coarse_substeps = 2;
        p.coarse_iterations = 3;
        p.fractional_seed = true;
        p.inherit_subpixel = true;
        p.seed_mode = seed_mode;
        p.motion_prior = motion_prior;
        p.coarse_prior = coarse_prior;
        p.score_noise = true;
        p.score_texture = true;
        p.noise_s = noise_s;
        p.noise_o = noise_o;
        p.score_center = score_center;
        p.coarse_refine_radius = coarse_refine_radius;
        p.middle_refine_radius = middle_refine_radius;
        p.coarse_global_enabled = coarse_global != 0;
        p.subpixel_mode = subpixel_mode;
        std::vector<int> B = halide_align::level_paddings(p);
        Expr raw_w = 2 * (base_l0.width() - 2 * B[0]);
        Expr raw_h = 2 * (base_l0.height() - 2 * B[0]);

        std::vector<Func> levels;
        auto wrap = [&](Input<Buffer<uint16_t>> &in, const char *name) {
            Func w(name);
            w(x, y) = in(x, y);
            return w;
        };
        levels.push_back(wrap(base_l0, "bl0f"));
        levels.push_back(wrap(base_l1, "bl1f"));
        levels.push_back(wrap(base_l2, "bl2f"));
        levels.push_back(wrap(base_l3, "bl3f"));
        levels.push_back(wrap(base_l4, "bl4f"));
        Func alt_f("alt_raw_f16");
        alt_f(x, y) = alt_raw(x, y);
        alignments = build_alignments(levels, alt_f, raw_w, raw_h, p);
    }
};


HALIDE_REGISTER_GENERATOR(AlignBurstBasePreciseF16, alignburst_base_precise_f16)
HALIDE_REGISTER_GENERATOR(AlignBurstF16Precise, alignburst_f16_precise)



HALIDE_REGISTER_GENERATOR(AlignBurstBaseF16, alignburst_base_f16)
HALIDE_REGISTER_GENERATOR(AlignBurstF16, alignburst_f16)

// ---------------------------------------------------------------------------
// Optional level-0 refinement (include/align_flow.h): per-tile gradient
// products once per burst, then a per-frame Gauss-Newton refinement of the
// stage-2 field at raw/2. Additive to alignburst_base/alignburst - the
// existing generators and their buffer ABI are untouched.
// ---------------------------------------------------------------------------

class AlignFlowBase : public Halide::Generator<AlignFlowBase> {
public:
    GeneratorParam<int> tile_size{"tile_size", 16};
    GeneratorParam<int> tile_stride{"tile_stride", 8};

    Input<Buffer<uint8_t>> base_l0{"base_l0", 2};
    Output<Buffer<int32_t>> gprod{"gprod", 3};

    void generate() {
        Var x("x"), y("y");
        Func f("flow_base_l0");
        f(x, y) = base_l0(x, y);
        halide_align::AlignParams p;
        p.tile_size = tile_size;
        p.tile_stride = tile_stride;
        gprod = halide_align::build_flow_base(f, p);
    }
};

class AlignFlowRefineF16 : public Halide::Generator<AlignFlowRefineF16> {
public:
    GeneratorParam<int> tile_size{"tile_size", 16};
    GeneratorParam<int> tile_stride{"tile_stride", 8};
    GeneratorParam<int> search_radius{"search_radius", 4};
    GeneratorParam<int> min_level{"min_level", 1};
    GeneratorParam<int> sqrt_encode{"sqrt_encode", 1};
    GeneratorParam<int> max_level{"max_level", 4};
    GeneratorParam<int> iterations{"iterations", 3};
    GeneratorParam<int> margin{"margin", 64};

    Input<Buffer<uint8_t>> base_l0{"base_l0", 2};
    Input<Buffer<int32_t>> gprod{"gprod", 3};
    Input<Buffer<Halide::float16_t>> alt_raw{"alt_raw", 2};
    Input<float> white_level{"white_level"};
    Input<Buffer<float>> seed{"seed", 3};

    Output<Buffer<float>> alignments{"alignments", 3};

    void generate() {
        Var x("x"), y("y"), c("c");
        halide_align::AlignParams p = make_params(
            tile_size, search_radius, min_level, max_level, sqrt_encode, white_level);
        p.tile_stride = tile_stride;
        std::vector<int> B = halide_align::level_paddings(p);
        Expr raw_w = 2 * (base_l0.width() - 2 * B[0]);
        Expr raw_h = 2 * (base_l0.height() - 2 * B[0]);

        Func bl0("fl_bl0");
        bl0(x, y) = base_l0(x, y);
        Func gp("fl_gprod");
        gp(c, x, y) = gprod(c, x, y);
        Func af("fl_alt");
        af(x, y) = alt_raw(x, y);
        Func sf("fl_seed");
        sf(c, x, y) = seed(c, x, y);
        halide_align::FlowParams fp;
        fp.iterations = iterations;
        fp.gate_margin = margin;
        alignments = halide_align::build_flow_refine(bl0, gp, af, sf, raw_w, raw_h, p, fp);
    }
};

HALIDE_REGISTER_GENERATOR(AlignFlowBase, alignflow_base)
HALIDE_REGISTER_GENERATOR(AlignFlowRefineF16, alignflow_f16)
