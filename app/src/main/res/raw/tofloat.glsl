#version 300 es
precision highp float;
precision highp usampler2D;
uniform usampler2D InputBuffer;
uniform vec3 whitePoint;
uniform int CfaPattern;
uniform int patSize;
uniform uint whitelevel;
uniform int yOffset;

out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(CfaPattern%2,yOffset+CfaPattern/2);
    ivec2 fact = xy%2;
    float balance;
    if(fact.x+fact.y == 1){
        balance = whitePoint.g;
    } else {
        if(fact.x == 0){
            balance = whitePoint.r;
        } else {
            balance = whitePoint.b;
        }
    }
    if(fact.x+fact.y == 1){
        Output = (float(texelFetch(InputBuffer, (xy), 0).x)/float(whitelevel));
    } else {
        Output = (float(texelFetch(InputBuffer, (xy), 0).x)/float(whitelevel));
    }
    Output = clamp(Output,0.0,balance);
}
