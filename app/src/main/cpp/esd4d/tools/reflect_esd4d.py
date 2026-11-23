#
# Created by eszdman on 22.11.2026.
#

"""Export one typed tensor/push-constant interface per family of compiled SPIR-V variants."""
import re
import subprocess
import sys
from pathlib import Path


def reflect(disassembler, path):
    asm = subprocess.check_output([disassembler, str(path)], text=True)
    types = {key: args.split() for key, args in re.findall(r'^\s*(%\w+) = OpType(.*)$', asm, re.M)}
    names = dict(re.findall(r'OpName (%\w+) "(\w+)"', asm))
    slots = dict(re.findall(r'OpDecorate (%\w+) Binding (\d+)', asm))
    if re.search(r'OpDecorate %\w+ DescriptorSet [1-9]', asm):
        raise ValueError(f'{path}: unsupported descriptor set')
    scalars = {('Int', '32', '1'): 'std::int32_t', ('Int', '32', '0'): 'std::uint32_t',
               ('Float', '32'): 'float'}

    def scalar(key):
        return scalars[tuple(types[key])]

    def value_type(key):
        kind, *args = types[key]
        if kind == 'Vector':
            return f'std::array<{scalar(args[0])}, {args[1]}>', 4 * int(args[1])
        if kind == 'Matrix' and types[args[0]][-1] == args[1] == '3':
            return 'std::array<std::array<float, 4>, 3>', 48
        return scalar(key), 4

    ports, fields, uniform = [], [], None
    for var, ptr, storage in re.findall(r'(%\w+) = OpVariable (%\w+) (Uniform|PushConstant)\b', asm):
        block = types[ptr][-1]
        if storage == 'Uniform' and f'OpDecorate {block} BufferBlock' in asm:
            words = set()
            for member in types[block][1:]:
                while types[member][0] in ('RuntimeArray', 'Vector'):
                    member = types[member][1]
                words.add(scalar(member))
            if len(words) != 1:
                raise ValueError(f'{path}: heterogeneous buffer {block}')
            ports.append((int(slots[var]), names[block], words.pop(), f'OpDecorate {var} NonWritable' not in asm))
            continue
        if storage == 'Uniform':
            uniform = int(slots[var])
        for i, member in enumerate(types[block][1:]):
            name = re.search(rf'OpMemberName {block} {i} "(\w+)"', asm)[1]
            offset = int(re.search(rf'OpMemberDecorate {block} {i} Offset (\d+)', asm)[1])
            typ, size = value_type(member)
            if types[member][0] == 'Matrix' and (f'{block} {i} MatrixStride 16' not in asm or
                                                  f'{block} {i} ColMajor' not in asm):
                raise ValueError(f'{path}: unsupported matrix layout')
            fields.append((name, typ, size, offset))
    ports.sort()
    if [p[0] for p in ports] != list(range(len(ports))) or uniform not in (None, len(ports)) or not fields:
        raise ValueError(f'{path}: unsupported parameter/descriptor layout')
    return ports, fields, uniform is not None


def main():
    dis, catalog, dest = sys.argv[1:]
    catalog = Path(catalog)
    families, variants = {}, []
    for name, family, row in re.findall(r'ESD_VARIANT\("(\w+)", (\w+), (true|false)\)', catalog.read_text()):
        layout = reflect(dis, catalog.parent / (name + '.spv'))
        if families.setdefault(family, layout) != layout:
            raise ValueError(f'{name}: interface differs from other {family} variants')
        if row == "false":  # The runtime picks the matching workgroup for the physical device.
            variants.append((name, family))
    if not families:
        raise ValueError('No ESD4D variants found in the shader catalog')
    lines = ['#pragma once', '#include "framework/esd4d/TensorPass.hpp"', 'namespace photon::esd4d::shaders {']
    for family, (ports, fields, uniform) in families.items():
        lines.extend([f'struct {family} {{', f'    static constexpr unsigned count = {len(ports)};',
                      f'    static constexpr bool uniform = {str(uniform).lower()};',
                      '    struct ' + ('alignas(16) ' if uniform else '') + 'Parameters {'])
        cursor = 0
        for name, typ, size, offset in fields:
            if offset > cursor:
                lines.append(f'        std::array<std::byte, {offset-cursor}> paddingBefore_{name}{{}};')
            lines.append(f'        {typ} {name}{{}};')
            cursor = offset + size
        lines.append('    };')
        for name, typ, size, offset in fields:
            lines.extend([f'    static_assert(offsetof(Parameters, {name}) == {offset});',
                          f'    static constexpr auto {name} = &Parameters::{name};'])
        for slot, name, typ, writable in ports:
            lines.append(f'    static constexpr TensorPort<{family}, {slot}, {typ}, {str(writable).lower()}> {name}{{}};')
        lines.append('};')
    lines.append('} // namespace photon::esd4d::shaders\nnamespace photon::esd4d::assets {')
    for name, family in variants:
        lines.append(f'inline constexpr ShaderAsset<shaders::{family}> {name}{{"{name}"}};')
    lines.append('}\n')
    Path(dest).write_text('\n'.join(lines))


if __name__ == '__main__':
    main()
