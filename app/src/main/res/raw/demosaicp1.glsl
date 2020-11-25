#version 300 es
precision highp float;
precision mediump usampler2D;
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
    //return max(abs(float(texelFetch(RawBuffer, (coords+ivec2(1,1)), 0).x)-float(texelFetch(RawBuffer, (coords), 0).x)),abs(float(texelFetch(RawBuffer, (coords), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(-1,-1)), 0).x)))/1.41421;
    return (float(texelFetch(RawBuffer, (coords+ivec2(1,1)), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(-1,-1)),0).x))/2.82842;
}
float dydf(ivec2 coords){
    //return max(abs(float(texelFetch(RawBuffer, (coords+ivec2(1,-1)), 0).x)-float(texelFetch(RawBuffer, (coords), 0).x)),abs(float(texelFetch(RawBuffer, (coords), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(-1,1)), 0).x)))/1.41421;
    return (float(texelFetch(RawBuffer, (coords+ivec2(1,-1)), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(-1,1)),0).x))/2.82842;
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

        t = dyf(xy+ivec2(-1,-1));
        E[1]+=1.0/sqrt(demosw + dy*dy + t*t);
        t = dyf(xy+ivec2(1,-1));
        E[1]+=1.0/sqrt(demosw + dy*dy + t*t);

        t = dxf(xy+ivec2(-1,-1));
        E[3]+=1.0/sqrt(demosw + dx*dx + t*t);
        t = dxf(xy+ivec2(-1,1));
        E[3]+=1.0/sqrt(demosw + dx*dx + t*t);

        t = dxf(xy+ivec2(1,-1));
        E[5]+=1.0/sqrt(demosw + dx*dx + t*t);
        t = dxf(xy+ivec2(1,1));
        E[5]+=1.0/sqrt(demosw + dx*dx + t*t);

        t = dyf(xy+ivec2(1,1));
        E[7]+=1.0/sqrt(demosw + dy*dy + t*t);
        t = dyf(xy+ivec2(-1,1));
        E[7]+=1.0/sqrt(demosw + dy*dy + t*t);

        //E[1]+=E[0]+E[2];
        //E[3]+=E[0]+E[6];
        //E[5]+=E[2]+E[4];
        //E[7]+=E[4]+E[6];
        //float all = (E[1]+E[3]+E[5]+E[7]+(E[0]+E[2]+E[4]+E[6])/4.);
        float all = (E[1]+E[3]+E[5]+E[7]);
        if(all > 0.0001)
        //outp = (E[1]*P[1] + E[3]*P[3] + E[5]*P[5] + E[7]*P[7] + ((P[1]+P[3]+P[5]+P[7])/4.)*(E[0]+E[2]+E[4]+E[6])/4.)/all;
        outp = (E[1]*P[1] + E[3]*P[3] + E[5]*P[5] + E[7]*P[7])/all;
        else outp = (P[1] + P[3] + P[5] + P[7])/4.;
        //Output = clamp(outp/float(WhiteLevel),0.,1.);
        Output = outp;
    }
    else {
    //Output = clamp(float(texelFetch(RawBuffer, (xy), 0).x)/float(WhiteLevel),0.,1.);
        Output = float(texelFetch(RawBuffer, (xy), 0).x);
    }
}
