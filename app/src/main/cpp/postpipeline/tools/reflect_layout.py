#
# Created by eszdman on 21.11.2026.
#

"""Generate host ABI assertions from the final SPIR-V module."""
import re
import subprocess
import sys
from pathlib import Path

disassembler, module, header, shader, block = sys.argv[1:]
assembly = subprocess.check_output([disassembler, module], text=True)
pattern = rf'OpMemberName %{block} (\d+) "(\w+)"'
members = {int(index): name for index, name in re.findall(pattern, assembly)}
offsets = {int(i): int(n) for i, n in re.findall(rf'OpMemberDecorate %{block} (\d+) Offset (\d+)', assembly)}
if not members or members.keys() != offsets.keys():
    raise ValueError(f"{module}: incomplete {block} reflection")
if block == "Tile":
    host_type = "photon::post::Tile"
else:
    host_type = f"photon::post::shaders::{shader}::Parameters"
lines = ['#include <cstddef>']
for index, member in members.items():
    lines.append(f'static_assert(offsetof({host_type}, {member}) == {offsets[index]}, "{shader}.{member} ABI");')
bindings = sorted(int(b) for b in re.findall(r'OpDecorate %\w+ Binding (\d+)', assembly))
if bindings != list(range(len(bindings))) or re.search(r'OpDecorate %\w+ DescriptorSet [1-9]', assembly):
    raise ValueError(f"{module}: descriptor set or binding layout differs from the host")
storage_params = 'false' if block == 'Tile' else f'photon::post::shaders::{shader}::parameterBuffer'
lines.append(f'static_assert(photon::post::shaders::{shader}::count + {storage_params} == {len(bindings)}, "{shader} descriptors");')
with Path(header).open('a') as out:
    out.write('\n'+'\n'.join(lines)+'\n')
