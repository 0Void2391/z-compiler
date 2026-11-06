package src.AST;

import src.Codegen.IRBuilder;
import src.Parser.TypeRef;

public abstract class ExprAST {

    /**
     * What this node produces.
     *
     */
    public abstract TypeRef type(IRBuilder builder);

    /**
     * Emits the code for this node.
     *
     * @param builder the module being built
     * @return the value produced, or {@code null} if generation failed — in which
     *         case an error has already been reported
     */
    public abstract LLVMTextUnit codeGen(final IRBuilder builder);

    /**
     * Evaluates this node at compile time when it does not need instructions.
     *
     * @param builder the module being built
     * @return the constant value, or {@code null} when the node needs code generation
     */
    public LLVMTextUnit constantValue(final IRBuilder builder) {
        return null;
    }

    /**
     * Converts a generated llvmTextUnit to {@code target}, emitting the conversion.
     *
     * @return the llvmTextUnit in the requested type, or the original when it already is
     */
    public LLVMTextUnit coerce(final IRBuilder builder, final LLVMTextUnit llvmTextUnit, final TypeRef target) {
        if (llvmTextUnit == null || target == null) return llvmTextUnit;
        final TypeRef from = llvmTextUnit.type();
        if (from == target) return llvmTextUnit;

        if (from.isString() || target.isString()) {
            System.err.println("Error: cannot convert between str and " + (from.isString() ? target.zName() : from.zName()));
            builder.markFailed();
            return null;
        }

        if (from.isPointer() || target.isPointer()) {
            System.err.println("Error: cannot convert between " + from.zName() + " and " + target.zName());
            builder.markFailed();
            return null;
        }

        final String result = builder.nextRegister();

        if (from.isFloat() && target.isFloat()) {
            // float is narrower than double; double is widened to it otherwise.
            builder.appendLine(result + " = " + (target.bits() < from.bits() ? "fptrunc " : "fpext ") + from.llvm() + " " + llvmTextUnit.text() + " to " + target.llvm());
            return new LLVMTextUnit(result, target);
        }
        if (from.isFloat() && target.isIntegral()) {
            final String op = target.isUnsigned() ? "fptoui " : "fptosi ";
            builder.appendLine(result + " = " + op + from.llvm() + " " + llvmTextUnit.text() + " to " + target.llvm());
            return new LLVMTextUnit(result, target);
        }
        if (from.isIntegral() && target.isFloat()) {
            final String op = from.isUnsigned() || from.isBoolean() ? "uitofp " : "sitofp ";
            builder.appendLine(result + " = " + op + from.llvm() + " " + llvmTextUnit.text() + " to " + target.llvm());
            return new LLVMTextUnit(result, target);
        }
        if (from.isIntegral() && target.isIntegral()) {
            if (from.bits() > target.bits()) {
                builder.appendLine(result + " = trunc " + from.llvm() + " " + llvmTextUnit.text() + " to " + target.llvm());
                return new LLVMTextUnit(result, target);
            }
            if (from.bits() < target.bits()) {
                final String op = from.isUnsigned() || from.isBoolean() ? "zext " : "sext ";
                builder.appendLine(result + " = " + op + from.llvm() + " " + llvmTextUnit.text() + " to " + target.llvm());
                return new LLVMTextUnit(result, target);
            }
            // Same width, different signedness. The bits don't change; only
            // the interpretation does. No instruction needed.
            return new LLVMTextUnit(llvmTextUnit.text(), target);
        }

        // Anything else (str, void) cannot be coerced.
        return llvmTextUnit;
    }
}
