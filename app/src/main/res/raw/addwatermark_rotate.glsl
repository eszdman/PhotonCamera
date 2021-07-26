#version 300 es
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D Watermark;
uniform int yOffset;
uniform int rotate;
out vec4 Output;
#define WATERMARK 1
#define watersizek (15.0)
#import interpolation
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 texSize = ivec2(textureSize(InputBuffer, 0));
    vec2 texS = vec2(textureSize(InputBuffer, 0));
    vec2 watersize = vec2(textureSize(Watermark, 0));
    vec4 water;
    vec2 cr;
    xy+=ivec2(0,yOffset);
    switch(rotate){
        case 0:
        cr = (vec2(xy+ivec2(0,-texSize.y))/(texS));
        Output = texelFetch(InputBuffer, (xy), 0);
        break;
        case 1:
        cr = (vec2(xy+ivec2(0,-texSize.x))/(texS));
        Output = texelFetch(InputBuffer, ivec2(texSize.x-xy.y,xy.x), 0);
        break;
        case 2:
        cr = (vec2(xy+ivec2(0,-texSize.y))/(texS));
        Output = texelFetch(InputBuffer, ivec2(texSize.x-xy.x,texSize.y-xy.y), 0);
        break;
        case 3:
        cr = (vec2(xy+ivec2(0,-texSize.x))/(texS));
        Output = texelFetch(InputBuffer, ivec2(xy.y,texSize.y-xy.x),0);
        break;
    }
    #if WATERMARK == 1
    cr+=vec2(0.0,1.0/watersizek);
    cr.x*=(texS.x)/(texS.y);
    cr.x/=watersize.x/watersize.y;
    cr.x*=1.025;
    cr*=watersizek;
    if(cr.x >= 0.0 && cr.y >= 0.0){
    water = textureBicubicHardware(Watermark,cr);
    Output = mix(Output,water.rgba,water.a);
    }
    #endif
    Output.a=1.0;
}
