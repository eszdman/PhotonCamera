//Hue to RGB (red, green, blue).
//Source: https://github.com/tobspr/GLSL-Color-Spaces/blob/master/ColorSpaces.inc.glsl
#ifndef saturate
#define saturate(v) clamp(v,0.,1.)
//      clamp(v,0.,1.)
#endif
vec3 hue2rgb(float hue){
    hue=fract(hue);
    return saturate(vec3(
    abs(hue*6.-3.)-1.,
    2.-abs(hue*6.-2.),
    2.-abs(hue*6.-4.)
    ));
}


//HSL to RGB.
//Source: https://github.com/Jam3/glsl-hsl2rgb/blob/master/index.glsl
vec3 hsltorgb(vec3 hsl){
    if(hsl.y==0.){
        return vec3(hsl.z); //Luminance.
    }else{
        float b;
        if(hsl.z<.5){
            b=hsl.z*(1.+hsl.y);
        }else{
            b=hsl.z+hsl.y-hsl.y*hsl.z;
        }
        float a=2.*hsl.z-b;
        return a+hue2rgb(hsl.x)*(b-a);
        /*vec3(
            hueRamp(a,b,hsl.x+(1./3.)),
            hueRamp(a,b,hsl.x),
            hueRamp(a,b,hsl.x-(1./3.))
        );*/
    }
}