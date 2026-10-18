package src.AST;

import java.util.ArrayList;
import java.util.List;

public final class ModuleAST {
    private final String moduleName;
    private final List<ImportAST> imports = new ArrayList<>();
    private final List<ExternExprAST> externals = new ArrayList<>();
    private final List<FunctionAST> functions = new ArrayList<>();

    public ModuleAST(final String moduleName) {
        this.moduleName = moduleName;
    }

    public String name() {
        return moduleName;
    }

    public void addImport(final ImportAST anImport) {
        imports.add(anImport);
    }

    public void addExtern(final ExternExprAST extern) {
        externals.add(extern);
    }

    public void addFunction(final FunctionAST function) {
        functions.add(function);
    }

    public List<ImportAST> imports() {
        return imports;
    }

    public List<ExternExprAST> externs() {
        return externals;
    }

    public List<FunctionAST> functions() {
        return functions;
    }
}
