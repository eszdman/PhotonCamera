#pragma version(1)
#pragma rs java_package_name(com.particlesdevs.photoncamera)
#pragma rs_fp_relaxed

rs_allocation referenceBuffer;
rs_allocation inputBuffer;
rs_allocation alignVectors;
ushort prevScale;
rs_allocation alignOutput;

#define RS_KERNEL __attribute__((kernel))
#define gets3(x,y, alloc)(rsGetElementAt_ushort3(alloc,x,y))
#define sets3(x,y, alloc,in)(rsSetElementAt_ushort3(alloc,in,x,y))

#define gets2(x,y, alloc)(rsGetElementAt_ushort(alloc,x,y))
#define gets(x,y, alloc)(*((ushort*)rsGetElementAt(alloc,x,y)))

#define getc(x,y, alloc)(rsGetElementAt_uchar(alloc,x,y))
#define getc4(x,y, alloc)(rsGetElementAt_uchar4(alloc,x,y))
#define getc3(x,y, alloc)(rsGetElementAt_uchar3(alloc,x,y))
#define setc4(x,y, alloc,in)(rsSetElementAt_uchar4(alloc,in,x,y))
#define setus(x,y, alloc,in)(rsSetElementAt_ushort(alloc,in,x,y))
#define sets2(x,y, alloc,in)(rsSetElementAt_ushort2(alloc,in,x,y))
#define setf3(x,y, alloc,in)(rsSetElementAt_float3(alloc,in,x,y))
#define getf3(x,y, alloc)(rsGetElementAt_float3(alloc,x,y))
#define getf4(x,y, alloc)(rsGetElementAt_float4(alloc,x,y))
#define seth3(x,y, alloc,in)(rsSetElementAt_half3(alloc,in,x,y))
#define geth3(x,y, alloc)(rsGetElementAt_half3(alloc,x,y))
#define TILESIZE (256)
#define SCANSIZE (256)
#define TILESCALE (TILESIZE/2)
#define MAXMOVE (TILESCALE/2)
void RS_KERNEL align(int x, int y) {
short2 prevAlign;
if(prevScale != 0){
prevAlign = gets2(x/prevScale,y/prevScale,alignVectors)*prevScale;
}
x*=TILESCALE;
y*=TILESCALE;
short2 shift;
float dist = 0.f;
 for(int h = -MAXMOVE;h<MAXMOVE;h++){

   for(int w = -MAXMOVE;w<MAXMOVE;w++){
   shift = (short2)(w,h);
   shift+=prevAlign;
     for(int h0= -SCANSIZE/2;h0<SCANSIZE/2;h0++){
        for(int w0= -SCANSIZE/2;w0<SCANSIZE/2;w0++){

        }
     }


    dist = 0.f;
   }
 }
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