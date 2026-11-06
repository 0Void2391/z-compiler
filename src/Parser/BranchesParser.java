package src.Parser;

import org.jspecify.annotations.Nullable;
import src.AST.BlockAST;
import src.AST.ExprAST;
import src.AST.IfAST;
import src.Lexer.Lexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static src.Parser.Parser.error;

public class BranchesParser {
    private final ParserContext context;
    private final ExpressionParser expressionParser;
    private final Parser parser;

    public BranchesParser(final Parser parser) {
        this.parser = parser;
        this.context = parser.getContext();
        this.expressionParser = parser.getExpressionParser();
    }

    /**
     * Parses a conditional: {@code if (cond) { } eif (cond) { } else { }}.
     *
     * @return an IfAST node or an error
     * @throws IOException if an exception is found
     */
    public @Nullable IfAST parseIf() throws IOException {
        context.next();  // skip 'if'

        final List<IfAST.Branch> branches = new ArrayList<>();
        ExprAST elseBody = null;

        final IfAST.Branch first = parseIfBranch("if");
        if (first == null) return null;
        branches.add(first);

        while (Lexer.Tokens.fromValues(context.getCurTok()) == Lexer.Tokens.EIF) {
            context.next();  // skip 'eif'
            final IfAST.Branch branch = parseIfBranch("eif");
            if (branch == null) return null;
            branches.add(branch);
        }

        if (Lexer.Tokens.fromValues(context.getCurTok()) == Lexer.Tokens.ELSE) {
            context.next();  // skip 'else'
            if (context.getCurTok() != Lexer.Tokens.OPEN_FUNC.value) {
                return error("Expected '{' after 'else'");
            }
            context.next();
            final List<ExprAST> stmts = parseBlockBody();
            if (stmts == null) return null;
            elseBody = new BlockAST(stmts);
        }

        return new IfAST(branches, elseBody);
    }

    /**
     * Parses one {@code (cond) { body }} pair, shared by {@code if} and {@code eif}.
     */
    public IfAST.@Nullable Branch parseIfBranch(final String keyword) throws IOException {
        if (context.getCurTok() != Lexer.Tokens.OPEN_PAR.value) {
            return error("Expected '(' after '" + keyword + "'");
        }
        context.next();
        final ExprAST cond = expressionParser.parseExpression(false);
        if (cond == null) return null;
        if (context.getCurTok() != Lexer.Tokens.CLOSE_PAR.value) {
            return error("Expected ')' after condition in " + keyword);
        }
        context.next();

        if (context.getCurTok() != Lexer.Tokens.OPEN_FUNC.value) {
            return error("Expected '{' after condition in " + keyword);
        }
        context.next();
        final List<ExprAST> stmts = parseBlockBody();
        if (stmts == null) return null;
        final ExprAST body = new BlockAST(stmts);
        return new IfAST.Branch(cond, body);
    }

    /**
     * Parses {@code stmt*} and consumes the closing {@code '}'}'.
     *
     * @return the statements, or null on error
     */
    public List<ExprAST> parseBlockBody() throws IOException {
        final List<ExprAST> stmts = new ArrayList<>();
        while (context.getCurTok() != Lexer.Tokens.CLOSE_FUNC.value && context.getCurTok() != Lexer.Tokens.EOF.value) {
            if (context.getCurTok() == ';') {
                context.next();
                continue;
            }
            final ExprAST s = parser.parseStatement();
            if (s == null) return Collections.emptyList();
            stmts.add(s);
        }
        if (context.getCurTok() != Lexer.Tokens.CLOSE_FUNC.value) {
            return error("Expected '}' at the end of the block");
        }
        context.next();
        return stmts;
    }
}
