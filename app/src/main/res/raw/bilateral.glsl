#version 300 es
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D NoiseMap;
uniform ivec2 size;
uniform vec2 mapsize;
uniform int yOffset;
out vec4 Output;

#define SIGMA 10.0
#define BSIGMA 0.1
#define MSIZE 7
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
float normpdf(in float x, in float sigma)
{
    return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;
}

float normpdf3(in vec3 v, in float sigma)
{
    return 0.39894*exp(-0.5*dot(v,v)/(sigma*sigma))/sigma;
}
float lum(in vec4 color) {
    return length(color.xyz);
}

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec3 c = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    float noisefactor = texture(NoiseMap, vec2(xy)/vec2(INSIZE)).g;
    {
        //declare stuff
        //const int kSize = (MSIZE-1)/2;
        float kernel[MSIZE];
        vec3 final_colour = vec3(0.0);
        //float sigX = sigma.x;
        //float sigY = sigma.y;
        float sigX = 3.5;
        vec3 brp =
         vec3(texelFetch(InputBuffer, xy+ivec2(-1,0), 0).rgb)+
         vec3(texelFetch(InputBuffer, xy+ivec2(1,0), 0).rgb)+
         vec3(texelFetch(InputBuffer, xy+ivec2(0,-1), 0).rgb)+
         vec3(texelFetch(InputBuffer, xy+ivec2(0,1), 0).rgb)+
         c*2.0;
        float br = (length(brp))/6.;
        br = clamp(br,NRcancell,1.0);
        br = 1.0-br;
        //sigX*=br/(1.0-NRcancell);
        //sigX*=((NRcancell-br)*(NRcancell-br))/(NRcancell*NRcancell);
        //sigX =clamp(sigX+NRshift,minNR,maxNR);

        float sigY = sqrt(noisefactor*NOISES*(INTENSE*2.2 + 1.0)/2.0 + NOISEO*INTENSE*2.2);
        //sigY = max(0.01,sigY);
        //create the 1-D kernel
        float Z = 0.0;
        for (int j = 0; j <= KSIZE; ++j)
        {
            kernel[KSIZE+j] = kernel[KSIZE-j] = normpdf(float(j), sigX);
        }
        vec3 cc;
        float factor;
        float bZ = 1.0/normpdf(0.0, sigY);
        //read out the texels
        for (int i=-KSIZE; i <= KSIZE; ++i)
        {
            for (int j=-KSIZE; j <= KSIZE; ++j)
            {
                cc = vec3(texelFetch(InputBuffer, xy+ivec2(i,j), 0).rgb);
                factor = normpdf3(cc-c, sigY)*bZ*kernel[KSIZE+j]*kernel[KSIZE+i];
                Z += factor;
                final_colour += factor*cc;

            }
        }
        if (Z < 0.0001f) {
            Output = vec4(c,1.0);
        } else {
            Output = vec4(clamp(final_colour/Z,0.0,1.0),1.0);
        }
        //vec4 test = vec4(texture(NoiseMap, vec2(xy)/mapsize).r);
        //test = clamp(test*1.0,0.0,1.0);
        //Output = test;
    }
}