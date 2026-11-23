#
# Created by eszdman on 22.11.2026.
#

"""Emit references to tunable field groups owned by the native shot settings."""
import json
from pathlib import Path
import subprocess
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[2] / "framework" / "tuning"))
from generate import children, metadata


def records(node, prefix=""):
    for child in node.get("inner", []):
        if child["kind"] == "NamespaceDecl":
            yield from records(child, prefix + child["name"] + "::")
        elif child["kind"] == "CXXRecordDecl" and child.get("completeDefinition"):
            yield prefix + child["name"], child


def field_groups(decls, name, path):
    record = decls.get(name)
    if record is None:
        return
    if name.startswith("settings::") or any(metadata(f, "field") is not None for f in children(record, "FieldDecl")):
        yield path
        return
    for base in record.get("bases", []):
        parent = type_name(base)
        yield from field_groups(decls, parent, f"static_cast<{parent}&>({path})")
    for field in record.get("inner", []):
        if field["kind"] == "FieldDecl":
            yield from field_groups(decls, type_name(field), path + "." + field["name"])


def type_name(decl):
    typ = decl["type"]
    return typ.get("desugaredQualType", typ["qualType"]).removeprefix("photon::post::")


if __name__ == "__main__":
    dest, clang, *args = sys.argv[1:]
    ast = subprocess.check_output([clang, "-DPHOTON_DISCOVER_TUNABLES", "-std=c++17", "-x", "c++", "-fsyntax-only",
                                   "-Xclang", "-ast-dump=json", "-Xclang", "-ast-dump-filter=photon::post",
                                   *args], text=True).strip()
    decls = {}
    while ast:
        namespace, end = json.JSONDecoder().raw_decode(ast)
        decls.update(records(namespace))
        ast = ast[end:].lstrip()
    if "PostPipelineSettings" not in decls:
        raise ValueError("Missing native PostPipelineSettings definition")
    paths = field_groups(decls, "PostPipelineSettings", "shot")
    Path(dest).write_text('#include "PostPipeline.hpp"\n#include <tuple>\nnamespace photon::post {\n'
                          'static auto settingsLayout(PostPipelineSettings& shot) {\n    return std::tie(\n        '
                          + ',\n        '.join(paths) + ');\n}\n}\n')
