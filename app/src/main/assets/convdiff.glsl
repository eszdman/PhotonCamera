#version 300 es
#define SIZE 1
#define INSIZE 1
#define tvar vec2
#define tscal float
#define TSAMP sampler2D
#define coordstp(x,y) (ivec2(y,x))
#define stepping(i,j) (coordstp(-1+j,i))
#define stepping2(i,j) (coordstp(1+j,i))
#define stepping0(i,j) (coordstp(0+j,i))
#define stepping3(i,j) (coordstp(-2+j,i))
#define stepping4(i,j) (coordstp(2+j,i))
uniform float rotation;
#define BLURRING algorithm
precision mediump TSAMP;
precision mediump tscal;
uniform TSAMP InputBuffer;
#define j 0
#define i 0
#import coords
out tvar Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 insizev = ivec2(INSIZE);
    vec2 xy2 = vec2(xy)-vec2(insizev)/2.0;
    if(abs(rotation) >= 0.01){
        xy.x = int(xy2.x*cos(rotation)-xy2.y*sin(rotation));
        xy.y = int(xy2.y*sin(rotation)+xy2.y*cos(rotation));
        xy+=insizev/2;
    }
    //int j =0;
    //for(int i =-1; i<1;i++){
        //for(int j = -1; j<1;j++){
            BLURRING;
        //}
    //}
    //Output=max(min((Output-0.02)*10.0,1.0),0.0);
    Output=abs(Output)/float(1);
}
