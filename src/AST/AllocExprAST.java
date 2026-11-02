package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Parser.TypeRef;
import src.Parser.ZType;

/**
 * {@code alloc(size)}: bump-allocates {@code size} bytes and returns a
 * {@code ptr}.
 */
public final class AllocExprAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(AllocExprAST.class);

    private final ExprAST size;

    public AllocExprAST(final ExprAST size) {
        this.size = size;
    }

    @Override
    public TypeRef type(final IRBuilder builder) {
        return ZType.PTR;
    }

    @Override
    public Value codeGen(final IRBuilder builder) {
        final Value sz = size.codeGen(builder);
        if (sz == null) return null;
        final Value fitted = size.coerce(builder, sz, ZType.UINT64);
        if (fitted == null) return null;
        return new Value(builder.emitBumpAlloc(fitted.text()), ZType.PTR);
    }
}