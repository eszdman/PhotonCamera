//
// Created by eszdman on 21.11.2026.
//

uniform sampler2D Base;
uniform sampler2D Scene;
layout(std430,binding=2) buffer Medians { uint histogram[]; };
void main() {
    ivec2 p = ivec2(gl_GlobalInvocationID.xy);
    if (any(greaterThanEqual(p, textureSize(Base,0)))) return;
    uvec3 rgb = uvec3(round(texelFetch(Base,p,0).rgb * 255.0));
    atomicAdd(histogram[rgb.r],1u);
    atomicAdd(histogram[rgb.g],1u);
    atomicAdd(histogram[rgb.b],1u);
    if (all(equal(p % 3, ivec2(0)))) {
        uint bin = uint(clamp(texelFetch(Scene,p,0).r * 255.0, 0.0, 255.0));
        atomicAdd(histogram[256u+bin],1u);
    }
}
