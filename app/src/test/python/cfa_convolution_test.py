"""Audit mergeCombineWeight1's convolution on physical CFA positions.

The oracle enumerates raw sample coordinates rather than reproducing the
shader's half-texel expressions. No residual reconstruction or temporal mix.
GPU textures are RGBA16F; NumPy's reference arithmetic is float64.
"""
import argparse,json,sys
from pathlib import Path
import numpy as np,moderngl
ROOT=Path(__file__).resolve().parents[4]
sys.path.insert(0,str(ROOT/'tools/burst-replay'))
from inspect_merge_robustness import Combine,SHADER

SITES=np.array([[0,0],[1,0],[0,1],[1,1]])

def blur_code(code=None):
    code=code or SHADER.read_text()
    line='imageStore(outTexture, xy, mix(base, diff, weight * comb));'
    assert code.count(line)==1
    return code.replace(line,'imageStore(outTexture, xy, localDiff2 / Z);')

def reference(source,kernel):
    """Same finite quad support, distances derived in absolute raw pixels."""
    h,w,_=source.shape;s1,s2,rho=kernel
    covariance=np.array([[s2*s2,rho*s1*s2],[rho*s1*s2,s1*s1]])
    inverse=np.linalg.inv(covariance)
    yy,xx=np.mgrid[6:h-6,6:w-6];out=np.empty((*xx.shape,4));centroids=[]
    for c in range(4):
        total=.001*source[6:-6,6:-6,c].astype('f8');z=.001;moment=np.zeros(2)
        # No R/B mixing; both green sites have the same filter color.
        channels=[1,2] if c in [1,2] else [c]
        for j in range(-5,6):
            for i in range(-5,6):
                for cc in channels:
                    raw_offset=2*np.array([i,j])+SITES[cc]-SITES[c]
                    d=raw_offset/2
                    weight=np.exp(-d@inverse@d)
                    total+=weight*source[yy+j,xx+i,cc];z+=weight;moment+=weight*raw_offset
        out[...,c]=total/z;centroids.append((moment/z).tolist())
    return out,centroids

def checks(ctx):
    yy,xx=np.mgrid[:56,:64];rows=[]
    for kernel in [(2,2,0),(1.2,1.7,.4),(.3,.6,-.5),(4,3,.6)]:
        k=np.array(kernel,dtype='f2').astype('f4')
        kernels=np.zeros((29,33,4),'f2');kernels[...,:3]=k;kernels[...,3]=1
        renderer=Combine(ctx,kernels,code=blur_code())
        for name in ['constant','ramp','curved','impulse']:
            source=np.empty((*xx.shape,4),'f4')
            for c,(sx,sy) in enumerate(SITES):
                rx,ry=2*xx+sx,2*yy+sy
                gain=[.6,1,1,.8][c]
                if name=='constant':a=np.full_like(rx,.3,dtype='f4')
                elif name=='ramp':a=.1+.001*rx+.0007*ry
                elif name=='curved':a=.3+.1*np.sin(.21*rx+.07*ry)
                else:a=np.full_like(rx,.1,dtype='f4')+((rx==65)&(ry==48))*.5
                source[...,c]=gain*a
            source=source.astype('f2').astype('f4')
            expected,centroid=reference(source,k)
            actual=renderer.run(source,source,(0,0),.5,0,.0001)[6:-6,6:-6]
            error=float(np.max(abs(actual-expected)))
            assert error<.0005,(kernel,name,error)
            rows.append({'kernel':k.tolist(),'scene':name,'max_abs_error':error,'centroids_raw_px':centroid})
        # Deliberately reverse the half-pixel green phases: the oracle must fail.
        bad=blur_code().replace('i - 0.5, v = j + 0.5','i + 0.5, v = j - 0.5').replace('u = i + 0.5; v = j - 0.5','u = i - 0.5; v = j + 0.5')
        wrong=Combine(ctx,kernels,code=bad).run(source,source,(0,0),.5,0,.0001)[6:-6,6:-6]
        wrong_error=float(np.max(abs(wrong-expected)))
        assert wrong_error>.0005,(kernel,wrong_error)
        rows[-1]['reversed_phases_negative_control_error']=wrong_error
    return rows

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    ctx=moderngl.create_standalone_context(require=430,backend='egl')
    result={'method':__doc__,'cases':checks(ctx)}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(result,indent=2)+'\n')
    print('PASS:',len(result['cases']),'shader/oracle comparisons, four reversed-phase negative controls')
