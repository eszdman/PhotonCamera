#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform usampler2D RawBuffer;
uniform int CfaPattern;

out uint Output;
vec3 demosaic(ivec2 coords){
    int fact1 = coords.x%2;
    int fact2 = coords.y%2;
    
    return pRGB;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(CfaPattern%2,yOffset+CfaPattern/2);
    int fact1 = coords.x%2;
    int fact2 = coords.y%2;
    if(fact1+fact2 == 1){
    
    }
    else {
    Output = uint(texelFetch(RawBuffer, (xy), 0).x);
    }
}
