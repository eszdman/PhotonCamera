#version 300 es
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D NoiseMap;
uniform sampler2D ToneMap;
#define M_PI 3.1415926535897932384626433832795
#define TONEMAPED 0
#define KERNEL 1
#define ISOFACTOR (0.5)
#define NOISEDISTR (0.8)
#define SIZE (1.0,1.0)
#define STR (1.0)
#define SMOOTHING (1)
#define MEDIAN 0
#define sinc(x) (sin(x) / (x+0.0001))
#import interpolation
//#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
#define luminocity(x) dot(x.rgb, vec3(0.2, 0.8, 0.1))
//#define luminocity(x) (x.g+x.r*0.1+x.b*0.1)
#define distributegauss(x,dev,sigma) ((exp(-(x-dev) * (x-dev) / (2.0 * sigma * sigma)) / (sqrt(2.0 * M_PI) * sigma)))
#define distributesinc(x,dev,sigma) (sin(M_PI*x/sigma)/(M_PI*x*sigma))
#define distribute2(x,dev) (1.0-abs(x-dev)*STR)
#define pdf(x) (exp(-0.5*x*x/(STR*STR))/STR)
#define pdf2(x) (sinc((x)*3.0)/(STR*3.0))
#if MEDIAN == 1
#define distribute distributegauss
#else
#define distribute distributesinc
#endif
#import median
#define USEGREEN 1
float bilateral(ivec2 coords) {
    float processed = 0.0;
    float weights = 0.0;
    float noisefactor = clamp((textureBicubicHardware(NoiseMap, vec2(gl_FragCoord.xy)/vec2(SIZE)).r)*0.55*ISOFACTOR,0.0005,1.0);

    #if TONEMAPED == 1
    float inp = (texelFetch(ToneMap,coords,0).r*2.0);
    noisefactor*=clamp((1.3-texelFetch(ToneMap,coords,0).r*10.0),0.3,1.0);
    #else
    float inp = luminocity(texelFetch(InputBuffer, coords,0).rgb);
    #endif
    noisefactor*=noisefactor;
    noisefactor*=0.6;
    #if MEDIAN == 1
    float arr[5];
    arr[0] = luminocity(texelFetch(InputBuffer, coords+ivec2(0,0),0).rgb);
    arr[1] = luminocity(texelFetch(InputBuffer, coords+ivec2(-1,0),0).rgb);
    arr[2] = luminocity(texelFetch(InputBuffer, coords+ivec2(0,-1),0).rgb);
    arr[3] = luminocity(texelFetch(InputBuffer, coords+ivec2(1,0),0).rgb);
    arr[4] = luminocity(texelFetch(InputBuffer, coords+ivec2(0,1),0).rgb);
    float med = median5(arr);
    inp = med;
    processed+=med*1.5;
    weights+=1.5;
    #endif

    for(int i = -KERNEL; i <= KERNEL; i++) {
        for(int j = -KERNEL; j <= KERNEL; j++) {
            ivec2 sxy = abs(ivec2(i,j));
            #if MEDIAN == 1
            if(sxy.x+sxy.y <= 1) continue; else {
                if(i != 0)
                sxy.x-=1;
                else if(j != 0) sxy.y-=1;
            }
            #endif
            float dist = pdf(float(sxy.x)/float(KERNEL))*pdf(float(sxy.y)/float(KERNEL));
            ivec2 patchCoord = coords + ivec2(i, j);
            float sigma = (0.01*0.5 + noisefactor*0.25);
            float w;
            #if TONEMAPED == 1
            w = distribute(inp,(texelFetch(ToneMap,  patchCoord,0).r*2.0), NOISEDISTR)*dist;
            #else
            w = distribute(inp, luminocity(texelFetch(InputBuffer, patchCoord,0).rgb), NOISEDISTR)*dist;
            #endif
            processed += w * luminocity(texelFetch(InputBuffer, patchCoord,0).rgb);
            weights += w;
        }
    }
    //processed = clamp(processed,0.0,10000.0);
    //weights = clamp(weights,0.0,10000.0);
    if(abs(weights) > 0.0001){
        return ((processed) / (weights));
    } else return luminocity(texelFetch(InputBuffer, coords,0).rgb);
}

//////////////////////////////////////////////////////////////////////////////////
out vec3 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec3 rgbS = (texelFetch(InputBuffer, xy,0).rgb)+0.00001;
    float br = luminocity(rgbS);
    rgbS/=br;
    br = bilateral(xy);
    Output = clamp(rgbS*br - 0.00001,0.0,1.0);
}