#version 300 es
/*
3x3 Median
Morgan McGuire and Kyle Whitson
http://graphics.cs.williams.edu


Copyright (c) Morgan McGuire and Williams College, 2006
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
precision mediump float;
precision mediump sampler2D;
// Input texture
uniform sampler2D InputBuffer;
uniform int CfaPattern;
uniform vec3 WhitePoint;
uniform float whitelevel;
uniform int yOffset;
#define s2(a, b)				temp = a; a = min(a, b); b = max(temp, b);
#define mn3(a, b, c)			s2(a, b); s2(a, c);
#define mx3(a, b, c)			s2(b, c); s2(a, c);

#define mnmx3(a, b, c)			mx3(a, b, c); s2(a, b);                                   // 3 exchanges
#define mnmx4(a, b, c, d)		s2(a, b); s2(c, d); s2(a, c); s2(b, d);                   // 4 exchanges
#define mnmx5(a, b, c, d, e)	s2(a, b); s2(c, d); mn3(a, c, e); mx3(b, d, e);           // 6 exchanges
#define mnmx6(a, b, c, d, e, f) s2(a, d); s2(b, e); s2(c, f); mn3(a, b, c); mx3(d, e, f); // 7 exchanges
out uint Output;
void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    int fact1 = (xy.x-CfaPattern%2)%2;
    int fact2 = (xy.y-CfaPattern/2)%2;
    float v[9];
    // Add the pixels which make up our window to the pixel array.
    if(fact1+fact2 == 1){
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
                // If a pixel in the window is located at (x+dX, y+dY), put it at index (dX + R)(2R + 1) + (dY + R) of the
                // pixel array. This will fill the pixel array, with the top left pixel of the window at pixel[0] and the
                // bottom right pixel of the window at pixel[N-1].
                v[(dX + 1) * 3 + (dY + 1)] = float(texelFetch(InputBuffer, xy + offset*2, 0).r);
            }
        }
    }
    float avr = (v[0]+v[1]+v[2]+v[3]+v[5]+v[6]+v[7]+v[8])/8.0;
    float balance;
    ivec2 fact = (xy-ivec2(CfaPattern%2,CfaPattern/2))%2;
    if(fact.x+fact.y == 1){
        balance = WhitePoint.g;
    } else {
        if(fact.x == 0){
            balance = WhitePoint.r;
        } else {
            balance = WhitePoint.b;
        }
    }
    if(v[4]*0.7 > avr){
        float temp;
        // Starting with a subset of size 6, remove the min and max each time
        mnmx6(v[0], v[1], v[2], v[3], v[4], v[5]);
        mnmx5(v[1], v[2], v[3], v[4], v[6]);
        mnmx4(v[2], v[3], v[4], v[7]);
        mnmx3(v[3], v[4], v[8]);
    }
    Output = uint(v[4]*whitelevel);
}