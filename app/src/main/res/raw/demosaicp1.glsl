#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform usampler2D RawBuffer;
uniform int yOffset;
uniform int CfaPattern;
uniform int WhiteLevel;
out float Output;
float dxf(ivec2 coords){
    return abs(float(texelFetch(RawBuffer, (coords+ivec2(-1,0)), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(1,0)), 0).x))/2.;
}
float dyf(ivec2 coords){
    return abs(float(texelFetch(RawBuffer, (coords+ivec2(0,-1)), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(0,1)), 0).x))/2.;
}
float dxdf(ivec2 coords){
    return abs(float(texelFetch(RawBuffer, (coords+ivec2(1,1)), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(-1,-1)), 0).x))/2.82842;
}
float dydf(ivec2 coords){
    return abs(float(texelFetch(RawBuffer, (coords+ivec2(1,-1)), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(-1,1)), 0).x))/2.82842;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    int fact1 = xy.x%2;
    int fact2 = xy.y%2;
    xy+=ivec2(CfaPattern%2,yOffset+CfaPattern/2);
    float outp = 0.0;
    if(fact1+fact2 != 1){
        //Alexey Lukin, Denis Kubasov demosaicing
        float P[9];
        for(int i =0; i<9;i++){
            P[i] = float(texelFetch(RawBuffer, (xy+ivec2(i%3 - 1,i/3 - 1)), 0).x);
        }
        float dx = abs(P[3]-P[5])/2.;
        float dy = abs(P[1]-P[7])/2.;
        float dxd = abs(P[2]-P[6])/2.82842;//2V2
        float dyd = abs(P[0]-P[8])/2.82842;
        float t;
        float E[8];
        //t = dydf(xy+ivec2(-1,-1));
        //E[0] = 1.0/sqrt(1 + dyd*dyd + t*t);
        t = dydf(xy+ivec2(0,-1));
        E[1] = 1.0/sqrt(1.0 + dy*dy + t*t);
        //t = dydf(xy+ivec2(1,-1));
        //E[2] = 1.0/sqrt(1 + dxd*dxd + t*t);
        t = dydf(xy+ivec2(-1,0));
        E[3] = 1.0/sqrt(1.0 + dx*dx + t*t);

        //t = dydf(xy+ivec2(1,0));
        //E[4] = 1.0/sqrt(1 + dx*dx + t*t);
        t = dydf(xy+ivec2(-1,1));
        E[5] = 1.0/sqrt(1.0 + dxd*dxd + t*t);
        //t = dydf(xy+ivec2(0,1));
        //E[6] = 1.0/sqrt(1 + dy*dy + t*t);
        t = dydf(xy+ivec2(1,1));
        E[7] = 1.0/sqrt(1.0 + dyd*dyd + t*t);
        outp = (E[1]*P[1] + E[3]*P[3] + E[5]*P[5] + E[7]*P[7])/(E[1]+E[3]+E[5]+E[7]);
        Output = (outp/float(WhiteLevel));
    }
    else {
    Output = (float(texelFetch(RawBuffer, (xy), 0).x)/float(WhiteLevel));
    }

}
