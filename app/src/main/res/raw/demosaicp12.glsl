#version 300 es
precision highp float;
precision mediump sampler2D;
uniform sampler2D RawBuffer;
uniform sampler2D GradBuffer;
#define QUAD 0
#define demosw (1.0/10000.0)

#define size1 (1.2)
#define MSIZE1 3
#define KSIZE ((MSIZE1-1)/2)
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
        green[0] = texelFetch(RawBuffer,ivec2(xy+ivec2(0,-1)),0).r;
        green[1] = texelFetch(RawBuffer,ivec2(xy+ivec2(-1,0)),0).r;
        green[2] = texelFetch(RawBuffer,ivec2(xy+ivec2(1,0)),0).r;
        green[3] = texelFetch(RawBuffer,ivec2(xy+ivec2(0,1)),0).r;
        float grad[4];
        vec2 initialGrad = texelFetch(GradBuffer,ivec2(xy),0).rg;
        grad[0] = initialGrad.g*initialGrad.g;
        grad[1] = initialGrad.r*initialGrad.r;
        grad[2] = initialGrad.r*initialGrad.r;
        grad[3] = initialGrad.g*initialGrad.g;
        for(int i =1;i<=2;i++){
            if(i == 0) continue;
            float t = texelFetch(GradBuffer,ivec2(xy+ivec2(0,-i)),0).g;
            grad[0] += t*t;
            t = texelFetch(GradBuffer,ivec2(xy+ivec2(-i,0)),0).r;
            grad[1] += t*t;
            t = texelFetch(GradBuffer,ivec2(xy+ivec2(i,0)),0).r;
            grad[2] += t*t;
            t = texelFetch(GradBuffer,ivec2(xy+ivec2(0,i)),0).g;
            grad[3] += t*t;
        }
        grad[0] = 1.0/sqrt(grad[0]+demosw);
        grad[1] = 1.0/sqrt(grad[1]+demosw);
        grad[2] = 1.0/sqrt(grad[2]+demosw);
        grad[3] = 1.0/sqrt(grad[3]+demosw);
        /*
        Output = green[0]*grad[0] + green[1]*grad[1]+ green[2]*grad[2] + green[3]*grad[3];
        Output/=grad[0]+grad[1]+grad[2]+grad[3];*/
        vec2 HV = initialGrad*1.15+demosw;
        float weights = 1.15;
        for(int j =-2;j<=2;j++)
        for(int i =-2;i<=2;i++){
            if(i == 0 && j == 0) continue;
            float cw = 1.0/((float(abs(i)+abs(j)))/(1.15));
            vec2 div = texelFetch(GradBuffer,ivec2(xy+ivec2(i,j)),0).rg*cw;
            //if((div.r+div.g) < (HV.r+HV.g)*0.2) break;
            HV += div;
            weights += cw;
            //HV += texelFetch(GradBuffer,ivec2(xy+ivec2(i,j)),0).rg/((float(abs(i)+abs(j)))/(1.0));


            //H += ingrad.r;//*(1.0 - float(abs(i)+abs(j))/5.0);
            //V += ingrad.g;//*(1.0 - float(abs(i)+abs(j))/5.0);
        }

        //float badGR = (abs(HV.r-HV.g)*5.0)/((HV.r+HV.g));
        HV/=weights;
        //if((initialGrad.r+initialGrad.g) > (HV.r+HV.g)*0.4) {
            if (HV.g > HV.r){
                Output = (green[1]*grad[1] + green[2]*grad[2])/(grad[1]+grad[2]);
                //Output = (green[1] + green[2])/(2.0);
            } else {
                Output = (green[0]*grad[0] + green[3]*grad[3])/(grad[0]+grad[3]);
                //Output = (green[0] + green[3])/(2.0);
            }
        /*} else {
            Output = green[0]*grad[0] + green[1]*grad[1]+ green[2]*grad[2] + green[3]*grad[3];
            Output/=grad[0]+grad[1]+grad[2]+grad[3];
        }*/
    }
    else {
        Output = float(texelFetch(RawBuffer, (xy), 0).x);
    }
}
