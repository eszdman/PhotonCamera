#version 300 es
precision highp float;
precision highp usampler2D;
uniform usampler2D InputBuffer;
uniform vec3 whitePoint;
uniform int CfaPattern;
uniform float whitelevel;
uniform int yOffset;

out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(CfaPattern%2,yOffset+CfaPattern/2);
    int fact1 = xy.x%2;
    int fact2 = xy.y%2;
    if(fact1+fact2 == 1){
        Output = (float(texelFetch(InputBuffer, (xy), 0).x)/whitelevel)/whitePoint.g;
    } else {
        Output = (float(texelFetch(InputBuffer, (xy), 0).x)/whitelevel)/whitePoint[fact2*2];
    }
}