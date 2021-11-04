precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
//uniform sampler2D NoiseMap;
uniform int size;
uniform vec2 mapsize;
uniform vec2 sigma;
uniform int yOffset;
out vec3 Output;
#define SIGMA 10.0
#define BSIGMA 0.1
#define MSIZE 7
#define TRANSPOSE 2
#define NRcancell (0.7)
#define NRshift (1.2)
#define maxNR (7.)
#define minNR (0.5)
#define NOISES 0.0
#define NOISEO 0.0
#import xyztoxyy
#import xyytoxyz
#import gaussian
vec4 getCol(in ivec2 xy){
    vec3 inp = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    return vec4((inp)/((inp.g+0.0001)),((inp.g+0.0001)));
    //return normalize(inp);
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    //vec3 c = XYZtoxyY(vec3(texelFetch(InputBuffer, xy, 0).rgb));
    Output = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    //if(xy.x%2 == 0 && xy.y%2 == 0){
        vec4 c = getCol(xy);
        //float clen = (c.r+c.g+c.b);
        //float noisefactor = texture(NoiseMap, vec2(xy)/mapsize).r;
        {
            //declare stuff
            const int kSize = (MSIZE-1)/2;
            vec3 final_colour = vec3(0.0);

            //float sigX = noisefactor*45.0;

            //sigX = 5.0;
            //int transposing = 1;
            //sigX*=(1.0-br)*(1.0-br);

            //sigX = clamp(sigX+NRshift,minNR,maxNR);

            vec3 Z = vec3(0.0);
            float sigY = abs(sqrt(Output.g*NOISES + NOISEO)*2.0)+0.00001;

            float sigX = 3.0+sigY;
            vec4 cc;
            vec3 factor;
            //read out the texels
            for (int i=-kSize; i <= kSize; ++i)
            {
                float pdf0 = pdf(float(i)/float(sigX));
                for (int j=-kSize; j <= kSize; ++j)
                {
                    //cc = XYZtoxyY(vec3(texelFetch(InputBuffer, xy+ivec2(i*transposing,j*transposing), 0).rgb));
                    cc = getCol(xy+ivec2(i, j));
                    factor = pdf3((cc.rgb-c.rgb)/sigY)*pdf(float(j)/float(sigX))*pdf0;

                    Z += factor;
                    final_colour += factor*cc.rgb;
                }
            }
            Output.rb = (clamp(c.a*final_colour.rb/(Z.rb+0.0001), 0.0, 1.0));
            //Output = (clamp(c.a*final_colour/(Z+0.0001), 0.0, 1.0));
            //Output = vec4(xyYtoXYZ(vec3(clamp(final_colour.rg/Z,0.0,1.0),c.z)),1.0);
        }
    //}
}