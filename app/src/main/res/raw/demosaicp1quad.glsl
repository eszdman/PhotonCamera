#version 300 es
uniform sampler2D RawBuffer;
uniform int yOffset;
out float Output;
#define getRaw(coords) (float(texelFetch(RawBuffer, (coords), 0).x))
float dxf(ivec2 coords){
    return (float(texelFetch(RawBuffer, (coords), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(1,0)), 0).x))/2.;
}
float dyf(ivec2 coords){
    return (float(texelFetch(RawBuffer, (coords), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(0,1)), 0).x))/2.;
}
float dxyf(ivec2 coords){
    return (float(texelFetch(RawBuffer, (coords), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(1,1)), 0).x))/2.;
}
float dyxf(ivec2 coords){
    return (float(texelFetch(RawBuffer, (coords+ivec2(1,0)), 0).x)-float(texelFetch(RawBuffer, (coords+ivec2(0,1)), 0).x))/2.;
}
    #define demosw (1.0/10000.0)
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    ivec2 fact = (xy/2)%2;
    xy+=ivec2(0,yOffset);
    float outp = 0.0;

    if(fact.x+fact.y != 1){
        ivec2 shift = xy%2;
        /*if(shift.x != 0 || shift.y != 0) {
        Output = float(texelFetch(RawBuffer, (xy), 0).x)*0.0;
        return;
        }*/
        //Green channel interpolation
        float P[9];
        P[1] = getRaw(xy + ivec2(0,-shift.y-1));
        P[3] = getRaw(xy + ivec2(-shift.x-1,0));
        P[5] = getRaw(xy + ivec2(-shift.x+2,0));
        P[7] = getRaw(xy + ivec2(0,-shift.y+2));

        /*
        //Bilinear
        float old = P[1];
        P[1] = mix(P[1],P[7],float(shift.y));
        P[7] = mix(P[7],old,float(shift.y));
        old = P[3];
        P[3] = mix(P[3],P[5],float(shift.x));
        P[5] = mix(P[5],old,float(shift.x));*/

        P[0] = (getRaw(xy + ivec2(-shift.x-1,-shift.y))+getRaw(xy + ivec2(-shift.x,-shift.y-1)))/2.0;
        P[2] = (getRaw(xy + ivec2(-shift.x+1,-shift.y-1))+getRaw(xy + ivec2(-shift.x+2,-shift.y)))/2.0;
        //P[4] = getRaw(xy + ivec2(-shift.x-1,-shift.y))+getRaw(xy + ivec2(-shift.x,-shift.y-1));
        P[6] = (getRaw(xy + ivec2(-shift.x,-shift.y+2))+getRaw(xy + ivec2(-shift.x-1,-shift.y+1)))/2.0;
        P[8] = (getRaw(xy + ivec2(-shift.x+2,-shift.y+1))+getRaw(xy + ivec2(-shift.x+1,-shift.y+2)))/2.0;


        float dx = (P[3]-P[5])/2.;
        float dy = (P[1]-P[7])/2.;
        float dxy = (P[0]-P[8])/2.0;
        float dyx = (P[2]-P[6])/2.0;
        P[0]=(P[0]+P[8])/2.0;
        P[2]=(P[2]+P[6])/2.0;
        float t;
        float E[8];

        /*t = dyf(xy+ivec2(0,-1));
        E[1] = (demosw + dy*dy + t*t);
        t = dxf(xy+ivec2(-1,0));
        E[3] = (demosw + dx*dx + t*t);
        t = dxf(xy+ivec2(1,0));
        E[5] = (demosw + dx*dx + t*t);
        t = dyf(xy+ivec2(0,1));
        E[7] = (demosw + dy*dy + t*t);*/

        t = dyf(xy+ivec2(0,-shift.y));
        E[1]+=dy*dy+t*t;
        E[7]+=dy*dy+t*t;

        t = dxf(xy+ivec2(-shift.x,0));
        E[3]+=dx*dx+t*t;
        E[5]+=dx*dx+t*t;

        if(shift.x+shift.y != 1){
            t = dxyf(xy+ivec2(-shift.x,-shift.y))*1.0;
            //E[0]+=dxy*dxy;
            //E[0]+=t*t;
        }

        if(shift.x+shift.y == 1){
            t = dyxf(xy+ivec2(-shift.x,-shift.y))*1.0;
            //E[2]+=dyx*dyx;
            //E[2]+=+t*t;
        }

        E[1]+=demosw;
        E[7]+=demosw;
        E[3]+=demosw;
        E[5]+=demosw;
        E[0]+=demosw;
        E[2]+=demosw;

        E[1] = 1.0/sqrt(E[1]);
        E[3] = 1.0/sqrt(E[3]);
        E[5] = 1.0/sqrt(E[5]);
        E[7] = 1.0/sqrt(E[7]);
        E[0] = 1.0/sqrt(E[0]);
        E[2] = 1.0/sqrt(E[2]);
        float all = (E[1]+E[3]+E[5]+E[7]
        //+E[0]+E[2]
        );
        outp = (E[1]*P[1] + E[3]*P[3] + E[5]*P[5] + E[7]*P[7]
        //+ E[0]*P[0] + E[2]*P[2]
        )/all;



        E[1] = getRaw(xy + ivec2(-shift.x,-shift.y));
        E[3] = getRaw(xy + ivec2(-shift.x+1,-shift.y));
        E[5] = getRaw(xy + ivec2(-shift.x,-shift.y+1));
        E[7] = getRaw(xy + ivec2(-shift.x+1,-shift.y+1));

        all = (E[1]+E[3]+E[5]+E[7]
        +E[0]+E[2]
        );
        float all2 = (P[1]+P[3]+P[5]+P[7]
        +E[0]+E[2]
        );
        float cur = getRaw(xy);
        float sat = abs(cur-getRaw(xy+shift*3));
        sat = 1.0-clamp(sat*3. - 0.3,0.0,1.0);
        //outp = mix(((getRaw(xy)*all/all2)),outp,(all)/(E[2]+E[0]));
        //outp = ((getRaw(xy)*all/all2));
        //outp = mix(((getRaw(xy)*all/all2)),outp,sat);


        Output = outp;
        //Output = (getRaw(xy + ivec2(0,-shift.y-1))+getRaw(xy + ivec2(0,-shift.y+2)))/2.0;
        //Output = 0.0;
        }
        else {
        Output = float(texelFetch(RawBuffer, (xy), 0).x);
        }
}
