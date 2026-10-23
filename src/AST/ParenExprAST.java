package src.AST;

import src.Codegen.IRBuilder;

public final class ParenExprAST extends ExprAST {

    private final ExprAST inner;

    public ParenExprAST(final ExprAST inner) {
        this.inner = inner;
    }

    /** @return the expression inside the brackets */
    public ExprAST inner() {
        return inner;
    }

    @Override
    public src.Parser.ZType type(final IRBuilder builder) {
        return inner.type(builder);
    }

    @Override
    public Value codeGen(final IRBuilder builder) {
        return inner.codeGen(builder);
    }

    @Override
    public Value constantValue(final IRBuilder builder) {
        return inner.constantValue(builder);
    }
}
