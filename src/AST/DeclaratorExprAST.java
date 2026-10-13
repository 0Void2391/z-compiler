package src.AST;

import src.Codegen.IRBuilder;
import src.Codegen.SymbolTable;
import src.Parser.ZType;

/**
 *
 */
public final class DeclaratorExprAST extends ExprAST {

    private final ZType type;
    private final String name;
    private final ExprAST initialiser;
    private final boolean constant;

    public DeclaratorExprAST(final ZType type, final String name,
                             final ExprAST initialiser, final boolean constant) {
        this.type = type;
        this.name = name;
        this.initialiser = initialiser;
        this.constant = constant;
    }

    /** @return the declared type */
    public ZType declaredType() {
        return type;
    }

    /** @return the declared name */
    public String name() {
        return name;
    }

    @Override
    public ZType type(final IRBuilder builder) {
        return type;
    }

    @Override
    public Value Codegen(final IRBuilder builder) {
        if (builder.symbols().lookupCurrentScope(name) != null) {
            System.err.println("Error: Shadowing o ridichiarazione locale: " + name);
            builder.markFailed();
            return null;
        }

        String text;
        if (initialiser == null) {
            text = type.initial();
        } else {
            final Value value = initialiser.Codegen(builder);
            if (value == null) {

                builder.markFailed();
                return null;
            }

            final Value fitted = initialiser.coerce(builder, value, type);
            if (fitted == null) return null;
            text = fitted.text();
        }

        final String slot = builder.emitAlloca(name, type.llvm());
        builder.emitStore(text, type.llvm(), slot);
        builder.symbols().define(name, new SymbolTable.SymbolInfo(
                name, type, slot, constant));
        return null;
    }
}
