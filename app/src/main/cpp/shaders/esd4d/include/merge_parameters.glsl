//
// Created by eszdman on 19.11.2026.
//

#ifndef PHOTON_MERGE_PARAMETERS_GLSL
#define PHOTON_MERGE_PARAMETERS_GLSL

layout(push_constant) uniform MergeParameters {
    ivec2 rawHalf;
    ivec2 cfaShift;
    ivec2 kernelSize;
    float noiseS;
    float noiseO;
    float exposure;
    uint frameIndex;
    float weight;
};

#endif
