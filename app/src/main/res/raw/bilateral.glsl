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
#define MSIZE 7
#define TRANSPOSE 1
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
    {
        //declare stuff
        const int kSize = (MSIZE-1)/2;
        float kernel[MSIZE];
        vec3 final_colour = vec3(0.0);
        float sigX = sigma.x;
        vec3 brp = vec3(texelFetch(InputBuffer, xy+ivec2(-1,0), 0).rgb)+vec3(texelFetch(InputBuffer, xy+ivec2(1,0), 0).rgb)+
        vec3(texelFetch(InputBuffer, xy+ivec2(0,-1), 0).rgb)+vec3(texelFetch(InputBuffer, xy+ivec2(0,1), 0).rgb)+c;
        float br = (length(brp))/4.;
        br = clamp(br,0.,0.6);
        sigX*=(0.6-br)*(0.6-br);
        sigX+=1.0;
        //create the 1-D kernel
        float Z = 0.0;
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
                cc = vec3(texelFetch(InputBuffer, xy+ivec2(i*TRANSPOSE,j*TRANSPOSE), 0).rgb);
                factor = normpdf3(cc-c, sigma.y)*bZ*kernel[kSize+j]*kernel[kSize+i];
                Z += factor;
                final_colour += factor*cc;

            }
        }


        Output = vec4(final_colour/Z, 1.0);
    }
}





/*


    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    vec2 texc = vec2(xy);
    float sigS = sigma.x;
    float sigL = sigma.y;
    float facS = -1./(2.*sigS*sigS);
    float facL = -1./(2.*sigL*sigL);
    float sumW = 0.;
    float halfSize = sigS*2.;
    vec4  sumC = vec4(0.);
    float dist;
    for (float i = (-halfSize); i <= halfSize; i++){
        for (float j = (-halfSize); j <= halfSize; j++){
            vec2 pos = vec2(i, j);
            vec4 offsetColor =vec4(texture(InputBuffer, (texc+pos)/size));
            //dist = clamp((i*i+j*j)/(halfSize*halfSize),0.,1.);
            float distS = clamp((i*i+j*j)/(halfSize*halfSize),0.,1.);
            float distL = lum(offsetColor);

            float wS = exp(facS*float(distS*distS));
            float wL = exp(facL*float(distL*distL));
            float w = wS*wL;

            sumW += w;
            sumC += offsetColor * w;
        }
    }
    if (sumW < 0.0001f) {
        Output = texelFetch(InputBuffer, xy, 0);
    }
    Output = sumC/sumW;
}
*/