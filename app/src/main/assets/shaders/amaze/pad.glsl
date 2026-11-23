// AMaZE pass 0: fetch one tile of the CFA into its padded staging window.
//
// Reproduces amaze_glsl/pad.py exactly: the top border rows are sliced from
// the image (border row j <- image row 32 - j, period-32 mirror) while the
// side columns are sliced from the already row-padded buffer (left border
// col j <- image col 16 - j, right border col j <- image col W - 2 - j;
// bottom row j <- image row H - 2 - j).  All rules preserve the Bayer phase.
//
// Tiled processing: u_off places the window anywhere in the full image.
// Window texels are addressed in whole-image padded coordinates and clamped
// to that domain before mirroring, so a tile reaching past the image edge
// sees exactly the values the whole-image pad plus the staging edge clamp
// would have produced.
precision highp float;
precision highp int;
precision highp sampler2D;
precision highp image2D;
layout(local_size_x = 8, local_size_y = 8) in;

uniform sampler2D u_in; // @post-r32f — full-resolution CFA (rgba16f), mosaic in .r, [0, 1]
uniform ivec2 u_insize;       // full (W, H)
uniform ivec2 u_off;          // window origin in full-image coords (ox - B - 16, oy - B - 16)
uniform ivec2 u_size;         // padded window size (tile + 2*B + 32)
layout(binding = 0, r32f) writeonly uniform highp image2D img_out;

int srcRow(int r, int n) {
    if (r < 16) return 32 - r;
    if (r < n + 16) return r - 16;
    return 2 * n + 14 - r;
}

int srcCol(int c, int n) {
    if (c < 16) return 16 - c;
    if (c < n + 16) return c - 16;
    return 2 * n + 14 - c;
}

void main() {
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (p.x >= u_size.x || p.y >= u_size.y) return;
    ivec2 fp = clamp(p + u_off + ivec2(16), ivec2(0), u_insize + ivec2(31));
    ivec2 s = ivec2(srcCol(fp.x, u_insize.x), srcRow(fp.y, u_insize.y));
    s = clamp(s, ivec2(0), u_insize - 1);
    imageStore(img_out, p, vec4(texelFetch(u_in, s, 0).r));
}
