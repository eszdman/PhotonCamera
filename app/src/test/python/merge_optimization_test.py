"""Compare optimized CFA shaders against a saved pre-optimization pair on EGL.

Includes spatially varying anisotropic kernels, all four CFA boundaries,
ordinary/residual paths, and repeated/random/extreme alignment fields.
"""
import argparse
import json
from pathlib import Path
import sys
import tempfile

import moderngl
import numpy as np
from halide_subpixel_test import Renderer, RAW_H, RAW_W, SRC, checks
from cfa_residual_fusion_test import oracle

ROOT = SRC.parents[1]
sys.path.insert(0, str(ROOT/'tools/burst-replay'))
from inspect_merge_robustness import Combine
from cfa_convolution_test import checks as convolution_checks


def compare(ctx, before):
    rng = np.random.default_rng(87142)
    result = {'align_cases': 0, 'align_max_abs': 0., 'combine_cases': 0,
              'combine_max_abs': 0., 'support_max_abs': 0.}
    for mode in (1, 2, 3, 4):
        for residual in (False, True):
            programs = []
            for directory in (before, SRC/'main/assets/shaders/merge'):
                code = (directory/'mergeAlign.glsl').read_text()
                if residual:
                    code = code.replace('#define CFA_RESIDUAL_FUSION 0', '#define CFA_RESIDUAL_FUSION 1')
                with tempfile.TemporaryDirectory() as tmp:
                    path = Path(tmp)/'shader.glsl'; path.write_text(code)
                    renderer = Renderer(ctx, mode, 32, shader_path=path)
                for name, value in dict(cfaResidualStrength=.5, cfaNoiseS=.0001, cfaNoiseO=.00001).items():
                    if name in renderer.program: renderer.program[name].value = value
                programs.append(renderer)
            for cfa in ((0,0), (1,0), (0,1), (1,1)):
                shape = (RAW_H//2+cfa[1], RAW_W//2+cfa[0], 4)
                source = rng.uniform(.001, .99, shape).astype('f2').astype('f4')
                base = rng.uniform(.001, .99, shape).astype('f2').astype('f4')
                for kind in ('constant', 'random', 'repeated', 'large'):
                    vectors = rng.uniform(-3,3,(RAW_H//32+1,RAW_W//32+1,2)).astype('f4')
                    if kind == 'constant': vectors[:] = (.375,-.625)
                    if kind == 'repeated': vectors[:,1::2] = vectors[:,:-1:2]
                    if kind == 'large': vectors *= 100
                    a,b = [p.run(source,base,0,0,cfa=cfa,vectors=vectors)[0] for p in programs]
                    error = float(np.max(abs(a-b)))
                    assert error <= .0005, (mode,residual,cfa,kind,error)
                    result['align_max_abs'] = max(result['align_max_abs'],error)
                    result['align_cases'] += 1
            for p in programs: p.program.release()

    # Kernels span broad and narrow lobes, positive/negative cross terms, and
    # strong anisotropy. Test acceptance, final pixels, and accumulated support.
    for robust in (False, True):
        for kernel in ((.3,.6,-.5),(1.2,1.7,.4),(4,3,.6),'varying'):
            kernels = np.zeros((29,33,4),'f2')
            if kernel == 'varying':
                kernels[...,:2] = rng.uniform(.25,4,kernels[...,:2].shape)
                kernels[...,2] = rng.uniform(-.95,.95,kernels[...,2].shape)
            else: kernels[...,:3] = kernel
            pair = [Combine(ctx,kernels,robust=robust,code=(d/'mergeCombineWeight1.glsl').read_text())
                    for d in (before,SRC/'main/assets/shaders/merge')]
            for origin in ((0,0),(1984,1480),(1,1)):
                base = rng.uniform(.02,.8,(56,64,4)).astype('f4')
                for n in (2,3,9):
                    candidate = base+rng.normal(0,.015,base.shape)
                    candidate[20:28,24:32] += .1  # concentrated moving outlier
                    a,b = [p.run(base,candidate,origin,1/n,.0002,.0001) for p in pair]
                    assert np.isfinite(a).all() and np.isfinite(b).all()
                    error = float(np.max(abs(a-b)))
                    assert error <= .0005, (robust,kernel,origin,n,error)
                    result['combine_max_abs'] = max(result['combine_max_abs'],error)
                    result['combine_cases'] += 1
                    if robust:
                        sa,sb = [np.frombuffer(p.support.read(),'f4') for p in pair]
                        error = float(np.max(abs(sa-sb)))
                        assert error < .00002, error
                        result['support_max_abs'] = max(result['support_max_abs'],error)
            for p in pair:
                p.program.release();p.kernel.release()
                if p.support is not None:p.support.release()
    return result


if __name__ == '__main__':
    parser=argparse.ArgumentParser()
    parser.add_argument('--before',type=Path,required=True)
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args()
    ctx=moderngl.create_standalone_context(require=430,backend='egl')
    report={'gpu':ctx.info['GL_RENDERER'],'equivalence':compare(ctx,args.before)}
    print(report,flush=True)
    checks(ctx)
    report['independent_residual_oracle']=oracle(ctx)
    report['independent_convolution_oracle']=convolution_checks(ctx)
    args.report.write_text(json.dumps(report,indent=2)+'\n')
    print('PASS: optimized shaders and independent CFA oracles',flush=True)
