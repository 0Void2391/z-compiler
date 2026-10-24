package src.AST;

import src.Codegen.IRBuilder;
import src.Parser.ZType;

public abstract class ExprAST {

    /**
     * What this node produces.
     *
     */
    public abstract ZType type(IRBuilder builder);

    /**
     * Emits the code for this node.
     *
     * @param builder the module being built
     * @return the value produced, or {@code null} if generation failed — in which
     *         case an error has already been reported
     */
    public abstract Value codeGen(final IRBuilder builder);

    /**
     * Evaluates this node at compile time when it does not need instructions.
     *
     * @param builder the module being built
     * @return the constant value, or {@code null} when the node needs code generation
     */
    public Value constantValue(final IRBuilder builder) {
        return null;
    }

    /**
     * Converts a generated value to {@code target}, emitting the conversion.
     *
     * @return the value in the requested type, or the original when it already is
     */
    public Value coerce(final IRBuilder builder, final Value value, final ZType target) {
        if (value == null || target == null) return value;
        final ZType from = value.type();
        if (from == target) return value;

        if (from.isString() || target.isString()) {
            System.err.println("Error: cannot convert between str and " + (from.isString() ? target.zName() : from.zName()));
            builder.markFailed();
            return null;
        }

        if (from.isPointer() || target.isPointer()) {
            if (from != target) {
                System.err.println("Error: cannot convert between " + from.zName()
                        + " and " + target.zName());
                builder.markFailed();
                return null;
            }
            return value;
        }

        final String result = builder.nextRegister();

        if (from.isFloat() && target.isFloat()) {
            // float is narrower than double; double is widened to it otherwise.
            builder.appendLine(result + " = " + (target.bits() < from.bits() ? "fptrunc " : "fpext ") + from.llvm() + " " + value.text() + " to " + target.llvm());
            return new Value(result, target);
        }
        if (from.isFloat() && target.isIntegral()) {
            final String op = target.isUnsigned() ? "fptoui " : "fptosi ";
            builder.appendLine(result + " = " + op + from.llvm() + " " + value.text() + " to " + target.llvm());
            return new Value(result, target);
        }
        if (from.isIntegral() && target.isFloat()) {
            final String op = from.isUnsigned() || from.isBoolean() ? "uitofp " : "sitofp ";
            builder.appendLine(result + " = " + op + from.llvm() + " " + value.text() + " to " + target.llvm());
            return new Value(result, target);
        }
        if (from.isIntegral() && target.isIntegral()) {
            if (from.bits() > target.bits()) {
                builder.appendLine(result + " = trunc " + from.llvm() + " " + value.text() + " to " + target.llvm());
                return new Value(result, target);
            }
            if (from.bits() < target.bits()) {
                final String op = from.isUnsigned() || from.isBoolean() ? "zext " : "sext ";
                builder.appendLine(result + " = " + op + from.llvm() + " " + value.text() + " to " + target.llvm());
                return new Value(result, target);
            }
            // Same width, different signedness. The bits don't change; only
            // the interpretation does. No instruction needed.
            return new Value(value.text(), target);
        }

        // Anything else (str, void) cannot be coerced.
        return value;
    }
}
