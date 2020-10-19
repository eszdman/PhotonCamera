#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D LowRes;
uniform ivec2 insize;
uniform ivec2 lowsize;
uniform float str;
uniform int yOffset;
out vec4 Output;
#define M_PI 3.1415926535897932384626433832795f
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec2 xyInterp = vec2(xy)/vec2(insize);
    vec3 lowrespix = vec3(texture(LowRes, xyInterp).rgb);
    //float weight = 1.0 + cos(avrbr*PI*1.0);
    //float weight = sin(length(lowrespix)*2.0*M_PI);

    float absbr = 0.7 - length(lowrespix);
    if(absbr<0.0) //Output = vec4(texelFetch(InputBuffer, xy, 0).rgb+((absbr)*str*1.4),1.0);
    Output = vec4((texelFetch(InputBuffer, xy, 0).rgb+1.0)*(1.0+((absbr)*str*1.4)),1.0)-1.0;
    else Output = vec4((texelFetch(InputBuffer, xy, 0).rgb+1.0)*(1.0+((absbr)*str*0.7)),1.0)-1.0;
    Output*=1.03;
    Output = clamp(Output,0.0,1.0);
    Output.a = 1.0;
    //Output = texelFetch(InputBuffer, xy, 0)+weight*str;
    //Output = lowrespix;
}
