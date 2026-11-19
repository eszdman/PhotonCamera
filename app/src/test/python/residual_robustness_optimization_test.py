"""Compare robustness pixels/support to a saved shader, including CFA borders.

Uses actual RGBA16F shaders, all Bayer origins, incomplete workgroups, windows
too small for an interior path, anisotropic kernels, highlights and outliers.
"""
import argparse
import json
from pathlib import Path

import moderngl
import numpy as np

SHADER=Path(__file__).resolve().parents[2]/'main/assets/shaders/merge/mergeCombineWeight1.glsl'


class Combine:
    def __init__(self,ctx,path):
        self.ctx=ctx
        code=path.read_text().replace('#define RESIDUAL_ROBUSTNESS 0','#define RESIDUAL_ROBUSTNESS 1')
        code=code.replace('#define LAYOUT //','#define LAYOUT layout(local_size_x=8,local_size_y=8) in;')
        self.program=ctx.compute_shader('#version 430\n'+code)

    def run(self,base,candidate,kernel,support,cfa,initialized,exposure):
        h,w,_=base.shape;kh,kw,_=kernel.shape
        textures=[self.ctx.texture((w,h),4,x.astype('f2').tobytes(),dtype='f2') for x in (base,candidate)]
        textures.append(self.ctx.texture((w,h),4,dtype='f2'))
        textures.append(self.ctx.texture((w,h),1,support.astype('f4').tobytes(),dtype='f4'))
        textures.append(self.ctx.texture((kw,kh),4,kernel.astype('f2').tobytes(),dtype='f2'))
        for i in range(4):textures[i].bind_to_image(i,read=i!=2,write=i>=2)
        textures[4].filter=(moderngl.LINEAR,moderngl.LINEAR)
        textures[4].repeat_x=False;textures[4].repeat_y=False;textures[4].use(0)
        for name,value in dict(rawHalf=(w-cfa[0],h-cfa[1]),cfaShift=cfa,
                               supportInitialized=initialized,kernelsMap=0,noiseS=.0003,
                               noiseO=.00006,exposure=exposure,weight=.125).items():
            if name in self.program:self.program[name].value=value
        self.program.run((w+7)//8,(h+7)//8);self.ctx.memory_barrier()
        pixels=np.frombuffer(textures[2].read(),'f2').astype('f4').reshape(h,w,4)
        counts=np.frombuffer(textures[3].read(),'f4').copy().reshape(h,w)
        for t in textures:t.release()
        return pixels,counts


def check(ctx,before):
    pair=[Combine(ctx,p) for p in (before,SHADER)]
    rng=np.random.default_rng(591021);cases=[]
    for wh in ((5,4),(11,9),(65,49)):
        for cfa in ((0,0),(1,0),(0,1),(1,1)):
            w,h=wh[0]+cfa[0],wh[1]+cfa[1];y,x=np.mgrid[:h,:w]
            truth=(.035+.12*x/w+.08*y/h)[...,None]*np.array([.42,1,1,.23])
            seed=np.clip(truth+rng.normal(0,.008,(h,w,4)),0,1).astype('f2').astype('f4')
            kernel=np.zeros(((h+1)//2,(w+1)//2,4),'f2')
            kernel[...,:2]=rng.uniform(.3,4,kernel[...,:2].shape)
            kernel[...,2]=rng.uniform(-.95,.95,kernel[...,2].shape)
            for initialized in (False,True):
                pixels=[seed.copy(),seed.copy()]
                count=rng.uniform(1,24,(h,w)).astype('f4')
                counts=[count.copy(),count.copy()]
                for frame in range(8):
                    candidate=np.clip(truth+rng.normal(0,.015,(h,w,4)),0,1)
                    if frame%3==0:candidate[h//3:2*h//3,w//3:2*w//3]+=np.array([.04,.08,.08,.02])
                    exposure=.5 if frame%3==1 else 1.
                    if exposure<1:candidate[:h//2,:w//2]=.9
                    previous_pixels=[x.copy() for x in pixels]
                    previous_counts=[x.copy() for x in counts]
                    for i in range(2):
                        pixels[i],counts[i]=pair[i].run(pixels[i],candidate,kernel,counts[i],cfa,initialized or frame>0,exposure)
                    a,b=pixels;sa,sb=counts
                    assert np.isfinite(a).all() and np.isfinite(b).all()
                    assert np.isfinite(sa).all() and np.isfinite(sb).all()
                    error=float(np.max(abs(a-b)));count_error=float(np.max(abs(sa-sb)))
                    assert error<=.0005,(wh,cfa,initialized,frame,error)
                    assert count_error<.00005,(wh,cfa,initialized,frame,count_error)
                    # Highlight rejection must neither change the base nor add support.
                    if exposure<1:
                        region=np.s_[:h//2,:w//2]
                        for i in range(2):
                            np.testing.assert_array_equal(pixels[i][region],previous_pixels[i][region])
                            np.testing.assert_array_equal(counts[i][region],previous_counts[i][region])
                    cases.append(dict(raw_half=wh,cfa=cfa,initialized=initialized,frame=frame,
                                      max_pixel_error=error,changed_pixels=int(np.count_nonzero(a!=b)),
                                      max_support_error=count_error))
    for p in pair:p.program.release()
    return dict(cases=cases,max_pixel_error=max(c['max_pixel_error'] for c in cases),
                max_support_error=max(c['max_support_error'] for c in cases),
                comparisons=len(cases))


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--before',type=Path,required=True)
    p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    ctx=moderngl.create_standalone_context(require=430,backend='egl')
    result=check(ctx,a.before);result['gpu']=ctx.info['GL_RENDERER']
    a.report.write_text(json.dumps(result,indent=2)+'\n')
    print({k:v for k,v in result.items() if k!='cases'})
