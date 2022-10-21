precision highp float;
precision mediump sampler2D;
uniform sampler2D RawBuffer;
uniform int yOffset;
#define QUAD 0
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
#define demosw (1.0/10000.0)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    int fact1 = xy.x%2;
    int fact2 = xy.y%2;
    float outp = 0.0;
    if(fact1+fact2 != 1){
        //Gradient green channel interpolation
        float P[9];
        for(int i =0; i<9;i++){
            P[i] = float(texelFetch(RawBuffer, (xy+ivec2(i%3 - 1,i/3 - 1)), 0).x);
        }
        float dx = (P[3]-P[5])/2.;
        float dy = (P[1]-P[7])/2.;
        float dxd = dxdf(xy);//2V2
        float dyd = dydf(xy);
        #if QUAD == 1
        dx*=0.4;
        dy*=0.4;
        #endif
        float t;
        float E[8];
        #if QUAD == 1
        E[1] = 0.0;
        E[3] = 0.0;
        E[5] = 0.0;
        E[7] = 0.0;
        #else
        t = dyf(xy+ivec2(0,-1));
        E[1] = (demosw + dy*dy + t*t);
        t = dxf(xy+ivec2(-1,0));
        E[3] = (demosw + dx*dx + t*t);
        t = dxf(xy+ivec2(1,0));
        E[5] = (demosw + dx*dx + t*t);
        t = dyf(xy+ivec2(0,1));
        E[7] = (demosw + dy*dy + t*t);
        #endif

        t = dyf(xy+ivec2(-1,-1));
        E[1]+=(t*t);
        t = dyf(xy+ivec2(1,-1));
        E[1]+=(t*t);

        t = dxf(xy+ivec2(-1,-1));
        E[3]+=t*t;
        t = dxf(xy+ivec2(-1,1));
        E[3]+=t*t;

        t = dxf(xy+ivec2(1,-1));
        E[5]+=t*t;
        t = dxf(xy+ivec2(1,1));
        E[5]+=t*t;

        t = dyf(xy+ivec2(1,1));
        E[7]+=t*t;
        t = dyf(xy+ivec2(-1,1));
        E[7]+=t*t;

        E[1] = 1.0/sqrt(E[1]);
        E[3] = 1.0/sqrt(E[3]);
        E[5] = 1.0/sqrt(E[5]);
        E[7] = 1.0/sqrt(E[7]);
        float all = (E[1]+E[3]+E[5]+E[7]);
        Output = (E[1]*P[1] + E[3]*P[3] + E[5]*P[5] + E[7]*P[7])/all;
    }
    else {
        Output = float(texelFetch(RawBuffer, (xy), 0).x);
    }
}
