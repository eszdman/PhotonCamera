precision highp float;
// esd3d2_steered_prod: consensus weights + guided 3x3 reference + tensor
// steering. A tap must match BOTH the raw center (strict -> keeps edges
// sharp) and the self-guided reference (stable -> no tap-selection flips);
// the two robust weights are multiplied. Zero new tunables.
// Requires GradBuffer (gx, gy) in .rg.
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
#define MSIZE 15
#define KSIZE (MSIZE-1)/2
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
    vec3 final_colour = vec3(0.0);
    vec3 final_colour2 = vec3(0.0);
    float sigX = 2.5;
    float sigY = max(NOISES*noisefactor + NOISEO, 0.0000001);
    //float sigY = max(NOISES*noisefactor + NOISES*NOISES * 3.0/8.0 + NOISEO, 0.0000001);
    vec3 chromaDiff = (abs(cavg-cinX)+abs(cavg-cinY)+abs(cavg-cinXY)+abs(cavg-cin))/4.0;
    //chromaDiff *= (length(chromaDiff)/(length(chromaDiff)+sigY*64.0));
    chromaDiff *= max(abs(xDelta),abs(yDelta));
    float chromaNoise = max(chromaDiff.r,max(chromaDiff.g,chromaDiff.b))-min(chromaDiff.r,min(chromaDiff.g,chromaDiff.b));
    float sigZ = max(sigY,min(abs(chromaNoise)*MOIRE,0.2));
    //sigY += min(abs(chromaNoise)/32.0,0.2);
    float Z = 0.01f;
    float Z2 = 0.01f;
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
    final_colour += cin*Z;
    final_colour2 += cin*Z;
    //sigY /= 25.0;
    // Use hybrid SNN filtering to denoise the image
    //vec3 cc[4];
    for (int i=0; i <= KSIZE; ++i)
    {
        for (int j=0; j <= KSIZE; ++j)
        {
            ivec2 pos = ivec2(i,j);
            ivec2 pos2 = ivec2(-i,-j);
            ivec2 pos3 = ivec2(i,-j);
            ivec2 pos4 = ivec2(-i,j);
            vec3 cc0 = vec3(texelFetch(InputBuffer, xy+pos, 0).rgb);
            vec3 cc1 = vec3(texelFetch(InputBuffer, xy+pos2, 0).rgb);
            vec3 cc2 = vec3(texelFetch(InputBuffer, xy+pos3, 0).rgb);
            vec3 cc3 = vec3(texelFetch(InputBuffer, xy+pos4, 0).rgb);
            // Consensus weights: raw center x self-guided reference
            vec4 d = vec4(length(abs(cc0-cref)),length(abs(cc1-cref)),length(abs(cc2-cref)),length(abs(cc3-cref)));
            vec4 d0 = vec4(length(abs(cc0-cin)),length(abs(cc1-cin)),length(abs(cc2-cin)),length(abs(cc3-cin)));
            vec4 w = (1.0-d*d/(d*d + sigY))*(1.0-d0*d0/(d0*d0 + sigY));
            vec4 w2 = (1.0-d*d/(d*d + sigZ))*(1.0-d0*d0/(d0*d0 + sigZ));
            float wm = min(min(min(w[0],w[1]),w[2]),w[3])*1.0;
            vec4 ws = w - wm;
            ws /= length(ws) + 0.000001;
            vec4 w2s = w2 - wm;
            w2s /= length(w2s) + 0.000001;
            w *= ws;
            w2 *= w2s;
            float ut = float(i)*tng.x + float(j)*tng.y;
            float f1 = mix(normpdf(float(i),KERNELSIZE)*normpdf(float(j),KERNELSIZE),
                           normpdf(ut,KERNELSIZE), coh);
            final_colour += f1*mat4x3(cc0,cc1,cc2,cc3)*w;
            final_colour2 += f1*mat4x3(cc0,cc1,cc2,cc3)*w2;
            Z += dot(vec4(f1),w);
            Z2 += dot(vec4(f1),w2);
        }
    }

    //if (Z <= 0.002f) {
    //    Output = vec4(cin,1.0);
    //} else {
    float br = dot(final_colour/Z,vec3(0.25,0.5,0.25));
    br = mix(dot(cin,vec3(0.25,0.5,0.25)),br,LUMA);
    vec3 resColour = final_colour2/Z2;
    resColour /= max(1e-6,dot(resColour,vec3(0.25,0.5,0.25)));
    // no upper clamp: inpaint-opposed reconstruction emits scene-referred
    // values above 1.0 that must survive the denoise
    resColour = max(resColour*br,0.0);
    Output = vec4(resColour,1.0);
    //Output = vec4(final_colour/Z,1.0);
    //}
}
