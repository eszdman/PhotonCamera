//
// Created by eszdman on 19.11.2026.
//

#ifndef PHOTON_MERGE_INPUTS_GLSL
#define PHOTON_MERGE_INPUTS_GLSL

// Each Bayer quad occupies eight bytes, as in the RGBA16F GLES images.
layout(std430, set=0, binding=0) readonly buffer Base { uvec2 base[]; };
layout(std430, set=0, binding=1) readonly buffer Aligned { uvec2 aligned[]; };
layout(std430, set=0, binding=2) writeonly buffer Output { uvec2 merged[]; };
layout(std430, set=0, binding=3) buffer Support { float support[]; };
layout(std430, set=0, binding=4) readonly buffer KernelMap { uvec2 kernels[]; };

#include "merge_parameters.glsl"

#endif
