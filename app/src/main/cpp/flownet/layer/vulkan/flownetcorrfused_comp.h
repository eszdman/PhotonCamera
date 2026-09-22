#ifndef FLOWNETCORRFUSED_COMP_H
#define FLOWNETCORRFUSED_COMP_H

#include <cstddef>

static const unsigned char flownetcorrfused_comp_data[] = R"(#version 450

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(binding = 0) readonly buffer feat0_buf { sfp feat0_data[]; };
layout(binding = 1) readonly buffer feat1_buf { sfp feat1_data[]; };
layout(binding = 2) readonly buffer coords_buf { sfp coords_data[]; };
layout(binding = 3) writeonly buffer out_buf { sfp out_data[]; };

layout(push_constant) uniform parameter
{
    int H;
    int W;
    int C;
    int coords_cstep;
    int out_cstep;
    int KK;
    int K2;
    int radius;
} p;

#define TILE_W 10
#define TILE_H 10
#define TILE_CH 16
#define TILE_NUM (TILE_W * TILE_H * TILE_CH)
#define NUM_DOTS (TILE_W * TILE_H)

shared lfp tile[TILE_NUM];
shared float dots[NUM_DOTS];

void main()
{
    const int x = int(gl_WorkGroupID.x);
    const int y = int(gl_WorkGroupID.y);
    if (x >= p.W || y >= p.H)
        return;

    const int n = y * p.W + x;
    const int tid = int(gl_LocalInvocationIndex);
    const int NT = 64;

    const float cx = buffer_ld1(coords_data, 0 * p.coords_cstep + n);
    const float cy = buffer_ld1(coords_data, 1 * p.coords_cstep + n);

    const int tx0 = int(floor(cx)) - p.radius;
    const int ty0 = int(floor(cy)) - p.radius;

    const int f0base = n * p.C;
    const float scale = 1.0 / sqrt(float(p.C));

    const float fx = cx - floor(cx);
    const float fy = cy - floor(cy);
    const float w00 = (1.0 - fx) * (1.0 - fy);
    const float w10 = fx * (1.0 - fy);
    const float w01 = (1.0 - fx) * fy;
    const float w11 = fx * fy;

    const int dot_a_idx = tid;
    const int dot_b_idx = tid + 64;
    const bool has_dot_b = dot_b_idx < NUM_DOTS;

    float dot_a = 0.0;
    float dot_b = 0.0;

    const int chunks = (p.C + TILE_CH - 1) / TILE_CH;
    for (int cch = 0; cch < chunks; cch++)
    {
        const int c0 = cch * TILE_CH;
        const int nch = (p.C - c0 < TILE_CH) ? (p.C - c0) : TILE_CH;

        for (int idx = tid; idx < TILE_NUM; idx += NT)
        {
            const int cc = idx % TILE_CH;
            const int sp = idx / TILE_CH;
            const int dy = sp / TILE_W;
            const int dx = sp - dy * TILE_W;
            const int tx = tx0 + dx;
            const int ty = ty0 + dy;
            float val = 0.0;
            if (cc < nch && tx >= 0 && tx < p.W && ty >= 0 && ty < p.H)
                val = buffer_ld1(feat1_data, (ty * p.W + tx) * p.C + c0 + cc);
            tile[idx] = afp2lfp(val);
        }

        barrier();

        float da0 = 0.0, da1 = 0.0, da2 = 0.0, da3 = 0.0;
        float db0 = 0.0, db1 = 0.0, db2 = 0.0, db3 = 0.0;
        for (int c = 0; c + 3 < nch; c += 4)
        {
            const float f0 = buffer_ld1(feat0_data, f0base + c0 + c);
            const float f1 = buffer_ld1(feat0_data, f0base + c0 + c + 1);
            const float f2 = buffer_ld1(feat0_data, f0base + c0 + c + 2);
            const float f3 = buffer_ld1(feat0_data, f0base + c0 + c + 3);
            const int ta = dot_a_idx * TILE_CH + c;
            da0 += f0 * lfp2afp(tile[ta]);
            da1 += f1 * lfp2afp(tile[ta + 1]);
            da2 += f2 * lfp2afp(tile[ta + 2]);
            da3 += f3 * lfp2afp(tile[ta + 3]);
            if (has_dot_b)
            {
                const int tb = dot_b_idx * TILE_CH + c;
                db0 += f0 * lfp2afp(tile[tb]);
                db1 += f1 * lfp2afp(tile[tb + 1]);
                db2 += f2 * lfp2afp(tile[tb + 2]);
                db3 += f3 * lfp2afp(tile[tb + 3]);
            }
        }
        for (int c = (nch / 4) * 4; c < nch; c++)
        {
            const float f = buffer_ld1(feat0_data, f0base + c0 + c);
            da0 += f * lfp2afp(tile[dot_a_idx * TILE_CH + c]);
            if (has_dot_b)
                db0 += f * lfp2afp(tile[dot_b_idx * TILE_CH + c]);
        }
        dot_a += (da0 + da1) + (da2 + da3);
        if (has_dot_b)
            dot_b += (db0 + db1) + (db2 + db3);

        barrier();
    }

    dots[dot_a_idx] = dot_a;
    if (has_dot_b)
        dots[dot_b_idx] = dot_b;

    barrier();

    {
        const int k_a = tid;
        if (k_a < p.KK * p.K2)
        {
            const int i = k_a / p.K2;
            const int j = k_a - i * p.K2;
            const int tt = j * TILE_W + i;
            const float va = w00 * dots[tt]
                           + w10 * dots[tt + 1]
                           + w01 * dots[tt + TILE_W]
                           + w11 * dots[tt + TILE_W + 1];
            buffer_st1(out_data, k_a * p.out_cstep + n, va * scale);
        }
    }
    {
        const int k_b = tid + 64;
        if (k_b < p.KK * p.K2)
        {
            const int i = k_b / p.K2;
            const int j = k_b - i * p.K2;
            const int tt = j * TILE_W + i;
            const float vb = w00 * dots[tt]
                           + w10 * dots[tt + 1]
                           + w01 * dots[tt + TILE_W]
                           + w11 * dots[tt + TILE_W + 1];
            buffer_st1(out_data, k_b * p.out_cstep + n, vb * scale);
        }
    }
}
)";

static const size_t flownetcorrfused_comp_data_size = sizeof(flownetcorrfused_comp_data) - 1;

#endif // FLOWNETCORRFUSED_COMP_H
