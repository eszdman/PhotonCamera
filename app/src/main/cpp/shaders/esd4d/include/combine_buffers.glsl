//
// Created by eszdman on 20.11.2026.
//

#include "workgroup.glsl"

#include "merge_inputs.glsl"

#define supportInitialized (frameIndex > 1u)
ivec2 outputSize() { return rawHalf + cfaShift; }
int indexOf(ivec2 p) { return p.y * outputSize().x + p.x; }
vec4 decodeQuad(uvec2 q) { return vec4(unpackHalf2x16(q.x), unpackHalf2x16(q.y)); }
bool outsideQuad(ivec2 p) { return any(lessThan(p,ivec2(0))) || any(greaterThanEqual(p,outputSize())); }
vec4 readBase(ivec2 p) { return outsideQuad(p) ? vec4(0) : decodeQuad(base[indexOf(p)]); }
vec4 readAligned(ivec2 p) { return outsideQuad(p) ? vec4(0) : decodeQuad(aligned[indexOf(p)]); }
float readSupport(ivec2 p) { return support[indexOf(p)]; }
void writeSupport(ivec2 p, float v) { support[indexOf(p)] = v; }
void writeMerged(ivec2 p, vec4 v) {
    merged[indexOf(p)] = uvec2(packHalf2x16(v.rg), packHalf2x16(v.ba));
}
vec4 kernelTexel(ivec2 p) {
    p = clamp(p, ivec2(0), kernelSize-1);
    return decodeQuad(kernels[p.y*kernelSize.x+p.x]);
}
// Match texture(kernelsMap, p / (2 * kernelSize)) with LINEAR and CLAMP_TO_EDGE.
vec4 sampleKernel(vec2 uv) {
    vec2 pos = uv*vec2(kernelSize) - 0.5;
    ivec2 i = ivec2(floor(pos));
    vec2 f = fract(pos);
    return mix(mix(kernelTexel(i), kernelTexel(i+ivec2(1,0)), f.x),
               mix(kernelTexel(i+ivec2(0,1)), kernelTexel(i+1), f.x), f.y);
}
#define ESD4D_BUFFER_BACKEND 1
#define inTexture readBase
#define diffTexture readAligned
#define outTexture writeMerged
vec4 supportTexture(ivec2 p) { return vec4(readSupport(p)); }
void supportTexture(ivec2 p,vec4 v) { writeSupport(p,v.x); }
#define imageLoad(resource,p) resource(p)
#define imageStore(resource,p,v) resource(p,v)
#define imageSize(resource) outputSize()
#define textureSize(resource,lod) kernelSize
#define texture(resource,uv) sampleKernel(uv)
