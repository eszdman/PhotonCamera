"""Cubic green lattice: independent two-warp oracle, CFA borders and quality."""
import json
import tempfile
from pathlib import Path

import moderngl
import numpy as np
from halide_subpixel_test import Renderer, RAW_H, RAW_W, SRC

ROOT=SRC.parents[1]/'tools/burst-replay'
OUT=ROOT/'outputs/synthetic-maze'


def resample(a,dx,dy,mode=3):
    # Independent Keys kernel in distance form, two actual interpolation passes.
    y,x=np.mgrid[:a.shape[0],:a.shape[1]];px=x+dx;py=y+dy
    ix=np.floor(px).astype(int);iy=np.floor(py).astype(int)
    def kernel(d):
        d=np.abs(d)
        if mode==2:return np.maximum(1-d,0)
        if mode==4:return np.where(d<2,np.sinc(d)*np.sinc(d/2),0)
        return np.where(d<=1,1.5*d**3-2.5*d**2+1,
                        np.where(d<2,-.5*d**3+2.5*d**2-4*d+2,0))
    result=np.zeros_like(a);norm=np.zeros_like(a)
    for j in range(-1,3):
        for i in range(-1,3):
            weight=kernel(px-ix-i)*kernel(py-iy-j)
            result+=a[np.clip(iy+j,0,a.shape[0]-1),np.clip(ix+i,0,a.shape[1]-1)]*weight
            norm+=weight
    return result/norm


def renderer(ctx,code,mode=3):
    code=code.replace('#define CFA_RESIDUAL_FUSION 0','#define CFA_RESIDUAL_FUSION 1')
    with tempfile.TemporaryDirectory() as tmp:
        path=Path(tmp)/'shader.glsl';path.write_text(code)
        r=Renderer(ctx,mode,32,shader_path=path)
    r.program['cfaResidualStrength'].value=.5
    return r


def oracle(ctx,code,mode=3):
    r=renderer(ctx,code,mode);rng=np.random.default_rng(7913)
    h,w=RAW_H//2,RAW_W//2;y,x=np.mgrid[:h,:w]
    shape=(h+w+32,h+w+32)
    gb=rng.uniform(.2,.7,shape).astype('f2').astype('f4')
    gs=rng.uniform(.2,.7,shape).astype('f2').astype('f4')
    base=rng.uniform(.2,.7,(h,w,4)).astype('f2').astype('f4')
    source=rng.uniform(.2,.7,(h,w,4)).astype('f2').astype('f4')
    u=x+y+8;v=x-y+h+8
    for c,off in ((1,0),(2,-1)):
        base[...,c]=gb[v+off,u];source[...,c]=gs[v+off,u]
    errors=[]
    for d in [(0,0),(2,-3),(.375,-.625),(-1.125,2.75),(.1,.6),(-.9,.2)]:
        actual,decoded=r.run(source,base,*d);dx,dy=decoded
        forward=resample(gb,-dx-dy,-dx+dy,mode)
        direct=resample(gs,dx+dy,dx-dy,mode)
        back=resample(gs-forward,dx+dy,dx-dy,mode)
        expected=np.empty_like(base)
        for c,off in ((1,0),(2,-1)):
            expected[...,c]=.5*base[...,c]+.5*(direct+back)[v+off,u]
        for c in (0,3):
            f=resample(base[...,c],-dx,-dy,mode)
            expected[...,c]=.5*base[...,c]+.5*resample(source[...,c],dx,dy,mode)+.5*resample(source[...,c]-f,dx,dy,mode)
        e=np.max(np.abs(actual[10:-10,10:-10]-np.clip(expected[10:-10,10:-10],0,1)))
        assert e<.0007,(d,e)
        errors.append(float(e))
    checks=0;colors=np.array([.125,.35,.35,.7],'f4')
    for cfa in [(0,0),(1,0),(0,1),(1,1)]:
        yy,xx=np.mgrid[:h+cfa[1],:w+cfa[0]]
        source=np.broadcast_to(colors,(*xx.shape,4)).copy();valid=[]
        for c in range(4):
            low=(np.array(cfa)-[c%2,c//2]+1)//2
            high=(np.array([RAW_W,RAW_H])-1+np.array(cfa)-[c%2,c//2])//2
            mask=(xx>=low[0])&(xx<=high[0])&(yy>=low[1])&(yy<=high[1]);valid.append(mask)
            source[~mask,c]=1-c*.19
        for d in [(.375,-.625),(-20.4,30.2)]:
            for exposure in [.5,1,1.25]:
                actual,_=r.run(source,source*exposure,*d,cfa=cfa,exposure=exposure)
                for c in range(4):np.testing.assert_allclose(actual[...,c][valid[c]],colors[c]*exposure,atol=.001,rtol=0)
                checks+=1
        black=np.zeros_like(source);actual,_=r.run(black,black,.3,-.2,cfa=cfa)
        assert np.max(abs(actual))==0;checks+=1
    return dict(two_warp_cases=len(errors),max_abs=max(errors),cfa_border_dc_black_checks=checks)


def quality(ctx,code):
    models={name:renderer(ctx,code,mode) for name,mode in [('bilinear',2),('cubic',3),('lanczos2',4)]}
    h,w=RAW_H//2,RAW_W//2;y,x=np.mgrid[:h,:w];roi=np.s_[12:-12,12:-12]
    rows=[]
    for frequency in [0,.04,.11,.21,.32]:
        def scene(x,y):
            result=[]
            for c,(sx,sy) in enumerate([(0,0),(1,0),(0,1),(1,1)]):
                rx=2*x+sx;ry=2*y+sy
                v=.07+.015*rx/RAW_W
                if frequency:v+=.012*np.sin(2*np.pi*(frequency*rx+.017*ry))+.008*np.cos(2*np.pi*(.037*rx+.6*frequency*ry))
                result.append(v*[.35,1,1,.2][c])
            return np.stack(result,-1)
        for error in [0,.3]:
            rng=np.random.default_rng(8911);sigma=.012;truth=scene(x,y)
            initial=(truth+rng.normal(0,sigma,truth.shape)).astype('f2').astype('f4')
            outputs={k:initial.copy() for k in models}
            for n in range(2,23):
                d=rng.uniform(-1,1,2);estimate=d+rng.normal(0,error/2,2)
                source=scene(x-d[0],y-d[1])+rng.normal(0,sigma,truth.shape)
                for k,m in models.items():
                    m.program['cfaNoiseS'].value=0;m.program['cfaNoiseO'].value=sigma*sigma
                    candidate,_=m.run(source,outputs[k],*estimate)
                    outputs[k]=(outputs[k]+(candidate-outputs[k])/n).astype('f2').astype('f4')
            row=dict(frequency_raw=frequency,motion_error_sigma_raw=error,variants={})
            for k,out in outputs.items():
                e=(out-truth)[roi];g=e[...,1:3]
                row['variants'][k]=dict(green_rmse=float(np.mean(g*g)**.5),
                    same_phase_neighbor_error_correlation=float(np.corrcoef(g[:,1:].ravel(),g[:,:-1].ravel())[0,1]))
            rows.append(row);print(row,flush=True)
    return rows


if __name__=='__main__':
    OUT.mkdir(parents=True,exist_ok=True)
    ctx=moderngl.create_standalone_context(require=430,backend='egl')
    code=(SRC/'main/assets/shaders/merge/mergeAlign.glsl').read_text()
    report=dict(oracle={str(mode):oracle(ctx,code,mode) for mode in [2,3,4]},fixed_equal_weights_quality=quality(ctx,code))
    (OUT/'quincunx-lanczos-tests.json').write_text(json.dumps(report,indent=2)+'\n')
    print(report['oracle'])
