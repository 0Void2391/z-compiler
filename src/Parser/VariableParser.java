package src.Parser;

import org.jspecify.annotations.Nullable;
import src.AST.DeclaratorExprAST;
import src.AST.ExprAST;
import src.Lexer.Lexer;

import java.io.IOException;

import static src.Parser.Parser.error;

public class VariableParser {
    private final ParserContext context;
    private final TypeParser typeParser;
    private final ExpressionParser parseExpression;

    public VariableParser(final Parser parser) {
        this.context = parser.getContext();
        this.typeParser = parser.getParserType();
        this.parseExpression = parser.getExpressionParser();
    }

    /**
     * Parses a definition and assignment of a constant
     *
     * @return an AST node or an error
     * @throws IOException if an exception is found
     */
    public @Nullable DeclaratorExprAST parseConstant() throws IOException {
        context.next();

        final TypeRef zType = typeParser.parseType(false);
        if (zType == null) {
            return error("Expected type after 'cn'");
        }

        if (context.getCurTok() != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected constant name");
        }
        final String name = context.getLex().getIdentifierStr();
        context.next();

        if (context.getCurTok() != Lexer.Tokens.ASSIGN.value && context.getCurTok() != Lexer.Tokens.SAME.value) {
            return error("Expected '->' or '=' in constant declaration");
        }
        context.next();

        final ExprAST value = parseExpression.parseExpression();
        if (value == null) return null;

        return new DeclaratorExprAST(zType, name, value, true, false);
    }

    /**
     * Parses a variable
     *
     * @return an AST node or an error
     * @throws IOException if an exception is found
     */
    public @Nullable DeclaratorExprAST parseVariable(boolean globals) throws IOException {
        final TypeRef zType = typeParser.parseType(false);
        if (zType == null) return null;

        if (context.getCurTok() != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected name after type");
        }
        final String name = context.getLex().getIdentifierStr();
        context.next();

        if (context.getCurTok() != Lexer.Tokens.ASSIGN.value && context.getCurTok() != Lexer.Tokens.SAME.value) {
            return new DeclaratorExprAST(zType, name, null, false, globals);
        }
        context.next();
        final ExprAST val = parseExpression.parseExpression();
        if (val == null) return null;
        return new DeclaratorExprAST(zType, name, val, false, globals);
    }
}
