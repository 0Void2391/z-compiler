package src.AST;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Codegen.Loop;
import src.Parser.ZType;

/**
 * A {@code break} or {@code continue} inside a for loop.
 *
 * <p>Both jump out of the innermost loop's body: {@code break} to the end of
 * the loop, {@code continue} to its update, which then checks the condition again.
 */
public final class LoopJumpAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(LoopJumpAST.class);

    private final boolean isBreak;

    public LoopJumpAST(final boolean isBreak) {
        this.isBreak = isBreak;
    }

    /** @return true for {@code break}, false for {@code continue} */
    public boolean isBreak() {
        return isBreak;
    }

    @Override
    public ZType type(final IRBuilder builder) {
        return ZType.VOID;
    }

    @Override
    public @Nullable LLVMTextUnit codeGen(final IRBuilder builder) {
        final Loop loop = builder.currentLoop();
        if (loop == null) {
            logger.error("Error: '{}' used outside of a loop", isBreak ? "break" : "continue");
            builder.markFailed();
            return null;
        }

        if (isBreak) {
            builder.emitBr(loop.breakLabel());
        } else {
            loop.markContinued();
            builder.emitBr(loop.continueLabel());
        }
        return null;
    }
}
