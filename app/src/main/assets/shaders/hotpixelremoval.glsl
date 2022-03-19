
precision mediump float;
precision mediump usampler2D;
uniform usampler2D InputBuffer;
uniform int CfaPattern;
uniform int yOffset;
out uint Output;
/*
*  This Quickselect routine is based on the algorithm described in
*  "Numerical recipes in C", Second Edition,
*   Cambridge University Press, 1992, Section 8.5, ISBN 0-521-43108-5
*  This code by Nicolas Devillard - 1998. Public domain.
*/
#define ELEM_SWAP(a,b) { uint t=(a); a =(b); b =t; }
uint quick_select(uint arr[9], int n)
{

int low, high ; int median;
int middle, ll, hh;
low = 0; high = n-1 ; median = (low + high) / 2;
uint t = uint(0);
for  (;;) {
    if (high <= low) /* One element only */
    return  arr[median];

    if (high == low + 1) {  /* Two elements only */
        if(arr[low]  >  arr[high]){
            //ELEM_SWAP(arr[low], arr[high]);
            t=arr[low];arr[low]=arr[high];arr[high]=t;
        }
        return arr[median];
    }
    /* Find median of low, middle and high items; swap into position low */
    middle = (low + high) / 2;
    if (arr[middle] > arr[high]){
        //ELEM_SWAP(arr[middle], arr[high]);
        t=arr[middle];arr[middle]=arr[high];arr[high]=t;
    }
    if (arr[low] > arr[high]){
        //ELEM_SWAP(arr[low], arr[high]);
        t=arr[low];arr[low]=arr[high];arr[high]=t;
    }
    if (arr[middle] > arr[low]){
        //ELEM_SWAP(arr[middle], arr[low]);
        t=arr[middle];arr[middle]=arr[low];arr[low]=t;
    }
    /* Swap low item (now in position middle) into position (low+1) */
        //ELEM_SWAP(arr[middle],arr[low+1]);
    t=arr[middle];arr[middle]=arr[low+1];arr[low+1]=t;
    /* Nibble from each end towards middle, swapping items when stuck */
    ll = low + 1;
    hh = high;
    for  (;;) {
        do ll++; while (arr[low] > arr[ll]);
        do hh--; while (arr[hh]   > arr[low]);
        if (hh < ll)
        break;
        //ELEM_SWAP(arr[ll], arr[hh]) ;
        t=arr[ll];arr[ll]=arr[hh];arr[hh]=t;
    }
        /* Swap middle item (in position low) back into correct position */
        //ELEM_SWAP(arr[low], arr[hh]) ;
    t=arr[low];arr[low]=arr[hh];arr[hh]=t;
    /* Re-set active partition */
    if (hh <= median) low = ll;
    if (hh >= median)
    high = hh - 1;
    }
    return arr[middle];
}
#undef  ELEM_SWAP

void main() {
    ivec2 xy = ivec2(gl_FragCoord.xy);
    int fact1 = xy.x%2;
    int fact2 = xy.y%2;
    xy+=ivec2(CfaPattern%2,yOffset+CfaPattern/2);
    xy+=ivec2(0,yOffset);
    uint P[9];
    if(fact1 ==0 && fact2 == 0) {//rggb
        P[0] = uint(texelFetch(InputBuffer, (xy+ivec2(-2,-2)), 0).x);
        P[1] = uint(texelFetch(InputBuffer, (xy+ivec2( 0,-2)), 0).x);
        P[2] = uint(texelFetch(InputBuffer, (xy+ivec2( 2,-2)), 0).x);
        P[3] = uint(texelFetch(InputBuffer, (xy+ivec2(-2, 0)), 0).x);
        P[4] = uint(texelFetch(InputBuffer, (xy+ivec2( 0, 0)), 0).x);
        P[5] = uint(texelFetch(InputBuffer, (xy+ivec2( 2, 0)), 0).x);
        P[6] = uint(texelFetch(InputBuffer, (xy+ivec2(-2, 2)), 0).x);
        P[7] = uint(texelFetch(InputBuffer, (xy+ivec2( 0, 2)), 0).x);
        P[8] = uint(texelFetch(InputBuffer, (xy+ivec2( 2, 2)), 0).x);
        if(P[4]*uint(7) > P[0]+P[1]+P[2]+P[3]+P[5]+P[6]+P[7]+P[8]){
            P[4] = quick_select(P,9);
        }
        Output = P[4];
    } else
    if(fact1 ==1 && fact2 == 0) { //grbg
        P[0] = uint(texelFetch(InputBuffer, (xy+ivec2(-1,-1)), 0).x);
        P[1] = uint(texelFetch(InputBuffer, (xy+ivec2( 1,-1)), 0).x);
        P[2] = uint(texelFetch(InputBuffer, (xy+ivec2( 0, 0)), 0).x);
        P[3] = uint(texelFetch(InputBuffer, (xy+ivec2( 1, 1)), 0).x);
        P[4] = uint(texelFetch(InputBuffer, (xy+ivec2(-1, 1)), 0).x);
        if(P[2]*uint(3) > P[0]+P[1]+P[3]+P[4]){
            P[2] = quick_select(P,5);
        }
        Output = P[2];
    } else
    if(fact1 ==0 && fact2 == 1) { //gbrg
        P[0] = uint(texelFetch(InputBuffer, (xy+ivec2(-1,-1)), 0).x);
        P[1] = uint(texelFetch(InputBuffer, (xy+ivec2( 1,-1)), 0).x);
        P[2] = uint(texelFetch(InputBuffer, (xy+ivec2( 0, 0)), 0).x);
        P[3] = uint(texelFetch(InputBuffer, (xy+ivec2( 1, 1)), 0).x);
        P[4] = uint(texelFetch(InputBuffer, (xy+ivec2(-1, 1)), 0).x);
        if(P[2]*uint(3) > P[0]+P[1]+P[3]+P[4]){
            P[2] = quick_select(P,5);
        }
        Output = P[2];
    } else { //bggr
        P[0] = uint(texelFetch(InputBuffer, (xy+ivec2(-2,-2)), 0).x);
        P[1] = uint(texelFetch(InputBuffer, (xy+ivec2( 0,-2)), 0).x);
        P[2] = uint(texelFetch(InputBuffer, (xy+ivec2( 2,-2)), 0).x);
        P[3] = uint(texelFetch(InputBuffer, (xy+ivec2(-2, 0)), 0).x);
        P[4] = uint(texelFetch(InputBuffer, (xy+ivec2( 0, 0)), 0).x);
        P[5] = uint(texelFetch(InputBuffer, (xy+ivec2( 2, 0)), 0).x);
        P[6] = uint(texelFetch(InputBuffer, (xy+ivec2(-2, 2)), 0).x);
        P[7] = uint(texelFetch(InputBuffer, (xy+ivec2( 0, 2)), 0).x);
        P[8] = uint(texelFetch(InputBuffer, (xy+ivec2( 2, 2)), 0).x);
        if(P[4]*uint(7) > P[0]+P[1]+P[2]+P[3]+P[5]+P[6]+P[7]+P[8]){
            P[4] = quick_select(P,9);
        }
        Output = P[4];
    }
}
