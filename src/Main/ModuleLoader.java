package src.Main;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.AST.*;
import src.Parser.Parser;
import src.lexer.Lexer;

import java.io.IOException;
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
            if (load(anImport.moduleName(), fromDir, dependencies)) return null;
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
        boolean declarationsStarted = false;

        while (!parser.atEOF()) {
            final Lexer.Tokens token = Lexer.Tokens.fromValues(parser.currentToken());

            if (token == Lexer.Tokens.IMPORT) {
                if (declarationsStarted) {
                    logger.error("Error: 'import' must appear before any 'extern' or definition");
                    return null;
                }
                final ImportAST anImport = parser.parseImport();
                if (anImport == null) return null;
                module.addImport(anImport);
                continue;
            }

            declarationsStarted = true;

            if (token == Lexer.Tokens.EXTERN) {
                final ExternExprAST extern = parser.parseExtern();
                if (extern == null) return null;
                module.addExtern(extern);
            } else if (token == Lexer.Tokens.FUNC) {
                final FunctionAST function = parser.parseDefinition();
                if (function == null) return null;
                module.addFunction(function);
            } else if (parser.atTypeDeclaration()) {
                final DeclaratorExprAST variable = parser.parseVariable(true);
                if (variable == null) return null;
                module.addVar(variable);
            } else {
                if (logger.isErrorEnabled()) {
                    final String found = token == null ? String.valueOf(parser.currentToken()) : token.description;
                    logger.error("Error: unexpected token at top level: {}", found);
                }
                return null;
            }
        }

        return module;
    }
}
