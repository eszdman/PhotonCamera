#!/usr/bin/env python3
"""Compile-gate every SR shader the way the app loads it, so a broken SR
shader fails here before any device round.

Compute shaders expand the `#define LAYOUT //` placeholder into the local
size (as GLProg.setLayout does) and get `#version 310 es` prepended;
fragment shaders get the version only. Exit code 0 = all good.

Usage: python3 check_shaders.py [path-to-glslangValidator]
"""
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.normpath(os.path.join(
    HERE, "..", "..", "app", "src", "main", "assets", "shaders"))

COMPUTE = [
    "merge/srluma.glsl",
    "merge/srscatter.glsl",
    "merge/srrefine.glsl",
    "merge/srlattice.glsl",
    "merge/srlattice1.glsl",
    "merge/srrecover.glsl",
    "merge/srclear.glsl",
]
FRAGMENT = [
    "srpre/inject.glsl",
    "srpre/drizzlecompose.glsl",
    "srpre/band.glsl",
    # The reconstruction itself: needs the app's #import expansion below.
    "upscalecrop/anisoupscale.glsl",
]

UTILS = os.path.normpath(os.path.join(ASSETS, "utils"))


def expand_imports(src, depth=0):
    """Replicate GLInterface.readProgram's #import expansion: `#import name`
    loads shaders/utils/name.glsl (spaces -> underscores), recursively."""
    if depth > 8:
        return src
    out = []
    for line in src.splitlines():
        if "#import" in line and "//" not in line:
            name = line.replace("#", "").replace(" ", "_").strip()
            path = os.path.join(UTILS, name + ".glsl")
            if os.path.exists(path):
                out.append(expand_imports(open(path).read(), depth + 1))
                continue
        out.append(line)
    return "\n".join(out)

LAYOUT = ("layout(local_size_x = 8, local_size_y = 8, local_size_z = 1) in;")


def preprocess(rel, compute):
    src = expand_imports(open(os.path.join(ASSETS, rel)).read().lstrip("\n"))
    if compute:
        src = src.replace("#define LAYOUT //", LAYOUT)
        src = src.replace("\nLAYOUT\n", "\n")
    return "#version 310 es\n" + src


def main():
    glslang = sys.argv[1] if len(sys.argv) > 1 else "glslangValidator"
    failures = []
    with tempfile.TemporaryDirectory() as tmp:
        for rel in COMPUTE + FRAGMENT:
            compute = rel in COMPUTE
            stage = "comp" if compute else "frag"
            path = os.path.join(tmp, os.path.basename(rel) + "." + stage)
            with open(path, "w") as f:
                f.write(preprocess(rel, compute))
            proc = subprocess.run([glslang, "-S", stage, path],
                                  capture_output=True, text=True)
            if proc.returncode != 0:
                failures.append(rel)
                print("FAIL %s\n%s" % (rel, proc.stdout + proc.stderr))
            else:
                print("ok   %s" % rel)
    if failures:
        print("%d SR shader(s) failed: %s" % (len(failures), ", ".join(failures)))
        return 1
    print("all SR shaders compile")
    return 0


if __name__ == "__main__":
    sys.exit(main())
