//
// Created by eszdman on 21.11.2026.
//

uniform sampler2D InputBuffer;
uniform ivec2 size;
out vec4 Output;
#import interpolation
void main() {
    Output = textureBicubicHardware(InputBuffer, gl_FragCoord.xy / vec2(size));
}
