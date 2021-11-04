
precision highp float;
precision highp sampler2D;
uniform sampler2D InputBuffer;
uniform sampler2D BaseBuffer;

out vec4 Output;
//Generate smaller centers for InputBuffer
void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);

}
