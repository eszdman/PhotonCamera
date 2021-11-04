precision highp float;
precision highp sampler2D;
// Input texture
uniform sampler2D InputBuffer;
out vec3 Output;
#define TRANSPOSE (1,1)
#define MEDSIZE 3
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
}