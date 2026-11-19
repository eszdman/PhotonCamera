"""Independent rotated-lattice oracle and physical CFA burst tests for joint green."""
import argparse
import json
from pathlib import Path
import moderngl
import numpy as np
from scipy.ndimage import map_coordinates
from cfa_residual_fusion_test import Merge, RAW_H, RAW_W


def oracle(ctx):
    rng = np.random.default_rng(7913)
    h, w = RAW_H//2, RAW_W//2
    y, x = np.mgrid[:h, :w]
    lattice_shape = (w+h+32, w+h+32)
    yy, xx = np.mgrid[:lattice_shape[0], :lattice_shape[1]]
    gbase = rng.uniform(.2, .7, lattice_shape).astype('f2').astype('f4')
    gsource = rng.uniform(.2, .7, lattice_shape).astype('f2').astype('f4')
    base = rng.uniform(.2, .7, (h,w,4)).astype('f2').astype('f4')
    source = rng.uniform(.2, .7, (h,w,4)).astype('f2').astype('f4')
    u, v = x+y+8, x-y+h+8
    for c, off in ((1,0),(2,-1)):
        base[...,c] = gbase[v+off,u]
        source[...,c] = gsource[v+off,u]
    merge = Merge(ctx,.5,sampling=2)
    errors = []
    for dx,dy in ((0,0),(2,-3),(.375,-.625),(-1.125,2.75),(.1,.6),(-.9,.2)):
        actual,d = merge.align.run(source,base,dx,dy)
        du,dv = d[0]+d[1],d[0]-d[1]
        # Two actual bilinear operations on an independently stored green
        # lattice, rather than reproducing the shader's nine-tap formula.
        forward = map_coordinates(gbase,[yy-dv,xx-du],order=1,mode='nearest')
        back = map_coordinates(gsource-forward,[yy+dv,xx+du],order=1,mode='nearest')
        direct = map_coordinates(gsource,[yy+dv,xx+du],order=1,mode='nearest')
        expected = np.empty_like(base)
        for c,off in ((1,0),(2,-1)):
            expected[...,c] = .5*(base[...,c]+back[v+off,u])+.5*direct[v+off,u]
        for c in (0,3):
            forward = map_coordinates(base[...,c],[y-d[1],x-d[0]],order=1,mode='nearest')
            back = map_coordinates(source[...,c]-forward,[y+d[1],x+d[0]],order=1,mode='nearest')
            direct = map_coordinates(source[...,c],[y+d[1],x+d[0]],order=1,mode='nearest')
            expected[...,c] = .5*(base[...,c]+back)+.5*direct
        error = float(np.max(abs(actual[8:-8,8:-8]-expected[8:-8,8:-8])))
        assert error < .0007, (d,error)
        errors.append(error)
    # Real green sites share a constant value. Poison every invalid CFA site
    # independently so edge clamping cannot borrow another color's padding.
    checks = 0
    colors = np.array([.125,.35,.35,.7],'f4')
    for cfa in ((0,0),(1,0),(0,1),(1,1)):
        hh,ww = h+cfa[1],w+cfa[0];y,x = np.mgrid[:hh,:ww]
        source = np.broadcast_to(colors,(hh,ww,4)).copy();valid=[]
        for c in range(4):
            low=(np.array(cfa)-[c%2,c//2]+1)//2
            high=(np.array([RAW_W,RAW_H])-1+np.array(cfa)-[c%2,c//2])//2
            mask=(x>=low[0])&(x<=high[0])&(y>=low[1])&(y<=high[1]);valid.append(mask)
            source[~mask,c]=1-c*.19
        for d in ((.375,-.625),(-20.4,30.2)):
            for exposure in (.5,1,1.25):
                actual,_=merge.align.run(source,source*exposure,*d,cfa=cfa,exposure=exposure)
                for c in range(4):np.testing.assert_allclose(actual[...,c][valid[c]],colors[c]*exposure,atol=.001,rtol=0)
                checks+=1
        black=np.zeros_like(source);actual,_=merge.align.run(black,black,.3,-.2,cfa=cfa)
        assert np.max(abs(actual))==0;checks+=1
    return {'independent_forward_adjoint_cases':len(errors),'max_abs':max(errors),'cfa_boundary_dc_exposure_black_checks':checks}


def physical_scene(x,y,f):
    channels=[]
    for c,(sx,sy) in enumerate(((0,0),(1,0),(0,1),(1,1))):
        rx,ry=2*x+sx,2*y+sy
        signal=.35+.06*np.sin(2*np.pi*(f*rx+.017*ry))+.035*np.cos(2*np.pi*(.037*rx+.6*f*ry))
        channels.append(signal*[.6,1,1,.8][c])
    return np.stack(channels,-1)


def quality(ctx):
    variants={'bilinear':Merge(ctx,.5,robust=True,sampling=2),
              'cubic':Merge(ctx,.5,robust=True),
              'lanczos2':Merge(ctx,.5,robust=True,sampling=4)}
    y,x=np.mgrid[:RAW_H//2,:RAW_W//2];roi=np.s_[12:-12,12:-12,:];rows=[]
    for f in (0,.04,.11,.21,.32):
        for shift_error in (0,.15):
            rng=np.random.default_rng(8911);sigma=.012
            truth=physical_scene(x,y,f) if f else np.broadcast_to([.21,.35,.35,.28],(*x.shape,4)).copy()
            initial=(truth+rng.normal(0,sigma,truth.shape)).astype('f2').astype('f4')
            images={k:initial.copy() for k in variants}
            for n in range(2,23):
                d=rng.uniform(-1,1,2);estimated=d+rng.normal(0,shift_error,2)
                source=(physical_scene(x-d[0],y-d[1],f) if f else truth)+rng.normal(0,sigma,truth.shape)
                for k,m in variants.items():images[k]=m.run(images[k],source,estimated,n,sigma)
            row={'frequency_cycles_per_raw_pixel':f,'alignment_error_sigma_raw_pixels':shift_error*2,'variants':{}}
            for k,a in images.items():
                error=(a-truth)[roi]
                row['variants'][k]={'rmse':float(np.sqrt(np.mean(error**2))),'green_rmse':float(np.sqrt(np.mean(error[...,1:3]**2)))}
            rows.append(row);print(row,flush=True)
    return rows


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    ctx=moderngl.create_standalone_context(require=430,backend='egl')
    result={'method':__doc__,'oracle':oracle(ctx),'physical_bursts':quality(ctx)}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(result,indent=2)+'\n')
    print(result['oracle'])
