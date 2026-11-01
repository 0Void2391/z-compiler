package src.Parser;

import org.jspecify.annotations.Nullable;
import src.AST.BlockAST;
import src.AST.ExprAST;
import src.AST.FunctionAST;
import src.AST.PrototypeAST;
import src.Lexer.Lexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static src.Parser.Parser.error;
import static src.Parser.TypeParser.describeToken;

public class DefinitionParser {
    private final ParserContext context;
    private final HeaderParser headerParser;
    private final Parser parser;

    public DefinitionParser(final Parser parser) {
        this.parser = parser;
        this.context = parser.getContext();
        this.headerParser = parser.getHeaderParser();
    }

    /**
     * Parses a function definition
     *
     * @return null or a FunctionAST node
     * @throws IOException if an exception is found
     */
    public @Nullable FunctionAST parseDefinition() throws IOException {
        if (context.getCurTok() == Lexer.Tokens.EOF.value) {
            return null;
        }

        if (Lexer.Tokens.fromValues(context.getCurTok()) == Lexer.Tokens.IMPORT) {
            return error("'import' must appear before any 'extern' or definition");
        }

        if (context.getCurTok() != Lexer.Tokens.FUNC.value) {
            return error("Expected 'proc' at the start of a definition, got " + describeToken(context.getCurTok()) + " instead");
        }

        // consume 'proc' so the prototype starts on the function name
        context.next();
        final PrototypeAST proto = headerParser.parsePrototype(false);
        if (proto == null) return null;

        if (context.getCurTok() == Lexer.Tokens.OPEN_FUNC.value) {
            context.next();
        }

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
            return error("Expected '}' at the end of the function body");
        }
        context.next();

        final ExprAST body = bodyStatements.size() == 1 ? bodyStatements.getFirst() : new BlockAST(bodyStatements);
        return new FunctionAST(proto, body);
    }
}
