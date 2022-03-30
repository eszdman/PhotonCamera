precision highp float;
precision highp int;
layout(rgba16f, binding = 0) uniform highp readonly image2D inTexture;
layout(rgba16f, binding = 1) uniform highp readonly image2D gradTexture;
layout(rgba16f, binding = 2) uniform highp writeonly image2D outTexture;
#define QUAD 0
#define demosw (1.0/10000.0)
#define EPS (0.01)
#define size1 (1.2)
#define MSIZE1 3
#define KSIZE ((MSIZE1-1)/2)
#define GRADSIZE 1.5
#define FUSEMIN 0.0
#define FUSEMAX 1.0
#define FUSESHIFT -0.1
#define FUSEMPY 1.4
#define NOISEO 0.0
#define NOISES 0.0
#define SCANGRAD 15
#define PI 3.1415926535897932384626433832795
#define LAYOUT //
#define OUTSET 0,0
float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}
LAYOUT
void main() {
    ivec2 xy = ivec2(gl_GlobalInvocationID.xy);
    if(xy.x >= ivec2(OUTSET).x) return;
    if(xy.y >= ivec2(OUTSET).y) return;
    int fact1 = xy.x%2;
    int fact2 = xy.y%2;
    vec4 Output;
    if(fact1+fact2 != 1){
        float outp = 0.0;
        float weight = 0.0;
        float green[4];
        green[0] = imageLoad(inTexture, ivec2(xy+ivec2(0, -1))).r;
        green[1] = imageLoad(inTexture, ivec2(xy+ivec2(-1, 0))).r;
        green[2] = imageLoad(inTexture, ivec2(xy+ivec2(1, 0))).r;
        green[3] = imageLoad(inTexture, ivec2(xy+ivec2(0, 1))).r;
        float grad[4];
        vec2 initialGrad = imageLoad(gradTexture, ivec2(xy)).rg;
        grad[0] = initialGrad.g*initialGrad.g;
        grad[1] = initialGrad.r*initialGrad.r;
        grad[2] = initialGrad.r*initialGrad.r;
        grad[3] = initialGrad.g*initialGrad.g;
        for (int i =1;i<=2;i++){
            if (i == 0) continue;
            float t = imageLoad(gradTexture, ivec2(xy+ivec2(0, -i))).g;
            grad[0] += t*t;
            t = imageLoad(gradTexture, ivec2(xy+ivec2(-i, 0))).r;
            grad[1] += t*t;
            t = imageLoad(gradTexture, ivec2(xy+ivec2(i, 0))).r;
            grad[2] += t*t;
            t = imageLoad(gradTexture, ivec2(xy+ivec2(0, i))).g;
            grad[3] += t*t;
        }
        grad[0] = 1.0/sqrt(grad[0]+demosw);
        grad[1] = 1.0/sqrt(grad[1]+demosw);
        grad[2] = 1.0/sqrt(grad[2]+demosw);
        grad[3] = 1.0/sqrt(grad[3]+demosw);
        vec2 HV = vec2(demosw);
        float weights = 0.00001;
        for (int j =0;j<=SCANGRAD;j++)
        {
            float k0 = normpdf(float(j), GRADSIZE);
            if((j%2) != 1) continue;
            float k = k0;
            vec2 div;
            div += abs(imageLoad(gradTexture, ivec2(xy+ivec2(0, j))).rg);
            div += abs(imageLoad(gradTexture, ivec2(xy+ivec2(0, -j))).rg);
            div += abs(imageLoad(gradTexture, ivec2(xy+ivec2(j, 0))).rg);
            div += abs(imageLoad(gradTexture, ivec2(xy+ivec2(-j, 0))).rg);

            HV += div*k/4.0;
            if(abs(HV.r-HV.g) > weights*50.8) break;
            weights+=k;
        }

        float MIN = min(min(green[0],green[1]),min(green[2],green[3]));
        float MAX = max(max(green[0],green[1]),max(green[2],green[3]));
        float dmax = 1.0 - MAX;
        float W;
        if(dmax < MIN){
            W = dmax/MAX;
        } else {
            W = MIN/MAX;
        }
        float avr = (green[0]+green[1]+green[2]+green[3])/4.0;
        HV/=weights;
        vec2 dxy2 = vec2((HV.x+HV.y)/2.0,(HV.y-HV.x)/2.0);
        float avrg = (green[0]*grad[0]+green[1]*grad[1]+green[2]*grad[2]+green[3]*grad[3])/(grad[0]+grad[1]+grad[2]+grad[3]);
        float outp2 = 0.0;
        if (HV.g > HV.r){
            Output.r = (green[1] + green[2])/(2.0);
        } else {
            Output.r = (green[0] + green[3])/(2.0);
        }
        Output.a = 1.0;
        imageStore(outTexture, xy, Output);
    } else {
        Output = imageLoad(inTexture,xy);
        Output.a = 1.0;
        imageStore(outTexture, xy, Output);
    }
}