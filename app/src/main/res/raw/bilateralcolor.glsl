#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform int size;
uniform vec2 sigma;
uniform int yOffset;
out vec4 Output;

#define SIGMA 10.0
#define BSIGMA 0.1
#define MSIZE 5
#define TRANSPOSE 3
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
vec3 getCol(in ivec2 xy){
    vec3 inp = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    return inp/((inp.r+inp.g+inp.b+0.0001));
    //return inp;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec3 c = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    float clen = c.r+c.g+c.b+0.0001;
    {
        //declare stuff
        const int kSize = (MSIZE-1)/2;
        float kernel[MSIZE];
        vec3 final_colour = vec3(0.0);
        float sigX = sigma.x;
        vec3 brp = vec3(texelFetch(InputBuffer, xy+ivec2(-1,0), 0).rgb)+vec3(texelFetch(InputBuffer, xy+ivec2(1,0), 0).rgb)+
        vec3(texelFetch(InputBuffer, xy+ivec2(0,-1), 0).rgb)+vec3(texelFetch(InputBuffer, xy+ivec2(0,1), 0).rgb)+c*2.0;
        float br = length(brp)/7.;
        br = clamp(br,0.,1.0);
        sigX*=(1.0-br)*(1.0-br);
        sigX+=0.7;
        sigX = clamp(sigX,0.5,5.);
        //create the 1-D kernel
        float Z = 0.0;

        //c/=clen;

        for (int j = 0; j <= kSize; ++j)
        {
            kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), sigX);
        }
        vec3 cc;
        float factor;
        float bZ = 1.0/normpdf(0.0, sigma.y);
        //read out the texels
        for (int i=-kSize; i <= kSize; ++i)
        {
            for (int j=-kSize; j <= kSize; ++j)
            {
                cc = getCol(xy+ivec2(i*TRANSPOSE,j*TRANSPOSE));
                factor = normpdf3(cc-c, sigma.y)*bZ*kernel[kSize+j]*kernel[kSize+i];
                Z += factor;
                final_colour += factor*cc;

            }
        }
        Output = vec4(final_colour/Z, 1.0)*clen;
    }
}