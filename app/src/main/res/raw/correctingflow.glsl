#version 300 es
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D CorrectingFlow;
#define SIZE 0,0
out vec3 Output;

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec2 flowR = texture(CorrectingFlow,vec2(xy.x*3,xy.y)/vec2(SIZE)).xy;
    flowR*=vec2(SIZE)/4.0;
    vec2 flowG = texture(CorrectingFlow, vec2(xy.x*3 + 1,xy.y)/vec2(SIZE)).xy;
    flowG*=vec2(SIZE)/4.0;
    vec2 flowB = texture(CorrectingFlow, vec2(xy.x*3 + 2,xy.y)/vec2(SIZE)).xy;
    flowB*=vec2(SIZE)/4.0;
    float Rc = texelFetch(InputBuffer, (xy+ivec2(flowR)), 0).r;
    float Gc = texelFetch(InputBuffer, (xy+ivec2(flowG)), 0).g;
    float Bc = texelFetch(InputBuffer, (xy+ivec2(flowB)), 0).b;
    Output = vec3(Rc,Gc,Bc);
}