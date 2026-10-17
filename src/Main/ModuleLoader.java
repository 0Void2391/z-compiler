package src.Main;

import src.AST.ExternExprAST;
import src.AST.FunctionAST;
import src.AST.ImportAST;
import src.AST.ModuleAST;
import src.Parser.Parser;
import src.lexer.Lexer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModuleLoader {

    private final Map<String, ModuleAST> compiled = new LinkedHashMap<>();

    private final Set<String> loading = new HashSet<>();

    private final String rootName;

    public ModuleLoader(final String rootName) {
        this.rootName = rootName;
    }

    public List<ModuleAST> loadAll(final List<ImportAST> imports, final Path fromDir) throws IOException {
        final List<ModuleAST> dependencies = new ArrayList<>();
        for (final ImportAST anImport : imports) {
            if (!load(anImport.moduleName(), fromDir, dependencies)) return null;
        }
        return dependencies;
    }

    private boolean load(final String moduleName, final Path fromDir, final List<ModuleAST> dependencies) throws IOException {
        if (compiled.containsKey(moduleName)) return true;

        if (moduleName.equals(rootName)) {
            System.err.println("Error: the root file '" + moduleName + "' cannot be imported");
            return false;
        }

        if (!loading.add(moduleName)) {
            System.err.println("Error: circular import involving module '" + moduleName + "'");
            return false;
        }

        try {
            final Path file = (fromDir == null ? Path.of(".") : fromDir).resolve(moduleName + ".z");
            if (!Files.isRegularFile(file)) {
                System.err.println("Error: import file '" + file + "' not found");
                return false;
            }

            final ModuleAST module = parseFile(file, moduleName);
            if (module == null) return false;

            for (final ImportAST nested : module.imports()) {
                if (!load(nested.moduleName(), file.getParent(), dependencies)) return false;
            }

            compiled.put(moduleName, module);
            dependencies.add(module);
            return true;
        } finally {
            loading.remove(moduleName);
        }
    }

    public static ModuleAST parseFile(final Path file, final String moduleName) throws IOException {
        final Lexer lexer = new Lexer(Files.newInputStream(file));
        return parseUnit(new Parser(lexer, file.getFileName().toString()), moduleName);
    }


    public static ModuleAST parseUnit(final Parser parser, final String moduleName) throws IOException {
        final ModuleAST module = new ModuleAST(moduleName);

        while (true) {
            final Outcome<ImportAST> outcome = captured(parser::ParseImport);
            if (outcome.reportedError()) return null;
            if (outcome.value() == null) break;
            module.addImport(outcome.value());
        }

        while (true) {
            final Outcome<ExternExprAST> outcome = captured(parser::ParseExtern);
            if (outcome.reportedError()) return null;
            if (outcome.value() == null) break;
            module.addExtern(outcome.value());
        }

        while (true) {
            final Outcome<FunctionAST> outcome = captured(() -> parser.ParseDefinition(false));
            if (outcome.reportedError()) return null;
            if (outcome.value() == null) {
                if (parser.atEOF()) break;
                System.err.println("Syntax error");
                return null;
            }
            module.addFunction(outcome.value());
        }

        return module;
    }

    private record Outcome<T>(T value, boolean reportedError) {
    }

    private interface ParseStep<T> {
        T run() throws IOException;
    }

    private static <T> Outcome<T> captured(final ParseStep<T> step) throws IOException {
        final PrintStream diagnostics = System.err;
        final ByteArrayOutputStream reported = new ByteArrayOutputStream();

        final T value;
        System.setErr(new PrintStream(reported, true, StandardCharsets.UTF_8));
        try {
            value = step.run();
        } finally {
            System.setErr(diagnostics);
        }

        if (reported.size() > 0) {
            diagnostics.print(reported.toString(StandardCharsets.UTF_8));
            return new Outcome<>(null, true);
        }
        return new Outcome<>(value, false);
    }
}
