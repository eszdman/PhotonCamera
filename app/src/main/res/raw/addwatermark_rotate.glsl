#version 300 es
precision mediump float;
precision mediump sampler2D;
precision mediump usampler2D;
uniform sampler2D InputBuffer;
uniform usampler2D Watermark;
uniform int yOffset;
uniform int rotate;
out vec4 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 texSize = ivec2(textureSize(InputBuffer, 0))-1;
    ivec2 watersize = ivec2(textureSize(Watermark, 0));
    xy+=ivec2(0,yOffset);
    //Output = texelFetch(InputBuffer, (ivec2(texSize.x-xy.y,xy.x)), 0)+vec4(texelFetch(Watermark, (xy), 0).r);
    switch(rotate){
        case 0:
        Output = texelFetch(InputBuffer, (xy), 0)+vec4(texelFetch(Watermark, (xy+ivec2(0,-texSize.y+watersize.y)), 0).r)/16384.0;
        break;
        case 1:
        Output = texelFetch(InputBuffer, (ivec2(texSize.x-xy.y,xy.x)), 0)+vec4(texelFetch(Watermark, (xy+ivec2(0,-texSize.x+watersize.y)), 0).r)/16384.0;
        break;
        case 2:
        Output = texelFetch(InputBuffer, (ivec2(texSize.x-xy.x,texSize.y-xy.y)), 0)+vec4(texelFetch(Watermark, (xy+ivec2(0,-texSize.y+watersize.y)), 0).r)/16384.0;
        break;
        case 3:
        Output = texelFetch(InputBuffer, (ivec2(xy.y,texSize.y-xy.x)), 0)+vec4(texelFetch(Watermark, (xy+ivec2(0,-texSize.x+watersize.y)), 0).r)/16384.0;
        break;
    }
    Output.a=1.0;
}
