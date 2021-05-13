#version 300 es
#define SIZE 1
#define INSIZE 1
#define tvar vec2
#define tscal float
#define TSAMP sampler2D
precision mediump TSAMP;
precision mediump tscal;
uniform TSAMP InputBuffer;
uniform TSAMP InputBuffer2;
#import coords
out tvar Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 xy2 = xy*4;
    vec2 t = vec2(texelFetch(InputBuffer,xy,0).rg);
    vec2 intemp = t;
    for(int i =-2; i<2;i++){
        for(int j = -2; j<2;j++){
            t = max(vec2(texelFetch(InputBuffer2,xy2+ivec2(i,j),0).rg),t);
        }
    }
    Output=(intemp*2.0+t)/3.0;
}
