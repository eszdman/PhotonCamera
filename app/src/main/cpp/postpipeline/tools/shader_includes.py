#
# Created by eszdman on 21.11.2026.
#

"""Resolve the shared GLES asset imports and quoted GLSL includes for CMake."""
import re
from pathlib import Path

DIRECTIVE = re.compile(r'^[ \t]*#(?:import\s+(\w+)|include\s+"([^"]+)")[ \t]*(?://[^\n]*)?$', re.M)


def expand(path, root, dependencies, parents=()):
    path = path.resolve()
    if path in parents:
        raise ValueError("Cyclic GLSL include: " + " -> ".join(map(str, (*parents, path))))
    dependencies.add(path)
    def include(match):
        asset, local = match.groups()
        source = root/'utils'/f'import_{asset}.glsl' if asset else path.parent/local
        return expand(source, root, dependencies, (*parents, path))
    return DIRECTIVE.sub(include, path.read_text())


def write_depfile(path, target, sources):
    def escape(value):
        return str(value).replace('$','$$').replace('#','\\#').replace(' ','\\ ')
    path.write_text(f"{escape(target)}: " + ' '.join(escape(s) for s in sorted(sources)) + '\n')
