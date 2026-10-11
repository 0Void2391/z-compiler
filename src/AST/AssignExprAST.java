package src.AST;

import src.Codegen.IRBuilder;
import src.Codegen.SymbolTable;
import src.Parser.ZType;

/**
 * Assignment expression
 */
public final class AssignExprAST extends ExprAST {

    private final String varName;
    private final ExprAST expr;

    public AssignExprAST(final String varName, final ExprAST expr) {
        this.varName = varName;
        this.expr = expr;
    }

    /** @return the variable name being assigned to */
    public String varName() {
        return varName;
    }

    /** @return the expression being assigned */
    public ExprAST expr() {
        return expr;
    }

    @Override
    public ZType type(final IRBuilder builder) {
        final SymbolTable.SymbolInfo info = builder.symbols().lookup(varName);
        if (info == null) {
            return ZType.VOID;
        }
        final String llvmType = info.getLlvmType();
        for (final ZType type : ZType.values()) {
            if (type.llvm().equals(llvmType)) return type;
        }
        return ZType.VOID;
    }

    @Override
    public Value Codegen(final IRBuilder builder) {
        final SymbolTable.SymbolInfo info = builder.symbols().lookup(varName);
        if (info == null) {
            System.err.println("Error: Undefined variable: " + varName);
            builder.markFailed();
            return null;
        }

        if (info.isConstant()) {
            System.err.println("Error: Cannot assign to constant: " + varName);
            builder.markFailed();
            return null;
        }

        final Value value = expr.Codegen(builder);
        if (value == null) {
            builder.markFailed();
            return null;
        }

        final String llvmType = info.getLlvmType();
        ZType varType = null;
        for (final ZType type : ZType.values()) {
            if (type.llvm().equals(llvmType)) {
                varType = type;
                break;
            }
        }
        if (varType == null) {
            System.err.println("Error: Unknown LLVM type: " + llvmType);
            builder.markFailed();
            return null;
        }

        final Value fitted = expr.coerce(builder, value, varType);
        if (fitted == null) {
            builder.markFailed();
            return null;
        }

        builder.emitStore(fitted.text(), varType.llvm(), info.getPointerReg());
        return fitted;
    }
}
