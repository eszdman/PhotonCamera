#define SIZE 2
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
    vec2 totaldxy = vec2(0.0);
    vec4 M = vec4(0.0,0.0,0.0,0.0);
    for(int i = -SIZE; i<=SIZE;i++){
        for(int j = -SIZE; j<=SIZE;j++){
            /*vec2 dxy = texelFetch(InputBufferdxy,mirrorCoords2(xy+ivec2(i,j),ivec2(INSIZE)),0).rg
            *unscaledGaussian(float(i),0.8)*unscaledGaussian(float(j),0.8);
            float corner = (dxy.x*dxy.x) + (dxy.y*dxy.y) - (dxy.x*dxy.y)*(dxy.x*dxy.y) - 0.05*(dxy.x*dxy.x + dxy.y*dxy.y);
            if(corner > maxCorner){
                maxCorner = corner;
            }*/
            vec2 dref = texelFetch(InputBufferdxy,mirrorCoords2(xy+ivec2(i,j),ivec2(INSIZE)),0).rg
            *unscaledGaussian(float(i)/float(SIZE),0.8)
            *unscaledGaussian(float(j)/float(SIZE),0.8);
            M.r += dref.r*dref.r;
            M.g += dref.r*dref.g;
            M.b += dref.r*dref.g;
            M.a += dref.g*dref.g;
        }
    }
    float det = M.r*M.a - M.g*M.b;
    //float left = (totaldxy.x*totaldxy.x + totaldxy.y*totaldxy.y) / 2.0;
    //float b = (totaldxy.x*totaldxy.x - totaldxy.y*totaldxy.y) / 2.0;
    //float right = (b*b + totaldxy.x*totaldxy.y*totaldxy.x*totaldxy.y);
    //Output.r = y1*y2 - 0.05*(y1+y2);
    Output.r = det/(M.r+M.a);
    Output.g = 4.0*det/((M.r+M.a)*(M.r+M.a));
    //if(y1 > y2) Output = texelFetch(InputBufferdxy,xy,0).rg;
    //if(Output.r < 0.08) Output.r = 0.0;
    //if(Output.r+Output.g < 0.03) Output = vec2(0.0);
}
