#version 300 es
precision highp float;
precision mediump sampler2D;
uniform sampler2D RawBuffer;
uniform sampler2D GradBuffer;
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
#define PI 3.1415926535897932384626433832795
out float Output;
float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    int fact1 = xy.x%2;
    int fact2 = xy.y%2;
    float outp = 0.0;
    if(fact1+fact2 != 1){
        float outp = 0.0;
        float weight = 0.0;
        float green[4];
        green[0] = texelFetch(RawBuffer, ivec2(xy+ivec2(0, -1)), 0).r;
        green[1] = texelFetch(RawBuffer, ivec2(xy+ivec2(-1, 0)), 0).r;
        green[2] = texelFetch(RawBuffer, ivec2(xy+ivec2(1, 0)), 0).r;
        green[3] = texelFetch(RawBuffer, ivec2(xy+ivec2(0, 1)), 0).r;
        float grad[4];
        vec2 initialGrad = texelFetch(GradBuffer, ivec2(xy), 0).rg;
        grad[0] = initialGrad.g*initialGrad.g;
        grad[1] = initialGrad.r*initialGrad.r;
        grad[2] = initialGrad.r*initialGrad.r;
        grad[3] = initialGrad.g*initialGrad.g;
        for (int i =1;i<=2;i++){
            if (i == 0) continue;
            float t = texelFetch(GradBuffer, ivec2(xy+ivec2(0, -i)), 0).g;
            grad[0] += t*t;
            t = texelFetch(GradBuffer, ivec2(xy+ivec2(-i, 0)), 0).r;
            grad[1] += t*t;
            t = texelFetch(GradBuffer, ivec2(xy+ivec2(i, 0)), 0).r;
            grad[2] += t*t;
            t = texelFetch(GradBuffer, ivec2(xy+ivec2(0, i)), 0).g;
            grad[3] += t*t;
        }
        grad[0] = 1.0/sqrt(grad[0]+demosw);
        grad[1] = 1.0/sqrt(grad[1]+demosw);
        grad[2] = 1.0/sqrt(grad[2]+demosw);
        grad[3] = 1.0/sqrt(grad[3]+demosw);

        vec2 HV = vec2(demosw);
        float weights = 0.00001;
        for (int j =-2;j<=2;j++)
        {
            float k0 = normpdf(float(j), GRADSIZE);
            for (int i =-2;i<=2;i++){
                if((i%2) + (j%2) != 1) continue;
                float k = normpdf(float(i), GRADSIZE)*k0;
                vec2 div = texelFetch(GradBuffer, ivec2(xy+ivec2(i, j)), 0).rg;
                HV += div*k;
                weights+=k;
            }
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
        //float N = sqrt(avr*NOISES + NOISEO)/5.0;
        //W=W*W;
        //W=sqrt(W);
        //float badGR = (abs(HV.r-HV.g)*5.0)/((HV.r+HV.g));
        HV/=weights;
        //if(abs(HV.r) > N || abs(HV.g) > N){
        //float angle = atan(dxy.y,dxy.x)+PI;
        vec2 dxy2 = vec2((HV.x+HV.y)/2.0,(HV.y-HV.x)/2.0);
        float avrg = (green[0]*grad[0]+green[1]*grad[1]+green[2]*grad[2]+green[3]*grad[3])/(grad[0]+grad[1]+grad[2]+grad[3]);
        grad[0] = 1.0/sqrt(HV.x*HV.x+abs(green[0]-avrg)+demosw);
        grad[1] = 1.0/sqrt(HV.y*HV.y+abs(green[1]-avrg)+demosw);
        grad[2] = 1.0/sqrt(HV.y*HV.y+abs(green[2]-avrg)+demosw);
        grad[3] = 1.0/sqrt(HV.x*HV.x+abs(green[3]-avrg)+demosw);

        //Output = (green[0]*grad[0] +green[1]*grad[1] + green[2]*grad[2]+ green[3]*grad[3])/(grad[0]+grad[1]+grad[2]+grad[3]);
        float outp2 = 0.0;

        if (HV.y > HV.x){
            Output = (green[1]*grad[1] + green[2]*grad[2])/(grad[1]+grad[2]);
            //Output = (green[1] + green[2])/(2.0);
        } else {
            Output = (green[0]*grad[0] + green[3]*grad[3])/(grad[0]+grad[3]);
            //Output = (green[0] + green[3])/(2.0);
        }
        //Output = mix(outp2,Output,0.5 - 0.5*abs(HV.y-HV.x));
        /*} else {
            Output = avr;
        }*/


        W = mix(FUSEMIN,FUSEMAX,clamp((W+FUSESHIFT)*FUSEMPY,0.0,1.0));
        //Output = mix(Output,(green[0]*grad[0] + green[1]*grad[1]+ green[2]*grad[2] + green[3]*grad[3])/(grad[0]+grad[1]+grad[2]+grad[3]),W);


        /*} else {
            Output = green[0]*grad[0] + green[1]*grad[1]+ green[2]*grad[2] + green[3]*grad[3];
            Output/=grad[0]+grad[1]+grad[2]+grad[3];
        }*/
    }
    else {
        Output = float(texelFetch(RawBuffer, (xy), 0).x);
    }
}
