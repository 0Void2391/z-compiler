package src.AST;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Codegen.SymbolTable;
import src.Parser.ZType;

/**
 *
 */
public final class DeclaratorExprAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(DeclaratorExprAST.class);

    private final ZType type;
    private final String name;
    private final ExprAST initialiser;
    private final boolean constant;
    private final boolean global;

    public DeclaratorExprAST(final ZType type, final String name, final ExprAST initialiser, final boolean constant, boolean globals) {
        this.type = type;
        this.name = name;
        this.initialiser = initialiser;
        this.constant = constant;
        this.global = globals;
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
    public @Nullable Value codeGen(final IRBuilder builder) {
        if (builder.symbols().lookupCurrentScope(name) != null && !global) {
            logger.error("Error: Shadowing or redeclaration of the local variable: {}",  name);
            builder.markFailed();
            return null;
        }

        String text;
        if (initialiser == null) {
            text = type.initial();
        } else {
            final Value value = initialiser.codeGen(builder);
            if (value == null) {
                builder.markFailed();
                return null;
            }

            final Value fitted = initialiser.coerce(builder, value, type);
            if (fitted == null) return null;
            text = fitted.text();
        }

        if(!global) {
            final String slot = builder.emitAlloca(name, type.llvm());
            builder.emitStore(text, type.llvm(), slot);
            builder.symbols().define(name, new SymbolTable.SymbolInfo(name, type, slot, constant));
        }
        else {
            final String slot = builder.emitAllocaGlobal(name, type.llvm());
            builder.emitStore(text, type.llvm(), slot);
        }


        return null;
    }
}
