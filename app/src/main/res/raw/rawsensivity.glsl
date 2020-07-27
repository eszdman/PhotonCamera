#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform usampler2D RawBuffer;
uniform float whitelevel;
uniform usampler2D HotPixelMap;
uniform ivec2 HotPixelMapSize;
uniform int yOffset;
uniform float PostRawSensivity;

out vec4 Output;
float int2gl(in uint inp){
return float(inp)/255.0 + 1.0/512.0;
}
uint lowbits(in uint inp){
return inp&uint(255);
}
uint highbits(in uint inp){
return inp>>8;
}
vec4 raw2gl(in uvec2 inp){
return vec4(int2gl(lowbits(inp.r)),int2gl(highbits(inp.r)),int2gl(lowbits(inp.g)),int2gl(highbits(inp.g)));
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy.x*=2;
    xy+=ivec2(0,yOffset);
    uvec2 rawpart = uvec2(uint(texelFetch(RawBuffer, (xy), 0).x),uint(texelFetch(RawBuffer, (xy+ivec2(1,0)), 0).x));
    vec2 flpart = vec2(rawpart)/whitelevel;
    flpart =clamp(flpart*PostRawSensivity*whitelevel,0.0,65535.0);
    //flpart*=PostRawSensivity*whitelevel;
    rawpart = uvec2(flpart);
    Output = raw2gl(rawpart);
}