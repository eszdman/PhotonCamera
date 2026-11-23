precision highp float;
// esd3d2_steered: guided 3x3 reference + structure-tensor kernel steering.
// Averages along edges (kills steps/jaggies) without averaging across them
// (keeps sharpness). Optional tone-aware shadow sigma (TONEAWARE /
// TONESTRENGTH defines). Requires GradBuffer (gx, gy) in .rg.
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D GradBuffer;
uniform sampler2D NoiseMap;
uniform ivec2 size;
uniform vec2 mapsize;
uniform int yOffset;
uniform float noiseS;
uniform float noiseO;
out vec4 Output;

#define SIGMA 10.0
#define BSIGMA 0.1
#define KERNELSIZE 3.5
uniform int kernelRadius;
#define TRANSPOSE 1
#define INSIZE 1,1
#define NRcancell (0.90)
#define NRshift (+0.6)
#define maxNR (7.)
#define minNR (0.2)
#define NOISES 0.0
#define NOISEO 0.0
#define INTENSE 1.0
#define MOIRE 1.0
#define LUMA 0.0
#define TONEAWARE 0
#define TONESTRENGTH 1.0
#define PI 3.1415926535897932384626433832795

float normpdf(in float x, in float sigma)
{
return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;
}
float normpdf3(in vec3 v, in float sigma)
{
return 0.39894*exp(-0.5*dot(v,v)/(sigma*sigma))/sigma;
}
float normpdf2(in vec2 v, in float sigma)
{
return 0.39894*exp(-0.5*dot(v,v)/(sigma*sigma))/sigma;
}

float lum(in vec4 color) {
    return length(color.xyz);
}

float atan2(in float y, in float x) {
bool s = (abs(x) > abs(y));
return mix(PI/2.0 - atan(x,y+0.00001), atan(y,x+0.00001), s);
}

#define ESD_CONSENSUS 0
#define ESD_PEEL_AXES 0
#import esd3d2_kernel

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec3 cin = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    vec3 cinX = vec3(texelFetch(InputBuffer, xy+ivec2(1,0), 0).rgb);
    vec3 cinY = vec3(texelFetch(InputBuffer, xy+ivec2(0,1), 0).rgb);
    vec3 cinXY = vec3(texelFetch(InputBuffer, xy+ivec2(1,1), 0).rgb);
    vec3 cavg = (cin+cinX+cinY+cinXY)/4.0;
    float noisefactor = dot(cin,vec3(0.25,0.5,0.25));
    float xDelta = 0.0;
    float yDelta = 0.0;
    float sxx = 0.0; float sxy = 0.0; float syy = 0.0;
    for (int i=-1; i <= 1; ++i) {
        for (int j=-1; j <= 1; ++j) {
            vec2 g = texelFetch(GradBuffer, xy + ivec2(i, j), 0).rg;
            xDelta += float(i) * length(g);
            yDelta += float(j) * length(g);
            sxx += g.x*g.x; sxy += g.x*g.y; syy += g.y*g.y;
        }
    }
    // structure tensor steering: tangent = major eigenvector, coherence 0..1
    float tr = sxx + syy;
    float dlt = sqrt(max((sxx-syy)*(sxx-syy) + 4.0*sxy*sxy, 0.0));
    float lam1 = 0.5*(tr + dlt);
    float lam2 = max(0.5*(tr - dlt), 0.0);
    float coh = (lam1-lam2)/max(lam1+lam2, 0.000001);
    coh *= coh;
    vec2 tng = vec2(sxy, lam1 - sxx);
    tng = mix(tng, vec2(1.0,0.0), step(dot(tng,tng), 0.000000000001));
    tng /= max(length(tng), 0.000001);
    xDelta /= 3.0;
    yDelta /= 3.0;
    // calculate chromatic noise percentage
    float sigX = 2.5;
    // Deep-shadow sigma boost. Below the noise model's read-noise crossover
    // (NOISEO/NOISES) the signal is read-noise dominated, and every later
    // shadow-lifting stage (AE gain -> Reinhard -> sRGB encode -> shadows
    // curve) amplifies the residual by its local slope ~ sqrt(x)/x. Pass that
    // ratio as variance (sigma^2 *= 1/x) but ONLY inside the crossover zone:
    // above it the boost fades out and sigma is stock, so midtones and
    // highlights are untouched. The cap keeps the weights from degenerating
    // into a box blur at x -> 0.
    float nfRaw = max(max(cavg.r, max(cavg.g, cavg.b)), 0.0001);
    float xC = clamp(NOISEO/max(NOISES,0.000000001), 0.0001, 0.02);
    float deepFade = 1.0 - smoothstep(0.25*xC, 2.0*xC, nfRaw);
    float toneBoost = 1.0;
#if TONEAWARE == 1
    toneBoost = 1.0 + (min(1.0/nfRaw, 144.0) - 1.0) * deepFade * TONESTRENGTH;
#endif
    float sigY = max((NOISES*noisefactor + NOISEO) * toneBoost, 0.0000001);
    vec3 chromaDiff = (abs(cavg-cinX)+abs(cavg-cinY)+abs(cavg-cinXY)+abs(cavg-cin))/4.0;
    //chromaDiff *= (length(chromaDiff)/(length(chromaDiff)+sigY*64.0));
    chromaDiff *= max(abs(xDelta),abs(yDelta));
    float chromaNoise = max(chromaDiff.r,max(chromaDiff.g,chromaDiff.b))-min(chromaDiff.r,min(chromaDiff.g,chromaDiff.b));
    float sigZ = max(sigY,min(abs(chromaNoise)*MOIRE,0.2));
    //sigY += min(abs(chromaNoise)/32.0,0.2);
    // self-guided stable reference: 3x3 similarity-weighted mean around cin,
    // using the same robust weight shape and sigY as the main filter
    vec3 gref = vec3(0.0);
    float gW = 0.0;
    for (int i=-1; i <= 1; ++i) {
        for (int j=-1; j <= 1; ++j) {
            vec3 c = texelFetch(InputBuffer, xy+ivec2(i,j), 0).rgb;
            float dd = length(abs(c-cin));
            float ww = max(0.0, 1.0-dd*dd/(dd*dd + sigY));
            gref += c*ww;
            gW += ww;
        }
    }
    vec3 cref = gref/max(gW,0.0001);
    SteeredKernel kernel = SteeredKernel(xy, cin, cref, tng, coh, sigY, sigZ);
    FilterSum sum = FilterSum(cin * 0.01, cin * 0.01, 0.01, 0.01);
    filterWindow(kernel, sum);
    vec3 filtered = sum.luma / sum.lumaWeight;
    float br = dot(filtered, vec3(0.25,0.5,0.25));
    br = mix(dot(cin,vec3(0.25,0.5,0.25)),br,LUMA);
    // When moire does not widen sigma, luma and chroma use identical weights.
    vec3 resColour = sigZ == sigY ? filtered : sum.chroma / sum.chromaWeight;
    resColour /= max(1e-6,dot(resColour,vec3(0.25,0.5,0.25)));
    // no upper clamp: inpaint-opposed reconstruction emits scene-referred
    // values above 1.0 that must survive the denoise
    resColour = max(resColour*br,0.0);
    Output = vec4(resColour,1.0);
}
