#pragma version(1)
#pragma rs java_package_name(com.particlesdevs.photoncamera)

#pragma rs_fp_relaxed

int2 kernelSize;
int2 nsize;
double2 perAngle;
int temporalSize;
rs_allocation gyroOutput;
rs_allocation inputBuffer;


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

static short2 mirrorCoords3(int x,int y, short boundsx,short boundsy);
static short2 mirrorCoords4(int x,int y, short boundsx,short boundsy);
static int2 mirrorCoords6(int x,int y, ushort2 bounds);
static short2 mirrorCoords5(int x,int y, short boundsx,short boundsy);
float *gyroSamples0;
float *gyroSamples1;
float *gyroSamples2;
void RS_KERNEL fill(int x, int y) {
int x0 = x * kernelSize.x + kernelSize.x / 2 - nsize.x / 2;
int y0 = y * kernelSize.y + kernelSize.y / 2 - nsize.y / 2;
float xf = 0.0;
float yf = 0.0;
float zf = 0.0;
for (int t = 0; t < temporalSize; t++) {
    xf += gyroSamples0[t];
    yf += gyroSamples1[t];
    zf += gyroSamples2[t];
    int x2 = (int) (x0 * native_cos(zf) - y0 * native_sin(zf) + xf * perAngle.x);
    int y2 = (int) (x0 * native_sin(zf) + y0 * native_cos(zf) + yf * perAngle.y);
    x2 %= kernelSize.x;
    y2 %= kernelSize.y;
    if (x2 < 0) x2 += kernelSize.x;
    if (y2 < 0) y2 += kernelSize.y;
    rsAtomicInc((int32_t*)rsGetElementAt(gyroOutput,x,y,x2 + y2 * kernelSize.x));
    //in.BlurKernels[xk][yk][x + y * kernelSize.x] += 1.0f;
    }
}
