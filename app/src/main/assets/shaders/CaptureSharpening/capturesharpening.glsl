precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform float size;
uniform float strength;
// Origin of this draw's tile in full-image coordinates. Sampling is
// addressed absolutely so callers can bind the full-size input directly
// instead of pre-shifting a window copy; the frame bounds checks and the
// fetches then use true image coordinates. 0,0 for the legacy full-frame
// path (set explicitly by every caller).
uniform ivec2 u_inOrigin;
out vec3 Output;
//#define depthMin (0.012)
#define depthMin (0.006)
#define depthMax (0.890)
#define colour (0.2)
#define size1 (1.1)
#define SHARPSIZE 5
#define SHARPSIZEKER 3.0
#define SHARPSTR 1.0
#define INSIZE 0,0
#import gaussian
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy) + u_inOrigin;
    vec3 mask = vec3(0.0);
    vec3 cur = (texelFetch(InputBuffer, (xy), 0).rgb);
    float pdfsize = 0.0;
    ivec2 sizeImage = ivec2(INSIZE);
    for (int i=-SHARPSIZE; i <= SHARPSIZE; ++i){
        float pdf2 = pdf(float(i)/float(SHARPSIZEKER));
        if(i+xy.x >= sizeImage.x || i+xy.x <= 0) continue;
        for (int j=-SHARPSIZE; j <= SHARPSIZE; ++j){
            if(j+xy.y >= sizeImage.y || j+xy.y <= 0) continue;
            float pdfv = pdf(float(j)/float(SHARPSIZEKER))*pdf2;
            mask+=vec3(texelFetch(InputBuffer, (xy+ivec2(i, j)), 0).rgb)*pdfv;
            pdfsize+=pdfv;
        }
    }
    mask/=pdfsize;
    mask = cur-mask;

    cur+=(mask.r+mask.g+mask.b)*(float(SHARPSTR)/3.0);
    Output = clamp(cur,0.0,1.0);
}
