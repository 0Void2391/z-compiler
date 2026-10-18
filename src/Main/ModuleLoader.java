package src.Main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.*;

public final class ModuleLoader {
    private static final Logger logger = LoggerFactory.getLogger(ModuleLoader.class);

    private final Map<String, ModuleAST> compiled = new LinkedHashMap<>();

    private final Set<String> loading = new HashSet<>();

    private final String rootName;

    public ModuleLoader(final String rootName) {
        this.rootName = rootName;
    }

    public List<ModuleAST> loadAll(final List<ImportAST> imports, final Path fromDir) throws IOException {
        final List<ModuleAST> dependencies = new ArrayList<>();
        for (final ImportAST anImport : imports) {
            if (load(anImport.moduleName(), fromDir, dependencies)) return Collections.emptyList();
        }
        return dependencies;
    }

    private boolean load(final String moduleName, final Path fromDir, final List<ModuleAST> dependencies) throws IOException {
        if (compiled.containsKey(moduleName)) return false;

        if (moduleName.equals(rootName)) {
            if (logger.isErrorEnabled()) {
                logger.error("Error: the root file '{}' cannot be imported", moduleName);
            }
            return true;
        }

        if (!loading.add(moduleName)) {
            if (logger.isErrorEnabled()) {
                logger.error("Error: circular import involving module '{}'", moduleName);
            }
            return true;
        }

        try {
            final Path file = (fromDir == null ? Path.of(".") : fromDir).resolve(moduleName + ".z");
            if (!Files.isRegularFile(file)) {
                if (logger.isErrorEnabled()) {
                    logger.error("Error: import file '{}' not found", file);
                }
                return true;
            }

            final ModuleAST module = parseFile(file, moduleName);
            if (module == null) return true;

            for (final ImportAST nested : module.imports()) {
                if (load(nested.moduleName(), file.getParent(), dependencies)) return true;
            }

            compiled.put(moduleName, module);
            dependencies.add(module);
            return false;
        } finally {
            loading.remove(moduleName);
        }
    }

    public static ModuleAST parseFile(final Path file, final String moduleName) throws IOException {
        final Lexer lexer = new Lexer(Files.newInputStream(file));
        return parseUnit(new Parser(lexer), moduleName);
    }


    public static ModuleAST parseUnit(final Parser parser, final String moduleName) throws IOException {
        final ModuleAST module = new ModuleAST(moduleName);

        while (true) {
            final Outcome<ImportAST> outcome = captured(parser::parseImport);
            if (outcome.reportedError()) return null;
            if (outcome.value() == null) break;
            module.addImport(outcome.value());
        }

        while (true) {
            final Outcome<ExternExprAST> outcome = captured(parser::parseExtern);
            if (outcome.reportedError()) return null;
            if (outcome.value() == null) break;
            module.addExtern(outcome.value());
        }

        while (true) {
            final Outcome<FunctionAST> outcome = captured(() -> parser.parseDefinition());
            if (outcome.reportedError()) return null;
            if (outcome.value() == null) {
                if (parser.atEOF()) break;
                logger.error("Syntax error");
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
