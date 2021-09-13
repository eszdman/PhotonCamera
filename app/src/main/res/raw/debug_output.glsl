#version 300 es
precision highp float;
precision highp sampler2D;
precision highp isampler2D;
precision highp usampler2D;
uniform isampler2D InputBuffer;
#define EPS 0.0001
#define PI 3.1415926535897932384626433832795
out vec4 Output;
#import hsvtoxyz
#import vectortorgb
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec2 V = vec2(texelFetch(InputBuffer, xy, 0).xy);
    //Output = vec4(vectortorgb(V),1.0);
    Output = vec4(V.r/256.0,V.g/256.0,0.0,1.0);
}
