precision highp float;
precision highp sampler2D;
// Input texture
uniform sampler2D InputBuffer;
out vec3 Output;
#define NOISEO 0.0
#define NOISES 0.0
#define TRANSPOSE (1,1)
#define SIZE 3
#import median
#import gaussian
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    //Get center pixel
    Output = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    float pdfsize = 0.0;
    vec3 minavrmax = vec3(1.0,0.0,0.000001);
    for (int dX = -SIZE; dX <= SIZE; ++dX) {
        for (int dY = -SIZE; dY <= SIZE; ++dY) {
            vec3 colorInp = texelFetch(InputBuffer,xy+ ivec2(dX,dY),0).rgb;
            float brInp = abs(colorInp.g-colorInp.r)+abs(colorInp.g-colorInp.b)+0.00001;
            minavrmax.r = min(minavrmax.r,brInp);
            minavrmax.b = max(minavrmax.b,brInp);
            minavrmax.g+=brInp;
        }
    }
    minavrmax.g/=float(SIZE*2 + 1);
    minavrmax.g/=float(SIZE*2 + 1);
    float sigY = abs(sqrt(minavrmax.g*NOISES + NOISEO)*2.0)+0.00001;
    vec2 outV = vec2(0.0);
    vec2 normal = vec2(0.0001);
    for (int dX = -SIZE; dX <= SIZE; ++dX) {
        for (int dY = -SIZE; dY <= SIZE; ++dY) {
            ivec2 offset = ivec2((dX), (dY));
            vec3 colorInp = vec3(texelFetch(InputBuffer, xy + offset, 0).rgb);
            vec2 brInp = vec2(abs(colorInp.g-colorInp.r),abs(colorInp.g-colorInp.b))+0.00001;
            vec2 dist = 0.5 - (brInp-minavrmax.r)/minavrmax.b;
            vec2 pdfV = pdf2(dist/sigY);
            outV.rg += colorInp.rb*pdfV;
            normal+=pdfV;
        }
    }
    Output.rb=clamp(outV.rg/normal,0.0,1.0);
}