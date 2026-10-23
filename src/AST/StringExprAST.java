package src.AST;

import src.Codegen.IRBuilder;
import src.Parser.ZType;

import java.nio.charset.StandardCharsets;

/**
 * A string literal: {@code "hello"}.
 *
 * <p>LLVM has no string literal values. The bytes live in a private global
 * constant; the expression's value is a pointer to its first byte, obtained
 * with a {@code getelementptr}. The global is emitted through the
 * {@link IRBuilder} so that all the string literals in a module end up in
 * one place, in front of the functions that use them.
 */
public final class StringExprAST extends ExprAST {

    private final String value;

    public StringExprAST(final String value) {
        this.value = value;
    }

    /** @return the string as written, escapes already resolved */
    public String value() {
        return value;
    }

    @Override
    public ZType type(final IRBuilder builder) {
        return ZType.STRING;
    }

    @Override
    public Value codeGen(final IRBuilder builder) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        final int length = bytes.length + 1;  // +1 for the null terminator
        final String globalName = builder.emitStringConstant(bytes);

        final String reg = builder.nextRegister();
        builder.appendLine(reg + " = getelementptr inbounds [" + length + " x i8], ptr @" + globalName + ", i32 0, i32 0");
        builder.setRegisterType(reg, ZType.STRING.llvm());
        return new Value(reg, ZType.STRING);
    }

    @Override
    public Value constantValue(final IRBuilder builder) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        final String globalName = builder.emitStringConstant(bytes);
        return new Value("@" + globalName, ZType.STRING);
    }
}