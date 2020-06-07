#pragma version(1)
#pragma rs java_package_name(com.eszdman.photoncamera)
#pragma rs_fp_relaxed

//Input Parameters
char cfaPattern; // The Color Filter Arrangement pattern used
uint rawWidth; // Width of raw buffer
uint rawHeight; // Height of raw buffer

float4 blacklevel;
float3 whitepoint;
float ccm[9];
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

float power;
rs_allocation inputjpg;
rs_allocation inputblur;

rs_matrix3x3 sensorToIntermediate; // Color transform from sensor to a wide-gamut colorspace
rs_matrix3x3 intermediateToSRGB; // Color transform from wide-gamut colorspace to sRGB

#define RS_KERNEL __attribute__((kernel))
#define gets3(x,y, alloc)(rsGetElementAt_ushort3(alloc,x,y))
#define gets(x,y, alloc)(rsGetElementAt_ushort(alloc,x,y))
#define getc(x,y, alloc)(rsGetElementAt_uchar(alloc,x,y))
#define getc4(x,y, alloc)(rsGetElementAt_uchar(alloc,x,y))
#define setc4(x,y, alloc,in)(rsSetElementAt_uchar4(alloc,in,x,y))
#define getraw(x,y)(gets(x,y,inputRawBuffer))
#define square3(i,x,y)(getraw((x)-1 + (i%3),(y)-1 + i/3))
#define square2(i,x,y)(getraw((x) + (i%2),(y) + i/2))

static float4 getGain(uint x, uint y) {
    float interpX = (((float) x) / rawWidth) * gainMapWidth;
    float interpY = (((float) y) / rawHeight) * gainMapHeight;
    uint gX = (uint) interpX;
    uint gY = (uint) interpY;
    uint gXNext = (gX + 1 < gainMapWidth) ? gX + 1 : gX;
    uint gYNext = (gY + 1 < gainMapHeight) ? gY + 1 : gY;
    float4 tl = *((float4 *) rsGetElementAt(gainMap, gX, gY));
    float4 tr = *((float4 *) rsGetElementAt(gainMap, gXNext, gY));
    float4 bl = *((float4 *) rsGetElementAt(gainMap, gX, gYNext));
    float4 br = *((float4 *) rsGetElementAt(gainMap, gXNext, gYNext));
    float fracX = interpX - (float) gX;
    float fracY = interpY - (float) gY;
    float invFracX = 1.f - fracX;
    float invFracY = 1.f - fracY;
    return tl * invFracX * invFracY + tr * fracX * invFracY +
            bl * invFracX * fracY + br * fracX * fracY;
}
// Apply gamma correction using sRGB gamma curve
#define x0 0.0648
#define x1 2.8114
#define x2 -3.5701
#define x3 1.6807
static float gammaEncode2(float x) {
    return (x <= 0.0031308f) ? x * 12.92f : 1.055f * pow((float)x, 0.4166667f) - 0.055f;
}
static float gammaEncode(float x) {
    return (x0+x1*x+x2*x*x+x3*x*x*x);
}

// Apply gamma correction to each color channel in RGB pixel
static float3 gammaCorrectPixel(float3 rgb) {
    float3 ret;
    ret.x = gammaEncode(rgb.x);
    ret.y = gammaEncode(rgb.y);
    ret.z = gammaEncode(rgb.z);
    return ret;
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
    float3 intermediate = rsMatrixMultiply(&sensorToIntermediate, pRGB);
    intermediate = tonemap(intermediate);
    return gammaCorrectPixel(clamp(rsMatrixMultiply(&intermediateToSRGB, intermediate), 0.f, 1.f));
}
// Blacklevel subtract, and normalize each pixel in the outputArray, and apply the
// gain map.
static float3 linearizeAndGainmap(uint x, uint y, ushort whiteLevel,
        uint cfa) {
    uint kk = 0;
    float inputArray[4];
    for(int i = 0; i<4;i++) inputArray[i] = (square2(i,((x)*2 + cfa%2),((y)*2 + cfa/2)));
    for(int i =0; i<4;i++) {
            float bl = 0.f;
            float g = 1.f;
            float4 gains = 1.f;
            if (hasGainMap) {
                gains = getGain(x + i%2 + cfa%2, y + i/2 + cfa/2);
            }
            inputArray[i] = clamp(gains[i] * (inputArray[i] - blacklevel[i]) / (whiteLevel - blacklevel[i]), 0.f, 1.f);
            kk++;
     }
    float3 pRGB;
    pRGB.r = inputArray[0];
    pRGB.g = (inputArray[1]+inputArray[2])/2.f;
    pRGB.b = inputArray[3];
    return pRGB;
}
static float3 linearizeAndGainmapStock(uint x, uint y, ushort whiteLevel,
        uint cfa) {
    uint kk = 0;
    float inputArray[9];
    uint index = (x & 1) | ((y & 1) << 1);
    index |= (cfa << 2);
    for(int i = 0; i<9;i++) inputArray[i] = (square3(i,x*2,y*2));
    for (uint j = y - 1; j <= y + 1; j++) {
        for (uint i = x - 1; i <= x + 1; i++) {
            uint index = (i & 1) | ((j & 1) << 1);  // bits [0,1] are blacklevel offset
            index |= (cfa << 2);  // bits [2,3] are cfa
            float bl = 0.f;
            float g = 1.f;
            float4 gains = 1.f;
            if (hasGainMap) {
                gains = getGain(i, j);
            }
            switch (index) {
                // RGGB
                case 0:
                    bl = blacklevel.x;
                    g = gains.x;
                    break;
                case 1:
                    bl = blacklevel.y;
                    g = gains.y;
                    break;
                case 2:
                    bl = blacklevel.z;
                    g = gains.z;
                    break;
                case 3:
                    bl = blacklevel.w;
                    g = gains.w;
                    break;
                // GRBG
                case 4:
                    bl = blacklevel.x;
                    g = gains.y;
                    break;
                case 5:
                    bl = blacklevel.y;
                    g = gains.x;
                    break;
                case 6:
                    bl = blacklevel.z;
                    g = gains.w;
                    break;
                case 7:
                    bl = blacklevel.w;
                    g = gains.z;
                    break;
                // GBRG
                case 8:
                    bl = blacklevel.x;
                    g = gains.y;
                    break;
                case 9:
                    bl = blacklevel.y;
                    g = gains.w;
                    break;
                case 10:
                    bl = blacklevel.z;
                    g = gains.x;
                    break;
                case 11:
                    bl = blacklevel.w;
                    g = gains.z;
                    break;
                // BGGR
                case 12:
                    bl = blacklevel.x;
                    g = gains.w;
                    break;
                case 13:
                    bl = blacklevel.y;
                    g = gains.y;
                    break;
                case 14:
                    bl = blacklevel.z;
                    g = gains.z;
                    break;
                case 15:
                    bl = blacklevel.w;
                    g = gains.x;
                    break;
            }
            inputArray[kk] = clamp(g * (inputArray[kk] - bl) / (whiteLevel - bl), 0.f, 1.f);
            kk++;
        }
    }
    float3 pRGB;
        switch (index) {
            case 0:
            case 5:
            case 10:
            case 15:  // Red centered
                      // B G B
                      // G R G
                      // B G B
                pRGB.x = inputArray[4];
                pRGB.y = (inputArray[1] + inputArray[3] + inputArray[5] + inputArray[7]) / 4.f;
                pRGB.z = (inputArray[0] + inputArray[2] + inputArray[6] + inputArray[8]) / 4.f;
                break;
            case 1:
            case 4:
            case 11:
            case 14: // Green centered w/ horizontally adjacent Red
                     // G B G
                     // R G R
                     // G B G
                pRGB.x = (inputArray[3] + inputArray[5]) / 2.f;
                pRGB.y = inputArray[4];
                pRGB.z = (inputArray[1] + inputArray[7]) / 2.f;
                break;
            case 2:
            case 7:
            case 8:
            case 13: // Green centered w/ horizontally adjacent Blue
                     // G R G
                     // B G B
                     // G R G
                pRGB.x = (inputArray[1] + inputArray[7]) / 2.f;
                pRGB.y = inputArray[4];
                pRGB.z = (inputArray[3] + inputArray[5]) / 2.f;
                break;
            case 3:
            case 6:
            case 9:
            case 12: // Blue centered
                     // R G R
                     // G B G
                     // R G R
                pRGB.x = (inputArray[0] + inputArray[2] + inputArray[6] + inputArray[8]) / 4.f;
                pRGB.y = (inputArray[1] + inputArray[3] + inputArray[5] + inputArray[7]) / 4.f;
                pRGB.z = inputArray[4];
                break;
        }
        return pRGB;
}
const static float3 gMonoMult = {0.299f, 0.587f, 0.114f};

#define BlackWhiteLevel(in)(clamp((in-blacklevel[0])/(((float)whitelevel-(float)blacklevel[0])),0.f,1.f))
static float3 demosaic(uint x, uint y, uint cfa) {
    uint index = (x & 1) | ((y & 1) << 1);
    index |= (cfa << 2);
    float inputArray[9];
    for(int i = 0; i<9;i++) inputArray[i] = BlackWhiteLevel(square3(i,x,y));
    //locality = gets3(x/4,yin-1,inputRawBuffer);inputArray[0] = (float3)((float)locality.x,(float)locality.y,(float)locality.z);
    //locality = gets3(x/4,yin,inputRawBuffer);inputArray[1] = (float3)((float)locality.x,(float)locality.y,(float)locality.z);
    //locality = gets3(x/4,yin+1,inputRawBuffer);inputArray[2] = (float3)((float)locality.x,(float)locality.y,(float)locality.z);
    float3 pRGB;
    switch (index) {
        case 0:
        case 5:
        case 10:
        case 15:  // Red centered
                  // B G B
                  // G R G
                  // B G B
            pRGB.x = inputArray[4];
            pRGB.y = (inputArray[1] + inputArray[3] + inputArray[5] + inputArray[7]) / 4.f;
            pRGB.z = (inputArray[0] + inputArray[2] + inputArray[6] + inputArray[8]) / 4.f;
            break;
        case 1:
        case 4:
        case 11:
        case 14: // Green centered w/ horizontally adjacent Red
                 // G B G
                 // R G R
                 // G B G
            pRGB.x = (inputArray[3] + inputArray[5]) / 2.f;
            pRGB.y = inputArray[4];
            pRGB.z = (inputArray[1] + inputArray[7]) / 2.f;
            break;
        case 2:
        case 7:
        case 8:
        case 13: // Green centered w/ horizontally adjacent Blue
                 // G R G
                 // B G B
                 // G R G
            pRGB.x = (inputArray[1] + inputArray[7]) / 2.f;
            pRGB.y = inputArray[4];
            pRGB.z = (inputArray[3] + inputArray[5]) / 2.f;
            break;
        case 3:
        case 6:
        case 9:
        case 12: // Blue centered
                 // R G R
                 // G B G
                 // R G R
            pRGB.x = (inputArray[0] + inputArray[2] + inputArray[6] + inputArray[8]) / 4.f;
            pRGB.y = (inputArray[1] + inputArray[3] + inputArray[5] + inputArray[7]) / 4.f;
            pRGB.z = inputArray[4];
            break;
    }
    return pRGB;
}
static float3 ApplyCCM(float3 in){
in.r*= ccm[0]*in.r+ccm[3]*in.g+ccm[6]*in.b;
in.g*= ccm[1]*in.r+ccm[4]*in.g+ccm[7]*in.b;
in.b*= ccm[2]*in.r+ccm[5]*in.g+ccm[8]*in.b;
in*=5.f;
return in;
}
static float3 ColorPointCorrection(float3 in){
in.r/=whitepoint[0];
in.g/=whitepoint[1];
in.b/=whitepoint[2];
return in;
}
#define c1 0.9425f
#define c2 -1.3116f
#define c3 1.3576f
static float3 ExposureCompression(float3 in){
float3 in2 = in*c1 + in*in*c2 + in*in*in*c3;
return (in*(1-compression)+in2*compression);
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
uchar4 RS_KERNEL demosaicing(uint x, uint y) {
    float3 pRGB, sRGB;
    uchar4 tRGB;
    pRGB = linearizeAndGainmap(x, y, whitelevel, cfaPattern);
    sRGB = applyColorspace(pRGB);
    //Apply additional saturation
    sRGB = mix(dot(sRGB.rgb, gMonoMult), sRGB.rgb, saturationFactor);
    sRGB = ExposureCompression(sRGB);
    sRGB=clamp(sRGB*gain - 0.08,0.f,1.f);
    return rsPackColorTo8888(sRGB);
}
