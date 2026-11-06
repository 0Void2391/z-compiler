package src.Parser;

import org.jspecify.annotations.Nullable;
import src.AST.ExprAST;
import src.AST.RetAST;
import src.Lexer.Lexer;

import java.io.IOException;

public class RetParser {
    private final ParserContext context;
    private final ExpressionParser expressionParser;

    public RetParser(final Parser parser) {
        this.context = parser.getContext();
        this.expressionParser = parser.getExpressionParser();
    }

    /**
     * Parses a Ret instruction
     *
     * @return an RetAST node
     * @throws IOException if an exception is found
     */
    protected @Nullable ExprAST parseRet() throws IOException {
        boolean inTailPosition = true;

        context.next();
        if (context.getCurTok() == Lexer.Tokens.CLOSE_FUNC.value) {
            return new RetAST(ZType.VOID);
        }
        final ExprAST rightSide = expressionParser.parseExpression(inTailPosition);
        if (rightSide == null) return null;

        return new RetAST(rightSide);
    }
}
