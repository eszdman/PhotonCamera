"""Run with tools/alignment-bench/.venv/bin/python (numpy + moderngl, EGL).

Checks the production GPU prewarp and mergeAlign shader against an independent
numpy Bayer-plane reference, including CFA shifts, subpixels, and H(p + residual).
No camera or training images required. Both production compute shaders run via EGL.
"""
from pathlib import Path

import moderngl
import numpy as np

SRC = Path(__file__).resolve().parents[2]
W, H = 130, 98
YY, XX = np.mgrid[:H, :W]
# Each CFA site has a distinct offset; mixing colors cannot hide in a grey test.
RAW = (0.08 + 0.15 * ((XX & 1) + 2 * (YY & 1))
       + 0.001 * XX + 0.0006 * YY).astype('f2').astype('f4')
MATRICES = [
    np.eye(3, dtype='f4'),
    np.array([[1, 0, 2.5], [0, 1, -1.25], [0, 0, 1]], dtype='f4'),
    np.array([[0.9998, -0.02, 1.3], [0.02, 0.9998, -1.1],
              [0.00004, -0.00003, 1]], dtype='f4'),
]


def sample_plane(matrix, x, y, px, py):
    z = matrix[2, 0]*x + matrix[2, 1]*y + matrix[2, 2]
    sx = (matrix[0, 0]*x + matrix[0, 1]*y + matrix[0, 2])/z
    sy = (matrix[1, 0]*x + matrix[1, 1]*y + matrix[1, 2])/z
    plane = RAW[py::2, px::2]
    u = np.clip((sx-px)/2, 0, plane.shape[1]-1)
    v = np.clip((sy-py)/2, 0, plane.shape[0]-1)
    a, b = np.floor(u).astype(int), np.floor(v).astype(int)
    c, d = np.minimum(a+1, plane.shape[1]-1), np.minimum(b+1, plane.shape[0]-1)
    dx, dy = u-a, v-b
    return ((plane[b, a]*(1-dx)+plane[b, c]*dx)*(1-dy)
            + (plane[d, a]*(1-dx)+plane[d, c]*dx)*dy)


def prewarp_checks():
    ctx = moderngl.create_standalone_context(require=430, backend='egl')
    shader = (SRC/'main/assets/shaders/alignment/gyroPrewarp.glsl').read_text()
    code = '#version 430\n'+shader.replace('#define LAYOUT //',
                '#define LAYOUT layout(local_size_x=8,local_size_y=8) in;')
    program = ctx.compute_shader(code)
    source = ctx.texture((W, H), 1, RAW.astype('f2').tobytes(), dtype='f2')
    output = ctx.buffer(reserve=W*H*2)
    source.use(0)
    output.bind_to_storage_buffer(0)
    program['rawTexture'].value = 0
    for matrix in MATRICES:
        program['gyroHomography'].write(matrix.T.copy().tobytes())
        program.run((W//2+7)//8, (H+7)//8)
        ctx.memory_barrier()
        actual = np.frombuffer(output.read(), dtype='f2').reshape(H, W)
        expected = np.zeros_like(RAW)
        for py in range(2):
            for px in range(2):
                expected[py::2, px::2] = sample_plane(matrix, XX[py::2, px::2],
                                                     YY[py::2, px::2], px, py)
        np.testing.assert_allclose(actual, expected, atol=5e-4, rtol=0)
    output.release()
    source.release()
    program.release()
    ctx.release()
    print('GPU prewarp: identity, fractional translation, projective rotation, all parity planes and edges passed')


def shader_checks(black=False):
    ctx = moderngl.create_standalone_context(require=430, backend='egl')
    shader = (SRC/'main/cpp/shaders/shared/shaders/merge/mergeAlign.glsl').read_text()
    cases = 0
    for tile in (16, 32):
        for enabled in ((1,) if black else (0, 1)):
            code = '#version 430\n'+shader.replace('#define LAYOUT //',
                    '#define LAYOUT layout(local_size_x=8,local_size_y=8) in;')
            code = code.replace('#define TILE_AL 16', f'#define TILE_AL {tile}')
            code = code.replace('#define GYRO_WARP 0', f'#define GYRO_WARP {enabled}')
            program = ctx.compute_shader(code)
            for shift in ((0, 0), (1, 0), (0, 1), (1, 1)):
                width, height = W//2+shift[0], H//2+shift[1]
                y, x = np.mgrid[:height, :width]
                packed = np.empty((height, width, 4), dtype='f2')
                for ch in range(4):
                    sx, sy = ch % 2, ch//2
                    packed[..., ch] = RAW[np.clip(2*y-shift[1]+sy, 0, H-1),
                                          np.clip(2*x-shift[0]+sx, 0, W-1)]
                residuals = ((0, 0), (0.375, -0.625)) if enabled else ((0, 0),)
                for matrix in MATRICES if enabled else MATRICES[:1]:
                    for dx, dy in residuals:
                        expected = packed.astype('f4').copy()
                        if enabled:
                            for ch in range(4):
                                sx, sy = ch % 2, ch//2
                                expected[..., ch] = sample_plane(matrix,
                                    2*(x+dx)-shift[0]+sx, 2*(y+dy)-shift[1]+sy,
                                    (sx-shift[0]) % 2, (sy-shift[1]) % 2)
                        aw, ah = W//tile+1, H//tile+1
                        atlas = np.empty((ah, aw, 4), dtype='f2')
                        atlas[:] = (np.floor(dx)/(W//2), np.floor(dy)/(H//2),
                                    dx-np.floor(dx), dy-np.floor(dy))
                        inputs = [ctx.texture((W, H), 1, RAW.astype('f2').tobytes(), dtype='f2'),
                                  ctx.texture((aw, ah), 4, atlas.tobytes(), dtype='f2')]
                        images = [ctx.texture((width, height), 4, expected.astype('f2').tobytes(), dtype='f2'),
                                  ctx.texture((width, height), 4, packed.tobytes(), dtype='f2'),
                                  ctx.texture((width, height), 4, dtype='f2')]
                        for unit, tex in enumerate(inputs):
                            tex.use(unit)
                            tex.filter = (moderngl.NEAREST, moderngl.NEAREST)
                        images[0].bind_to_image(2, read=True, write=False)
                        images[1].bind_to_image(4, read=True, write=False)
                        images[2].bind_to_image(3, read=False, write=True)
                        uniforms = dict(inTexture=0, alignmentTexture=1, exposure=1,
                                        cfaShift=shift, rawHalf=(W//2, H//2),
                                        alignmentSize=(aw, ah), shift=(0, 0),
                                        noiseS=0.01, noiseO=0.0001, minLevel=0.001,
                                        border=(16,16))
                        for key, value in uniforms.items():
                            if key in program: program[key].value = value
                        if enabled: program['gyroHomography'].write(matrix.T.copy().tobytes())
                        program.run((width+7)//8, (height+7)//8)
                        ctx.memory_barrier()
                        actual = np.frombuffer(images[2].read(), dtype='f2').reshape(expected.shape)
                        np.testing.assert_allclose(actual, expected, atol=8e-4, rtol=0,
                            err_msg=f'tile={tile}, gyro={enabled}, cfa={shift}, residual={(dx,dy)}')
                        for tex in inputs+images: tex.release()
                        cases += 1
            program.release()
    print(f'mergeAlign: {cases} shader executions passed (black={black}, all CFA offsets, both tile grids, subpixels)')
    ctx.release()


if __name__ == '__main__':
    prewarp_checks()
    shader_checks()
    RAW.fill(0)
    shader_checks(black=True)
