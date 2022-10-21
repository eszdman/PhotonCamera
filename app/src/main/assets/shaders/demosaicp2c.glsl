precision highp float;
precision highp int;
layout(rgba16f, binding = 0) uniform highp readonly image2D inTexture;
layout(rgba16f, binding = 1) uniform highp readonly image2D greenTexture;
layout(rgba16f, binding = 2) uniform highp writeonly image2D outTexture;

uniform int yOffset;


//#define greenmin (0.04)
#define greenmin (0.01)
//#define greenmax (0.9)
#define greenmax (0.99)
#define OUTSET 0,0
#define LAYOUT //
float interpolateColor(in ivec2 coords){
    bool usegreen = true;
    float green[5];
    green[0] = float(imageLoad(greenTexture, (coords+ivec2(-1,-1))).x);
    green[1] = float(imageLoad(greenTexture, (coords+ivec2(1,-1))).x);
    green[2] = float(imageLoad(greenTexture, (coords+ivec2(-1,1))).x);
    green[3] = float(imageLoad(greenTexture, (coords+ivec2(1,1))).x);
    green[4] = float(imageLoad(greenTexture, (coords)).x);
    for(int i = 0; i<5; i++)if(green[i] < greenmin || green[i] > greenmax) usegreen = false;
    if(usegreen){
        float coeff[4];
        coeff[0] = float(imageLoad(inTexture, (coords+ivec2(-1,-1))).x)/(green[0]);
        coeff[1] = float(imageLoad(inTexture, (coords+ivec2(1,-1))).x)/(green[1]);
        coeff[2] = float(imageLoad(inTexture, (coords+ivec2(-1,1))).x)/(green[2]);
        coeff[3] = float(imageLoad(inTexture, (coords+ivec2(1,1))).x)/(green[3]);
        return (green[4]*(coeff[0]+coeff[1]+coeff[2]+coeff[3])/4.);
    } else {
        return ((float(imageLoad(inTexture, (coords+ivec2(-1,-1))).x)+float(imageLoad(inTexture, (coords+ivec2(1,-1))).x)
        +float(imageLoad(inTexture, (coords+ivec2(-1,1))).x)+float(imageLoad(inTexture, (coords+ivec2(1,1))).x))/(4.));
        }
}
float interpolateColorx(in ivec2 coords){
    bool usegreen = true;
    float green[3];
    green[0] = float(imageLoad(greenTexture, (coords+ivec2(-1,0))).x);
    green[1] = float(imageLoad(greenTexture, (coords+ivec2(0,0))).x);
    green[2] = float(imageLoad(greenTexture, (coords+ivec2(1,0))).x);
    for(int i = 0; i<3; i++)if(green[i] < greenmin || green[i] > greenmax) usegreen = false;
    if(usegreen){
        float coeff[2];
        coeff[0] = float(imageLoad(inTexture, (coords+ivec2(-1,0))).x)/(green[0]);
        coeff[1] = float(imageLoad(inTexture, (coords+ivec2(1,0))).x)/(green[2]);
        return (green[1]*(coeff[0]+coeff[1])/2.);
    } else {
        return ((float(imageLoad(inTexture, (coords+ivec2(-1,0))).x)+float(imageLoad(inTexture, (coords+ivec2(1,0))).x))/(2.));
    }
}
float interpolateColory(in ivec2 coords){
    bool usegreen = true;
    float green[3];
    green[0] = float(imageLoad(greenTexture, (coords+ivec2(0,-1))).x);
    green[1] = float(imageLoad(greenTexture, (coords+ivec2(0,0))).x);
    green[2] = float(imageLoad(greenTexture, (coords+ivec2(0,1))).x);
    for(int i = 0; i<3; i++)if(green[i] < greenmin || green[i] > greenmax) usegreen = false;
    if(usegreen){
        float coeff[2];
        coeff[0] = float(imageLoad(inTexture, (coords+ivec2(0,-1))).x)/(green[0]);
        coeff[1] = float(imageLoad(inTexture, (coords+ivec2(0,1))).x)/(green[2]);
        return (green[1]*(coeff[0]+coeff[1])/2.);
    } else {
        return ((float(imageLoad(inTexture, (coords+ivec2(0,-1))).x)+float(imageLoad(inTexture, (coords+ivec2(0,1))).x))/(2.));
    }
}
LAYOUT
void main() {
    ivec2 xy = ivec2(gl_GlobalInvocationID.xy);
    vec4 Output;
    int fact1 = xy.x%2;
    int fact2 = xy.y%2;
    if(xy.x >= ivec2(OUTSET).x) return;
    if(xy.y >= ivec2(OUTSET).y) return;
    xy+=ivec2(0,yOffset);
    if(fact1 ==0 && fact2 == 0) {//rggb
        Output.g = imageLoad(greenTexture, xy).x;
        Output.r = float(imageLoad(inTexture, (xy)).x);
        Output.b = interpolateColor(xy);
    } else
    if(fact1 ==1 && fact2 == 0) {//grbg
        Output.g = imageLoad(greenTexture, (xy)).x;
        Output.r = interpolateColorx(xy);
        Output.b = interpolateColory(xy);

    } else
    if(fact1 ==0 && fact2 == 1) {//gbrg
        Output.g = imageLoad(greenTexture, (xy)).x;
        Output.b = interpolateColorx(xy);
        Output.r = interpolateColory(xy);

    } else  {//bggr
        Output.g = imageLoad(greenTexture, (xy)).x;
        Output.b = float(imageLoad(inTexture, (xy)).x);
        Output.r = interpolateColor(xy);
    }
    Output = clamp(Output,0.0,1.0);
    //Output.rb*=0.0;
    imageStore(outTexture,xy,Output);
}