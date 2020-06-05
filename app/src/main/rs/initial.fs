#pragma version(1)
#pragma rs java_package_name(com.eszdman.photoncamera)
#pragma rs_fp_relaxed

//Input Parameters
char cfaPattern; // The Color Filter Arrangement pattern used
uint rawWidth; // Width of raw buffer
uint rawHeight; // Height of raw buffer
float blacklevel[4];
float whitepoint[3];
ushort whitelevel;

rs_allocation inputRawBuffer; // RAW16 buffer of dimensions (raw image stride) * (raw image height)
rs_allocation gainMap; // Gainmap to apply to linearized raw sensor data.
uint offsetX; // X offset into inputRawBuffer
uint offsetY; // Y offset into inputRawBuffer
#define RS_KERNEL __attribute__((kernel))
#define gets3(x,y, alloc)(rsGetElementAt_ushort3(alloc,x,y))
#define gets(x,y, alloc)(rsGetElementAt_ushort(alloc,x,y))
#define getraw(x,y)(gets(x,y,inputRawBuffer))
#define square(i,x,y)(getraw(x-1 + (i%3)*3,y-1 + i/3))
static float3 demosaic(uint x, uint y, uint cfa) {
    uint index = (x & 1) | ((y & 1) << 1);
    index |= (cfa << 2);
    float inputArray[9];
    //ushort inlin = getraw(x,y-1);
    inputArray[0] = (float)(getraw(x-1,y-1));
    inputArray[1] = (float)(getraw(x,y-1));
    inputArray[2] = (float)(getraw(x+1,y-1));
    //inlin = getraw(x,y);
    inputArray[4] = (float)(getraw(x-1,y));
    inputArray[4] = (float)(getraw(x,y));
    inputArray[5] = (float)(getraw(x+1,y));
    //inlin = getraw(x,y+1);
    inputArray[6] = (float)(getraw(x-1,y+1));
    inputArray[7] = (float)(getraw(x,y+1));
    inputArray[8] = (float)(getraw(x+1,y+1));
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
static float3 BlackWhiteLevel(float3 in){
float whitefactor = 1.0f / (whitelevel-blacklevel[0]);
return (in-blacklevel[0])*whitefactor;
}
// Apply gamma correction using sRGB gamma curve
static float gammaEncode(float x) {
    return (x <= 0.0031308f) ? x * 12.92f : 1.055f * pow(x, 0.4166667f) - 0.055f;
}
// Apply gamma correction to each color channel in RGB pixel
static float3 gammaCorrectPixel(float3 rgb) {
    float3 ret;
    ret.x = gammaEncode(rgb.x);
    ret.y = gammaEncode(rgb.y);
    ret.z = gammaEncode(rgb.z);
    return ret;
}
static uchar4 PackInto8Bit(float3 in){
uchar4 out;
float limit = 1.f;
in = clamp(in*limit,0.f,255.f);
out.x = (uchar)(in.x);
out.y = (uchar)(in.y);
out.z = (uchar)(in.z);
return out;
}
uchar4 RS_KERNEL demosaicing(uint x, uint y) {
    float3 pRGB, sRGB;
    uchar4 tRGB;
    uint xP = x + offsetX;
    uint yP = y + offsetY;
    if (xP == 0) xP = 1;
    if (yP == 0) yP = 1;
    if (xP == rawWidth - 1) xP = rawWidth - 2;
    if (yP == rawHeight - 1) yP = rawHeight  - 2;
    //pRGB = demosaic(xP, yP, cfaPattern);
    ushort brightness = getraw(x,y);
    pRGB.x = (float)(brightness);
    pRGB.y = (float)(brightness);
    pRGB.z = (float)(brightness);
    //pRGB = gammaCorrectPixel(pRGB);
    pRGB = BlackWhiteLevel(pRGB);
    uchar4 out = PackInto8Bit(pRGB);
    return out;
    //return (tRGB);
}