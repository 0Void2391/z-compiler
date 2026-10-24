package src.AST;

import src.Codegen.IRBuilder;
import src.Parser.ZType;

/** The literals {@code true} and {@code false}. */
public final class BoolExprAST extends ExprAST {

    private final boolean value;

    public BoolExprAST(final boolean value) {
        this.value = value;
    }

    @Override
    public ZType type(final IRBuilder builder) {
        return ZType.BOOL;
    }

    @Override
    public Value codeGen(final IRBuilder builder) {
        return new Value(Boolean.toString(value), ZType.BOOL);
    }

    @Override
    public Value constantValue(final IRBuilder builder) {
        return codeGen(builder);
    }
}
