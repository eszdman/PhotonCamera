#version 300 es
#define mtrxSize 5
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform ivec2 tpose;
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
uniform int yOffset;
void main()
{
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    for(int dX = -2; dX <= 2; ++dX) {
        for (int dY = -2; dY <= 2; ++dY) {
            ivec2 offset = ivec2((dX), (dY));
            neighborhoods[(dX + 2) * mtrxSize + (dY + 2)] = (vec3(texelFetch(InputBuffer, xy + offset*tpose, 0).rgb)).r;
        }
    }
    sortNeighborhoods();

    Output = vec3(neighborhoods[(mtrxSize*mtrxSize)/2]);
}