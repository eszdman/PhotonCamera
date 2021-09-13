//RGB to HSL (hue, saturation, lightness/luminance).
//Source: https://gist.github.com/yiwenl/745bfea7f04c456e0101
vec3 rgbtohsl(vec3 c){
    float cMin=min(min(c.r,c.g),c.b),
    cMax=max(max(c.r,c.g),c.b),
    delta=cMax-cMin;
    vec3 hsl=vec3(0.,0.,(cMax+cMin)/2.);
    if(delta!=0.0){ //If it has chroma and isn't gray.
        if(hsl.z<.5){
            hsl.y=delta/(cMax+cMin); //Saturation.
        }else{
            hsl.y=delta/(2.-cMax-cMin); //Saturation.
        }
        float deltaR=(((cMax-c.r)/6.)+(delta/2.))/delta,
        deltaG=(((cMax-c.g)/6.)+(delta/2.))/delta,
        deltaB=(((cMax-c.b)/6.)+(delta/2.))/delta;
        //Hue.
        if(c.r==cMax){
            hsl.x=deltaB-deltaG;
        }else if(c.g==cMax){
            hsl.x=(1./3.)+deltaR-deltaB;
        }else{ //if(c.b==cMax){
            hsl.x=(2./3.)+deltaG-deltaR;
        }
        hsl.x=fract(hsl.x);
    }
    return hsl;
}