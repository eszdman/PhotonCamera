#
# Created by eszdman on 20.11.2026.
#

"""Discover annotated native fields with Clang and emit their JNI/desktop bindings."""
import argparse
import ast
import hashlib
import json
import math
import struct
import subprocess
from dataclasses import dataclass
from pathlib import Path


def children(node, kind):
    return [item for item in node.get("inner", []) if item["kind"] == kind]


def declarations(node, namespace=""):
    for item in node.get("inner", []):
        if item["kind"] == "NamespaceDecl":
            prefix = item.get("name")
            yield from declarations(item, namespace + prefix + "::" if prefix else namespace)
        elif item["kind"] in ("EnumDecl", "CXXRecordDecl") and "name" in item and not item.get("isImplicit"):
            name = namespace + item["name"]
            yield name, item
            if item["kind"] == "CXXRecordDecl":
                yield from declarations(item, name + "::")


def field_declarations(group, decls):
    record = decls[group]
    namespace = group.rpartition("::")[0]
    for base in record.get("bases", []):
        base_type = base["type"]
        name = base_type.get("desugaredQualType", base_type["qualType"]).lstrip(":")
        qualified = namespace + "::" + name if namespace else name
        base_name = qualified if qualified in decls else name
        if base_name in decls:
            yield from field_declarations(base_name, decls)
    yield from record.get("inner", [])


def expression(expr):
    while expr["kind"] in ("ConstantExpr", "ImplicitCastExpr", "ParenExpr"):
        expr = expr["inner"][0]
    return expr


def annotation_literal(arg):
    expr = expression(arg)
    if expr["kind"] == "StringLiteral":
        # Clang prints non-ASCII UTF-8 bytes as C octal escapes.
        return ast.literal_eval("b" + expr["value"]).decode()
    if expr["kind"] in ("IntegerLiteral", "FloatingLiteral"):
        return json.loads(expr["value"])
    if arg["type"]["qualType"] in ("int", "float", "double") and "value" in arg:
        return json.loads(arg["value"])
    raise ValueError("Expected a string or numeric constant in tunable metadata")


def metadata(node, tag):
    for attr in children(node, "AnnotateAttr"):
        args = attr.get("inner", [])
        if args and expression(args[0]).get("value") == json.dumps("photon." + tag):
            return [annotation_literal(arg) for arg in args[1:]]
    return None


def parse(clang, source, namespace=None):
    cmd = [clang, "-x", "c++", "-std=c++17", "-DPHOTON_DISCOVER_TUNABLES",
           "-I", str(Path(__file__).resolve().parents[2]),
           "-Werror=constant-conversion", "-Werror=literal-conversion", "-fsyntax-only",
           "-Xclang", "-ast-dump=json"]
    if namespace:
        cmd += ["-Xclang", "-ast-dump-filter=" + namespace]
    result = subprocess.run([*cmd, "-"], input=source, stdout=subprocess.PIPE, text=True, check=True)
    return json.loads(result.stdout)


JAVA_TYPES = {"bool": "boolean", "int": "int", "float": "float", "double": "double"}


@dataclass
class Field:
    name: str
    kind: str
    category: str
    title: str
    description: str
    low: float
    high: float
    step: float
    choices: list
    labels: list
    default: float = 0


def check_default(group, f):
    # A float preference is rounded to its field type when Android stores it.
    limit = "float" if f.kind == "float" else "double"
    value = f"static_cast<double>({group}{{}}.{f.name})"
    return (f'static_assert({value} >= static_cast<{limit}>({f.low}) && '
            f'{value} <= static_cast<{limit}>({f.high}), "{f.name}: default outside range");')


def discover(clang, source_file, allow_empty=False):
    include = '#include ' + json.dumps(source_file.resolve().as_posix()) + '\n'
    decls = dict(declarations(parse(clang, include)))
    groups = [(name, node) for name, node in decls.items() if metadata(node, "group") is not None]
    if not groups:
        groups = [(name, node) for name, node in decls.items()
                  if any(metadata(field, "field") is not None for field in node.get("inner", []))]
    if not groups and allow_empty:
        return None
    if len(groups) != 1:
        raise ValueError("Expected one tunable class in " + str(source_file))
    group, record = groups[0]
    java_name, = metadata(record, "group") or [record["name"]]
    fields, keys = [], set()
    probe = [include, "namespace photon::tuning {", f"template<> struct Schema<::{group}> {{",
             f"using Group = ::{group};"]
    for node in field_declarations(group, decls):
        args = metadata(node, "field")
        if args is None:
            continue
        name = node["name"]
        if node["kind"] != "FieldDecl":
            raise ValueError(f"{name}: TUNABLE requires a non-static data member")
        if name.lower() in keys:
            raise ValueError(f"{name}: duplicate case-insensitive preference key")
        keys.add(name.lower())
        if len(args) < 2:
            raise ValueError(f"{name}: expected category and title")
        category, title, *bounds = args
        description = bounds.pop(0) if bounds and isinstance(bounds[0], str) else ""
        if not all(isinstance(s, str) for s in [category, title]):
            raise ValueError(f"{name}: expected category and title strings")
        kind = node["type"].get("desugaredQualType", node["type"]["qualType"]).replace("(anonymous namespace)::", "")
        local_type = group.rpartition("::")[0] + "::" + kind
        if local_type in decls:
            kind = local_type
        if group + "::" + kind in decls:
            kind = group + "::" + kind
        choices, labels = [], []
        if kind in decls and decls[kind]["kind"] == "EnumDecl":
            entries = children(decls[kind], "EnumConstantDecl")
            if not entries:
                raise ValueError(f"{name}: a choice needs at least one enumerator")
            for index, entry in enumerate(entries):
                choice = entry["name"]
                choices.append(choice)
                label = metadata(entry, "label")
                labels.append(label[0] if label else choice)
                probe.append(f'static_assert(static_cast<int>(::{kind}::{choice}) == {index}, '
                             f'"{name}: enum choices must be contiguous from zero");')
        elif kind not in JAVA_TYPES:
            raise ValueError(f"{name}: unsupported tunable type {kind}")
        if choices or kind == "bool":
            if bounds:
                raise ValueError(f"{name}: ranges apply only to numeric fields")
            low, high, step = 0, len(choices) - 1 if choices else 1, 1
        else:
            if len(bounds) != 3 or not all(isinstance(n, (int, float)) and math.isfinite(n) for n in bounds):
                raise ValueError(f"{name}: expected a finite minimum, maximum and step")
            low, high, step = bounds
            if low >= high or not 0 < step <= high - low:
                raise ValueError(f"{name}: invalid range or step")
            if kind == "int" and not all(float(n).is_integer() for n in bounds):
                raise ValueError(f"{name}: integer tunables require integral bounds and step")
        if not node.get("hasInClassInitializer"):
            raise ValueError(f"{name}: missing default initializer")
        # Have the compiler evaluate the initializer, including constexpr expressions.
        # Extract its IEEE bits rather than Clang's rounded diagnostic float text.
        probe.append(f'static constexpr double value_{name} = static_cast<double>(Group{{}}.{name});')
        field = Field(name, kind, category, title, description, low, high, step, choices, labels)
        probe.append(check_default("Group", field))
        fields.append(field)
    if not fields:
        raise ValueError(f"{group}: no annotated fields")
    probe += ["}; }", "namespace photon_tuning_defaults {", f"using S = ::photon::tuning::Schema<::{group}>;"]
    for field in fields:
        probe.append(f'[[clang::annotate("value", __builtin_bit_cast(unsigned long long, S::value_{field.name}))]] '
                     f'extern int {field.name};')
    probe.append("}")
    evaluated = parse(clang, "\n".join(probe), "photon_tuning_defaults")
    values = {v["name"]: int(children(v, "AnnotateAttr")[0]["inner"][0]["value"])
              for v in children(evaluated, "VarDecl") if children(v, "AnnotateAttr")}
    for field in fields:
        field.default = struct.unpack(">d", values[field.name].to_bytes(8, "big"))[0]
    return group, java_name, fields


def java_number(value):
    return str(value) + ("f" if isinstance(value, float) else "")


def java_source(template, group, fields, tag):
    declarations, packed = [], [f"            {tag}L,"]
    for f in fields:
        meta = [f"{k} = {json.dumps(getattr(f, k))}" for k in ("title", "category", "description")]
        value = f.name
        if f.choices:
            kind = "String"
            options = ", ".join(map(json.dumps, f.choices))
            meta += ["entries = {" + ", ".join(map(json.dumps, f.labels)) + "}",
                     "entryValues = {" + options + "}", "defaultValue = " + str(int(f.default))]
            initial = json.dumps(f.choices[int(f.default)])
            value = f"Arrays.asList({options}).indexOf({f.name})"
        else:
            kind = JAVA_TYPES[f.kind]
            initial = java_number(f.default) if kind == "float" else str(f.default)
            meta += [f"{key} = {java_number(v)}" for key, v in
                     [("min", f.low), ("max", f.high), ("step", f.step), ("defaultValue", f.default)]]
            if kind == "boolean":
                initial, value = ("true" if f.default else "false"), value + " ? 1 : 0"
            elif kind == "int":
                initial = str(int(f.default))
        declarations.append("    @Tunable(" + ", ".join(meta) + f")\n    public {kind} {f.name} = {initial};")
        packed.append(f"            {value},")
    return (template.read_text().replace("@GROUP@", group).replace("@FIELDS@", "\n\n".join(declarations))
            .replace("@WIRE@", "\n".join(packed)))


def binding_checks(fields):
    lines = []
    # Cross-compilation can see different platform constants than discovery.
    # Verify the target compiler agrees with the contract used by Java.
    for f in fields:
        lines.append(f'    static_assert(std::is_same_v<decltype(Group::{f.name}), {f.kind}>, '
                     f'"{f.name}: binding type mismatch");')
        lines.append(f'    static_assert(static_cast<double>(Group{{}}.{f.name}) == {f.default!r}, '
                     f'"{f.name}: binding default mismatch");')
        for index, choice in enumerate(f.choices):
            lines.append(f'    static_assert(static_cast<int>(::{f.kind}::{choice}) == {index}, '
                         f'"{f.name}: binding choice mismatch");')
    return lines


def post_reader(group, java_name, fields):
    namespace, _, name = group.rpartition("::")
    lines = [f"namespace {namespace} {{", "template<class Read>",
             f"void readFields({name}& obj, Read&& read) {{", f"    using Group = {name};",
             *binding_checks(fields)]
    for f in fields:
        key = f"pref_tunable_{java_name}_{f.name}".lower()
        choices = ", {" + ", ".join(map(json.dumps, f.choices)) + "}" if f.choices else ""
        lines.append(f'    read("{key}", obj.{f.name}{choices});')
    return "\n".join([*lines, "}", "}", ""])


def cpp_source(group, fields, tag):
    lines = ["// Generated from annotated C++ members.", "#pragma once", "namespace photon::tuning {",
             f"template<> struct Schema<::{group}> {{", f"    static constexpr double tag = {tag}.0;",
             f"    static constexpr std::size_t packedSize = {len(fields) + 1};",
             f"    using Group = ::{group};"]
    lines.extend(binding_checks(fields))
    lines.append("    template<class Visitor> static void visit(Group& obj, Visitor&& field) {")
    for f in fields:
        choices = ", ".join(map(json.dumps, f.choices))
        lines.append(f'        field("{f.name}", obj.{f.name}, {f.low}, {f.high}, {{{choices}}});')
    return "\n".join([*lines, "    }", "};", "} // namespace photon::tuning", ""])


def write_output(path, source):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists() or path.read_text() != source:
        path.write_text(source)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source_file", type=Path, help="C++ source or header declaring the tunable group")
    parser.add_argument("output", type=Path)
    formats = parser.add_mutually_exclusive_group()
    formats.add_argument("--java-template", type=Path)
    formats.add_argument("--post-reader", action="store_true")
    parser.add_argument("--clang", default="clang++")
    args = parser.parse_args()
    discovered = discover(args.clang, args.source_file, allow_empty=args.post_reader)
    if discovered is None:
        write_output(args.output, "// No annotated fields.\n")
        return
    group, java_name, fields = discovered
    wire = [(f.name, f.kind, f.low, f.high, f.default, f.choices) for f in fields]
    tag = int.from_bytes(hashlib.sha256(json.dumps(wire).encode()).digest()[:6], "big")
    if args.java_template:
        source = java_source(args.java_template, java_name, fields, tag)
    elif args.post_reader:
        source = post_reader(group, java_name, fields)
    else:
        source = cpp_source(group, fields, tag)
    write_output(args.output, source)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error))
