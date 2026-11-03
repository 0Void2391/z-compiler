package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Parser.TypeRef;
import src.Parser.ZType;

/**
 * {@code free(p)}: returns a block to the allocator.
 *
 * <p>A statement, not an expression: it produces nothing.
 */
public final class FreeExprAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(FreeExprAST.class);

    private final ExprAST pointer;

    public FreeExprAST(final ExprAST pointer) {
        this.pointer = pointer;
    }

    @Override
    public TypeRef type(final IRBuilder builder) {
        return ZType.VOID;
    }

    @Override
    public Value codeGen(final IRBuilder builder) {
        final Value p = pointer.codeGen(builder);
        if (p == null) return null;
        if (!p.type().isPointer() && !p.type().isString()) {
            logger.error("Error: free expects a pointer, got {}", p.type().zName());
            builder.markFailed();
            return null;
        }
        if (p.type().isString()) {
            final String data = builder.nextRegister();
            builder.appendLine(data + " = extractvalue { i8*, i64 } " + p.text() + ", 0");
            builder.emitFree(data);
            return null;
        }
        builder.emitFree(p.text());
        return null;
    }
}