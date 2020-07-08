#version 300 es
precision mediump float;
precision mediump usampler2D;
precision mediump sampler2D;
uniform sampler2D Fullbuffer;
uniform sampler2D GainMap;
uniform int RawSizeX;
uniform int RawSizeY;
uniform vec4 blackLevel;
uniform int yOffset;

out vec4 Output;
vec3 linearizeAndGainMap(ivec2 coords){
    vec3 pRGB;
    vec4 inbuff = texelFetch(Fullbuffer,coords,0);
    vec2 xyInterp = vec2(float(coords.x) / float(RawSizeX), float(coords.y) / float(RawSizeY));
    vec4 gains = texture(GainMap, xyInterp);
    pRGB.r = gains.r*float(inbuff.r-blackLevel.r);
    pRGB.g = ((gains.g+gains.b)/2.)*float(inbuff.g-(blackLevel.g+blackLevel.b)/2.);
    pRGB.b = gains.a*float(inbuff.b-blackLevel.a);
    return pRGB;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec3 pRGB = linearizeAndGainMap(xy);
    Output = vec4(pRGB.r,pRGB.g,pRGB.b,1.0);
}