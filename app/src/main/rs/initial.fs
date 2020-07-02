#pragma version(1)
#pragma rs java_package_name(com.eszdman.photoncamera)
#pragma rs_fp_relaxed
//Main parameters
uint rawWidth; // Width of raw buffer
uint rawHeight; // Height of raw buffer
//Input Parameters
char cfaPattern; // The Color Filter Arrangement pattern used
float4 blacklevel;
float3 whitepoint;
float gain;
ushort whitelevel;
uint gainMapWidth;  // The width of the gain map
uint gainMapHeight;  // The height of the gain map
bool hasGainMap; // Does gainmap exist?
float3 neutralPoint; // The camera neutral
float4 toneMapCoeffs; // Coefficients for a polynomial tonemapping curve
float saturationFactor;
float compression;
rs_allocation inputRawBuffer; // RAW16 buffer of dimensions (raw image stride) * (raw image height)
rs_allocation gainMap; // Gainmap to apply to linearized raw sensor data.
rs_matrix3x3 sensorToIntermediate; // Color transform from sensor to a wide-gamut colorspace
rs_matrix3x3 intermediateToSRGB; // Color transform from wide-gamut colorspace to sRGB
float power;

rs_allocation demosaicOut;
rs_allocation remosaicIn1;
rs_allocation remosaicOut;
float remosaicSharp;
//IO buffer
rs_allocation iobuffer;



#define RS_KERNEL __attribute__((kernel))
#define gets3(x,y, alloc)(rsGetElementAt_ushort3(alloc,x,y))
#define sets3(x,y, alloc,in)(rsSetElementAt_ushort3(alloc,in,x,y))

#define gets2(x,y, alloc)(rsGetElementAt_ushort(alloc,x,y))
#define gets(x,y, alloc)(*((ushort*)rsGetElementAt(alloc,x,y)))

#define getc(x,y, alloc)(rsGetElementAt_uchar(alloc,x,y))
#define getc4(x,y, alloc)(rsGetElementAt_uchar4(alloc,x,y))
#define getc3(x,y, alloc)(rsGetElementAt_uchar3(alloc,x,y))
#define setc4(x,y, alloc,in)(rsSetElementAt_uchar4(alloc,in,x,y))
#define sets(x,y, alloc,in)(rsSetElementAt_ushort(alloc,in,x,y))
#define setf3(x,y, alloc,in)(rsSetElementAt_float3(alloc,in,x,y))
#define getf3(x,y, alloc)(rsGetElementAt_float3(alloc,x,y))
#define getf4(x,y, alloc)(rsGetElementAt_float4(alloc,x,y))
#define seth3(x,y, alloc,in)(rsSetElementAt_half3(alloc,in,x,y))
#define geth3(x,y, alloc)(rsGetElementAt_half3(alloc,x,y))

#define getraw(x,y)(gets(x,y,inputRawBuffer))

//rsGetElementAt
#define square(size,i,in,x,y)(in((x) + (i%size),(y) + i/size))
#define square3(i,x,y)(getraw((x)-1 + (i%3),(y)-1 + i/3))
#define square2(i,x,y)(getraw((x) + (i%2),(y) + i/2))


static float4 getGain(uint x, uint y) {
    float interpX = (((float) x) / rawWidth) * gainMapWidth;
    float interpY = (((float) y) / rawHeight) * gainMapHeight;
    uint gX = (uint) interpX;
    uint gY = (uint) interpY;
    uint gXNext = (gX + 1 < gainMapWidth) ? gX + 1 : gX;
    uint gYNext = (gY + 1 < gainMapHeight) ? gY + 1 : gY;
    //float4 tl = *((float4 *) rsGetElementAt(gainMap, gX, gY));
    //float4 tr = *((float4 *) rsGetElementAt(gainMap, gXNext, gY));
    //float4 bl = *((float4 *) rsGetElementAt(gainMap, gX, gYNext));
    //float4 br = *((float4 *) rsGetElementAt(gainMap, gXNext, gYNext));

    float4 tl = rsGetElementAt_float4(gainMap, gX, gY);
    float4 tr = rsGetElementAt_float4(gainMap, gXNext, gY);
    float4 bl = rsGetElementAt_float4(gainMap, gX, gYNext);
    float4 br = rsGetElementAt_float4(gainMap, gXNext, gYNext);

    float fracX = interpX - (float) gX;
    float fracY = interpY - (float) gY;
    float invFracX = 1.f - fracX;
    float invFracY = 1.f - fracY;
    return tl * invFracX * invFracY + tr * fracX * invFracY +
            bl * invFracX * fracY + br * fracX * fracY;
}
// Apply gamma correction using sRGB gamma curve
#define x1 2.8114f
#define x2 -3.5701f
#define x3 1.6807f
//CSEUS Gamma
//1.0 0.86 0.76 0.57 0.48 0.0 0.09 0.3
//0.999134635 0.97580 0.94892548 0.8547916 0.798550103 0.0000000 0.29694557 0.625511972
//#define x1 2.8586f
//#define x2 -3.1643f
//#define x3 1.2899f
static float gammaEncode2(float x) {
    return (x <= 0.0031308f) ? x * 12.92f : 1.055f * pow((float)x, (1.f/gain)) - 0.055f;
}
//Apply Gamma correction
static float3 gammaCorrectPixel(float3 x) {
float3 xx = x*x;
float3 xxx = xx*x;
return (x1*x+x2*xx+x3*xxx);
}

static float3 gammaCorrectPixel2(float3 rgb) {
    rgb.x = gammaEncode2(rgb.x);
    rgb.y = gammaEncode2(rgb.y);
    rgb.z = gammaEncode2(rgb.z);
    return rgb;
}

static float3 tonemap(float3 rgb) {
    float3 sorted = clamp(rgb, 0.f, 1.f);
    float tmp;
    int permutation = 0;
    // Sort the RGB channels by value
    if (sorted.z < sorted.y) {
        tmp = sorted.z;
        sorted.z = sorted.y;
        sorted.y = tmp;
        permutation |= 1;
    }
    if (sorted.y < sorted.x) {
        tmp = sorted.y;
        sorted.y = sorted.x;
        sorted.x = tmp;
        permutation |= 2;
    }
    if (sorted.z < sorted.y) {
        tmp = sorted.z;
        sorted.z = sorted.y;
        sorted.y = tmp;
        permutation |= 4;
    }
    float2 minmax;
    minmax.x = sorted.x;
    minmax.y = sorted.z;
    // Apply tonemapping curve to min, max RGB channel values
    minmax = native_powr(minmax, 3.f) * toneMapCoeffs.x +
            native_powr(minmax, 2.f) * toneMapCoeffs.y +
            minmax * toneMapCoeffs.z + toneMapCoeffs.w;
    // Rescale middle value
    float newMid;
    if (sorted.z == sorted.x) {
        newMid = minmax.y;
    } else {
        newMid = minmax.x + ((minmax.y - minmax.x) * (sorted.y - sorted.x) /
                (sorted.z - sorted.x));
    }
    float3 finalRGB;
    switch (permutation) {
        case 0: // b >= g >= r
            finalRGB.x = minmax.x;
            finalRGB.y = newMid;
            finalRGB.z = minmax.y;
            break;
        case 1: // g >= b >= r
            finalRGB.x = minmax.x;
            finalRGB.z = newMid;
            finalRGB.y = minmax.y;
            break;
        case 2: // b >= r >= g
            finalRGB.y = minmax.x;
            finalRGB.x = newMid;
            finalRGB.z = minmax.y;
            break;
        case 3: // g >= r >= b
            finalRGB.z = minmax.x;
            finalRGB.x = newMid;
            finalRGB.y = minmax.y;
            break;
        case 6: // r >= b >= g
            finalRGB.y = minmax.x;
            finalRGB.z = newMid;
            finalRGB.x = minmax.y;
            break;
        case 7: // r >= g >= b
            finalRGB.z = minmax.x;
            finalRGB.y = newMid;
            finalRGB.x = minmax.y;
            break;
        case 4: // impossible
        case 5: // impossible
        default:
            finalRGB.x = 0.f;
            finalRGB.y = 0.f;
            finalRGB.z = 0.f;
            break;
    }
    return clamp(finalRGB, 0.f, 1.f);
}
// Apply a colorspace transform to the intermediate colorspace, apply
// a tonemapping curve, apply a colorspace transform to a final colorspace,
// and apply a gamma correction curve.
static float3 applyColorspace(float3 pRGB) {
    pRGB.x = clamp(pRGB.x, 0.f, neutralPoint.x);
    pRGB.y = clamp(pRGB.y, 0.f, neutralPoint.y);
    pRGB.z = clamp(pRGB.z, 0.f, neutralPoint.z);
    pRGB = rsMatrixMultiply(&sensorToIntermediate, pRGB);
    pRGB = tonemap(pRGB);
    return gammaCorrectPixel2(gammaCorrectPixel(clamp(rsMatrixMultiply(&intermediateToSRGB, pRGB), 0.f, 1.f)));
}
// Blacklevel subtract, and normalize each pixel in the outputArray, and apply the
// gain map.
static float3 linearizeAndGainmap(uint x, uint y) {
    char cfa = cfaPattern;
    float4 blevel = blacklevel;
    float inputArray[4];
    float3 pRGB;
    for(int i = 0; i<4;i++) inputArray[i] = (square2(i,((x)*2 + cfa%2),((y)*2 + cfa/2)));
    pRGB.r = ((inputArray[0] - blevel[0])/(whitelevel - blevel[0]));
    pRGB.g = ((inputArray[1] - blevel[0])/(whitelevel - blevel[0])+(inputArray[2] - blevel[0])/(whitelevel - blevel[0]))/2.f;
    pRGB.b = (inputArray[3] - blevel[0])/(whitelevel - blevel[0]);
    half3 dem;
    dem.r = (half)pRGB.r;
    dem.g = (half)pRGB.g;
    dem.b = (half)pRGB.b;
    seth3(x,y,demosaicOut,dem);
    for(int i =0; i<4;i++) {
            float bl = 0.f;
            float g = 1.f;
            float4 gains = 1.f;
            if (hasGainMap) {
                gains = getGain(x + i%2 + cfa%2, y + i/2 + cfa/2);
            }
            inputArray[i] = clamp(gains[i] * (inputArray[i] - blevel[i]) / (whitelevel - blevel[i]), 0.f, 1.f);
            /*if(i == 0){
            pRGB.r=clamp(gains[i] * ((square2(i,((x)*2 + cfa%2),((y)*2 + cfa/2))) - blevel[i]) / (whitelevel - blevel[i]), 0.f, 1.f);
            } else if(i == 3){
            pRGB.b=clamp(gains[i] * ((square2(i,((x)*2 + cfa%2),((y)*2 + cfa/2))) - blevel[i]) / (whitelevel - blevel[i]), 0.f, 1.f);
            } else pRGB.g+=clamp(gains[i] * ((square2(i,((x)*2 + cfa%2),((y)*2 + cfa/2))) - blevel[i]) / (whitelevel - blevel[i]), 0.f, 1.f)/2.;*/
     }
    pRGB.r = inputArray[0];
    pRGB.g = (inputArray[1]+inputArray[2])/2.f;
    pRGB.b = inputArray[3];
    return pRGB;
}
const static float3 gMonoMult = {0.299f, 0.587f, 0.114f};
static float3 ColorPointCorrection(float3 in){
in.r/=whitepoint[0];
in.g/=whitepoint[1];
in.b/=whitepoint[2];
return in;
}
#define c1 0.9048f
#define c2 -1.2591f
#define c3 1.30329f
static float3 ExposureCompression(float3 in){
float3 in2 = in*c1 + in*in*c2 + in*in*in*c3;
return (in*(1-(-gain))+in2*(-gain));
}
#define k1 2.8667f
#define k2 -10.0000f
#define k3 13.3333f
#define decomp 0.15f
static float3 ShadowDeCompression(float3 in){
if(fast_length(in) > 0.4f) return in;
float3 in2 = in*k1 + in*in*k2 + in*in*in*k3;
return (in*(1-decomp)+in2*decomp);
}
static uchar4 PackInto8Bit(float3 in){
uchar4 out;
in = clamp((in)*255.f,(float)0.f,(float)255.f);
if(in.y < 0.85f*255.f &&in.x+in.z > 1.9f*255.f) in.y = (in.x+in.z)/2.f;//Green Channel regeneration
out.x = (uchar)(in.x);
out.y = (uchar)(in.y);
out.z = (uchar)(in.z);
return out;
}
void RS_KERNEL color(uint x, uint y) {
    float3 pRGB, sRGB;
    pRGB = linearizeAndGainmap(x, y);
    sRGB = applyColorspace(pRGB);
    //Apply additional saturation
    //sRGB = ExposureCompression(sRGB);
    sRGB = mix(dot(sRGB.rgb, gMonoMult), sRGB.rgb, saturationFactor+sRGB.g*0.2f+sRGB.b*0.2f);
    setc4(x,y,iobuffer,rsPackColorTo8888(sRGB));
}
#define colthr 4.1f
static int normx(int x){
if(x<0) return 0;
else {
return min(x,(int)(rawWidth-1));
}
}
static int normy(int y){
if(y<0) return 0;
else {
return min(y,(int)(rawHeight-1));
}
}
void RS_KERNEL blurdem(uint x, uint y) {
    half3 in[9];
    half3 out;
    //in[0] = geth3(x-1,y-1,demosaicOut);
    in[1] = geth3(x,normy(y-2),demosaicOut);
    //in[2] = geth3(x+1,y-1,demosaicOut);
    in[3] = geth3(normx(x-2),y,demosaicOut);
    in[4] = geth3(x,y,demosaicOut);
    in[5] = geth3(normx(x+2),y,demosaicOut);

    //in[6] = geth3(x-1,y+1,demosaicOut);
    in[7] = geth3(x,normy(y+2),demosaicOut);
    //in[8] = geth3(x+1,y+1,demosaicOut);

    out =   (in[1]);
    out +=(in[3]+in[4]+in[5]);
    out +=   (in[7]);
    out/=5.f;
    //out = in[4];
    //half3 diff1,diff2;
    seth3(x,y,remosaicIn1,out);
}

void RS_KERNEL demosaicmask(uint x, uint y) {
    bool fact1 = (x%2 == 1);
    bool fact2 = (y%2 == 1);
    half3 blurred = geth3(x/2,y/2,remosaicIn1);
    float3 infl;
    uchar4 input = getc4(x/2,(y/2-2),iobuffer);
    infl.r = ((float)input.r);
    infl.g = ((float)input.g);
    infl.b = ((float)input.b);
    input = getc4((x/2-2),y/2,iobuffer);
    infl.r += ((float)input.r);
    infl.g += ((float)input.g);
    infl.b += ((float)input.b);
    input = getc4(x/2,y/2,iobuffer);
    infl.r += ((float)input.r);
    infl.g += ((float)input.g);
    infl.b += ((float)input.b);
    input = getc4((x/2+2),y/2,iobuffer);
    infl.r += ((float)input.r);
    infl.g += ((float)input.g);
    infl.b += ((float)input.b);
    input = getc4(x/2,(y/2+2),iobuffer);
    infl.r += ((float)input.r);
    infl.g += ((float)input.g);
    infl.b += ((float)input.b);
    infl/=255.f*(5.f);
     half mosaic[4];
     mosaic[0] = clamp(((half)(getraw(x + cfaPattern%2,y + cfaPattern/2)) - blacklevel[0]) / (whitelevel - blacklevel[0]), 0.f, 1.f);
     mosaic[1] = clamp(((half)(getraw(x +1+ cfaPattern%2,y + cfaPattern/2)) - blacklevel[0]) / (whitelevel - blacklevel[0]), 0.f, 1.f);
     mosaic[2] = clamp(((half)(getraw(x + cfaPattern%2,y +1+ cfaPattern/2)) - blacklevel[0]) / (whitelevel - blacklevel[0]), 0.f, 1.f);
     mosaic[3] = clamp(((half)(getraw(x +1+ cfaPattern%2,y +1+ cfaPattern/2)) - blacklevel[0]) / (whitelevel - blacklevel[0]), 0.f, 1.f);
        float3 output;
            if(fact1 ==0 && fact2 == 0) {//grbg
                //befinfl = mosin;
                output.b = mosaic[2];
                output.g = (mosaic[0]+mosaic[3])/2.f;
                output.r = mosaic[1];
            } else
            if(fact1 ==1 && fact2 == 0) {//bggr
                output.b = mosaic[0];
                output.g = (mosaic[2]+mosaic[1])/2.f;
                output.r = mosaic[3];
            } else
            if(fact1 ==0 && fact2 == 1) {//rggb
                output.b = mosaic[3];
                output.g = (mosaic[1]+mosaic[2])/2.f;
                output.r = mosaic[0];
            }
            else  {//gbrg
                output.b = mosaic[1];
                output.g = (mosaic[0]+mosaic[3])/2.f;
                output.r = mosaic[2];
            }
            output.r-=blurred.r;
            output.g-=blurred.g;
            output.b-=blurred.b;
            float outp = (output.r+output.g+output.b);
            outp/=3.f;
            if((outp)>0.03f) outp = 0.03f;
            else if(outp<-0.03f) outp = -0.03f;
            //outp*=1.5f-(infl.r+infl.g+infl.b)/3.f;
            //if(outp > 0.5f) outp=(outp+0.5f)*0.3f;
            //if(outp < -0.5f) outp=(outp-0.5f)*0.3f;
            infl+=outp*remosaicSharp;
            //infl+=output*remosaicSharp;
            setc4(x,y,remosaicOut,rsPackColorTo8888(infl));
}
void RS_KERNEL demosaicmask2(uint x, uint y) {
    bool fact1 = (x%2 == 1);
    bool fact2 = (y%2 == 1);
    half3 blurred = geth3(x/2,y/2,demosaicOut);
    uchar4 input;
    input = getc4(x/2,y/2,iobuffer);
    uchar3 demosaicked;
    demosaicked = getc3(x,y,remosaicIn1);
     float3 infl;
     infl.r = ((float)input.r)/255.f;
     infl.g = ((float)input.g)/255.f;
     infl.b = ((float)input.b)/255.f;
        float3 output;
        output.r = ((float)demosaicked.r)/255.f;
        output.g = ((float)demosaicked.g)/255.f;
        output.b = ((float)demosaicked.b)/255.f;
        output.r-=blurred.r;
        output.g-=blurred.g;
        output.b-=blurred.b;
        infl+=(output.r+output.g+output.b)*remosaicSharp/3.f;
        //infl+=output*remosaicSharp;
        setc4(x,y,remosaicOut,rsPackColorTo8888(infl));
}
void RS_KERNEL remosaic(uint x, uint y) {
    half3 out;
    bool fact1 = (x%2 == 1);
    bool fact2 = (y%2 == 1);
    half br;

    half3 blurred = geth3(x/2,y/2,remosaicIn1);
    //half3 demosout = geth3(x/2,y/2,demosaicOut);
     uchar4 input[5];
     //input[0] = getc4(x/2 -1,y/2 -1,iobuffer);
     input[0] = getc4(x/2,normy(y/2 -2),iobuffer);
     //input[2] = getc4(x/2 +1,y/2 -1,iobuffer);

     input[1] = getc4(normx(x/2 -2),y/2,iobuffer);
     input[2] = getc4(x/2,y/2,iobuffer);
     input[3] = getc4(normx(x/2 +2),y/2,iobuffer);

     //input[6] = getc4(x/2 -1,y/2 +1,iobuffer);
     input[4] = getc4(x/2,normy(y/2 +2),iobuffer);

     float3 t1,t2;
     float3 infl;
     /*uchar4 tmp = getc4(x,y,iobuffer);
     infl.r = ((float)tmp.r)/(255.f);
     infl.g = ((float)tmp.g)/(255.f);
     infl.b = ((float)tmp.b)/(255.f);*/
             for(int i =0; i<5; i++) {
             float3 temp;
              temp.r = ((float)input[i].r)/(255.f*(1.f));
              temp.g = ((float)input[i].g)/(255.f*(1.f));
              temp.b = ((float)input[i].b)/(255.f*(1.f));
              infl+=temp;
              if(i == 0) t1 = temp;
              if(i == 4) t1 -=temp;
              if(i == 1) t2 = temp;
              if(i == 3) t2 -=temp;
              }
              infl/=5.f;
             //float3 in;
             //if(br>0.4f) br = 0.0f;
             //if(br<-0.4f) br = -0.0f;
             //in.r = (br+infl.r);
             //in.g = (br+infl.g);
             //in.b = (br+infl.b);
             t1/=t1.r+t1.g+t1.b;
             t2/=t2.r+t2.g+t2.b;
             t1-=t2;
             t1 = fabs(t1);
             //if(fabs(t1.r-t1.g-t1.b) > 0.2) {setc4(x,y,remosaicOut,(input[2]));return;}
             /*if(fabs(t1.r-t1.b-t1.g) > 0.2f){
             blurred = geth3((x/2),(y/2) - 1,remosaicIn1)+geth3((x/2),(y/2) + 1,remosaicIn1);
             blurred/= 2.f;
             }
             if(fabs(t2.r-t2.b-t2.g) > 0.2f){
             blurred = geth3((x/2)-1,(y/2),remosaicIn1)+geth3((x/2)+1,(y/2),remosaicIn1);
             blurred/= 2.f;
             if(fabs(t1.r-t1.b-t1.g) > 0.2f){
              blurred += geth3((x/2),(y/2) - 1,remosaicIn1)+geth3((x/2),(y/2) + 1,remosaicIn1);
              blurred/= 3.f;
              }
             }*/
             half mosaic[4];
    mosaic[0] = clamp(((half)(getraw(x + cfaPattern%2,y + cfaPattern/2)) - blacklevel[0]) / (whitelevel - blacklevel[0]), 0.f, 1.f);
    if(fact1 ==0 % fact2 == 0) {
        br = mosaic[0] - blurred.g;
    }
    if(fact1 ==1 % fact2 == 0) {//b
        br = mosaic[0] - blurred.b;
    }
    if(fact1 ==0 % fact2 == 1) {//r
        br = mosaic[0] - blurred.r;
    }
    if(fact1 == 1 % fact2 == 1) {
        br = mosaic[0] - blurred.g;
    }
    //br+=blurred.r+blurred.g+blurred.b;
    //br*=remosaicSharp;
    //br/=(blurred.r+blurred.g+blurred.b+0.5f);
    //seth3(x,y,remosaicOut,(br-demosout.r,br-demosout.g,br-demosout.b));
    //float t00 = fmax(fabs(t1.r-t1.g-t1.b),0.2f);
    //br*=1.f-t00*5.f;
    float3 befinfl = infl;
    //infl+=br;

    //if(fabs(br) > 0.0075f*1.f || fabs(t1.r-t1.g-t1.b) > 0.15f*0.7f){
    //if( fabs(t1.r-t1.g-t1.b) > 0.15f*0.75f){
    if(0){

    //mosaic[1] = clamp(((half)(getraw(x +1+ cfaPattern%2,y + cfaPattern/2)) - blacklevel[0]) / (whitelevel - blacklevel[0]), 0.f, 1.f);
    //mosaic[2] = clamp(((half)(getraw(x + cfaPattern%2,y +1+ cfaPattern/2)) - blacklevel[0]) / (whitelevel - blacklevel[0]), 0.f, 1.f);
    //mosaic[3] = clamp(((half)(getraw(x +1+ cfaPattern%2,y +1+ cfaPattern/2)) - blacklevel[0]) / (whitelevel - blacklevel[0]), 0.f, 1.f);
    float3 output;
        if(fact1 ==0 % fact2 == 0) {
            //befinfl = mosin;
            output.r = mosaic[2];
            output.g = (mosaic[0]+mosaic[3])/2.f;
            output.b = mosaic[1];
        }
        if(fact1 ==1 % fact2 == 0) {//b
            output.r = mosaic[0];
            output.g = (mosaic[2]+mosaic[1])/2.f;
            output.b = mosaic[3];
        }
        if(fact1 ==0 % fact2 == 1) {//r
            output.r = mosaic[3];
            output.g = (mosaic[1]+mosaic[2])/2.f;
            output.b = mosaic[0];
        }
        if(fact1 == 1 % fact2 == 1) {
            output.r = mosaic[1];
            output.g = (mosaic[0]+mosaic[3])/2.f;
            output.b = mosaic[2];
        }
        output.r-=blurred.r;
        output.g-=blurred.g;
        output.b-=blurred.b;
        //infl+=output;
        setc4(x,y,remosaicOut,input[2]);
        //befinfl.r-=blurred.r;
        //befinfl.g-=blurred.g;
        //befinfl.b-=blurred.b;
        //infl+=befinfl;
    //setc4(x,y,remosaicOut,input[2]);
    return;
    }
    float c0 = 0.45f;
    float norm = 0.4f;
    float norm2 = 0.6f;
    //float norm2 = 0.5f;
    //float norm2 = 0.4;
    if(br > c0) br *= norm;
    if(br < -c0) br *= norm2;
    if(fabs(br) > 0.5f){br/=fabs(br); br*=0.5f;}
    br*=remosaicSharp;
    infl+=br;
    infl = clamp(infl, 0.f,1.f);
    setc4(x,y,remosaicOut,rsPackColorTo8888(infl));
}
#define readbayer(x,y)(clamp(((float)(getraw(x + cfaPattern%2,y + cfaPattern/2)) - blacklevel[0]) / (whitelevel - blacklevel[0]), 0.f, 1.f))
#define mingreen (0.01f)
void RS_KERNEL demosaic2(uint x, uint y) {
    bool fact1 = ((x)%2 == 1);
    bool fact2 = ((y)%2 == 1);
    float3 infl;
    if(fact1 == 0 && fact2 == 0) {//grbg
    infl.b = (readbayer(x,y+1)+readbayer(x,y-1))/2.f;
    infl.r = (readbayer(x+1,y)+readbayer(x-1,y))/2.f;
    infl.g = readbayer(x,y);
    setc4(x,y,remosaicOut,rsPackColorTo8888(infl));
    return;
    }
    if(fact1 == 0 && fact2 == 0) {//bggr
        float4 green;
        green.r = readbayer(x-1,y);
        green.g = readbayer(x+1,y);
        green.b = readbayer(x,y-1);
        green.a = readbayer(x,y+1);
        float DH =fabs(green.r-green.b);//Horiz grad
        float DV =fabs(green.g-green.a);//Vert grad
        if(DH > DV){
        infl.g = (green.g+green.a)/2.f;
        //if((grad[1]+grad[3]) > grad[0]+grad[2] && grad[4]<avr){
        //outpu = (grad[0]+grad[2])/2.;
        //}
        } else
        if(DV > DH){
        infl.g = (green.r+green.b)/2.f;
        //if((grad[1]+grad[3]) < grad[0]+grad[2] && grad[4]<avr){
        //outpu = (grad[1]+grad[3])/2.;
        //}
        } else {
        infl.g = (green.r+green.g+green.b+green.a)/4.f;
        }
        float4 red;
        red.r = readbayer(x-1,y-1);
        red.g = readbayer(x+1,y-1);
        red.b = readbayer(x-1,y+1);
        red.a = readbayer(x+1,y+1);
        infl.r = (red.r+red.g+red.b+red.a)/4.f;
        infl.b = readbayer(x,y);
        setc4(x,y,remosaicOut,rsPackColorTo8888(infl));
        return;
    }
    if(fact1 == 0 && fact2 == 0) {//rggb
        float4 green;
        green.r = readbayer(x-1,y);
        green.g = readbayer(x+1,y);
        green.b = readbayer(x,y-1);
        green.a = readbayer(x,y+1);
        float DH =fabs(green.r-green.b);//Horiz grad
        float DV =fabs(green.g-green.a);//Vert grad
        if(DH > DV){
        infl.g = (green.g+green.a)/2.f;
        //if((grad[1]+grad[3]) > grad[0]+grad[2] && grad[4]<avr){
        //outpu = (grad[0]+grad[2])/2.;
        //}
        } else
        if(DV > DH){
        infl.g = (green.r+green.b)/2.f;
        //if((grad[1]+grad[3]) < grad[0]+grad[2] && grad[4]<avr){
        //outpu = (grad[1]+grad[3])/2.;
        //}
        } else {
        infl.g = (green.r+green.g+green.b+green.a)/4.f;
        }
        float4 red;
        red.r = readbayer(x-1,y-1);
        red.g = readbayer(x+1,y-1);
        red.b = readbayer(x-1,y+1);
        red.a = readbayer(x+1,y+1);
        infl.b = (red.r+red.g+red.b+red.a)/4.f;
        infl.r = readbayer(x,y);
        setc4(x,y,remosaicOut,rsPackColorTo8888(infl));
        return;
    }
    if(fact1 == 0 && fact2 == 0) {//gbrg
    infl.r = (readbayer(x,y+1)+readbayer(x,y-1))/2.f;
    infl.b = (readbayer(x+1,y)+readbayer(x-1,y))/2.f;
    infl.g = readbayer(x,y);
    setc4(x,y,remosaicOut,rsPackColorTo8888(infl));
    return;
    }
}