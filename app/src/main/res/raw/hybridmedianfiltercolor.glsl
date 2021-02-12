#version 300 es
precision mediump float;
precision mediump sampler2D;
// Input texture
uniform sampler2D InputBuffer;
out vec3 Output;
uniform int yOffset;
#define TRANSPOSE (1,1)
#define MEDSIZE 3
#define MSIZE 3
#define kSize ((MSIZE-1)/2)
//#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
//#define luminocity(x) x.g
#define luminocity(x) dot(x.rgb, vec3(0.2, 0.8, 0.1))
#define normalized(x) t=x;t/=luminocity(t)
#import median
float normpdf(in float x, in float sigma)
{
    return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;
}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    //Get center pixel
    Output = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    if(xy.x%2 == 0 && xy.y%2 == 0){
        xy+=ivec2(0, yOffset);
        // Add the pixels which make up our window to the pixel array.
        float pdfsize = 0.0;
        #if MEDSIZE == 3
        vec3 v[9];
        for (int dX = -1; dX <= 1; ++dX) {
            for (int dY = -1; dY <= 1; ++dY) {
                ivec2 offset = ivec2((dX), (dY));
                vec3 inp = vec3(texelFetch(InputBuffer, xy + offset*ivec2(TRANSPOSE), 0).rgb+0.00001);
                inp/=luminocity(inp);
                v[(dX + 1) * 3 + (dY + 1)] = inp;
            }
        }
        Output = median9(v)*luminocity(Output);
        #endif
        #if MEDSIZE == 4
        vec4 v[7];
        /*for (int dX = -1; dX <= 1; ++dX) {
            for (int dY = -1; dY <= 1; ++dY) {
                ivec2 offset = ivec2((dX), (dY));
                vec3 inp = vec3(texelFetch(InputBuffer, xy + offset*ivec2(TRANSPOSE), 0).rgb)+0.00001;
                inp/=inp.g;
                if((offset.x+offset.y)%2 == 0){
                    v[(dX + 1) * 3 + (dY + 1)].rg = inp.rg;
                } else {
                    v[(dX + 1) * 3 + (dY + 1)].ba = inp.rg;
                }
            }
        }*/
        vec3 t;
        normalized(vec3(texelFetch(InputBuffer, xy + ivec2(-2,-2), 0).rgb));
        v[0].rg = t.rb;
        normalized(vec3(texelFetch(InputBuffer, xy + ivec2(-4,-2), 0).rgb));
        v[0].ba = t.rb;
        normalized(vec3(texelFetch(InputBuffer, xy + ivec2(-2,0), 0).rgb));
        v[1].rg = t.rb;
        normalized(vec3(texelFetch(InputBuffer, xy + ivec2(-2,2), 0).rgb));
        v[1].ba = t.rb;
        normalized(vec3(texelFetch(InputBuffer, xy + ivec2(0,-2), 0).rgb));
        v[2].rg = t.rb;
        normalized(vec3(texelFetch(InputBuffer, xy + ivec2(0,0), 0).rgb));
        v[2].ba = t.rb;
        normalized(vec3(texelFetch(InputBuffer, xy + ivec2(0,2), 0).rgb));
        v[3].rg = t.rb;
        normalized(vec3(texelFetch(InputBuffer, xy + ivec2(0,4), 0).rgb));
        v[3].ba = t.rb;
        normalized(vec3(texelFetch(InputBuffer, xy + ivec2(2,-2), 0).rgb));
        v[4].rg = t.rb;
        normalized(vec3(texelFetch(InputBuffer, xy + ivec2(2,0), 0).rgb));
        v[4].ba = t.rb;
        normalized(vec3(texelFetch(InputBuffer, xy + ivec2(2,2), 0).rgb));
        v[5].rg = t.rb;
        normalized(vec3(texelFetch(InputBuffer, xy + ivec2(2,-4), 0).rgb));
        v[5].ba = t.rb;
        normalized(vec3(texelFetch(InputBuffer, xy + ivec2(0,-4), 0).rgb));
        v[6].rg = t.rb;
        normalized(vec3(texelFetch(InputBuffer, xy + ivec2(0,4), 0).rgb));
        v[6].ba = t.rg;
        vec4 outp = median7(v);
        outp.rg = min(outp.rg,outp.ba);
        Output.rb = outp.rg*luminocity(Output);
        #endif
    }
}