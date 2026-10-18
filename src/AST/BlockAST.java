package src.AST;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Parser.ZType;

import java.util.List;

/**
*
 */
public final class BlockAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(BlockAST.class);

    private final List<ExprAST> statements;

    public BlockAST(final List<ExprAST> statements) {
        this.statements = List.copyOf(statements);
    }

    /** @return the statements, in order */
    public List<ExprAST> statements() {
        return statements;
    }

    @Override
    public ZType type(final IRBuilder builder) {
        return ZType.VOID;
    }

    @Override
    public @Nullable Value codeGen(final IRBuilder builder) {
        for (final ExprAST statement : statements) {
            if (builder.isTerminated()) {
                logger.error("Error: this statement is unreachable, " + "the block has already returned");
                builder.markFailed();
                return null;
            }
            statement.codeGen(builder);
            if (builder.hasFailed()) return null;
        }
        return null;
    }
}
