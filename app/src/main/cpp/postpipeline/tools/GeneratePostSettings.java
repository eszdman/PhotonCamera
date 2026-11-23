//
// Created by eszdman on 21.11.2026.
//

// Build-time only. Parse the app's @Tunable declarations without loading Android classes.
import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.nio.file.*;
import java.util.*;

class GeneratePostSettings {
    static Map<String, ExpressionTree> arguments(AnnotationTree tunable) {
        var args = new HashMap<String, ExpressionTree>();
        for (var item : tunable.getArguments()) {
            var assign = (AssignmentTree) item;
            args.put(assign.getVariable().toString(), assign.getExpression());
        }
        return args;
    }

    static String declaration(VariableTree field, Map<String, ExpressionTree> args) {
        String javaType = field.getType().toString();
        String type = switch (javaType) {
            case "boolean" -> "bool";
            case "int", "float", "double" -> javaType;
            case "String", "File" -> "std::string";
            default -> throw new IllegalArgumentException("Unsupported @Tunable type: " + field);
        };
        var value = args.get("defaultValue");
        String init;
        if (javaType.equals("File")) init = "\"\"";
        else if (javaType.equals("String") && args.containsKey("entryValues")) {
            var choices = ((NewArrayTree) args.get("entryValues")).getInitializers();
            var initial = field.getInitializer();
            init = initial != null && choices.stream().anyMatch(c -> c.toString().equals(initial.toString()))
                    ? initial.toString() : choices.get(0).toString();
        } else {
            if (value == null) value = args.get("min");
            String number = value == null ? "0" : value.toString();
            init = javaType.equals("String")
                    ? "\"" + (int)Double.parseDouble(number.replaceFirst("[fFdD]$", "")) + "\""
                    : "static_cast<" + type + ">(" + number + ")";
        }
        return "    " + type + " " + field.getName() + " = " + init + ";\n";
    }
    public static void main(String[] argv) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("A JDK is required to parse PostPipeline tunables");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        StringBuilder out = new StringBuilder("#pragma once\n#include <string>\nnamespace photon::post::settings {\n");
        try (var files = compiler.getStandardFileManager(diagnostics, null, null)) {
            var sources = files.getJavaFileObjectsFromStrings(Arrays.asList(argv).subList(1, argv.length));
            var task = (JavacTask)compiler.getTask(null, files, diagnostics, List.of("-proc:none"), null, sources);
            for (var unit : task.parse()) for (var decl : unit.getTypeDecls()) {
                if (!(decl instanceof ClassTree node)) continue;
                var members = new StringBuilder();
                var reads = new StringBuilder();
                for (var item : node.getMembers()) {
                    if (!(item instanceof VariableTree field)) continue;
                    for (var annotation : field.getModifiers().getAnnotations()) {
                        if (!annotation.getAnnotationType().toString().equals("Tunable")) continue;
                        var args = arguments(annotation);
                        members.append(declaration(field, args));
                        var name = field.getName();
                        var key = ("pref_tunable_" + node.getSimpleName() + "_" + name).toLowerCase(Locale.ROOT);
                        reads.append("        read(\"").append(key).append("\", ").append(name);
                        if (args.get("entryValues") instanceof NewArrayTree choices)
                            reads.append(", {").append(String.join(", ", choices.getInitializers().stream().map(Object::toString).toList())).append("}");
                        reads.append(");\n");
                    }
                }
                if (members.length() == 0) continue;
                out.append("struct ").append(node.getSimpleName()).append(" {\n").append(members);
                out.append("    template<class Read> void readFields(Read&& read) {\n").append(reads);
                out.append("    }\n};\n");
            }
            for (var d : diagnostics.getDiagnostics())
                if (d.getKind()==Diagnostic.Kind.ERROR) throw new IllegalArgumentException(d.toString());
        }
        out.append("} // namespace photon::post::settings\n");
        Path dest = Path.of(argv[0]);
        if (!Files.exists(dest) || !out.toString().equals(Files.readString(dest))) Files.writeString(dest,out);
    }
}
