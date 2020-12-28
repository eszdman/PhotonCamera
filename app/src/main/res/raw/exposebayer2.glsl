#version 300 es
precision mediump sampler2D;
precision highp float;
uniform sampler2D InputBuffer;
uniform float factor;
uniform vec3 neutralPoint;
out vec4 result;
uniform int yOffset;
#define DR (1.4)
#define DH (0.0)
void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    xyCenter+=ivec2(0,yOffset);
    xyCenter*=2;
    vec4 inp;
    inp.r = texelFetch(InputBuffer, xyCenter, 0).r;
    inp.g = texelFetch(InputBuffer, xyCenter+ivec2(1,0), 0).r;
    inp.b = texelFetch(InputBuffer, xyCenter+ivec2(0,1), 0).r;
    inp.a = texelFetch(InputBuffer, xyCenter+ivec2(1,1), 0).r;
    if(factor > 1.0){
        float br2 = inp.r+inp.g+inp.b+inp.a;
        br2/=4.0;
        inp*=factor*clamp((0.7-br2)*1.3,0.5,1.0);
    }
    result = clamp(inp,vec4(0.0001),neutralPoint.rggb);
    result/=neutralPoint.rggb;
}
