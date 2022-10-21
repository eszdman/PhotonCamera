
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform float size;
uniform float strength;
#import interpolation
#define mtrxSize 3
#define luminocity(x) dot(x.rgb, vec3(0.299, 0.587, 0.114))
float neighborhoods[mtrxSize*mtrxSize];
void sortNeighborhoods()
{
    for(int i = 1; i < mtrxSize*mtrxSize; i++)
    {
        for (int j=0; j < (mtrxSize*mtrxSize -1); j++)
        {
            if (neighborhoods[j+1] > neighborhoods[j])
            {
                float temp = neighborhoods[j];
                neighborhoods[j] = neighborhoods[j+1];
                neighborhoods[j+1] = temp;
            }
        }
    }
}
out vec3 Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    vec2 insize = vec2(textureSize(InputBuffer, 0));
    Output = textureBicubic(InputBuffer, vec2(gl_FragCoord.xy)/vec2(insize)).rgb;
    for(int dX = -1; dX <= 1; ++dX) {
        for (int dY = -1; dY <= 1; ++dY) {
            ivec2 offset = ivec2((dX), (dY));
            neighborhoods[(dX + 1) * mtrxSize + (dY + 1)] = luminocity(textureBicubic(InputBuffer, (gl_FragCoord.xy+vec2(offset)*size*4.0)/vec2(insize)).rgb);
        }
    }
    sortNeighborhoods();
    float mask = luminocity(Output)-neighborhoods[(mtrxSize*mtrxSize)/2];
    Output+=mask*strength;
}
