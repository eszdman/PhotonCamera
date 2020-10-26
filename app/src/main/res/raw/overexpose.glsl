#version 300 es

precision mediump float;
uniform sampler2D InputBuffer;
uniform float factor;
uniform vec3 neutralPoint;
out vec3 result;
uniform int yOffset;
#import sigmoid
#import xyztoxyy
#import xyytoxyz

void main() {
    ivec2 xyCenter = ivec2(gl_FragCoord.xy);
    xyCenter+=ivec2(0,yOffset);
    /*
    vec3 XYZ = texelFetch(InputBuffer, xyCenter, 0).xyz;
    vec3 xyY = XYZtoxyY(XYZ);
    xyY.z *= factor;
    //xyY.z = min(xyY.z, 1.f);

    xyY.z = sigmoid(xyY.z, 0.9f);
    result = xyYtoXYZ(xyY);*/
    result = clamp(texelFetch(InputBuffer, xyCenter, 0).xyz*factor,vec3(0.0),neutralPoint);
}
