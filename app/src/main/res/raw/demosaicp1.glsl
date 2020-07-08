#version 300 es
precision mediump float;
precision mediump usampler2D;
uniform usampler2D RawBuffer;
uniform int yOffset;
uniform int CfaPattern;
uniform int WhiteLevel;
out float Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(CfaPattern%2,yOffset+CfaPattern/2);
    int fact1 = xy.x%2;
    int fact2 = xy.y%2;
    float outp = 0.0;
    if(fact1+fact2 != 1){
        float grad[4];
        grad[0] = float(texelFetch(RawBuffer, (xy+ivec2(0,1)), 0).x);
        grad[1] = float(texelFetch(RawBuffer, (xy+ivec2(1,0)), 0).x);
        grad[2] = float(texelFetch(RawBuffer, (xy+ivec2(0,-1)), 0).x);
        grad[3] = float(texelFetch(RawBuffer, (xy+ivec2(-1,0)), 0).x);
        float dgrad = float(texelFetch(RawBuffer, (xy), 0).x);
        float dv = abs(grad[0]-grad[2]);
        float dh = abs(grad[1]-grad[3]);
        float avr = (grad[0]+grad[1]+grad[2]+grad[3])/4.;
        if(dv>dh){
            outp = (grad[0]+grad[2])/2.;
            if((grad[1]+grad[3]) > grad[0]+grad[2] && dgrad<avr){
                outp = (grad[0]+grad[2])/2.;
            }
        } else
        if(dh>dv){
            outp = (grad[1]+grad[3])/2.;
            if((grad[1]+grad[3]) < (grad[0]+grad[2]) && dgrad<avr){
                outp = (grad[1]+grad[3])/2.;
            }
        } else {
            outp = avr;
        }
        //Zipper effect removal
        for(int i =0; i<4;i++){
            if(grad[(i%4)] > avr && grad[((i+1)%4)]< avr &&
            grad[((i+2)%4)] < avr && grad[((i+3)%4)] < avr){
                outp = (grad[((i+1)%4)]+grad[((i+3)%4)])/2.;
            }
            if(grad[(i%4)] < avr && grad[((i+1)%4)] > avr &&
            grad[((i+2)%4)] > avr && grad[((i+3)%4)] > avr){
                outp = (grad[((i+3)%4)]+grad[((i+2)%4)])/2.;
            }
            if(grad[(i%4)] > avr && grad[((i+1)%4)] > avr &&
            grad[(i+2%4)] < avr && grad[((i+3)%4)] < avr){
                outp = (grad[(i%4)]+grad[((i+2)%4)])/2.;
            }
        }
        for(int i =0; i<2;i++){
            if(grad[(i%4)]*1. > avr && grad[((i+2)%4)]*1.  > avr &&
            grad[((i+1)%4)] < avr && grad[((i+3)%4)] < avr){
                if(i == 0 && dgrad*1.5>avr)outp = (grad[(i%4)]+grad[((i+2)%4)])/2.;
                if(i == 1 && dgrad*1.5>avr)outp = (grad[(i%4)]+grad[((i+2)%4)])/2.;
            }
        }
        Output = (outp/float(WhiteLevel));
    }
    else {
    Output = (float(texelFetch(RawBuffer, (xy), 0).x)/float(WhiteLevel));
    }

}
