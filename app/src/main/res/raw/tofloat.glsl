#version 300 es
precision highp float;
precision highp usampler2D;
uniform usampler2D InputBuffer;
uniform vec3 whitePoint;
uniform int CfaPattern;
uniform int patSize;
uniform uint whitelevel;
uniform float Regeneration;
uniform int MinimalInd;
uniform int yOffset;

out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 fact = xy%2;
    xy+=ivec2(CfaPattern%2,yOffset+CfaPattern/2);
    float balance;
    if(fact.x+fact.y == 1){
        balance = whitePoint.g;
        Output = (float(texelFetch(InputBuffer, (xy), 0).x)/float(whitelevel));
        //Green channel regeneration
        if(Output >= 0.97){
            float oldGreen = Output;
            Output = (float(texelFetch(InputBuffer, (xy-fact+ivec2(MinimalInd/2)), 0).x)/(float(whitelevel)*whitePoint[MinimalInd]));
            Output*= whitePoint[1];
            Output = mix(Output,oldGreen,clamp((1.0-oldGreen)/0.03,0.00000001,0.9999999));
        }
    } else {
        if(fact.x == 0){
            balance = whitePoint.r;
            Output = (float(texelFetch(InputBuffer, (xy), 0).x)/float(whitelevel));
        } else {
            balance = whitePoint.b;
            Output = (float(texelFetch(InputBuffer, (xy), 0).x)/float(whitelevel));
        }
    }
    Output = clamp(Output,0.0,balance*Regeneration)/Regeneration;
}
