package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Parser.ZType;

/**
 * {@code *p -> value}: store {@code value} where {@code p} points.
 */
public final class AssignThroughPointerExprAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(AssignThroughPointerExprAST.class);

    private final DerefExprAST target;
    private final ExprAST value;

    public AssignThroughPointerExprAST(final DerefExprAST target, final ExprAST value) {
        this.target = target;
        this.value = value;
    }

    @Override
    public ZType type(final IRBuilder builder) {
        return ZType.VOID;
    }

    @Override
    public Value codeGen(final IRBuilder builder) {
        final Value ptr = target.pointer().codeGen(builder);
        if (ptr == null) {
            builder.markFailed();
            return null;
        }
        final ZType elem = ptr.type().elementType();
        if (elem == null) {
            logger.error("Error: '*' needs a typed pointer, got {}", ptr.type().zName());
            builder.markFailed();
            return null;
        }
        final Value v = value.codeGen(builder);
        if (v == null) {
            builder.markFailed();
            return null;
        }
        final Value fitted = value.coerce(builder, v, elem);
        if (fitted == null) return null;
        builder.emitStore(fitted.text(), elem.llvm(), ptr.text());
        return null;
    }
}