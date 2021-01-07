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
#import median
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 fact = xy%2;
    xy+=ivec2(CfaPattern%2,yOffset+CfaPattern/2);
    float balance;
    vec4 gains = textureBicubicHardware(GainMap, vec2(xy)/vec2(RawSize));
    gains.rgb = vec3(gains.r,(gains.g+gains.b)/2.0,gains.a);
    vec3 level = vec3(blackLevel.r,(blackLevel.g+blackLevel.b)/2.0,blackLevel.a);
    if(fact.x+fact.y == 1){
        balance = whitePoint.g;
        float[5] g;
        g[0] = float(texelFetch(InputBuffer, (xy+ivec2(0,0)), 0).x);
        g[1] = float(texelFetch(InputBuffer, (xy+ivec2(-1,-1)), 0).x);
        g[2] = float(texelFetch(InputBuffer, (xy+ivec2(-1,1)), 0).x);
        g[3] = float(texelFetch(InputBuffer, (xy+ivec2(1,-1)), 0).x);
        g[4] = float(texelFetch(InputBuffer, (xy+ivec2(1,1)), 0).x);
        float sum = (g[1]+g[2]+g[3]+g[4])/4.0;
        if(g[0] > sum*1.9) g[0] = median5(g);
        Output = float(g[0])/float(whitelevel);
        Output = gains.g*(Output-level.g)/(1.0-level.g);
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
