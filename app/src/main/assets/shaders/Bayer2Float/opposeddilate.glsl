// Inpaint opposed - pass 2: mask dilation.
// One invocation per cell: ORs the per-channel clip bits over a radius-3
// octagon of neighbouring cells, so the chrominance statistics include the
// photosites immediately around clipped data. Cells closer than DILATE to
// the grid border keep their undilated bits.
precision highp float;
precision highp int;
uniform ivec2 u_msize;
#define DILATE 3
#define LAYOUT //
LAYOUT
layout(std430, binding = 1) buffer MaskIn {
    uint m[];
};
layout(std430, binding = 2) buffer MaskDil {
    uint d[];
};

void main() {
    ivec2 cell = ivec2(gl_GlobalInvocationID.xy);
    if (cell.x >= u_msize.x || cell.y >= u_msize.y) return;
    int i = cell.y * u_msize.x + cell.x;
    uint bits = m[i];
    bool safe = cell.x >= DILATE && cell.y >= DILATE
            && cell.x < u_msize.x - DILATE - 1 && cell.y < u_msize.y - DILATE - 1;
    if (safe) {
        for (int dy = -DILATE; dy <= DILATE; dy++) {
            for (int dx = -DILATE; dx <= DILATE; dx++) {
                if (abs(dx) == DILATE && abs(dy) == DILATE) continue;
                bits |= m[i + dy * u_msize.x + dx];
            }
        }
    }
    d[i] = bits;
}
