"""Controlled raw-CFA tests for accepted-mass normalization and ghost rejection."""
import argparse,json
from pathlib import Path
import moderngl,numpy as np
from scipy.ndimage import gaussian_filter
from cfa_residual_fusion_test import Merge,scene,RAW_H,RAW_W

def run(ctx):
    y,x=np.mgrid[:RAW_H//2,:RAW_W//2];shape=(*x.shape,4);roi=np.s_[12:-12,12:-12,:]
    variants={'old':Merge(ctx,.5),'fixed':Merge(ctx,.5,robust=True)};results=[]
    for kind in ('flat','initial_blotch','moving_occluder','texture','misaligned_texture'):
        for seed in (913,827):
            rng=np.random.default_rng(seed);sigma=.012
            truth=scene(x,y,.3) if 'texture' in kind else np.broadcast_to(np.array([.2,.3,.4,.5]),shape).copy()
            initial=truth+rng.normal(0,sigma,shape)
            if kind=='initial_blotch':
                blob=.035*np.exp(-((x-60)**2+(y-44)**2)/(2*4**2));initial+=blob[...,None]
            images={k:initial.astype('f2').astype('f4').copy() for k in variants}
            for n in range(2,26):
                d=rng.uniform(-1,1,2)
                source=scene(x-d[0],y-d[1],.3) if 'texture' in kind else truth.copy()
                if kind=='moving_occluder' and n%3==0:source[32:57,45:78]+=.25
                source+=rng.normal(0,sigma,shape)
                estimated=d+rng.normal(0,.15,2) if kind=='misaligned_texture' else d
                for k,m in variants.items():images[k]=m.run(images[k],source,estimated,n,sigma)
            row={'case':kind,'seed':seed,'variants':{}}
            for k,a in images.items():
                error=a-truth
                row['variants'][k]={'rmse':float(np.sqrt(np.mean(error[roi]**2))),
                    'patch_bias':float(np.mean(error[36:52,52:70])),
                    'low_frequency_error_rms':float(np.sqrt(np.mean(gaussian_filter(error,(3,3,0))[roi]**2)))}
            if kind in ('flat','initial_blotch'):
                assert row['variants']['fixed']['rmse'] < row['variants']['old']['rmse'],row
            if kind=='moving_occluder':assert abs(row['variants']['fixed']['patch_bias'])<.005,row
            print(row,flush=True);results.append(row)
    # Exact zero residual must receive full support, including all image borders.
    m=variants['fixed'];base=np.broadcast_to(np.array([.125,.25,.5,.75],'f4'),shape).copy()
    for n in range(2,26):base=m.run(base,base,(0,0),n,.015)
    count=np.frombuffer(m.support.read(),'f4').reshape(shape[:2])
    np.testing.assert_allclose(count,25,atol=1e-4,rtol=0)
    np.testing.assert_allclose(base,np.broadcast_to(np.array([.125,.25,.5,.75]),shape),atol=.0005,rtol=0)
    # Poison shifted padding to expose CFA contamination in boundary statistics.
    padding_cases=0
    for cfa in ((0,0),(1,0),(0,1),(1,1)):
        h,w=RAW_H//2+cfa[1],RAW_W//2+cfa[0];yy,xx=np.mgrid[:h,:w]
        colors=np.array([.125,.25,.5,.75],'f4');source=np.broadcast_to(colors,(h,w,4)).copy()
        valid=[]
        for c in range(4):
            lo=(np.array(cfa)-[c%2,c//2]+1)//2
            hi=(np.array([RAW_W,RAW_H])-1+np.array(cfa)-[c%2,c//2])//2
            mask=(xx>=lo[0])&(xx<=hi[0])&(yy>=lo[1])&(yy<=hi[1]);valid.append(mask);source[~mask,c]=1-c*.19
        base=source.copy()
        for n in range(2,9):base=m.run(base,source,(.375,-.625),n,.015,cfa=cfa)
        for c in range(4):np.testing.assert_allclose(base[...,c][valid[c]],colors[c],atol=.0005,rtol=0)
        np.testing.assert_allclose(np.frombuffer(m.support.read(),'f4'),8,atol=.0001,rtol=0)
        padding_cases+=1
    return {'cases':results,'zero_residual_full_acceptance_including_borders':True,
            'poisoned_cfa_padding_bursts':padding_cases,'method':__doc__}
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    ctx=moderngl.create_standalone_context(require=430,backend='egl');r=run(ctx);a.report.write_text(json.dumps(r,indent=2)+'\n')
