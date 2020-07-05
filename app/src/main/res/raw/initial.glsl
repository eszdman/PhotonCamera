#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform usampler2D RawBuffer;
uniform int RawSizeX;
uniform int RawSizeY;
uniform int CfaPattern;
uniform vec4 blackLevel;
uniform float whiteLevel;

uniform int yOffset;

out vec4 Output;
vec3 demosaic(ivec2 coords){
    int fact1 = coords.x%2;
    int fact2 = coords.y%2;
    float inputArray[4];
    for(int i =0; i<4;i++) inputArray[i] = float(texelFetch(RawBuffer,coords+ivec2((i%2)+CfaPattern%2,i/2+CfaPattern/2),0).x);
    vec3 pRGB;
    float bl = 0.f;
    float g = 1.f;
    vec4 gains = vec4(1.0);
    pRGB.r = inputArray[0]/(whiteLevel);
    pRGB.g = (inputArray[1]+inputArray[2])/((whiteLevel)*2.0);
    pRGB.b = inputArray[3]/whiteLevel/(whiteLevel);
    return pRGB;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    uint vx = texelFetch(RawBuffer, xy + ivec2(0,0), 0).x;
    vec3 pRGB = demosaic(xy);
    Output = vec4(pRGB.r,pRGB.g,pRGB.b,1.0);
}