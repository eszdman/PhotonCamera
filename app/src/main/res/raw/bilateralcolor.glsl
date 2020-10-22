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
#define TRANSPOSE 2
#define NRcancell (0.7)
#define NRshift (1.2)
#define maxNR (7.)
#define minNR (0.5)
#import xyztoxyy
#import xyytoxyz
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
vec4 getCol(in ivec2 xy){
    vec3 inp = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    inp+=0.001;
    return vec4((inp)/((inp.r+inp.g+inp.b)),((inp.r+inp.g+inp.b)));
    //return normalize(inp);
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    //vec3 c = XYZtoxyY(vec3(texelFetch(InputBuffer, xy, 0).rgb));
    vec4 c = getCol(xy);
    c+=0.001;
    //float clen = (c.r+c.g+c.b);
    float noisefactor = texture(NoiseMap, vec2(xy)/mapsize).r;
    {
        //declare stuff
        const int kSize = (MSIZE-1)/2;
        float kernel[MSIZE];
        vec3 final_colour = vec3(0.0);
        //float sigX = sigma.x*1.5;
        //float sigY = sigma.y*1.5;
        vec3 brp =
         vec3(texelFetch(InputBuffer, xy+ivec2(-1,0), 0).rgb)+
         vec3(texelFetch(InputBuffer, xy+ivec2(1,0), 0).rgb)+
         vec3(texelFetch(InputBuffer, xy+ivec2(0,-1), 0).rgb)+
         vec3(texelFetch(InputBuffer, xy+ivec2(0,1), 0).rgb)+
         c.a*2.0/3.0;
        //float br = length(brp)/7.;
        //br = clamp(br,0.,1.0);
        float sigX = noisefactor*45.0;
        //sigX = 5.0;
        int transposing = int(sigX/20.0)+1;
        //int transposing = 1;
        //sigX*=(1.0-br)*(1.0-br);
        sigX = clamp(sigX+NRshift,minNR,maxNR);
        //create the 1-D kernel
        float Z = 0.0;
        float sigY = sigX*2.0;
        for (int j = 0; j <= kSize; ++j)
        {
            kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), sigX);
        }
        vec4 cc;
        float factor;
        float bZ = 1.0/normpdf(0.0, sigY);
        //read out the texels
        for (int i=-kSize; i <= kSize; ++i)
        {
            for (int j=-kSize; j <= kSize; ++j)
            {
                //cc = XYZtoxyY(vec3(texelFetch(InputBuffer, xy+ivec2(i*transposing,j*transposing), 0).rgb));
                cc = getCol(xy+ivec2(i*transposing,j*transposing));
                factor = normpdf(cc.a-c.a, sigY)*bZ*kernel[kSize+j]*kernel[kSize+i];
                Z += factor;
                final_colour += factor*cc.rgb;

            }
        }
        Output = vec4(clamp(c.a*final_colour/Z,0.0,1.0),1.0);
        //Output = vec4(xyYtoXYZ(vec3(clamp(final_colour.rg/Z,0.0,1.0),c.z)),1.0);
    }
}