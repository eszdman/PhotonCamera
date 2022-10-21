
precision highp float;
precision mediump sampler2D;
// Input texture
uniform sampler2D InputBuffer;
uniform int CfaPattern;
uniform int whitelevel;
uniform int yOffset;
out uint Output;
#import median
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    ivec2 fact = (xy-ivec2(CfaPattern%2,CfaPattern/2))%2;
    float v[9];
    // Add the pixels which make up our window to the pixel array.
    if(fact.x+fact.y == 1){
        v[4] = float(texelFetch(InputBuffer, xy + ivec2(0,0), 0).r);
        v[1] = float(texelFetch(InputBuffer, xy + ivec2(1,1), 0).r);
        v[2] = float(texelFetch(InputBuffer, xy + ivec2(-1,-1), 0).r);
        v[3] = float(texelFetch(InputBuffer, xy + ivec2(1,-1), 0).r);
        v[0] = float(texelFetch(InputBuffer, xy + ivec2(-1,1), 0).r);
        v[5] = float(texelFetch(InputBuffer, xy + ivec2(0,2), 0).r);
        v[6] = float(texelFetch(InputBuffer, xy + ivec2(2,0), 0).r);
        v[7] = float(texelFetch(InputBuffer, xy + ivec2(0,-2), 0).r);
        v[8] = float(texelFetch(InputBuffer, xy + ivec2(-2,0), 0).r);
    } else {
        for (int dX = -1; dX <= 1; ++dX) {
            for (int dY = -1; dY <= 1; ++dY) {
                ivec2 offset = ivec2((dX), (dY));
                v[(dX + 1) * 3 + (dY + 1)] = float(texelFetch(InputBuffer, xy + offset*2, 0).r);
            }
        }
    }
    float avr = (v[0]+v[1]+v[2]+v[3]+v[5]+v[6]+v[7]+v[8])/8.0;
    float inp = v[4];
    if(inp*0.7 > avr){
        // Starting with a subset of size 6, remove the min and max each time
        inp = median9(v);
    }
    Output = uint(inp*float(whitelevel));
}