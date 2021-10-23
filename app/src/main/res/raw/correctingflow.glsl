#version 300 es
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
//uniform sampler2D CorrectingFlowRG;
//uniform sampler2D CorrectingFlowB;
#define SIZE 0,0
out vec3 Output;
#define C 0.5,0.5
#define RC 0.0,0.0
#define GC 0.0,0.0
#define BC 0.0,0.0
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec2 uv = gl_FragCoord.xy/vec2(SIZE);
    vec2 center = (uv-vec2(C))*abs((uv-vec2(C)));
    vec2 dxyR = center*vec2(RC);
    vec2 dxyG = center*vec2(GC);
    vec2 dxyB = center*vec2(BC);
    Output.r = texture(InputBuffer,(uv+dxyR)).r;
    Output.g = texture(InputBuffer,(uv+dxyG)).g;
    Output.b = texture(InputBuffer,(uv+dxyB)).b;


    /*
    vec4 flowRG = texture(CorrectingFlowRG,vec2(xy.x,xy.y)/vec2(SIZE));
    //flowRG*=vec2(SIZE)/4.0;
    vec2 flowB = flowRG.rg;
    //vec2 flowB = texture(CorrectingFlowB, vec2(xy.x,xy.y)/vec2(SIZE)).xy;
    //flowB*=vec2(SIZE)/4.0;
    flowRG*=vec2(SIZE).x/4.0;
    flowB*=vec2(SIZE).x/4.0;
    float Rc = texelFetch(InputBuffer, (xy+ivec2(flowRG.xy)), 0).r;
    float Gc = texelFetch(InputBuffer, (xy+ivec2(flowRG.ba)), 0).g;
    float Bc = texelFetch(InputBuffer, (xy+ivec2(flowB)), 0).b;*/
    //Output = vec3(Rc,Gc,Bc);
}