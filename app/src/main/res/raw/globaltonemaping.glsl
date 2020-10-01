#version 300 es
uniform sampler2D InputBuffer;
uniform sampler2D LowRes;
uniform ivec2 insize;
uniform ivec2 lowsize;
uniform float str;
uniform int yOffset;
out vec4 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec2 xyInterp = vec2(xy)/vec2(insize);
    vec3 lowrespix = vec3(texture(LowRes, xyInterp).rgb);
    //float weight = 1.0 + cos(avrbr*PI*1.0);
    float absbr = 0.7 - length(vec4(lowrespix,(lowrespix.r+lowrespix.g+lowrespix.b)/3.0));
    if(absbr<0.0) Output = vec4(texelFetch(InputBuffer, xy, 0).rgb+((absbr)*str*1.4),1.0);
    else Output = vec4(texelFetch(InputBuffer, xy, 0).rgb+((absbr)*str*0.7),1.0);
    //Output = texelFetch(InputBuffer, xy, 0)+((absbr)*str*1.4 + 0.05);
    //Output = lowrespix;
}
