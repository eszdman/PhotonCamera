#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D NoiseMap;
uniform int size;
uniform vec2 mapsize;
uniform vec2 sigma;
uniform int yOffset;
out vec4 Output;

#define SIGMA 10.0
#define BSIGMA 0.1
#define MSIZE 7
#define TRANSPOSE 1
#define NRcancell (0.88)
#define NRshift (+0.6)
#define maxNR (7.)
#define minNR (0.2)
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
    float noisefactor = texture(NoiseMap, vec2(xy)/mapsize).r;
    {
        //declare stuff
        const int kSize = (MSIZE-1)/2;
        float kernel[MSIZE];
        vec3 final_colour = vec3(0.0);
        //float sigX = sigma.x;
        //float sigY = sigma.y;
        float sigX = noisefactor*7.0;
        vec3 brp =
         vec3(texelFetch(InputBuffer, xy+ivec2(-1,0), 0).rgb)+
         vec3(texelFetch(InputBuffer, xy+ivec2(1,0), 0).rgb)+
         vec3(texelFetch(InputBuffer, xy+ivec2(0,-1), 0).rgb)+
         vec3(texelFetch(InputBuffer, xy+ivec2(0,1), 0).rgb)+
         c*2.0;
        float br = (length(brp))/6.;
        br = clamp(br,NRcancell,1.0);
        br = 1.0-br;
        sigX*=br/(1.0-NRcancell);
        //sigX*=((NRcancell-br)*(NRcancell-br))/(NRcancell*NRcancell);
        sigX =clamp(sigX+NRshift,minNR,maxNR);
        float sigY = sigX*1.2;
        //create the 1-D kernel
        float Z = 0.0;
        for (int j = 0; j <= kSize; ++j)
        {
            kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), sigX);
        }
        vec3 cc;
        float factor;
        float bZ = 1.0/normpdf(0.0, sigY);
        //read out the texels
        for (int i=-kSize; i <= kSize; ++i)
        {
            for (int j=-kSize; j <= kSize; ++j)
            {
                cc = vec3(texelFetch(InputBuffer, xy+ivec2(i*TRANSPOSE,j*TRANSPOSE), 0).rgb);
                factor = normpdf3(cc-c, sigY)*bZ*kernel[kSize+j]*kernel[kSize+i];
                Z += factor;
                final_colour += factor*cc;

            }
        }
        Output = vec4(final_colour/Z, 1.0);
        //vec4 test = vec4(texture(NoiseMap, vec2(xy)/mapsize).r);
        //test = clamp(test*1.0,0.0,1.0);
        //Output = test;
    }
}