#import amaze
// AMaZE pass 8b: nyquist refinement using G curvatures (C++ lines 994-1012).
// The bounding-box test is disabled: refinement runs wherever the dilated
// per-site flags say so.
uniform sampler2D u_gd;      // rgbgreen, dgrb0, dgrb2h, dgrb2v
uniform sampler2D u_cd2;
uniform sampler2D u_nyq2; // @post-r32f
uniform sampler2D u_grad;    // cfa rides in .a (written by gradcd)

ivec2 rbSlot(ivec2 p) { return ivec2(p.x & ~1, p.y); }
// Refinement can sample across a window edge. Clamp after mapping to the half grid.
ivec2 curvatureSlot(ivec2 p) { return clamp(rbSlot(p), ivec2(0), u_size - 1); }
float V(ivec2 p)   { return texelFetch(u_cd2, p, 0).r; }
float H(ivec2 p)   { return texelFetch(u_cd2, p, 0).g; }
float d2h(ivec2 p) { return texelFetch(u_gd, curvatureSlot(p), 0).b; }
float d2v(ivec2 p) { return texelFetch(u_gd, curvatureSlot(p), 0).a; }
float n2(ivec2 p)  { return texelFetch(u_nyq2, rbSlot(p), 0).r; }
float Cf(ivec2 p)  { return texelFetch(u_grad, p, 0).a; }

layout(binding = 0, rgba16f) writeonly uniform highp image2D img_out;
void emit(ivec2 p, vec4 v) { imageStore(img_out, p, v); }
float d0ref(ivec2 s) {   // refined colour difference at a nyquist R/B site
    float gvarh = EPSSQ + (GQUINC[0] * d2h(s)
        + GQUINC[1] * (d2h(s + ivec2(1, 1)) + d2h(s + ivec2(1, -1)) + d2h(s + ivec2(-1, -1)) + d2h(s + ivec2(-1, 1)))
        + GQUINC[2] * (d2h(s + ivec2(0, -2)) + d2h(s + ivec2(-2, 0)) + d2h(s + ivec2(2, 0)) + d2h(s + ivec2(0, 2)))
        + GQUINC[3] * (d2h(s + ivec2(2, 2)) + d2h(s + ivec2(2, -2)) + d2h(s + ivec2(-2, -2)) + d2h(s + ivec2(-2, 2))));
    float gvarv = EPSSQ + (GQUINC[0] * d2v(s)
        + GQUINC[1] * (d2v(s + ivec2(1, 1)) + d2v(s + ivec2(1, -1)) + d2v(s + ivec2(-1, -1)) + d2v(s + ivec2(-1, 1)))
        + GQUINC[2] * (d2v(s + ivec2(0, -2)) + d2v(s + ivec2(-2, 0)) + d2v(s + ivec2(2, 0)) + d2v(s + ivec2(0, 2)))
        + GQUINC[3] * (d2v(s + ivec2(2, 2)) + d2v(s + ivec2(2, -2)) + d2v(s + ivec2(-2, -2)) + d2v(s + ivec2(-2, 2))));
    return (H(s) * gvarv + V(s) * gvarh) / (gvarv + gvarh);
}
void main() {
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (p.x >= u_size.x || p.y >= u_size.y) return;
    vec4 base = texelFetch(u_gd, p, 0);
    vec4 o = vec4(base.r, base.g, 0.0, 0.0);
    if (!isG(p.y, p.x) && n2(p) > 0.5) {
        o.r = Cf(p) + d0ref(p);            // per-pixel green
    }
    if ((p.x & 1) == 0) {
        ivec2 s = ivec2(site_x(p), p.y);   // anchored site's half-grid d0
        if (n2(s) > 0.5) {
            o.g = d0ref(s);
        }
    }
    emit(p, o);
}
