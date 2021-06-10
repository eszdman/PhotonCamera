#version 300 es
#define SIZE 1
#define INSIZE 1, 1
#define tvar vec2
#define tscal float
#define TSAMP sampler2D
precision mediump TSAMP;
precision mediump tscal;
uniform TSAMP InputBufferdxy;
out tvar Output;
#import gaussian
#import coords
void main(){
    ivec2 xy = ivec2(gl_FragCoord.xy);
    float y1 = 0.0;
    float y2 = 0.0;
    float maxCorner = 0.0;
    for(int i = -3; i<=3;i++){
        for(int j = -3; j<=3;j++){
            vec2 dxy = texelFetch(InputBufferdxy,mirrorCoords2(xy+ivec2(i,j),ivec2(INSIZE)),0).rg
            *unscaledGaussian(float(i),0.8)*unscaledGaussian(float(j),0.8);
            float corner = (dxy.x*dxy.x) + (dxy.y*dxy.y) - (dxy.x*dxy.y)*(dxy.x*dxy.y) - 0.05*(dxy.x*dxy.x + dxy.y*dxy.y);
            if(corner > maxCorner){
                maxCorner = corner;
            }
        }
    }
    //Output.r = y1*y2 - 0.05*(y1+y2);
    Output.r = maxCorner;
    Output.g = maxCorner;
    //if(y1 > y2) Output = texelFetch(InputBufferdxy,xy,0).rg;
    //if(Output.r < 0.08) Output.r = 0.0;
    //if(Output.r+Output.g < 0.03) Output = vec2(0.0);
}
