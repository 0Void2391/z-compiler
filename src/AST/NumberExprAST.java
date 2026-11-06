package src.AST;

import src.Codegen.IRBuilder;
import src.Parser.TypeRef;
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
    public LLVMTextUnit codeGen(final IRBuilder builder) {
        final ZType type = type(builder);
        return new LLVMTextUnit(floating ? type.literal(real) : type.literal(integer), type);
    }

    @Override
    public LLVMTextUnit constantValue(final IRBuilder builder) {
        return codeGen(builder);
    }

    @Override
    public LLVMTextUnit coerce(final IRBuilder builder, final LLVMTextUnit llvmTextUnit, final TypeRef target) {
        if (target != null && target != type(null)) {
            if (floating) return new LLVMTextUnit(target.literal(real), target);
            if (target.isIntegral()) return new LLVMTextUnit(target.literal(integer), target);
            if (target.isFloat()) return new LLVMTextUnit(target.literal((double) integer), target);
        }
        return super.coerce(builder, llvmTextUnit, target);
    }
}
