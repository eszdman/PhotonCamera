#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform int yOffset;
out vec3 Output;
#define ModelKernel (1.5)
#define ModelK (10.0)
#define ModelSatRemove (3.0)
#define MSIZE1 7
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
vec3 getCol(in ivec2 xy){
    vec3 inp = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    if(length(inp) < 0.003) inp = vec3(0.0);
    //return inp/((inp.r+inp.g+inp.b+0.0001));
    return normalize(inp);
}
float stddev(vec3 XYZ) {
    float avg = (XYZ.x + XYZ.y + XYZ.z) / 3.f;
    vec3 diff = XYZ - avg;
    diff *= diff;
    return sqrt((diff.x + diff.y + diff.z) / 3.f);
}
float normpdf(in float x, in float sigma){return 0.39894*exp(-0.5*x*x/(sigma*sigma))/sigma;}
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    const int kSize = (MSIZE1-1)/2;
    float kernel[MSIZE1];
    vec3 mask = vec3(0.0);
    float pdfsize = 0.0;
    //vec3 lenp = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    //vec3 cur = getCol(xy);
    vec3 cur = vec3(texelFetch(InputBuffer, xy, 0).rgb);
    //float len = (lenp.r+lenp.g+lenp.b)/(cur.r+cur.g+cur.b);
    float len = cur.r+cur.g+cur.b;
    len/=3.0;
    for (int j = 0; j <= kSize; ++j) kernel[kSize+j] = kernel[kSize-j] = normpdf(float(j), ModelKernel);
    for (int i=-kSize; i <= kSize; ++i){
        for (int j=-kSize; j <= kSize; ++j){
            float pdf = kernel[kSize+j]*kernel[kSize+i];
            //mask+=getCol(xy+ivec2(i,j))*pdf;
            mask+=vec3(texelFetch(InputBuffer, xy+ivec2(i,j), 0).rgb)*pdf;
            pdfsize+=pdf;
        }
    }
    mask/=pdfsize;
    //vec3 outp = (cur-mask);
    vec3 outp = (cur/(cur.r+cur.g+cur.b)/3.0-mask/(mask.r+mask.g+mask.b)/3.0);
    //Output.r = clamp(float(outp.r+outp.g*0.5+outp.b)*len*10.0*(1.0-stddev(outp*len)*6.0),0.0,1.0);
    Output.g = luminocity(abs(cur-mask));
    Output.b = len;
    Output.r = luminocity(outp)*len*ModelK*clamp(1.0-stddev(mask)*ModelSatRemove,0.0,1.0);
    Output.r = abs(Output.r);
    //Output.r = clamp(float(outp.r+outp.g*0.5+outp.b)*len*10.0*(1.0-stddev(outp*len)*6.0),0.0,1.0);
}
