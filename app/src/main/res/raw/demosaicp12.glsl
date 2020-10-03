#version 300 es*/
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
    return max(abs(float(texelFetch(RawBuffer, (coords+ivec2(1,1)), 0).x)-float(texelFetch(RawBuffer, (coords), 0).x)),abs(float(texelFetch(RawBuffer, (coords), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(-1,-1)), 0).x)))/1.41421;
}
float dydf(ivec2 coords){
    return max(abs(float(texelFetch(RawBuffer, (coords+ivec2(1,-1)), 0).x)-float(texelFetch(RawBuffer, (coords), 0).x)),abs(float(texelFetch(RawBuffer, (coords), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(-1,1)), 0).x)))/1.41421;
}
#define demosw (1.5)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    int fact1 = xy.x%2;
    int fact2 = xy.y%2;
    xy+=ivec2(CfaPattern%2,yOffset+CfaPattern/2);
    float outp = 0.0;
    if(fact1+fact2 != 1){
        //Hybrid color filter array demosaicking for effective artifact suppression
        float C[5];
        float G[4];
        C[0] = float(texelFetch(RawBuffer, (xy+ivec2(-2,0)), 0).x);
        G[0] = float(texelFetch(RawBuffer, (xy+ivec2(-1,0)), 0).x);
        C[1] = float(texelFetch(RawBuffer, (xy+ivec2(0,0)), 0).x);
        G[1] = float(texelFetch(RawBuffer, (xy+ivec2(1,0)), 0).x);
        C[2] = float(texelFetch(RawBuffer, (xy+ivec2(2,0)), 0).x);
        G[2] = float(texelFetch(RawBuffer, (xy+ivec2(0,-1)), 0).x);
        C[3] = float(texelFetch(RawBuffer, (xy+ivec2(0,-2)), 0).x);
        G[3] = float(texelFetch(RawBuffer, (xy+ivec2(0,1)), 0).x);
        C[4] = float(texelFetch(RawBuffer, (xy+ivec2(0,2)), 0).x);

    }
    else {
    Output = (float(texelFetch(RawBuffer, (xy), 0).x)/float(WhiteLevel));
    }

}
