
precision highp float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D LowRes;
uniform ivec2 insize;
uniform ivec2 lowsize;
uniform float str;
uniform int yOffset;
out vec3 Output;
#define M_PI 3.1415926535897932384626433832795f
#import interpolation
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec2 xyInterp = vec2(xy)/vec2(insize);
    vec3 lowrespix = vec3(textureLinear(LowRes, xyInterp).rgb);
    //float weight = 1.0 + cos(avrbr*PI*1.0);
    //float weight = sin(length(lowrespix)*2.0*M_PI);

    float absbr = 0.6 - length(lowrespix);
    if(absbr<0.0) //Output = vec4(texelFetch(InputBuffer, xy, 0).rgb+((absbr)*str*1.4),1.0);
    Output = ((texelFetch(InputBuffer, xy, 0).rgb)*(1.0+((absbr)*str*0.0)));
    else Output = (texelFetch(InputBuffer, xy, 0).rgb)*(1.0+((absbr)*str*10.));
    //Output*=0.9;
    Output = clamp(Output,0.0,1.0);
    //Output = texelFetch(InputBuffer, xy, 0)+weight*str;
    //Output = lowrespix;
}
