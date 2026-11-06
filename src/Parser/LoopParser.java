package src.Parser;

import org.jspecify.annotations.Nullable;
import src.AST.BlockAST;
import src.AST.ExprAST;
import src.AST.ForAST;
import src.Lexer.Lexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static src.Parser.Parser.error;

public class LoopParser {
    private final ParserContext context;
    private final ExpressionParser expressionParser;
    private final TypeParser typeParser;
    private final Parser parser;

    public LoopParser(final Parser parser) {
        this.parser = parser;
        this.context = parser.getContext();
        this.expressionParser = parser.getExpressionParser();
        this.typeParser = parser.getParserType();
    }

    /**
     * Parses a for loop: {@code for (type name -> init | cond | op) { body }}
     *
     * @return a ForAST node or an error
     * @throws IOException if an exception is found
     */
    public @Nullable ForAST parseFor() throws IOException {
        context.next();  // skip 'for'

        if (context.getCurTok() != Lexer.Tokens.OPEN_PAR.value) {
            return error("Expected '(' after 'for'");
        }
        context.next();

        // Parse type
        final TypeRef varType = typeParser.parseType();
        if (varType == null) {
            return error("Expected type in for");
        }

        // Parse name
        if (context.getCurTok() != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected variable name in for");
        }
        final String varName = context.getLex().getIdentifierStr();
        context.next();

        // Parse initial value
        if (context.getCurTok() != Lexer.Tokens.ASSIGN.value && context.getCurTok() != Lexer.Tokens.SAME.value) {
            return error("Expected '->' or '=' for initial value in for");
        }
        context.next();
        final ExprAST init = expressionParser.parseExpression(false);
        if (init == null) return null;

        // Expect '|'
        if (context.getCurTok() != Lexer.Tokens.PARAMS.value) {
            return error("Expected '|' after initial value in for");
        }
        context.next();

        // Parse condition
        final ExprAST cond = expressionParser.parseExpression(false);
        if (cond == null) return null;

        // Expect '|'
        if (context.getCurTok() != Lexer.Tokens.PARAMS.value) {
            return error("Expected '|' after condition in for");
        }
        context.next();

        // Parse update
        final ExprAST update = expressionParser.parseExpression(false);
        if (update == null) return null;

        // Expect ')'
        if (context.getCurTok() != Lexer.Tokens.CLOSE_PAR.value) {
            return error("Expected ')' after for header");
        }
        context.next();

        // Parse body
        if (context.getCurTok() != Lexer.Tokens.OPEN_FUNC.value) {
            return error("Expected '{' for for body");
        }
        context.next();

        final List<ExprAST> bodyStatements = new ArrayList<>();
        while (context.getCurTok() != Lexer.Tokens.CLOSE_FUNC.value && context.getCurTok() != Lexer.Tokens.EOF.value) {
            if (context.getCurTok() == ';') {
                context.next();
                continue;
            }
            final ExprAST stmt = parser.parseStatement();
            if (stmt == null) return null;
            bodyStatements.add(stmt);
        }

        if (context.getCurTok() != Lexer.Tokens.CLOSE_FUNC.value) {
            return error("Expected '}' at the end of the for body");
        }
        context.next();

        final ExprAST body = bodyStatements.size() == 1 ? bodyStatements.getFirst() : new BlockAST(bodyStatements);

        return new ForAST(varType, varName, init, cond, update, body);
    }
}
