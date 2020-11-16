#version 300 es
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform float size;
uniform float strength;
#import interpolation
#define MSIZE 3
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
float normpdf(in float x, in float sigma)
{
    return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;
}
float normpdf3(in vec3 v, in float sigma)
{
    return 0.39894*exp(-0.5*dot(v,v)/(sigma*sigma))/sigma;
}

out vec3 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec2 insize = vec2(textureSize(InputBuffer, 0))+0.5;
    //Output = textureBicubic(InputBuffer, vec2(gl_FragCoord.xy)/vec2(insize)).rgb;
    Output = texelFetch(InputBuffer, (xy), 0).rgb;
    const int kSize = (MSIZE-1)/2;
    float kernel[MSIZE];
    float mask = 0.0;
    float Z = 0.0;
    float sigX = 0.9;
    float sigY = 0.25;
    vec3 final_colour = vec3(0.0);
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
            cc = vec3(textureBicubic(InputBuffer, (gl_FragCoord.xy+vec2(i,j)*1.1)/vec2(insize)).rgb);
            factor = normpdf3(cc-Output, sigY)*bZ*kernel[kSize+j]*kernel[kSize+i];
            Z += factor;
            final_colour += factor*cc;

        }
    }
    if (Z < 0.0001f) {
        mask = luminocity(Output);
    } else {
        mask = luminocity(clamp(final_colour/Z,0.0,1.0));
    }
    mask = luminocity(Output)-mask;
    Output+=mask*strength*4.0;
}
