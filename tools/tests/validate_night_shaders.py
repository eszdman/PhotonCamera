"""Compile both Night and legacy merge variants using the Android NDK glslc.

Usage: python3 tools/tests/validate_night_shaders.py /path/to/glslc
This validates GLSL ES 3.10 via the OpenGL SPIR-V frontend, not a phone driver.
"""
from pathlib import Path
import re
import subprocess
import sys
import tempfile

assets = Path(__file__).resolve().parents[2] / "app/src/main/assets/shaders"
compiler = sys.argv[1]


def expand(path):
    source = path.read_text()
    return re.sub(r"^#import (\w+)$", lambda match: expand(
        assets / "utils" / ("import_" + match[1] + ".glsl")), source, flags=re.MULTILINE)


with tempfile.TemporaryDirectory(prefix="photon-night-glsl-") as directory:
    for shader in ("merge/mergeAlign", "merge/mergeAlignFlow", "merge/mergeCombineWeight1",
                   "merge/nightUncertainty", "denoise/esd3d2_steered", "denoise/esd3d2_steered_prod",
                   "sharpening/lsharpening3", "CaptureSharpening/capturesharpening"):
        for night in (0, 1):
            raw = (assets / (shader + ".glsl")).read_text()
            # App substitutions apply to the entry asset, not to imported text.
            if "#import night_post_confidence" in raw:
                assert "#define NIGHT_CONFIDENCE 0" in raw
            source = "#version 310 es\n" + expand(assets / (shader + ".glsl"))
            source = re.sub(r"^#define LAYOUT.*$",
                            "#define LAYOUT layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;",
                            source, flags=re.MULTILINE)
            source = source.replace("#define NIGHT_WEIGHTED 0", f"#define NIGHT_WEIGHTED {night}")
            source = source.replace("#define NIGHT_CONFIDENCE 0", f"#define NIGHT_CONFIDENCE {night}")
            # Guard against the reserved-identifier regression seen on Adreno.
            uncommented = re.sub(r"//[^\n]*|/\*.*?\*/", "", source, flags=re.DOTALL)
            assert not re.search(r"\b(?:float|int|vec[234]|ivec[234])\s+(?:sampler|input|output|filter)\b", uncommented)
            suffix = "comp" if shader.startswith("merge/") else "frag"
            target = Path(directory) / f"{shader.replace('/', '-')}-{night}.{suffix}"
            target.write_text(source)
            subprocess.run([compiler, "--target-env=opengl", "-fauto-map-locations",
                            "-fauto-bind-uniforms", str(target), "-o", str(target) + ".spv"], check=True)
            print(f"PASS {shader} NIGHT_WEIGHTED={night}")
