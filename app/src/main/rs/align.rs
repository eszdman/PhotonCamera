#pragma version(1)
#pragma rs java_package_name(com.particlesdevs.photoncamera)
#pragma rs_fp_relaxed

rs_allocation referenceBuffer;
rs_allocation inputBuffer;
rs_allocation alignVectors;
ushort prevScale;
ushort TILESIZE;
ushort2 inputSize;
rs_allocation alignOutput;

#define RS_KERNEL __attribute__((kernel))
#define gets3(x,y, alloc)(rsGetElementAt_ushort3(alloc,x,y))
#define sets3(x,y, alloc,in)(rsSetElementAt_ushort3(alloc,in,x,y))


#define getc(x,y, alloc)(rsGetElementAt_uchar(alloc,x,y))
#define getc4(x,y, alloc)(rsGetElementAt_uchar4(alloc,x,y))
#define getc3(x,y, alloc)(rsGetElementAt_uchar3(alloc,x,y))
#define gets2(x,y, alloc)(rsGetElementAt_short2(alloc,x,y))
#define gets4(x,y, alloc)(rsGetElementAt_short4(alloc,x,y))
#define setc4(x,y, alloc,in)(rsSetElementAt_uchar4(alloc,in,x,y))
#define setus(x,y, alloc,in)(rsSetElementAt_ushort(alloc,in,x,y))
#define sets2(x,y, alloc,in)(rsSetElementAt_short2(alloc,in,x,y))
#define sets4(x,y, alloc,in)(rsSetElementAt_short4(alloc,in,x,y))
#define setf3(x,y, alloc,in)(rsSetElementAt_float3(alloc,in,x,y))
#define getf(x,y, alloc)(rsGetElementAt_float(alloc,x,y))
#define getf2(x,y, alloc)(rsGetElementAt_float2(alloc,x,y))
#define getf3(x,y, alloc)(rsGetElementAt_float3(alloc,x,y))
#define getf4(x,y, alloc)(rsGetElementAt_float4(alloc,x,y))
#define seth3(x,y, alloc,in)(rsSetElementAt_half3(alloc,in,x,y))
#define geth3(x,y, alloc)(rsGetElementAt_half3(alloc,x,y))
//#define TILESIZE (64)
#define SCANSIZE (TILESIZE)
#define TILESCALE (TILESIZE/2)
#define HIGHFLOAT (1000000.f)

static short2 mirrorCoords3(int x,int y, short boundsx,short boundsy);
static short2 mirrorCoords4(int x,int y, short boundsx,short boundsy);

void RS_KERNEL align(int x, int y) {
short4 prevAlign;
if(prevScale != 0){
prevAlign = gets4(x/prevScale,y/prevScale,alignVectors)*prevScale;
}
ushort2 frame;
frame.x = x*TILESCALE;
frame.y = y*TILESCALE;
short2 shift;
short4 outputAl;
float mindist = HIGHFLOAT;
float dist = 0.f;
 for(int h = -4;h<4;h++){
   for(int w = -4;w<4;w++){
   float dist3 = 6.0+fabs((float)(h)/(float)(4))+fabs((float)(w)/(float)(4));
   shift.x = w;
   shift.y = h;
   shift+=prevAlign.xy;
     for(int h0= -SCANSIZE/2;h0<SCANSIZE/2;h0++){
        for(int w0= -SCANSIZE/2;w0<SCANSIZE/2;w0++){
        short2 ref = mirrorCoords4(frame.x+w0,frame.y+h0,inputSize.x,inputSize.y);
        short2 inp = mirrorCoords4(frame.x+shift.x+w0,frame.y+shift.y+h0,inputSize.x,inputSize.y);
        float2 t =fabs( (getf2((ref.x),(ref.y),referenceBuffer))-
                    (getf2((inp.x),(inp.y),inputBuffer)) );
        dist+=t.r+t.g;
        }
     }
    dist*=dist3;
    if(dist < mindist){
    outputAl.xy = shift;
    mindist = dist;
    }
    dist = 0.f;
   }
 }
sets4(x,y,alignOutput,outputAl);
}


static short2 mirrorCoords(short2 xy, short4 bounds){
    if(xy.x < bounds.r){
        xy.x = 2*bounds.r-xy.x;
    } else {
        if(xy.x > bounds.b){
            xy.x = 2*bounds.b-xy.x;
        }
    }
    if(xy.y < bounds.g){
        xy.y = 2*bounds.g-xy.y;
    } else {
        if(xy.y > bounds.a){
            xy.y = 2*bounds.a-xy.y;
        }
    }
    return xy;
}


static short2 mirrorCoords2(short2 xy, short2 bounds){
    if(xy.x < 0){
        xy.x = -xy.x;
    } else {
        if(xy.x > bounds.r){
            xy.x = 2*bounds.r-xy.x;
        }
    }
    if(xy.y < 0){
        xy.y = -xy.y;
    } else {
        if(xy.y > bounds.g){
            xy.y = 2*bounds.g-xy.y;
        }
    }
    return xy;
}

static short2 mirrorCoords3(int x,int y, short boundsx,short boundsy){
    x%=boundsx*2;
    y%=boundsy*2;
    if(x < 0){
        x = (boundsx*2-x)%boundsx;
        if( x< 0 || !(x < boundsx)) rsDebug("x1 is:",x);
    } else {
        if(x > boundsx){
            x = (boundsx*2-x)%boundsx;
            if( x< 0 || !(x < boundsx)) rsDebug("x0 is:",x);
            //x = 2*boundsx-x;
        }
    }
    if(y < 0){
        y = (-y)%boundsy;
    } else {
        if(y > boundsy){
            y = boundsy + ((-y)%(boundsy));
            //y = 2*boundsy-y;
        }
    }
    if( x< 0 || !(x < boundsx)) rsDebug("x is:",x);
    return (x,y);
}
static short2 mirrorCoords4(int x,int y, short boundsx,short boundsy){
    x%=boundsx*2;
    y%=boundsy*2;
    if(x < 0 || x >= boundsx) x = (boundsx*2-x)%boundsx;
    if(y < 0 || y >= boundsx) y = (boundsy*2-y)%boundsy;
    return (x,y);
}