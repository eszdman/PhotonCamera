#version 300 es
precision highp float;
precision highp usampler2D;
precision mediump sampler2D;
uniform usampler2D InputBuffer;
uniform sampler2D GainMap;
uniform ivec2 RawSize;
uniform vec4 blackLevel;
uniform vec3 whitePoint;
uniform int CfaPattern;
uniform int patSize;
uniform uint whitelevel;
uniform float Regeneration;
uniform int MinimalInd;
uniform int yOffset;
#import interpolation
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 fact = xy%2;
    xy+=ivec2(CfaPattern%2,yOffset+CfaPattern/2);
    float balance;
    vec4 gains = textureBicubicHardware(GainMap, vec2(xy)/vec2(RawSize));
    gains.rgb = vec3(gains.r,(gains.g+gains.b)/2.0,gains.a);
    vec3 level = vec3(blackLevel.r,(blackLevel.g+blackLevel.b)/2.0,blackLevel.a);
    //Output*=whitePoint;
    if(fact.x+fact.y == 1){
        balance = whitePoint.g;
        Output = float(texelFetch(InputBuffer, (xy), 0).x)/float(whitelevel);
        Output = gains.g*(Output-level.g)/(1.0-level.g);

        /*float col = float(texelFetch(InputBuffer, (xy-fact+ivec2(MinimalInd/2)), 0).x)/float(whitelevel);
        col = gains[MinimalInd]*(col-level[MinimalInd])/(1.0-level[MinimalInd]);
        //Green channel regeneration
        if(Output > 0.999 && col > whitePoint[MinimalInd]){
            float oldGreen = Output;
            Output = col/whitePoint[MinimalInd];
            //Output*= whitePoint[1];
            //Output = mix(Output,oldGreen,clamp((1.0-oldGreen)/0.07,0.00000001,0.9999999));
            //Output = mix(oldGreen,Output,1.0-clamp((1.0-oldGreen)/0.03,0.0,1.0));
            //float k = clamp((1.0-oldGreen)/0.03,0.0,1.0);
            //Output = Output*k + oldGreen*(1.0-k);
        }*/
    } else {
        if(fact.x == 0){
            balance = whitePoint.r;
            Output = float(texelFetch(InputBuffer, (xy), 0).x)/float(whitelevel);
            Output = gains.r*(Output-level.r)/(1.0-level.r);
        } else {
            balance = whitePoint.b;
            Output = float(texelFetch(InputBuffer, (xy), 0).x)/float(whitelevel);
            Output = gains.b*(Output-level.b)/(1.0-level.b);
        }
    }
    Output = clamp(Output,0.0,balance*Regeneration)/Regeneration;
}
