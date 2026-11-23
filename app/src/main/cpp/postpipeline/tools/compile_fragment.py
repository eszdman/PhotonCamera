#
# Created by eszdman on 21.11.2026.
#

"""Compile a PostPipeline fragment asset as a compute pass with typed host bindings."""
import argparse
import re
import subprocess
from pathlib import Path
from shader_includes import expand, write_depfile

TYPES = {
    "mat3": ("std::array<std::array<float, 4>, 3>", 16), "ivec4": ("std::array<int32_t,4>",16),
    "float": ("float", 4), "int": ("int32_t", 4), "bool": ("int32_t", 4),
    "vec2": ("std::array<float, 2>", 8), "ivec2": ("Size", 8),
    "vec3": ("std::array<float, 3>", 16), "vec4": ("std::array<float, 4>", 16),
}


def reachable_functions(body):
    """Discard unused GLSL helpers before deriving the color shader interface."""
    functions = []
    pattern = re.compile(r'^\s*(?:\w+\s+)+(\w+)\s*\([^;{}]*?\)\s*\{', re.M)
    pos = 0
    while match := pattern.search(body, pos):
        depth = 1
        end = match.end()
        while depth:
            depth += (body[end] == '{') - (body[end] == '}')
            end += 1
        functions.append((match[1], match.start(), end))
        pos = end
    live = {'main'}
    while True:
        calls = {other for name, start, end in functions if name in live
                 for other in re.findall(r'\b(\w+)\s*\(', body[start:end])}
        if calls <= live:
            break
        live |= calls
    for name, start, end in reversed(functions):
        if name not in live:
            body = body[:start] + '\n' + body[end:]
    return body


def generate(args):
    dependencies = set()
    body = expand(args.source, args.root, dependencies)
    # Several GLProg assets rely on the shared yOffset injection.
    for setting in args.define:
        key, value = setting.split("=", 1)
        body, matches = re.subn(rf'^\s*#define {key}\b[^\n]*', f'\n#define {key} {value}', body, flags=re.M)
        if matches != 1:
            raise ValueError(f"{args.source}: expected one definition of {key}")
    for param in args.parameter:
        kind, key = param.split(":")
        body, matches = re.subn(rf'^\s*#define {key}\b[^\n]*', f'\nuniform {kind} {key};', body, flags=re.M)
        if matches != 1:
            raise ValueError(f"{args.source}: expected one parameter {key}")

    result = subprocess.run([args.compiler, '-E', '-fshader-stage=' + ('comp' if args.compute else 'frag'), '-'], check=True,
                            input='#version 310 es\n' + body, stdout=subprocess.PIPE, text=True)
    body = re.sub(r'^\s*#(?:version|line|extension)[^\n]*|\bprecision\s+\w+\s+\w+\s*;', '', result.stdout, flags=re.M)
    body = re.sub(r'\b(?:lowp|mediump|highp)\s+', '', body)
    if args.large:
        body = reachable_functions(body)
    uniform = re.compile(r'\buniform\s+(\w+)\s+(\w+)\s*;')
    image = re.compile(r'layout\s*\(\s*rgba16f\s*,\s*binding\s*=\s*\d+\s*\)\s*writeonly\s+uniform\s+image2D\s+(\w+)\s*;')
    images = image.findall(body)
    body = image.sub('', body)
    fields = uniform.findall(body)
    body = uniform.sub('', body)
    block = re.compile(r'layout\s*\(\s*std430\s*,\s*binding\s*=\s*\d+\s*\)\s*buffer\s+(\w+)\s*\{([^}]+)\}\s*;')
    blocks = dict(block.findall(body))
    body = block.sub('', body)
    output = None
    if not args.compute:
        out = re.search(r'\bout\s+(float|vec[234])\s+(\w+)\s*;', body)
        if not out:
            raise ValueError(f"{args.source}: missing fragment output")
        kind, output = out.groups()
        body = body[:out.start()] + f'{kind} {output};' + body[out.end():]
    used = [(kind, name) for kind, name in fields if re.search(rf'\b{name}\b', body)]
    inputs = [name for kind, name in used if kind == 'sampler2D']
    params = [(kind, name) for kind, name in used if kind != 'sampler2D' and name != 'yOffset']
    resources = [*inputs, *blocks, *images] if args.compute else [*inputs, output]
    n = len(resources)
    shader = args.name
    cpp = ['#pragma once', '#include "framework/post/PostPass.hpp"', 'namespace photon::post::shaders {',
           f'struct {shader} {{', f'    static constexpr char asset[] = "{shader}";',
           f'    static constexpr unsigned count = {n};', '    struct Parameters {',
           f'        alignas(8) std::array<Size, {n}> sizes{{}};']
    variants = [item.split('=',1) for item in args.define]
    switches = [f'        static constexpr int {key} = {value};' for key, value in variants if re.fullmatch(r'-?\d+',value)]
    if switches:
        cpp[5:5] = ['    struct Variant {', *switches, '    };']
    x, y = args.compute or (8, 8)
    cpp.insert(5, f'    static constexpr unsigned localX = {x}, localY = {y};')
    cpp.insert(5, f'    static constexpr bool parameterBuffer = {str(args.large).lower()};')
    layout = f'layout(std430,binding={n}) readonly buffer' if args.large else 'layout(push_constant) uniform'
    gl = ['#version 450' , f'layout(local_size_x={x}, local_size_y={y}) in;',
          f'{layout} Parameters {{ ivec2 sizes[{n}];']
    for typ, key in params:
        cpp_type, align = TYPES[typ]
        cpp.append(f'        alignas({align}) {cpp_type} {key}{{}};')
        gl.append(f'    {typ} {key};')
    cpp.append('    };')
    for _, key in params:
        cpp.append(f'    static constexpr auto {key} = &Parameters::{key};')
    if not args.large:
        cpp.append('    static_assert(sizeof(Parameters) <= 128);')
    gl += ['};','const int yOffset = 0;']
    fetch = []
    for i, key in enumerate(resources):
        if key in blocks:
            gl.append(f'layout(std430,binding={i}) buffer {key} {{ {blocks[key]} }};')
            cpp.append(f'    static constexpr ImageBinding<{shader}, {i}, Format::words> {key}{{}};')
            continue
        scalar = key in args.scalar
        pair = key in args.rg16f
        unorm = (args.unorm_output and key == output) or key in args.unorm
        wide = key in args.float_image
        fmt = 'rgba32f' if wide else 'rgba8' if unorm else 'r32f' if scalar else 'rg16f' if pair else 'rgba16f'
        cpp.append(f'    static constexpr ImageBinding<{shader}, {i}, Format::{fmt}> {key}{{}};')
        element = 'vec4' if wide else 'uint' if unorm or pair else 'float' if scalar else 'uvec2'
        access = 'writeonly' if key == output or key in images else 'readonly'
        gl.append(f'layout(std430, binding={i}) {access} buffer Buffer{i} {{ {element} data[]; }} buffer{i};')
        if key in images:
            gl.append(f'const int {key} = {i};')
            gl.append(f'void store_{key}(ivec2 p, vec4 v) {{ buffer{i}.data[p.y*sizes[{i}].x+p.x] = '
                      'uvec2(packHalf2x16(v.rg),packHalf2x16(v.ba)); }')
            continue
        if key == output:
            continue
        gl.append(f'const int {key} = {i};')
        value = (f'return buffer{i}.data[idx];' if wide else
                 f'return unpackUnorm4x8(buffer{i}.data[idx]);' if unorm else
                 f'return vec4(buffer{i}.data[idx], 0, 0, 1);' if scalar else
                 f'return vec4(unpackHalf2x16(buffer{i}.data[idx]), 0, 1);' if pair else
                 f'uvec2 v = buffer{i}.data[idx]; return vec4(unpackHalf2x16(v.x), unpackHalf2x16(v.y));')
        fetch.append(f'case {i}: {{ {value} }}')
    zero_border = [f'if (id == {resources.index(name)} && (any(lessThan(p,ivec2(0))) || any(greaterThanEqual(p,s)))) return vec4(0);'
                   for name in args.zero_border]
    # texelFetch outside the image is undefined in GLES. Use clamped coordinates for robust
    # access, and clamp the four taps of a filtered sampler to the edge.
    gl += ['ivec2 postSize(int id, int lod) { return sizes[id]; }',
           'vec4 postFetch(int id, ivec2 p, int lod) { ivec2 s = sizes[id];',
           *zero_border, 'p = clamp(p, ivec2(0), s-1);',
           'int idx = p.y*s.x + p.x; switch(id) {', *fetch, '} return vec4(0); }',
           'vec4 postSample(int id, vec2 uv) { vec2 p = uv * vec2(sizes[id]) - 0.5;',
           'ivec2 a = ivec2(floor(p)), m = sizes[id]-1; vec2 f=fract(p);',
           'return mix(mix(postFetch(id,clamp(a,ivec2(0),m),0),postFetch(id,clamp(a+ivec2(1,0),ivec2(0),m),0),f.x),',
           'mix(postFetch(id,clamp(a+ivec2(0,1),ivec2(0),m),0),postFetch(id,clamp(a+1,ivec2(0),m),0),f.x),f.y); }']
    substitutions = {'sampler2D':'int', 'sampler':'samplerIndex', 'texelFetch':'postFetch',
                     'textureSize':'postSize', 'texture':'postSample', 'main':'runFragment',
                     'gl_FragCoord':'(vec4(vec2(gl_GlobalInvocationID.xy)+0.5,0,1))'}
    for key in images:
        body = re.sub(rf'\bimageStore\s*\(\s*{key}\s*,', f'store_{key}(', body)
    if args.compute:
        substitutions.pop('main')
    body = re.sub(r'\b('+'|'.join(substitutions)+r')\b', lambda m: substitutions[m[0]], body)
    if args.compute:
        gl.append(body)
    else:
        as_rgba = {'float':f'vec4({output},0,0,1)', 'vec2':f'vec4({output},0,1)',
                   'vec3':f'vec4({output},1)', 'vec4':output}[kind]
        store = 'unpackHalf2x16(packHalf2x16(v.rg)).x' if output in args.scalar else 'uvec2(packHalf2x16(v.rg),packHalf2x16(v.ba))'
        if output in args.rg16f:
            store = 'packHalf2x16(v.rg)'
        if args.unorm_output:
            store = 'packUnorm4x8(v)'
        gl += [body, f'void main() {{ ivec2 p = ivec2(gl_GlobalInvocationID.xy), s = sizes[{n-1}];',
               'if(any(greaterThanEqual(p,s))) return;', f'runFragment(); vec4 v = {as_rgba};',
               f'buffer{n-1}.data[p.y*s.x + p.x] = {store}; }}']
    args.comp.write_text('\n'.join(gl)+'\n')
    args.header.write_text('\n'.join(cpp+['};', '} // namespace photon::post::shaders'])+'\n')
    write_depfile(args.depfile, args.spv, dependencies)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    for key in ('source','root','comp','header','depfile','spv'):
        parser.add_argument(key, type=Path)
    parser.add_argument('name')
    parser.add_argument('compiler')
    for key in ('parameter','define','scalar','rg16f','unorm'):
        parser.add_argument('--'+key, action='append', default=[])
    parser.add_argument('--float-image', action='append', default=[])
    parser.add_argument('--zero-border', action='append', default=[])
    parser.add_argument('--unorm-output', action='store_true')
    parser.add_argument('--large', action='store_true')
    parser.add_argument('--compute', nargs=2, type=int)
    generate(parser.parse_args())
