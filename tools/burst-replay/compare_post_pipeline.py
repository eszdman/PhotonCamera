#
# Created by eszdman on 21.11.2026.
#

"""Compare Android PostPipeline.Run and the portable Kompute graph on a merged BGGR DNG.

Build the target and instrumentation with :app:packageDebug :app:packageDebugAndroidTest
and -I tools/burst-replay/post-replay.gradle, then install both APKs. The Gradle init
script gives the oracle its own package and leaves the installed camera available.
Build the native runner with cmake --build <arm64-build-dir> --target post_replay.
Example: compare_post_pipeline.py merged.dng /path/to/arm64-v8a/post_replay output --package com.particlesdevs.photoncamera.postoracle
"""
import argparse
import hashlib
import json
import math
import subprocess
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image
import rawpy


def statistics(reference, candidate):
    if reference.shape != candidate.shape or not np.array_equal(reference[:, :, 3], candidate[:, :, 3]):
        raise ValueError("Geometry or output alpha differs")
    counts = np.zeros(256, dtype=np.int64)
    for y in range(0, len(reference), 128):
        delta = np.abs(reference[y:y+128, :, :3].astype(np.int16) - candidate[y:y+128, :, :3])
        counts += np.bincount(delta.ravel(), minlength=256)
    levels = np.arange(256)
    mean_square = counts @ (levels**2) / counts.sum()
    return dict(psnr_db=-10*math.log10(mean_square/255**2) if mean_square else None,
                mean_absolute_code=float(counts @ levels / counts.sum()),
                maximum=int(np.flatnonzero(counts)[-1]),
                equal_percent=100*float(counts[0])/counts.sum(),
                within_one_percent=100*float(counts[:2].sum())/counts.sum())


def run(args):
    adb = ['adb', '-s', args.serial] if args.serial else ['adb']
    def device(*command, **kwargs):
        return subprocess.run([*adb, *map(str, command)], check=True, **kwargs)
    with tempfile.TemporaryDirectory() as directory:
        work = Path(directory)
        with rawpy.imread(str(args.dng)) as raw:
            colors = np.frombuffer(raw.color_desc, dtype="S1")[raw.raw_colors_visible[:2, :2]]
            if not np.array_equal(colors, [[b'B', b'G'], [b'G', b'R']]):
                raise ValueError("Replay fixture expects BGGR Bayer phase")
            h, w = raw.raw_image_visible.shape
            black = np.asarray(raw.black_level_per_channel, dtype='f4')
            phase = raw.raw_colors_visible[:2, :2]
            pixels = np.empty((h,w), dtype='<f2')
            for y in range(2):
                for x in range(2):
                    level = black[phase[y,x]]
                    pixels[y::2,x::2] = (raw.raw_image_visible[y::2,x::2]-level)/(raw.white_level-level)
            if args.crop:
                cw,ch = args.crop
                if min(cw,ch)<2 or cw>w or ch>h or cw%2 or ch%2:
                    raise ValueError("Crop must have even dimensions inside the DNG")
                y,x=((h-ch)//2)&~1,((w-cw)//2)&~1
                pixels=pixels[y:y+ch,x:x+cw]
                w,h=cw,ch
            pixels.tofile(work/'post-input.f16')
        # The instrumentation takes ownership of a native allocation and writes
        # the real GLES bitmap and gain map in its target app's files directory.
        target = f'/sdcard/Android/data/{args.package}/files'
        remote = device('shell','mktemp','-d','/data/local/tmp/post-replay.XXXXXXXX',
                        capture_output=True,text=True).stdout.strip()
        try:
            device('shell','mkdir','-p',target)
            device('push', work/'post-input.f16', target+'/post-input.f16')
            proc = device('shell','am','instrument','-w','-e','class',
                          'com.particlesdevs.photoncamera.processing.opengl.PostPipelineReplayTest',
                          '-e','postWidth',w,'-e','postHeight',h,'-e','postHdr',1,
                          '-e','postTone',args.tone,'-e','postRotation',args.rotation,'-e','postMatrix',args.matrix,
                          args.package+'.test/androidx.test.runner.AndroidJUnitRunner',capture_output=True,text=True)
            if 'OK (1 test)' not in proc.stdout:
                raise RuntimeError(proc.stdout)
            device('pull',target+'/post-gles.rgba8',target+'/post-gles-gain.rgba8',work)
            device('push',args.runner,remote+'/post_replay')
            device('push',work/'post-input.f16',remote+'/post-input.f16')
            results = {}
            args.output.mkdir(parents=True, exist_ok=True)
            for mode,reference_name in [('sdr','post-gles'),('gainmap','post-gles-gain')]:
                with (work/(mode+'.log')).open('w') as log:
                    command = [remote+'/post_replay',mode,w,h,1024,remote+'/post-input.f16',remote+'/result',
                               '--tone',args.tone,'--rotation',args.rotation,'--matrix',args.matrix]
                    try:
                        device('shell',*command,stdout=log,stderr=subprocess.STDOUT)
                    except subprocess.CalledProcessError as exc:
                        raise RuntimeError((work/(mode+'.log')).read_text()[-3000:]) from exc
                device('pull',remote+'/result',work/'native.rgba8')
                reference,native = [np.memmap(path,'u1',mode='r',shape=(w,h,4) if args.rotation%180 else (h,w,4))
                                    for path in (work/(reference_name+'.rgba8'),work/'native.rgba8')]
                results[mode] = statistics(reference,native)
                if mode == 'sdr':
                    for name,image in [('gles',reference),('kompute',native)]:
                        Image.fromarray(image).convert('RGB').save(args.output/(name+'.png'))
            report = dict(input=args.dng.name, width=w, height=h,
                          device=device('shell','getprop','ro.product.model',capture_output=True,text=True).stdout.strip(),
                          runner_sha256=hashlib.sha256(args.runner.read_bytes()).hexdigest(),
                          camera_parameters=dict(cfa='BGGR',white_point=[.51,1,.72],noise_s=.0001,noise_o=.000001,
                                                 sensor_to_intermediate='identity',intermediate_to_srgb=args.matrix,
                                                 gain_map='unity',tone=args.tone,rotation=args.rotation),
                          results=results)
            (args.output/'comparison.json').write_text(json.dumps(report, indent=2)+'\n')
            print(json.dumps(results, indent=2))
        finally:
            if remote.startswith('/data/local/tmp/post-replay.'):
                device('shell','rm','-r',remote)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('dng', type=Path)
    parser.add_argument('runner', type=Path, help='Android arm64 post_replay executable')
    parser.add_argument('output', type=Path)
    parser.add_argument('--serial')
    parser.add_argument('--tone', choices=['curve','sky','new','off'], default='curve')
    parser.add_argument('--rotation', type=int, choices=[0,90,180,270], default=0)
    parser.add_argument('--matrix', default='1,0,0,0,1,0,0,0,1', help='Nine row-major intermediate-to-sRGB coefficients')
    parser.add_argument('--crop', nargs=2, type=int, metavar=('WIDTH','HEIGHT'))
    parser.add_argument('--package', default='com.particlesdevs.photoncamera.postoracle')
    run(parser.parse_args())
