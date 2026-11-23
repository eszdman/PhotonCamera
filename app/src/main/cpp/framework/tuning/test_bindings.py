#
# Created by eszdman on 20.11.2026.
#

"""A second group must acquire Java and native bindings without generator changes."""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

TOOLS = Path(__file__).resolve().parent
CLANG = sys.argv[1]


def check(directory):
    cpp = directory / "settings.cpp"
    template = directory / "Settings.java.in"
    native = directory / "bindings.inc"
    java = directory / "Settings.java"
    source = (TOOLS / "tests/Settings.cpp").read_text()
    for dependency in ("Annotations.hpp", "Loader.hpp"):
        source = source.replace(f'"../{dependency}"', json.dumps((TOOLS / dependency).as_posix()))
    cpp.write_text(source)
    generator = [sys.executable, str(TOOLS / "generate.py"), str(cpp), "--clang", CLANG]
    subprocess.run([*generator, native], check=True)
    template.write_text('''import com.particlesdevs.photoncamera.settings.annotations.Tunable;
import java.util.Arrays;
public class @GROUP@ {
@FIELDS@
    double[] pack() { return new double[] {
@WIRE@
    }; }
    public static void main(String[] args) {
        Settings s = new Settings();
        s.enabled = false;
        s.mode = "small";
        for (double value : s.pack()) System.out.println(value);
    }
}
''')
    subprocess.run([*generator, java, "--java-template", template], check=True)
    assert "public int count = 6;" in java.read_text()
    assert "scratch" not in java.read_text() and "scratch" not in native.read_text()
    if len(sys.argv) == 4:
        javac, jvm = sys.argv[2:]
        annotation = TOOLS.parents[2] / "java/com/particlesdevs/photoncamera/settings/annotations/Tunable.java"
        subprocess.run([javac, "-d", directory, annotation, java], check=True)
        packet = subprocess.check_output([jvm, "-cp", directory, "Settings"])
        cpp.write_text(source + '''

#include <array>
#include <iostream>
#include <sstream>
int main() {
    using T = example::Settings;
    std::array<double, photon::tuning::Schema<T>::packedSize> packet{};
    for (double& value : packet) if (!(std::cin >> value)) return 1;
    T live;
    live.scratch = 42;
    live.refresh(packet.data(), packet.size());
    if (!live.matches(6)) return 2;
    std::istringstream file("count=-3\\n");
    photon::tuning::inject(live, file);
    if (!live.matches(-3)) return 3;
    packet[1] = 1; // A valid change followed by an invalid enum slot.
    packet.back() = -1;
    try { live.refresh(packet.data(), packet.size()); return 4; }
    catch (const std::invalid_argument&) { }
    std::istringstream invalid("count=1\\nmode=invalid\\n");
    try { photon::tuning::inject(live, invalid); return 5; }
    catch (const std::invalid_argument&) { }
    return live.matches(-3) ? 0 : 6;
}
''')
        runner = directory / "check.exe"
        subprocess.run([CLANG, "-std=c++17", "-I", TOOLS, cpp, "-o", runner], check=True)
        subprocess.run([runner], input=packet, check=True)

    # Even a stale binding or target-specific initializer must fail native compilation.
    cpp.write_text(source.replace("2 * 3", "7"))
    stale = subprocess.run([CLANG, "-std=c++17", "-fsyntax-only", "-I", TOOLS, cpp],
                           capture_output=True, text=True)
    assert stale.returncode and "count: binding default mismatch" in stale.stderr, stale.stderr

    for old, new, message in [
        ("2 * 3", "12", "count: default outside range"),
        ("-8, 8, 1", "8, -8, 1", "count: invalid range"),
        ("int count", "long count", "count: unsupported tunable type"),
        ("int scratch", 'TUNABLE("Test", "Duplicate") bool ENABLED = true; int scratch',
         "duplicate case-insensitive preference key"),
        ("large TUNABLE_LABEL(\"Large\")", "large = 3", "enum choices must be contiguous"),
    ]:
        cpp.write_text(source.replace(old, new))
        failure = subprocess.run([*generator, native], capture_output=True, text=True)
        assert failure.returncode and message in failure.stderr, failure.stderr


def check_node_fields(directory):
    # A new field on a node without any class annotation must feed both targets.
    header = directory / "Focus.hpp"
    header.write_text('#include ' + json.dumps(str(TOOLS / "Annotations.hpp")) + """
namespace camera {
struct Focus {
    TUNABLE("Detail", "Strength", 0, 2, 0.01) float strength = 0.5f;
    int scratch = 0;
};
}
""")
    generator = [sys.executable, TOOLS / "generate.py", header, "--clang", CLANG]
    reader = directory / "reader.inc"
    subprocess.run([*generator, "--post-reader", reader], check=True)
    template = directory / "Focus.java.in"
    template.write_text("class @GROUP@ {\n@FIELDS@\n}")
    java = directory / "Focus.java"
    subprocess.run([*generator, "--java-template", template, java], check=True)
    assert "public float strength = 0.5f;" in java.read_text()
    assert "scratch" not in reader.read_text() and "scratch" not in java.read_text()
    probe = directory / "focus.cpp"
    probe.write_text("""#include <type_traits>
#include <string_view>
#include "Focus.hpp"
#include "reader.inc"
int main() {
    camera::Focus node;
    readFields(node, [](std::string_view key, float& field) {
        if (key == "pref_tunable_focus_strength") field = 1.5f;
    });
    return node.strength == 1.5f ? 0 : 1;
}
""")
    exe = directory / "focus.exe"
    subprocess.run([CLANG, "-std=c++17", probe, "-o", exe], check=True)
    subprocess.run([exe], check=True)


with tempfile.TemporaryDirectory() as temp:
    check(Path(temp))
    check_node_fields(Path(temp))
