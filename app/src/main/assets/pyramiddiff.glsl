precision highp float;
precision highp sampler2D;
uniform sampler2D target;
uniform sampler2D base;
uniform ivec2 size;
out vec4 result;
#import interpolation

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    result = texelFetch(target, xyCenter, 0) - textureBicubic(base, vec2(gl_FragCoord.xy)/vec2(size));
}
