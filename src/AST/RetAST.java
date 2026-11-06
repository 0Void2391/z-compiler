package src.AST;

import org.jspecify.annotations.Nullable;
import src.Codegen.IRBuilder;
import src.Parser.ZType;

import java.util.Objects;

public final class RetAST extends ExprAST {
    private final ZType type;
    private final ExprAST value;

    public RetAST(final ExprAST value) {
        this.value = value;
        this.type = null;
    }

    public RetAST(final ZType type) {
        this.type = type;
        this.value = null;
    }


    @Override
    public ZType type(final IRBuilder builder) {
        return ZType.VOID;
    }

    @Override
    public @Nullable LLVMTextUnit codeGen(final IRBuilder builder) {
        if (value == null && type == null) return null;
        if (value == null) {
            builder.emitReturn(null);
            return null;
        }

        final LLVMTextUnit produced = Objects.requireNonNull(value).codeGen(builder);
        if (produced == null) {
            builder.markFailed();
            return null;
        }
        builder.emitReturn(produced);
        return null;
    }
}
