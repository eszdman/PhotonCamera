#version 300 es
precision highp float;
precision mediump sampler2D;
uniform sampler2D RawBuffer;
uniform int yOffset;
uniform int WhiteLevel;
out float Output;
float dxf(ivec2 coords){
    return (float(texelFetch(RawBuffer, (coords+ivec2(-1,0)), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(1,0)), 0).x))/2.;
}
float dyf(ivec2 coords){
    return (float(texelFetch(RawBuffer, (coords+ivec2(0,-1)), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(0,1)), 0).x))/2.;
}
float dxdf(ivec2 coords){
    return max(abs(float(texelFetch(RawBuffer, (coords+ivec2(1,1)), 0).x)-float(texelFetch(RawBuffer, (coords), 0).x)),abs(float(texelFetch(RawBuffer, (coords), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(-1,-1)), 0).x)))/1.41421;
    //return (float(texelFetch(RawBuffer, (coords+ivec2(1,1)), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(-1,-1)),0).x))/2.82842;
}
float dydf(ivec2 coords){
    return max(abs(float(texelFetch(RawBuffer, (coords+ivec2(1,-1)), 0).x)-float(texelFetch(RawBuffer, (coords), 0).x)),abs(float(texelFetch(RawBuffer, (coords), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(-1,1)), 0).x)))/1.41421;
    //return (float(texelFetch(RawBuffer, (coords+ivec2(1,-1)), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(-1,1)),0).x))/2.82842;
}
    #define demosw (1.0)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    int fact1 = xy.x%2;
    int fact2 = xy.y%2;
    xy+=ivec2(0,yOffset);
    float outp = 0.0;
    if(fact1+fact2 != 1){
        //Alexey Lukin, Denis Kubasov demosaicing with an eszdman's upgrade
        float P[9];
        for(int i =0; i<9;i++){
            P[i] = float(texelFetch(RawBuffer, (xy+ivec2(i%3 - 1,i/3 - 1)), 0).x);
        }
        float dx = (P[3]-P[5])/2.;
        float dy = (P[1]-P[7])/2.;
        float dxd = dxdf(xy);//2V2
        float dyd = dydf(xy);
        float t;
        float E[8];
        t = dyf(xy+ivec2(0,-1));
        E[1] = 1.0/sqrt(demosw + dy*dy + t*t);
        t = dxf(xy+ivec2(-1,0));
        E[3] = 1.0/sqrt(demosw + dx*dx + t*t);
        t = dxf(xy+ivec2(1,0));
        E[5] = 1.0/sqrt(demosw + dx*dx + t*t);
        t = dyf(xy+ivec2(0,1));
        E[7] = 1.0/sqrt(demosw + dy*dy + t*t);

        //t = dxdf(xy+ivec2(-1,-1));
        //E[0] = 1.0/sqrt(demosw + dxd*dxd + t*t);
        //t = dydf(xy+ivec2(1,-1));
        //E[2] = 1.0/sqrt(demosw + dyd*dyd + t*t);
        //t = dxdf(xy+ivec2(1,1));
        //E[4] = 1.0/sqrt(demosw + dxd*dxd + t*t);
        //t = dydf(xy+ivec2(-1,1));
        //E[6] = 1.0/sqrt(demosw + dyd*dyd + t*t);
        /*float H = 0.0;
        float V = 0.0;
        for(int h =-12;h<12;h++){
            for(int w =-12;w<12;w++){
                if((h+w)%2 != 1) continue;
                H+=dyf(xy+ivec2(w, h));
                V+=dxf(xy+ivec2(w, h));

                //if (abs(H-V)>=2.5*(H+V)) break;
            }
        }*/
        for(int h =0;h<12;h++){
            for (int w =0;w<12;w++){
                if ((h+w)%2 != 1) continue;
                t = dyf(xy+ivec2(-w, -h));
                E[1]+=1.0/sqrt(demosw + dy*dy + t*t);


                t = dxf(xy+ivec2(-w, -h));
                E[3]+=1.0/sqrt(demosw + dx*dx + t*t);

                t = dxf(xy+ivec2(w, h));
                E[5]+=1.0/sqrt(demosw + dx*dx + t*t);

                t = dyf(xy+ivec2(w, h));
                E[7]+=1.0/sqrt(demosw + dy*dy + t*t);
            }
        }

        float all = (E[1]+E[3]+E[5]+E[7]);
        outp = (E[1]*P[1] + E[3]*P[3] + E[5]*P[5] + E[7]*P[7])/all;
        //Output = clamp(outp/float(WhiteLevel),0.,1.);
        Output = outp;
        /*if(H>=V){
            Output = clamp(((P[1]+P[7])/2.0),0.,1.);
        } else {
            Output = clamp(((P[3]+P[5])/2.0),0.,1.);
        }*/
    }
    else {
        //Output = clamp(float(texelFetch(RawBuffer, (xy), 0).x)/float(WhiteLevel),0.,1.);
        Output = float(texelFetch(RawBuffer, (xy), 0).x);
    }
}
