"""GPU CFA residual fusion: independent forward/adjoint check and burst quality.

Analytic continuous scenes (not bilinear-generated truth), independent noisy
frames, known fractional shifts and deliberately erroneous alignment. Runs the
production mergeAlign + mergeCombineWeight1 shaders with a fixed isotropic
kernel. These controlled tests do not substitute for real-burst evaluation.
"""
import argparse
import json
import tempfile
from pathlib import Path
import moderngl
import numpy as np
from scipy.ndimage import map_coordinates
from halide_subpixel_test import Renderer, SRC, RAW_H, RAW_W


class Merge:
    def __init__(self, ctx, strength=None, motion=False, fallback=True, robust=False, joint_green=False):
        self.ctx=ctx
        self.robust=robust; self.support=None
        code=(SRC/'main/assets/shaders/merge/mergeAlign.glsl').read_text()
        if strength is not None: code=code.replace('#define CFA_RESIDUAL_FUSION 0','#define CFA_RESIDUAL_FUSION 1')
        if joint_green: code=code.replace('#define CFA_GREEN_QUINCUNX 0','#define CFA_GREEN_QUINCUNX 1')
        if motion: code=code.replace('#define HALIDE_MOTION_FIELD 0','#define HALIDE_MOTION_FIELD 1')
        if not fallback: code=code.replace('#define HALIDE_PIXEL_FALLBACK 1','#define HALIDE_PIXEL_FALLBACK 0')
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/'merge.glsl';path.write_text(code)
            self.align=Renderer(ctx,3,32,shader_path=path)
        if strength is not None:self.align.program['cfaResidualStrength'].value=strength
        code=(SRC/'main/assets/shaders/merge/mergeCombineWeight1.glsl').read_text()
        if robust:code=code.replace('#define RESIDUAL_ROBUSTNESS 0','#define RESIDUAL_ROBUSTNESS 1')
        code=code.replace('#define LAYOUT //','#define LAYOUT layout(local_size_x=8,local_size_y=8) in;')
        self.combine=ctx.compute_shader('#version 430\n'+code)

    def run(self, base, source, d, n, sigma, vectors=None, gate=True, cfa=(0,0)):
        for name,value in dict(cfaNoiseS=0,cfaNoiseO=sigma*sigma).items():
            if name in self.align.program:self.align.program[name].value=value
        candidate,_=self.align.run(source,base,*d,vectors=vectors,cfa=cfa)
        if not gate:return (base+(candidate-base)/n).astype('f2').astype('f4')
        h,w,_=base.shape
        if self.robust:
            if self.support is None or self.support.size!=(w,h):
                if self.support is not None:self.support.release()
                self.support=self.ctx.texture((w,h),1,dtype='f4')
            self.support.bind_to_image(3,read=True,write=True)
            self.combine['supportInitialized'].value=n>2
            self.combine['rawHalf'].value=(w-cfa[0],h-cfa[1]); self.combine['cfaShift'].value=cfa
        textures=[self.ctx.texture((w,h),4,a.astype('f2').tobytes(),dtype='f2') for a in (base,candidate)]
        textures.append(self.ctx.texture((w,h),4,dtype='f2'))
        kernel=np.zeros((h//2+1,w//2+1,4),'f2');kernel[...,:2]=1.2
        textures.append(self.ctx.texture((kernel.shape[1],kernel.shape[0]),4,kernel.tobytes(),dtype='f2'))
        for i in range(3):textures[i].bind_to_image(i,read=i<2,write=i==2)
        textures[3].use(0)
        for name,value in dict(kernelsMap=0,noiseS=0,noiseO=sigma*sigma,weight=1/n,exposure=1).items():
            if name in self.combine:self.combine[name].value=value
        self.combine.run((w+7)//8,(h+7)//8);self.ctx.memory_barrier()
        result=np.frombuffer(textures[2].read(),'f2').astype('f4').reshape(h,w,4)
        for t in textures:t.release()
        return result


def oracle(ctx):
    rng=np.random.default_rng(9302);y,x=np.mgrid[:RAW_H//2,:RAW_W//2]
    base=rng.uniform(.2,.7,(*x.shape,4)).astype('f2').astype('f4')
    source=rng.uniform(.2,.7,base.shape).astype('f2').astype('f4')
    merge=Merge(ctx,.5);errors=[]
    for dx,dy in ((0,0),(2,-3),(.375,-.625),(-1.125,2.75)):
        actual,d=merge.align.run(source,base,dx,dy)
        predicted=np.stack([map_coordinates(base[...,c],[y-d[1],x-d[0]],order=1,mode='nearest') for c in range(4)],-1)
        residual=source-predicted
        back=np.stack([map_coordinates(residual[...,c],[y+d[1],x+d[0]],order=1,mode='nearest') for c in range(4)],-1)
        direct=np.stack([map_coordinates(source[...,c],[y+d[1],x+d[0]],order=1,mode='nearest') for c in range(4)],-1)
        expected=.5*(base+back)+.5*direct
        err=float(np.max(abs(actual[8:-8,8:-8]-expected[8:-8,8:-8])))
        assert err<.0007,(d,err)
        errors.append(err)
    # CFA borders, black, DC and exposure: no foreign-color padding may leak.
    checks=0
    for cfa in ((0,0),(1,0),(0,1),(1,1)):
        h,w=RAW_H//2+cfa[1],RAW_W//2+cfa[0]
        source=np.broadcast_to(np.array([.125,.25,.5,.75],'f4'),(h,w,4)).copy()
        for exposure in (.5,1,1.25):
            base=source*exposure
            actual,_=merge.align.run(source,base,.375,-.625,cfa=cfa,exposure=exposure)
            np.testing.assert_allclose(actual,base,atol=.0006,rtol=0);checks+=1
        black=np.zeros_like(source);actual,_=merge.align.run(black,black,.125,-.4,cfa=cfa)
        assert np.max(abs(actual))==0;checks+=1
    padding_checks=0
    for cfa in ((0,0),(1,0),(0,1),(1,1)):
        h,w=RAW_H//2+cfa[1],RAW_W//2+cfa[0]; yy,xx=np.mgrid[:h,:w]
        colors=np.array([.125,.25,.5,.75],'f4')
        source=np.broadcast_to(colors,(h,w,4)).copy(); base=source.copy(); valid=[]
        for c in range(4):
            low=(np.array(cfa)-[c%2,c//2]+1)//2
            high=(np.array([RAW_W,RAW_H])-1+np.array(cfa)-[c%2,c//2])//2
            mask=(xx>=low[0])&(xx<=high[0])&(yy>=low[1])&(yy<=high[1]);valid.append(mask)
            source[~mask,c]=1-c*.2; base[~mask,c]=c*.21
        for d in ((.375,-.625),(-20.4,30.2)):
            actual,_=merge.align.run(source,base,*d,cfa=cfa)
            for c in range(4):np.testing.assert_allclose(actual[...,c][valid[c]],colors[c],atol=.0006,rtol=0)
            padding_checks+=1
    return {'forward_adjoint_max_abs':max(errors),'forward_adjoint_cases':len(errors),
            'cfa_dc_exposure_black_cases':checks,'poisoned_cfa_padding_cases':padding_checks}


def scene(x,y,frequency):
    return np.stack([.2+.15*c+.055*np.sin(2*np.pi*(frequency*x+.027*y)+c*.53)
        +.035*np.cos(2*np.pi*(.041*x+frequency*.6*y)+c*.31) for c in range(4)],-1)


def quality(ctx):
    variants={'cubic':Merge(ctx),'motion_cubic':Merge(ctx,motion=True,fallback=False),
              'residual_0':Merge(ctx,0),'residual_025':Merge(ctx,.25),
              'residual_05':Merge(ctx,.5),'residual_075':Merge(ctx,.75),'residual_1':Merge(ctx,1)}
    y,x=np.mgrid[:RAW_H//2,:RAW_W//2];roi=np.s_[12:-12,12:-12,:]
    results=[]
    for frequency in (.05,.15,.3,.42):
        truth=scene(x,y,frequency).astype('f4')
        for sigma in (.003,.015):
            for shift_error in (0,.15):
                rng=np.random.default_rng(7281)
                images={k:(truth+rng.normal(0,sigma,truth.shape)).astype('f2').astype('f4') for k in ['seed']}
                images={k:images['seed'].copy() for k in variants}
                for n in range(2,17):
                    d=rng.uniform(-2,2,2);estimated=d+rng.normal(0,shift_error,2)
                    source=scene(x-d[0],y-d[1],frequency)+rng.normal(0,sigma,truth.shape)
                    for k,merge in variants.items():images[k]=merge.run(images[k],source,estimated,n,sigma)
                row={'frequency_cycles_per_cfa_plane_pixel':frequency,'sigma':sigma,'alignment_error_sigma_raw_pixels':shift_error*2}
                t=truth[roi];detail=t-t.mean((0,1),keepdims=True)
                row['variants']={k:{'rmse':float(np.sqrt(np.mean((v[roi]-t)**2))),
                    'texture_gain':float(np.sum((v[roi]-v[roi].mean((0,1),keepdims=True))*detail)/np.sum(detail**2))}
                    for k,v in images.items()}
                results.append(row)
                print(f'frequency={frequency} noise={sigma} alignment_error={shift_error}: '+
                    ' '.join(f'{k}={v["rmse"]:.5f}' for k,v in row['variants'].items()),flush=True)
    # Noise-only area and discontinuous edge test with biased alignment.
    stress=[]
    for kind in ('flat','edge','outlier'):
        truth=np.broadcast_to(np.where(x[...,None]<RAW_W//4,.2,.7) if kind!='flat' else np.array([.2,.35,.5,.65]),(*x.shape,4)).astype('f4').copy()
        rng=np.random.default_rng(4291);sigma=.015
        initial=(truth+rng.normal(0,sigma,truth.shape)).astype('f2').astype('f4')
        images={k:initial.copy() for k in variants}
        for n in range(2,17):
            d=rng.uniform(-1,1,2)
            signal=np.broadcast_to(np.where((x-d[0])[...,None]<RAW_W//4,.2,.7),truth.shape) if kind!='flat' else truth
            source=signal+rng.normal(0,sigma,truth.shape)
            estimate=d+([2.5,0] if kind=='outlier' and n%3==0 else [0,0])
            for k,merge in variants.items():images[k]=merge.run(images[k],source,estimate,n,sigma)
        stress.append({'case':kind,'variants':{k:{'rmse':float(np.sqrt(np.mean((v[roi]-truth[roi])**2))),
             'min':float(v[roi].min()),'max':float(v[roi].max())} for k,v in images.items()}})
    return {'analytic_bursts':results,'stress_bursts':stress}

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    ctx=moderngl.create_standalone_context(require=430,backend='egl')
    result={'method':__doc__,'gpu':ctx.info['GL_RENDERER'],'oracle':oracle(ctx),**quality(ctx)}
    a.report.parent.mkdir(parents=True,exist_ok=True);a.report.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result['stress_bursts'],indent=2))
