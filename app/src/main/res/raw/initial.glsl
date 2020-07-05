#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform usampler2D Fullbuffer;
uniform int RawSizeX;
uniform int RawSizeY;
uniform vec4 blackLevel;
uniform float whiteLevel;

uniform int yOffset;

out vec4 Output;
vec3 demosaic(ivec2 coords){
    vec3 pRGB;
    float bl = 0.f;
    float g = 1.f;
    vec4 gains = vec4(1.0);
    uvec4 inbuff = texelFetch(Fullbuffer,coords,0);
    pRGB.r = float(inbuff.r)/(whiteLevel);
    pRGB.g = float(inbuff.g)/(whiteLevel);
    pRGB.b = float(inbuff.b)/(whiteLevel);
    return pRGB;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec3 pRGB = demosaic(xy);
    Output = vec4(pRGB.r,pRGB.g,pRGB.b,1.0);
}