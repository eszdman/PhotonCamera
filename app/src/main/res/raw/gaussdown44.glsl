#version 300 es
precision mediump float;
precision mediump sampler2D;
uniform sampler2D InputBuffer;
uniform int yOffset;
out float Output;
#define size1 (1.9)
#define MSIZE1 5
#define resize (4)

// Sigma 1.36
float gauss[25] = float[](
0.01193f, 0.025908f, 0.033547f, 0.025908f, 0.01193f,
0.025908f, 0.056266f, 0.072856f, 0.056266f, 0.025908f,
0.033547f, 0.072856f, 0.094337f, 0.072856f, 0.033547f,
0.025908f, 0.056266f, 0.072856f, 0.056266f, 0.025908f,
0.01193f, 0.025908f, 0.033547f, 0.025908f, 0.01193f
);

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    xy+=ivec2(0,yOffset);
    xy*=resize;
    float val = 0.f;
    for (int i = 0; i < 25; i++) {
        ivec2 xyp = xy + ivec2((i % 5) - 2, (i / 5) - 2);
        val += gauss[i] * texelFetch(InputBuffer, xyp, 0).x;
    }
    Output = val;
}
