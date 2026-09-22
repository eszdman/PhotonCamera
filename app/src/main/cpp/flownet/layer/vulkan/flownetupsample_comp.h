#ifndef FLOWNETUPSAMPLE_COMP_H
#define FLOWNETUPSAMPLE_COMP_H

#include <cstddef>

static const unsigned char flownetupsample_comp_data[] = R"(#version 450

layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;

layout(constant_id = 0) const int K = 8;

layout(binding = 0) buffer flow_buf { sfp flow_data[]; };
layout(binding = 1) buffer mask_buf { sfp mask_data[]; };
layout(binding = 2) buffer out_buf { sfp out_data[]; };

layout(push_constant) uniform parameter
{
    int H;
    int W;
    int flow_cstep;
    int mask_cstep;
    int out_cstep;
    int K2;
} p;

void main()
{
    const int H = p.H;
    const int W = p.W;
    const int HW = H * W;
    const int kk = K * K;

    const int oy = int(gl_GlobalInvocationID.y);
    const int ox = int(gl_GlobalInvocationID.x);

    if (oy >= K * H || ox >= K * W)
        return;

    const int i = oy / K;
    const int ky = oy - i * K;
    const int j = ox / K;
    const int kx = ox - j * K;
    const int k = ky * K + kx;

    const int base = k * HW + i * W + j;
    float m[9];
    float mx = -1e30;
    for (int n = 0; n < 9; n++)
    {
        m[n] = buffer_ld1(mask_data, n * kk * p.mask_cstep + base);
        mx = max(mx, m[n]);
    }
    float s = 0.0;
    for (int n = 0; n < 9; n++)
    {
        m[n] = exp(m[n] - mx);
        s += m[n];
    }
    const float inv_s = 1.0 / s;

    const int out_ow = K * W;

    for (int o = 0; o < 2; o++)
    {
        float acc = 0.0;
        for (int n = 0; n < 9; n++)
        {
            const int dy = n / 3 - 1;
            const int dx = n % 3 - 1;
            const int iy = i + dy;
            const int jx = j + dx;

            float v = 0.0;
            if (iy >= 0 && iy < H && jx >= 0 && jx < W)
                v = buffer_ld1(flow_data, o * p.flow_cstep + iy * W + jx);

            acc += m[n] * inv_s * v;
        }
        buffer_st1(out_data, o * p.out_cstep + oy * out_ow + ox, acc * float(K));
    }
}
)";

static const size_t flownetupsample_comp_data_size = sizeof(flownetupsample_comp_data) - 1;

#endif // FLOWNETUPSAMPLE_COMP_H
