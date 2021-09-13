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
        /*
        Output = green[0]*grad[0] + green[1]*grad[1]+ green[2]*grad[2] + green[3]*grad[3];
        Output/=grad[0]+grad[1]+grad[2]+grad[3];*/
        vec2 HV = initialGrad*1.15+demosw;
        float weights = 0.00001;
        for (int j =-2;j<=2;j++)
        {
            float k0 = normpdf(float(j), GRADSIZE);
            for (int i =-2;i<=2;i++){
                if (i == 0 && j == 0) continue;
                float k = normpdf(float(i), GRADSIZE)*k0;
                //float cw = 1.0/((float(abs(i)+abs(j)))/(1.15));
                vec2 div = texelFetch(GradBuffer, ivec2(xy+ivec2(i, j)), 0).rg;
                //if((div.r+div.g) < (HV.r+HV.g)*0.2) break;
                HV += div*k;
                weights+=k;
                //weights += cw;
                //HV += texelFetch(GradBuffer,ivec2(xy+ivec2(i,j)),0).rg/((float(abs(i)+abs(j)))/(1.0));
                //H += ingrad.r;//*(1.0 - float(abs(i)+abs(j))/5.0);
                //V += ingrad.g;//*(1.0 - float(abs(i)+abs(j))/5.0);
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
        float N = sqrt(avr*NOISES + NOISEO);
        //W=W*W;
        //W=sqrt(W);
        //float badGR = (abs(HV.r-HV.g)*5.0)/((HV.r+HV.g));
        HV/=weights;
        if(abs(HV.r) > N || abs(HV.g) > N){
            if (HV.g > HV.r){
                Output = (green[1]*grad[1] + green[2]*grad[2])/(grad[1]+grad[2]);
                //Output = (green[1] + green[2])/(2.0);
            } else {
                Output = (green[0]*grad[0] + green[3]*grad[3])/(grad[0]+grad[3]);
                //Output = (green[0] + green[3])/(2.0);
            }
        } else {
            Output = avr;
        }

        W = mix(FUSEMIN,FUSEMAX,W*FUSEMPY+FUSESHIFT);
        Output = mix(Output,(green[0]*grad[0] + green[1]*grad[1]+ green[2]*grad[2] + green[3]*grad[3])/(grad[0]+grad[1]+grad[2]+grad[3]),W);


        /*} else {
            Output = green[0]*grad[0] + green[1]*grad[1]+ green[2]*grad[2] + green[3]*grad[3];
            Output/=grad[0]+grad[1]+grad[2]+grad[3];
        }*/
    }
    else {
        Output = float(texelFetch(RawBuffer, (xy), 0).x);
    }
}
