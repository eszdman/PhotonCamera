#version 300 es
uniform sampler2D InputBuffer;
uniform sampler2D LowRes;
uniform ivec2 insize;
uniform ivec2 lowsize;
uniform int yOffset;
out vec4 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec2 xyInterp = vec2(xy)/vec2(insize);
    vec4 lowrespix = vec4(texture(LowRes, xyInterp));
    //float weight = 1.0 + cos(avrbr*PI*1.0);
    Output = texelFetch(InputBuffer, xy, 0)-lowrespix*0.3;
    //Output = lowrespix;
}
