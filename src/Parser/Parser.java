package src.Parser;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.AST.*;
import src.Lexer.Lexer;

import java.io.IOException;
import java.util.List;

import static src.Parser.TypeParser.isTypeToken;

/**
 */
public final class Parser {
    private static final Logger logger = LoggerFactory.getLogger(Parser.class);
    private final ParserContext context;
    private final ExpressionParser expressionParser;
    private final TypeParser typeParser;
    private final HeaderParser headerParser;
    private final ArrayParser arrayParser;
    private final BranchesParser branchesParser;
    private final LoopParser loopParser;
    private final RetParser retParser;
    private final StructParser structParser;
    private final VariableParser variableParser;
    private final DefinitionParser definitionParser;

    DefinitionParser getDefinitionParser() { return definitionParser; }
    ArrayParser getArrayParser() { return arrayParser; }
    public ParserContext getContext() { return context; }
    TypeParser getParserType() { return typeParser; }
    HeaderParser getHeaderParser() { return headerParser; }
    ExpressionParser getExpressionParser() { return expressionParser; }
    BranchesParser getBranchesParser() { return branchesParser; }
    LoopParser getLoopParser() { return loopParser; }
    RetParser getRetParser() { return retParser; }
    StructParser getStructParser() { return structParser; }
    VariableParser getVariableParser() { return variableParser; }

    /**
     * Constructor for Parser
     * @param lex the lexer object
     * @throws IOException if an exception is found
     */
    public Parser(final Lexer lex) throws IOException {
        this.context = new ParserContext(lex);
        this.expressionParser = new ExpressionParser(this);
        this.typeParser = new TypeParser(this);
        this.headerParser = new HeaderParser(this);
        this.arrayParser = new ArrayParser(this);
        this.branchesParser = new BranchesParser(this);
        this.loopParser = new LoopParser(this);
        this.retParser = new RetParser(this);
        this.structParser = new StructParser(this);
        this.variableParser = new VariableParser(this);
        this.definitionParser = new DefinitionParser(this);
    }

    /**
     * @param message what was expected
     * @return null
     */
    static <T> T error(final String message) {
        if (logger.isErrorEnabled()) {
            logger.error("Error: {}", message);
        }
        return null;
    }

    /**
     * @return whether the parser is positioned on the end of the input
     */
    public boolean atEOF() {
        return context.getCurTok() == Lexer.Tokens.EOF.value;
    }


    /**
     * mainLoop method to parse the input
     * @throws IOException if an exception is found
     * @deprecated
     */
    @Deprecated
    public void mainLoop() throws IOException {
        while (true) {
            final Lexer.Tokens token = Lexer.Tokens.fromValues(context.getCurTok());

            if (token == Lexer.Tokens.EOF) {
                return;
            }
            if (context.getCurTok() == ';') {
                context.next();
                continue;
            }

            ExprAST parsed = null;
            if (token == Lexer.Tokens.FUNC) {
                parsed = parseDefinition();
            } else if (token == Lexer.Tokens.CN) {
                parsed = parseConstant();
            } else if (token == Lexer.Tokens.ARRAY_OPEN) {
                parsed = arrayParser.parseArray();
            } else if (isTypeToken(token)) {
                parsed = parseVariable(false);
            } else if (token == Lexer.Tokens.EXTERN){
                parsed = headerParser.parseExtern();
            } else {
                error("Unknown token in main loop: " + context.getCurTok());
                context.next();
                continue;
            }

            if (parsed == null) {
                return;
            }
        }
    }

    /**
     * Parses a function definition
     *
     * @return null or a FunctionAST node
     * @throws IOException if an exception is found
     */
    public @Nullable FunctionAST parseDefinition() throws IOException {
        return definitionParser.parseDefinition();
    }


    /**
     * Parses a variable
     *
     * @return an AST node or an error
     * @throws IOException if an exception is found
     */
    public @Nullable DeclaratorExprAST parseVariable(boolean globals) throws IOException {
        return variableParser.parseVariable(globals);
    }

    /**
     * Parses a definition and assignment of a constant
     *
     * @return an AST node or an error
     * @throws IOException if an exception is found
     */
    public @Nullable DeclaratorExprAST parseConstant() throws IOException {
        return variableParser.parseConstant();
    }

    /**
     * Parses a struct declaration:
     * {@code struct Point { int32 x | int32 y }}.
     *
     * @return a StructDeclAST node or an error
     * @throws IOException if an exception is found
     */
    public StructDeclAST parseStructDecl() throws IOException {
        return structParser.parseStructDecl();
    }


    /**
     * Parses a number expression
     *
     * @param numVal double value
     * @return a number Expression
     * @throws IOException if an exception is found
     */
    public ExprAST parseNumberExpr(final double numVal) throws IOException {
        return expressionParser.parseNumberExpr(numVal);
    }



    /**
     *
     * @param numVal the value the context.getLex()er produced
     * @param text the literal as written, or null if there is none
     * @param floating whether the source had a decimal point or an exponent
     * @return the number node
     */
    private static NumberExprAST literal(final double numVal, final String text, final boolean floating) {
        return ExpressionParser.literal(numVal, text, floating);
    }

    /**
     * Parses a parenthesis expression
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    public @Nullable ExprAST parseParenExpr() throws IOException {
        return expressionParser.parseParenExpr();
    }

    /**
     * Parses an identifier expression
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    public @Nullable ExprAST parseIdentifierExpr() throws IOException {
        return expressionParser.parseIdentifierExpr();
    }

    /**
     * Parses a primary expression (general one)
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    public @Nullable ExprAST parsePrimary() throws IOException {
        return expressionParser.parsePrimary();
    }


    /**
     * Parses a Ret instruction
     *
     * @return an RetAST node
     * @throws IOException if an exception is found
     */
    public @Nullable ExprAST parseRet() throws IOException {
        return retParser.parseRet();
    }

    /**
     * Parses {@code stmt*} and consumes the closing {@code '}'}'.
     *
     * @return the statements, or null on error
     */
    private List<ExprAST> parseBlockBody() throws IOException {
        return branchesParser.parseBlockBody();
    }

    /**
     * Parses one {@code (cond) { body }} pair, shared by {@code if} and {@code eif}.
     */
    public IfAST.@Nullable Branch parseIfBranch(final String keyword) throws IOException {
        return branchesParser.parseIfBranch(keyword);
    }

    /**
     * Parses a conditional: {@code if (cond) { } eif (cond) { } else { }}.
     *
     * @return an IfAST node or an error
     * @throws IOException if an exception is found
     */
    public @Nullable IfAST parseIf() throws IOException {
        return branchesParser.parseIf();
    }

    /**
     * Parses a for loop: {@code for (type name -> init | cond | op) { body }}
     *
     * @return a ForAST node or an error
     * @throws IOException if an exception is found
     */
    public @Nullable ForAST parseFor() throws IOException {
        return loopParser.parseFor();
    }


    /**
     * Parses a statement
     * @return a node, in reality it just calls other functions
     * @throws IOException if an exception is found
     */
    public @Nullable ExprAST parseStatement() throws IOException {
        final Lexer.Tokens token = Lexer.Tokens.fromValues(context.getCurTok());
        if (token == Lexer.Tokens.RET) {
            return parseRet();
        } else if (token == Lexer.Tokens.IF) {
            return parseIf();
        } else if (token == Lexer.Tokens.FOR) {
            return parseFor();
        } else if (token == Lexer.Tokens.BREAK || token == Lexer.Tokens.CONTINUE) {
            context.next();
            return new LoopJumpAST(token == Lexer.Tokens.BREAK);
        } else if (isTypeToken(token) || (token == Lexer.Tokens.IDENTIFIER && StructRegistry.isStruct(context.getLex().getIdentifierStr()))) {
            return parseVariable(false);
        } else if (token == Lexer.Tokens.CN) {
            return parseConstant();
        } else if (token == Lexer.Tokens.ARRAY_OPEN) {
            return arrayParser.parseArray();
        } else {
            return parseExpression();
        }
    }

    /**
     * Parses an expression (general use)
     * @return null or calls other functions
     * @throws IOException if an exception is found
     */
    public @Nullable ExprAST parseExpression() throws IOException {
        return expressionParser.parseExpression();
    }

    /**
     * Parses a binary operation
     * @param ExprPrev precedence of the operator
     * @param leftOperandSide left side
     * @return null or a BinaryExprAST node
     * @throws IOException if an exception is found
     */
    public @Nullable ExprAST parseBinOpRHS(final int ExprPrev, ExprAST leftOperandSide) throws IOException {
        return expressionParser.parseBinOpRHS(ExprPrev, leftOperandSide);
    }


    public @Nullable ImportAST parseImport() throws IOException {
        return headerParser.parseImport();
    }

    public @Nullable ExternExprAST parseExtern() throws IOException {
        return headerParser.parseExtern();
    }

    /**
     * Parses a function prototype
     * @return an error or a PrototypeAST node
     * @throws IOException if an exception is found
     */
    public @Nullable PrototypeAST parsePrototype(final boolean extern) throws IOException {
        return headerParser.parsePrototype(extern);
    }

    /**
     * Parses a type: a type keyword, optionally followed by {@code *} for a pointer.
     *
     * @return the type, or null when the current token is not a type
     * @throws IOException if an exception is found
     */
    public TypeRef parseType(final boolean varargs) throws IOException {
        return typeParser.parseType();
    }

    /**
     * @return whether the parser is positioned on a type keyword
     */
    public boolean atTypeDeclaration() {
        return typeParser.atTypeDeclaration();
    }
}
