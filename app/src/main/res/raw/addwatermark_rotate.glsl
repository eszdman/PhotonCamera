#version 300 es
precision mediump float;
precision mediump sampler2D;
precision mediump usampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D Watermark;
uniform int yOffset;
uniform int rotate;
out vec4 Output;
#define watersizek (5.0)
#define resize (3)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 texSize = ivec2(textureSize(InputBuffer, 0))-1;
    vec2 texS = vec2(textureSize(InputBuffer, 0));
    ivec2 watersize = ivec2(textureSize(Watermark, 0));
    vec4 water;
    vec2 cr;
    xy+=ivec2(0,yOffset);
    switch(rotate){
        case 0:
        cr = (vec2(xy*resize+ivec2(0,-texSize.y*resize))/(texS));
        Output = texelFetch(InputBuffer, (xy), 0);
        break;
        case 1:
        cr = (vec2(xy*resize+ivec2(0,-texSize.x*resize))/(texS));
        Output = texelFetch(InputBuffer, ivec2(texSize.x-xy.y,xy.x), 0);
        break;
        case 2:
        cr = (vec2(xy*resize+ivec2(0,-texSize.y*resize))/(texS));
        Output = texelFetch(InputBuffer, ivec2(texSize.x-xy.x,texSize.y-xy.y), 0);
        break;
        case 3:
        cr = (vec2(xy*resize+ivec2(0,-texSize.x*resize))/(texS));
        Output = texelFetch(InputBuffer, ivec2(xy.y,texSize.y-xy.x),0);
        break;
    }
    cr+=vec2(0.0,1.0/watersizek);
    cr.x*=(texS.x)/(texS.y);
    cr.x/=float(watersize.x)/float(watersize.y);
    cr*=watersizek;
    water = texture(Watermark,cr);
    Output = mix(Output,water.rgba,water.a);
    Output.a=1.0;
}
