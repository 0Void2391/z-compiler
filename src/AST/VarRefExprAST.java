package src.AST;

import src.Codegen.IRBuilder;
import src.Codegen.SymbolTable;
import src.Parser.ZType;

public final class VarRefExprAST extends ExprAST {

    private final String name;

    public VarRefExprAST(final String name) {
        this.name = name;
    }

    /** @return the name being read */
    public String name() {
        return name;
    }

    @Override
    public ZType type(final IRBuilder builder) {
        final SymbolTable.SymbolInfo info = builder.symbols().lookup(name);
        return info == null ? ZType.INT32 : info.getZType();
    }

    @Override
    public Value Codegen(final IRBuilder builder) {
        final SymbolTable.SymbolInfo info = builder.symbols().lookup(name);
        if (info == null) {
            System.err.println("Error: Variabile non dichiarata: " + name);
            builder.markFailed();
            return null;
        }
        final ZType type = info.getZType();
        return new Value(builder.emitLoad(type.llvm(), info.getPointerReg()), type);
    }
}