#version 300 es
#define SIZE 1
#define INSIZE 1, 1
#define tvar vec2
#define tscal float
#define TSAMP sampler2D
precision mediump TSAMP;
precision mediump tscal;
uniform TSAMP InputBufferdx;
uniform TSAMP InputBufferdy;
out tvar Output;
void main(){
    ivec2 xy = ivec2(gl_FragCoord.xy);
    Output =
    vec2(texelFetch(InputBufferdx,xy+ivec2(-1,0),0).rg+texelFetch(InputBufferdx,xy+ivec2(1,0),0).rg)*
    vec2(texelFetch(InputBufferdy,xy+ivec2(0,-1),0).rg+texelFetch(InputBufferdy,xy+ivec2(0,1),0).rg);
    //  if(Output.r+Output.g < 0.03) Output = vec2(0.0);
}
