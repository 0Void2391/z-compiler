package src.AST;

import src.Codegen.IRBuilder;
import src.Parser.ZType;


public final class NumberExprAST extends ExprAST {

    private final String literal;
    private final boolean floating;
    private final long integer;
    private final double real;

    /** A literal written without a decimal point. */
    public NumberExprAST(final String text, final long value) {
        this.literal = text;
        this.floating = false;
        this.integer = value;
        this.real = value;
    }

    /** A literal written with a decimal point or an exponent. */
    public NumberExprAST(final String text, final double value) {
        this.literal = text;
        this.floating = true;
        this.real = value;
        this.integer = (long) value;
    }

    /** @return the number as written */
    public String text() {
        return literal;
    }

    /** @return true when the source had a decimal point or an exponent */
    public boolean isFloating() {
        return floating;
    }

    @Override
    public ZType type(final IRBuilder builder) {
        return floating ? ZType.FLT64 : ZType.INT32;
    }

    @Override
    public Value codeGen(final IRBuilder builder) {
        final ZType type = type(builder);
        return new Value(floating ? type.literal(real) : type.literal(integer), type);
    }

    @Override
    public Value coerce(final IRBuilder builder, final Value value, final ZType target) {
        if (target != null && target != type(null)) {
            if (floating) return new Value(target.literal(real), target);
            if (target.isIntegral()) return new Value(target.literal(integer), target);
            if (target.isFloat()) return new Value(target.literal((double) integer), target);
        }
        return super.coerce(builder, value, target);
    }
}
