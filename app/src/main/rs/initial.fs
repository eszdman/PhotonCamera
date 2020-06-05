#pragma version(1)
#pragma rs java_package_name(com.eszdman.photoncamera)
#pragma rs_fp_relaxed

//Input Parameters
char cfaPattern; // The Color Filter Arrangement pattern used
uint rawWidth; // Width of raw buffer
uint rawHeight; // Height of raw buffer
float blacklevel[4];
float whitepoint[3];
float ccm[9];
ushort whitelevel;

rs_allocation inputRawBuffer; // RAW16 buffer of dimensions (raw image stride) * (raw image height)
rs_allocation gainMap; // Gainmap to apply to linearized raw sensor data.
#define RS_KERNEL __attribute__((kernel))
#define gets3(x,y, alloc)(rsGetElementAt_ushort3(alloc,x,y))
#define gets(x,y, alloc)(rsGetElementAt_ushort(alloc,x,y))
#define getraw(x,y)(gets(x,y,inputRawBuffer))
#define square(i,x,y)(getraw(x-1 + (i%3)*3,y-1 + i/3))

#define BlackWhiteLevel(in)(clamp((in-blacklevel[0])/((whitelevel-blacklevel[0])),0.f,1.f))
static float3 demosaic(uint x, uint y, uint cfa) {
    uint index = (x & 1) | ((y & 1) << 1);
    index |= (cfa << 2);
    float inputArray[9];
    for(int i = 0; i<9;i++) inputArray[i] = BlackWhiteLevel(square(i,x,y));
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
// Apply gamma correction using sRGB gamma curve
static float gammaEncode(float x) {
    return (x <= 0.0031308f) ? x * 12.92f : 1.055f * pow((float)x, 0.4166667f) - 0.055f;
}

// Apply gamma correction to each color channel in RGB pixel
static float3 gammaCorrectPixel(float3 rgb) {
    float3 ret;
    ret.x = gammaEncode(rgb.x);
    ret.y = gammaEncode(rgb.y);
    ret.z = gammaEncode(rgb.z);
    return ret;
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
    pRGB = demosaic(x, y, cfaPattern);
    //pRGB = gammaCorrectPixel(pRGB);
    pRGB = ApplyCCM(pRGB);
    pRGB = ColorPointCorrection(pRGB);
    sRGB = gammaCorrectPixel(pRGB);
    return PackInto8Bit(sRGB);
    //return (tRGB);
}