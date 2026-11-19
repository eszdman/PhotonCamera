"""GPU hot-pixel proposals versus an independent raw-coordinate maximum filter."""
import argparse
import json
import itertools
from pathlib import Path
import moderngl
import numpy as np
from scipy.ndimage import maximum_filter

ROOT = Path(__file__).resolve().parents[3]


def run():
    ctx = moderngl.create_standalone_context(require=430, backend='egl')
    src = (ROOT/'src/main/assets/shaders/merge/hotpixeldetect.glsl').read_text()
    src = src.replace('#define LAYOUT //', '#define LAYOUT layout(local_size_x=8,local_size_y=8) in;')
    program = ctx.compute_shader('#version 430\n'+src)
    h, w = 46, 62
    y, x = np.mgrid[:h, :w]
    raw = (.15 + .005*x/w + .006*y/h).astype('f2').astype('f4')
    raw[8,8] = 1; raw[15,15] = .9; raw[24,35] = .8; raw[35,24] = .7
    raw[13,36] = .185  # A warm proposal between the weak and strong thresholds.
    raw[:,48:] = 1  # A clipped region must not become an impulse list.
    raw[0,0] = 1; raw[-1,20] = 1  # Skip incomplete border neighborhoods.
    footprint = np.ones((3,3),bool); footprint[1,1] = False
    checks = []
    for sx, sy in ((0,0),(1,0),(0,1),(1,1)):
        hh, ww = h//2+sy, w//2+sx
        yy, xx = np.mgrid[:hh,:ww]
        for exposure, sigma in itertools.product((.25,1,1.5), (3,6)):
            packed = np.empty((hh,ww,4),'f2')
            for c in range(4):
                rx=2*xx-sx+c%2;ry=2*yy-sy+c//2
                packed[...,c] = raw[ry.clip(0,h-1),rx.clip(0,w-1)]*exposure
                packed[...,c][(rx<0)|(rx>=w)|(ry<0)|(ry>=h)] = 1000  # Poison padding.
            # Reconstruct real raw sites after upload quantization, independent
            # of the shader's packed-coordinate traversal.
            quantized=(raw*exposure).astype('f2').astype('f4')/exposure
            expected=set()
            for cy in range(2):
                for cx in range(2):
                    plane=quantized[cy::2,cx::2]
                    maximum=maximum_filter(plane,footprint=footprint,mode='nearest')
                    threshold=np.maximum(.02,sigma*np.sqrt(.0004*maximum+.000001))
                    iy,ix=np.nonzero(plane-maximum>threshold)
                    expected|={(int(2*j+cx),int(2*i+cy)) for i,j in zip(iy,ix)
                               if 4<=2*j+cx<w-4 and 4<=2*i+cy<h-4}
            tex=ctx.texture((ww,hh),4,packed.tobytes(),dtype='f2');tex.use(0)
            for capacity in (1,64):
                buffer=ctx.buffer(np.zeros(1+4*capacity,'u4').tobytes());buffer.bind_to_storage_buffer(1)
                for name,value in dict(inTexture=0,rawSize=(w,h),cfaShift=(sx,sy),exposure=exposure,
                                       noiseS=.0004,noiseO=.000001,detectThr=sigma,maxCount=capacity).items():
                    program[name].value=value
                program.run((ww+7)//8,(hh+7)//8);ctx.memory_barrier()
                data=np.frombuffer(buffer.read(),'u4');count=int(data[0]);actual=set()
                for px,py,mask,_ in data[1:1+4*min(count,capacity)].reshape(-1,4).astype(int):
                    for c in range(4):
                        if mask&(1<<c):actual.add((2*px-sx+c%2,2*py-sy+c//2))
                if capacity==64:
                    assert actual==expected,(sx,sy,exposure,actual,expected)
                else:
                    assert count>capacity and actual<=expected
                checks.append({'cfa_shift':[sx,sy],'exposure':exposure,'sigma':sigma,'capacity':capacity,'proposed_quads':count})
                buffer.release()
            tex.release()
    return {'gpu':ctx.info['GL_RENDERER'],'checks':checks,'count':len(checks)}


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--report',type=Path,required=True);a=p.parse_args()
    result=run();a.report.write_text(json.dumps(result,indent=2)+'\n');print('Passed',result['count'],'GPU checks')
