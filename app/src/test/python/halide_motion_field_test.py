"""Analytic affine scenes test patch-center placement and single CFA warp."""
import json
import tempfile
from pathlib import Path
import moderngl
import numpy as np
from halide_subpixel_test import Renderer, SRC, RAW_W, RAW_H, scene

ctx=moderngl.create_standalone_context(require=430,backend='egl')
shader=(SRC/'main/cpp/shaders/shared/shaders/merge/mergeAlign.glsl').read_text()
results=[]
with tempfile.TemporaryDirectory() as directory:
    path=Path(directory)/'motion.glsl'
    path.write_text(shader.replace('#define HALIDE_MOTION_FIELD 0','#define HALIDE_MOTION_FIELD 1'))
    for tile in [16,32]:
        old=Renderer(ctx,3,tile)
        new=Renderer(ctx,3,tile,shader_path=path)
        y,x=np.mgrid[:RAW_H//2,:RAW_W//2].astype('f4')
        aw,ah=RAW_W//tile+1,RAW_H//tile+1
        gy,gx=np.mgrid[:ah,:aw].astype('f4')
        px=gx*tile/2+tile/2-.5
        py=gy*tile/2+tile/2-.5
        for shear in [0,.01,.03,-.03]:
            # x_source=x+shear*y+dx; y_source=y+dy. Independently evaluate
            # continuous source and truth; never warp a discrete test image.
            for frequency in [.05,.13,.2]:
                dx,dy=.7,-.3
                source=scene(x-shear*(y-dy)-dx,y-dy,frequency)
                truth=scene(x,y,frequency)
                vectors=np.stack([dx+shear*py,np.full_like(px,dy)],-1)
                before,_=old.run(source,truth,dx,dy,vectors=vectors)
                after,_=new.run(source,truth,dx,dy,vectors=vectors)
                crop=(slice(24,-24),slice(24,-24))
                a=float(np.sqrt(np.mean((before[crop]-truth[crop])**2)))
                b=float(np.sqrt(np.mean((after[crop]-truth[crop])**2)))
                if shear==0: np.testing.assert_allclose(before,after,atol=.001)
                assert b<=a+3e-4,(tile,shear,frequency,a,b)
                results.append(dict(tile=tile,shear=shear,frequency=frequency,old_rmse=a,motion_rmse=b))
        old.program.release();new.program.release()
    for tile in [16,32]:
        old=Renderer(ctx,3,tile); new=Renderer(ctx,3,tile,shader_path=path)
        for cfa in [(0,0),(1,0),(0,1),(1,1)]:
            h,w=RAW_H//2+cfa[1],RAW_W//2+cfa[0]
            source=np.random.default_rng(734).uniform(.05,.8,(h,w,4)).astype('f4')
            for dx,dy in [(0,0),(3,-2),(.3,-.7),(-.8,.6)]:
                before,_=old.run(source,source,dx,dy,cfa)
                after,_=new.run(source,source,dx,dy,cfa)
                np.testing.assert_allclose(after,before,atol=.001)
            black=np.zeros_like(source)
            after,_=new.run(black,black,.3,-.7,cfa)
            assert np.isfinite(after).all() and after.max()==0
        old.program.release();new.program.release()
print(json.dumps(results,indent=2))
print('PASS',len(results),'analytic motion-field cases + 40 CFA/edge/black checks')
