//
// Created by eszdman on 21.11.2026.
//

// GLHistogram's scale-3 lattice. Direct integer atomics allow any bin count
// without device-specific shared-memory limits or per-bin-count SPIR-V.
uniform sampler2D InputBuffer;
uniform vec3 exposure;
uniform float binOffset;
uniform int bins;
layout(std430,binding=1) buffer Histogram { uint counts[]; };
void main() {
    ivec2 p = ivec2(gl_GlobalInvocationID.xy) * 3;
    if (any(greaterThanEqual(p, textureSize(InputBuffer,0)))) return;
    vec3 rgb = texelFetch(InputBuffer,p,0).rgb;
    uvec3 bin = uvec3(clamp(exposure * rgb + binOffset, 0.0, float(bins-1)));
    atomicAdd(counts[bin.r],1u);
    atomicAdd(counts[bins+bin.g],1u);
    atomicAdd(counts[bins*2+bin.b],1u);
}
