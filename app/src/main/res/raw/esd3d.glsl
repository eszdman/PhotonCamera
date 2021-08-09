#version 300 es
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D GradBuffer;
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
#define PI 3.1415926535897932384626433832795
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
    vec3 cin = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    float noisefactor = texture(NoiseMap, vec2(xy)/vec2(INSIZE)).g;
    {
        float kernel[MSIZE];
        vec3 final_colour = vec3(0.0);
        float sigX = 3.5;

        float sigY = sqrt(noisefactor*NOISES*(INTENSE*2.2 + 1.0)/2.0 + NOISEO*INTENSE*2.2);
        //sigY = max(0.01,sigY);
        //create the 1-D kernel
        float Z = 0.0;
        vec2 dxy;
        vec2 dxyabs;
        float sum = 0.0001;
        for (int i=-KSIZE; i <= KSIZE; i++){
            float k0 = normpdf(float(i),sigX);
            for (int j=-KSIZE; j <= KSIZE; j++){
                float k = normpdf(float(j),sigX)*k0;
                vec2 temp = k*texelFetch(GradBuffer, xy+ivec2(i,j), 0).rg;
                dxy+=temp;
                dxyabs+=abs(temp);
                sum+=k;
            }
        }
        dxy/=sum;
        dxyabs/=sum;
        float length = sqrt(dxy.x*dxy.x + dxy.y*dxy.y);
        float length2 = sqrt(dxyabs.x*dxyabs.x + dxyabs.y*dxyabs.y);
        float angle = atan(dxy.y,dxy.x)+PI;
        float width = 1.0/3.0 + normpdf(length,sigX);
        vec3 cc;
        float factor;
        float sino = sin(angle);
        sino*=sino;
        float coso = cos(angle);
        coso*=coso;
        float sin2o = sin(angle*2.0);
        vec2 sigo = vec2(3.5*1.5,3.5/2.35);
        //sigo*=2.0;
        //sigo = mix(vec2(3.5,3.5),sigo,
        //min(length2,1.0)
        //1.0
        //);
        sigo*=sigo;

        float a = (coso)/(2.0*sigo.x) + (sino)/(2.0*sigo.y);
        float b = -(sin2o)/(4.0*sigo.x) + (sin2o)/(4.0*sigo.y);
        float c = (sino)/(2.0*sigo.x) + (coso)/(2.0*sigo.y);
        //read out the texels
        for (int i=-KSIZE; i <= KSIZE; ++i)
        {
            for (int j=-KSIZE; j <= KSIZE; ++j)
            {
                cc = vec3(texelFetch(InputBuffer, xy+ivec2(i,j), 0).rgb);
                factor = normpdf3(cc-cin, sigY)*
                exp(-(a*float(i*i) + 2.0*b*float(i*j) + c*float(j*j)))
                ;
                Z += factor;
                final_colour += factor*cc;
            }
        }

        if (Z < 0.0001f) {
            Output = vec4(cin,1.0);
        } else {
            Output = vec4(clamp(final_colour/Z,0.0,1.0),1.0);
        }
        //Output = vec4(length2);
        //vec4 test = vec4(texture(NoiseMap, vec2(xy)/mapsize).r);
        //test = clamp(test*1.0,0.0,1.0);
        //Output = test;
    }
}