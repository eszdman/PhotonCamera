#
# Created by eszdman on 21.11.2026.
#

"""Adapt the existing AMaZE compute bodies to Vulkan storage buffers.
Emit the C++ binding names and formats from the same declarations as the SPIR-V.
"""
import argparse
import re
from pathlib import Path

from shader_includes import expand, write_depfile
UNIFORMS = {"u_size": "ivec2", "u_insize": "ivec2", "u_off": "ivec2", "u_outsize": "ivec2",
            "u_outoff": "ivec2", "u_inner": "ivec2", "u_fc": "ivec4", "u_clip": "float"}
SAMPLER = re.compile(r'^uniform sampler2D (\w+);', re.M)
IMAGE = re.compile(r'^layout\(binding\s*=\s*\d+,\s*(r32f|rgba16f)\) writeonly uniform highp image2D (\w+);', re.M)
UNIFORM = re.compile(r'^uniform (\w+) (\w+);', re.M)


def compile_pass(source, root, interface, glsl, header, depfile, spv):
    dependencies = {interface, interface.with_name('half_storage.glsl')}
    body = expand(source, root, dependencies)
    scalar_inputs = set(re.findall(r'^uniform sampler2D (\w+);[^\n]*@post-r32f', body, re.M))
    write_depfile(depfile, spv, dependencies)
    body = re.sub(r'//[^\n]*|/\*.*?\*/', '', body, flags=re.S)
    inputs = SAMPLER.findall(body)
    outputs = IMAGE.findall(body)
    body = SAMPLER.sub('', body)
    body = IMAGE.sub('', body)
    for kind, name in UNIFORM.findall(body):
        if UNIFORMS.get(name) != kind:
            raise ValueError(f"{source}: unsupported uniform {kind} {name}")
    body = UNIFORM.sub('', body)
    body = re.sub(r'^precision \w+ \w+;', '', body, flags=re.M)
    resources = [(name, 'r32f' if name in scalar_inputs else 'rgba16f', False)
                 for name in inputs if re.search(rf'\b{re.escape(name)}\b', body)]
    resources += [(name, fmt, True) for fmt, name in outputs]

    name = source.stem
    declarations = ['#version 450', interface.read_text(), interface.with_name('half_storage.glsl').read_text()]
    cpp = ['#pragma once', '#include "framework/post/PostPass.hpp"', 'namespace photon::post::shaders {',
           f'struct {name} {{', f'    static constexpr char asset[] = "amaze_{name}";',
           '    using Parameters = Tile;', '    static constexpr bool parameterBuffer = false;',
           f'    static constexpr unsigned count = {len(resources)};']
    for i, (key, fmt, output) in enumerate(resources):
        scalar = fmt == 'r32f'
        element = 'float' if scalar else 'uvec2'
        access = 'writeonly' if output else 'readonly'
        declarations.append(f'layout(std430, binding={i}) {access} buffer B{i} {{ {element} data[]; }} {key};')
        pitch = 'tile.imageSize.x' if key == 'u_in' or (name == 'final' and output) else 'tile.windowSize.x'
        index = f'p.y * {pitch} + p.x'
        if output:
            value = 'v.x' if scalar else 'uvec2(storeHalfPair(v.rg), storeHalfPair(v.ba))'
            declarations.append(f'void write_{key}(ivec2 p, vec4 v) {{ {key}.data[{index}] = {value}; }}')
        elif scalar:
            declarations.append(f'vec4 read_{key}(ivec2 p) {{ return vec4({key}.data[{index}], 0, 0, 1); }}')
        else:
            declarations.append(f'vec4 read_{key}(ivec2 p) {{ uvec2 v = {key}.data[{index}]; '
                                'return vec4(loadHalfPair(v.x), loadHalfPair(v.y)); }')
        cpp.append(f'    static constexpr ImageBinding<{name}, {i}, Format::{fmt}> {key}{{}};')
    cpp += ['};', '} // namespace photon::post::shaders']
    glsl.write_text('\n'.join([*declarations, body]))
    header.write_text('\n'.join(cpp) + '\n')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for argument in ['source', 'root', 'interface', 'glsl', 'header', 'depfile', 'spv']:
        parser.add_argument(argument, type=Path)
    compile_pass(**vars(parser.parse_args()))
