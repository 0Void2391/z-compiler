package src.Parser;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.AST.*;
import src.lexer.Lexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/**
 */
public class Parser {
    private static final Logger logger = LoggerFactory.getLogger(Parser.class);

    /**
     * curTok stores the current Token from the Lexer
     * lex stores the Lexer
     */
    private int curTok;
    private Lexer lex = null;

    /**
     * BinOpPrecedence stores the precedence of binary operators
     */
    protected static final HashMap<Integer, Integer> BinOpPrecedence = new HashMap<>();
    static {
        BinOpPrecedence.put(Lexer.Tokens.LESS.value, 10);
        BinOpPrecedence.put(Lexer.Tokens.MORE.value, 10);
        BinOpPrecedence.put(Lexer.Tokens.LE.value, 10);
        BinOpPrecedence.put(Lexer.Tokens.GE.value, 10);
        BinOpPrecedence.put(Lexer.Tokens.EQ.value, 10);
        BinOpPrecedence.put(Lexer.Tokens.NE.value, 10);
        BinOpPrecedence.put(Lexer.Tokens.SAME.value, 10);
        BinOpPrecedence.put(Lexer.Tokens.ASSIGN.value, 5);
        BinOpPrecedence.put(Lexer.Tokens.ADD.value, 20);
        BinOpPrecedence.put(Lexer.Tokens.SUB.value, 20);
        BinOpPrecedence.put(Lexer.Tokens.MUL.value, 40);
        BinOpPrecedence.put(Lexer.Tokens.DIV.value, 40);
        BinOpPrecedence.put(Lexer.Tokens.MOD.value, 40);
    }


    private static final double LONG_EXACT_LIMIT = 9007199254740992L; // nice, right?

    /**
     * Constructor for Parser
     * @param lex the lexer object
     * @throws IOException if an exception is found
     */
    public Parser(final Lexer lex) throws IOException {
        this.lex = lex;
        this.curTok = lex.getTok();
    }

    /**
     * @param message what was expected
     * @return null
     */
    private static <T> T error(final String message) {
        if (logger.isErrorEnabled()) {
            logger.error("Error: {}", message);
        }
        return null;
    }

    /**
     * The type a name written in the source refers to.
     *
     * @param name a type name as it appears in the program
     * @return the type, or null if there is no such type
     */
    private static ZType resolveType(final String name) {
        try {
            return ZType.fromZName(name);
        } catch (final IllegalArgumentException e) {
            if (logger.isErrorEnabled()) {
                logger.error("Error: {}", e.getMessage());
            }
            return null;
        }
    }

    /**
     * Checks if a token is a type token
     *
     * @param token the token to check, which may be null
     * @return true if the token is a type token, false otherwise
     */
    private static boolean isTypeToken(final Lexer.Tokens token) {
        if (token == null) return false;
        if (ZType.isType(token.description)) return true;
        return switch (token) {
            case STRING, UINT32, UINT64, BOOL, VOID -> true;
            default -> false;
        };
    }

    /**
     * Parses a type: a type keyword, optionally followed by {@code *} for a pointer.
     *
     * @return the type, or null when the current token is not a type
     * @throws IOException if an exception is found
     */
    public ZType parseType() throws IOException {
        final Lexer.Tokens typeTok = Lexer.Tokens.fromValues(curTok);
        if (!isTypeToken(typeTok)) return null;

        ZType type = resolveType(typeTok.description);
        if (type == null) return null;
        next();

        while (curTok == Lexer.Tokens.MUL.value) {
            type = type.pointerTo();
            next();
        }
        return type;
    }

    /**
     * Names a token for an error message.
     *
     * @param token the token value
     * @return its description, its character for punctuation, or its raw value
     */
    private static String describeToken(final int token) {
        final Lexer.Tokens known = Lexer.Tokens.fromValues(token);
        if (known != null) return known.description;
        if (token > 0 && token <= Character.MAX_VALUE) return "'" + (char) token + "'";
        return String.valueOf(token);
    }

    /**
     * @return whether the parser is positioned on the end of the input
     */
    public final boolean atEOF() {
        return curTok == Lexer.Tokens.EOF.value;
    }

    /**
     * @return the value of the token the parser is positioned on
     */
    public final int currentToken() {
        return curTok;
    }

    /**
     * @return whether the parser is positioned on a type keyword
     */
    public final boolean atTypeDeclaration() {
        return isTypeToken(Lexer.Tokens.fromValues(curTok));
    }


    /**
     * mainLoop method to parse the input
     * @throws IOException if an exception is found
     * @deprecated
     */
    @Deprecated
    public void mainLoop() throws IOException {
        while (true) {
            final Lexer.Tokens token = Lexer.Tokens.fromValues(curTok);

            if (token == Lexer.Tokens.EOF) {
                return;
            }
            if (curTok == ';') {
                next();
                continue;
            }

            ExprAST parsed = null;
            if (token == Lexer.Tokens.FUNC) {
                parsed = parseDefinition();
            } else if (token == Lexer.Tokens.CN) {
                parsed = parseConstant();
            } else if (token == Lexer.Tokens.ARRAY_OPEN) {
                parsed = parseArray();
            } else if (isTypeToken(token)) {
                parsed = parseVariable(false);
            } else if (token == Lexer.Tokens.EXTERN){
                parsed = parseExtern();
            } else {
                error("Unknown token in main loop: " + curTok);
                next();
                continue;
            }

            if (parsed == null) {
                return;
            }
        }
    }

    /**
     * Parses a variable
     *
     * @return an AST node or an error
     * @throws IOException if an exception is found
     */
    public final DeclaratorExprAST parseVariable(boolean globals) throws IOException {
        final ZType zType = parseType();
        if (zType == null) return null;

        if (curTok != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected name after type");
        }
        final String name = lex.identifierStr;
        next();

        if (curTok != Lexer.Tokens.ASSIGN.value && curTok != Lexer.Tokens.SAME.value) {
            return new DeclaratorExprAST(zType, name, null, false, globals);
        }
        next();
        final ExprAST val = parseExpression();
        if (val == null) return null;
        return new DeclaratorExprAST(zType, name, val, false, globals);
    }

    /**
     * Parses a definition and assignment of a constant
     *
     * @return an AST node or an error
     * @throws IOException if an exception is found
     */
    public final DeclaratorExprAST parseConstant() throws IOException {
        next();

        final ZType zType = parseType();
        if (zType == null) {
            return error("Expected type after 'cn'");
        }

        if (curTok != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected constant name");
        }
        final String name = lex.identifierStr;
        next();

        if (curTok != Lexer.Tokens.ASSIGN.value && curTok != Lexer.Tokens.SAME.value) {
            return error("Expected '->' or '=' in constant declaration");
        }
        next();

        final ExprAST value = parseExpression();
        if (value == null) return null;

        return new DeclaratorExprAST(zType, name, value, true, false);
    }
    public final ArrayAST parseArray() throws IOException {
        //next(); (I believe this isn't necessary here)

        final Lexer.Tokens typeTok = Lexer.Tokens.fromValues(curTok);
        if (!isTypeToken(typeTok)) {
            return error("Expected type after array definition");
        }
        final ZType zType = resolveType(typeTok.description);
        if (zType == null) return null;
        next();

        if (curTok != Lexer.Tokens.PARAMS.value) {
            return error("Expected '|' after type definition");
        }
        next();
        if (curTok != Lexer.Tokens.NUMBER.value) {
            error("expected number");
        }
        double size = lex.numVal;
        NumberExprAST arraySize = (NumberExprAST) parseNumberExpr(size);
        NumberExprAST arrayDepth = (NumberExprAST) parseNumberExpr(1);
        next();

        if (curTok == Lexer.Tokens.PARAMS.value) {
            next();
            if (curTok != Lexer.Tokens.NUMBER.value) {
                error("expected number");
            }
            double depth = lex.numVal;
            if (size < 1) {
                return error("Invalid depth");
            }
            arrayDepth = (NumberExprAST) parseNumberExpr(depth); //for matrixes, depth 2 means [][], 1 is default
            next();
        } else if (curTok != Lexer.Tokens.ARRAY_CLOSE.value) {
            return error("Expected closing bracket");
        }
        next();

        if (curTok != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected identifier name");
        }
        final String name = lex.identifierStr;
        next();

        if (curTok != Lexer.Tokens.OPEN_FUNC.value) {
            return new ArrayAST(zType, arraySize, arrayDepth, name, new ArrayList<NumberExprAST>());
        }
        next();

        if (curTok != Lexer.Tokens.ASSIGN.value && curTok != Lexer.Tokens.SAME.value) {
            return error("Expected '->' or '=' in constant declaration");
        }
        next();

        final List<NumberExprAST> members = new ArrayList<>((int) size);

        for (int i = 0; i < (int) size; ++i) {

            NumberExprAST valAST = (NumberExprAST) parseNumberExpr(lex.numVal); //qua dentro fa già un next
            members.add(valAST);
            if (curTok != Lexer.Tokens.PARAMS.value && curTok != Lexer.Tokens.CLOSE_FUNC.value) {
                return error("Expected '|' after type definition");
            } else if (curTok == Lexer.Tokens.CLOSE_FUNC.value) {
                break;
            }
            next();
        }

        return new ArrayAST(zType, arraySize, arrayDepth, name, members);
    }

    /**
     * Helper method to get the next token from the lexer
     *
     * @throws IOException if an exception is found
     */
    private final void next() throws IOException {
        curTok = lex.getTok();
    }

    /**
     * Parses a number expression
     *
     * @param numVal double value
     * @return a number Expression
     * @throws IOException if an exception is found
     */
    public final ExprAST parseNumberExpr(final double numVal) throws IOException {
        final ExprAST result = literal(numVal, lex.numberText, lex.numIsFloat);
        next();
        return result;
    }


    public final ImportAST parseImport() throws IOException {
        if (curTok != Lexer.Tokens.IMPORT.value) {
            return null;
        }

        next();  // consume 'import'

        if (curTok != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected module name after 'import'");
        }

        final String moduleName = lex.identifierStr;
        next();

        return new ImportAST(moduleName);
    }

    public final ExternExprAST parseExtern() throws IOException {
        if (curTok != Lexer.Tokens.EXTERN.value) {
            return null;
        }
        next();  // consume 'extern'

        final PrototypeAST proto = parsePrototype(true);
        if (proto == null) return null;

        if (curTok != Lexer.Tokens.ASSIGN.value) {
            return error("Missing return value for extern function: " + proto.name());
        }

        next();
        final Lexer.Tokens token = Lexer.Tokens.fromValues(curTok);
        if (!isTypeToken(token)) {
            return error("Invalid return type for extern function: " + proto.name());
        }
        final ZType returnType = resolveType(token.description);
        if (returnType == null) return null;
        next();

        return new ExternExprAST(proto, token.description);
    }

    /**
     *
     * @param numVal the value the lexer produced
     * @param text the literal as written, or null if there is none
     * @param floating whether the source had a decimal point or an exponent
     * @return the number node
     */
    private static NumberExprAST literal(final double numVal, final String text, final boolean floating) {
        if (!floating && text != null) {
            try {
                return new NumberExprAST(Long.toString(Long.parseLong(text)), Long.parseLong(text));
            } catch (final NumberFormatException ignored) {
                // nothing
            }
        }
        if (!floating && numVal == Math.rint(numVal) && Math.abs(numVal) <= LONG_EXACT_LIMIT) {
            final long asLong = (long) numVal;
            return new NumberExprAST(Long.toString(asLong), asLong);
        }
        return new NumberExprAST(Double.toString(numVal), numVal);
    }

    /**
     * Parses a parenthesis expression
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    public final ExprAST parseParenExpr() throws IOException {
        next();
        final ExprAST v = parseExpression();
        if (v == null) return null;

        if (curTok != Lexer.Tokens.CLOSE_PAR.value) {
            return error("expected ')'");
        }
        next();
        return new ParenExprAST(v);
    }

    /**
     * Parses an identifier expression
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    public final ExprAST parseIdentifierExpr() throws IOException {
        StringBuilder idName = new StringBuilder(lex.identifierStr);
        next();

        while (curTok == '.') {
            next();
            if (curTok != Lexer.Tokens.IDENTIFIER.value) {
                return error("Expected a name after '.' in '" + idName + "'");
            }
            idName.append(".").append(lex.identifierStr);
            next();
        }
        //TODO: add support for array indexing here

        if (curTok != Lexer.Tokens.OPEN_PAR.value) {
            return new VarRefExprAST(idName.toString());
        }

        next();
        final List<ExprAST> args = new ArrayList<>();
        if (curTok != Lexer.Tokens.CLOSE_PAR.value) {
            while (true) {
                final ExprAST arg = parseExpression();
                if (arg == null) return null;
                args.add(arg);

                if (curTok == Lexer.Tokens.CLOSE_PAR.value) break;

                if (curTok != Lexer.Tokens.PARAMS.value) {
                    return error("Expected ')' or '|' in argument list");
                }
                next();
            }
        }
        next();
        return new CallExprAST(idName.toString(), args);
    }

    /**
     * Parses a primary expression (general one)
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    public final ExprAST parsePrimary() throws IOException {
        if (curTok == Lexer.Tokens.OPEN_FUNC.value) {
            next();
        }

        if (curTok == Lexer.Tokens.IDENTIFIER.value) {
            return parseIdentifierExpr();
        } else if (curTok == Lexer.Tokens.NUMBER.value) {
            return parseNumberExpr(lex.numVal);
        } else if (curTok == Lexer.Tokens.STRING_LITERAL.value) {
            final String value = lex.stringVal;
            next();
            return new StringExprAST(value);
        } else if (curTok == Lexer.Tokens.TRUE.value || curTok == Lexer.Tokens.FALSE.value) {
            final boolean value = curTok == Lexer.Tokens.TRUE.value;
            next();
            return new BoolExprAST(value);
        } else if (curTok == Lexer.Tokens.OPEN_PAR.value) {
            return parseParenExpr();
        } else if (curTok == Lexer.Tokens.AND.value) {
            next();
            final ExprAST operand = parsePrimary();
            if (operand == null) return null;
            return new AddressOfExprAST(operand);
        } else if (curTok == Lexer.Tokens.MUL.value) {
            next();
            final ExprAST operand = parsePrimary();
            if (operand == null) return null;
            return new DerefExprAST(operand);
        } else if (curTok == Lexer.Tokens.SUB.value) {
            next();
            final ExprAST operand = parsePrimary();
            if (operand == null) return null;
            return new UnaryExprAST(operand);
        } else {
            return error("unknown token when expecting an expression: " + curTok);
        }
    }

    /**
     * Returns the precedence of the current token
     * @return the precedence of the operator or -1 if it's not a binary operator
     */
    public final int getTokPrecedence() {
        final Integer TokPrev = BinOpPrecedence.get(curTok);
        if (TokPrev == null || TokPrev <= 0) return -1;
        return TokPrev;
    }

    /**
     * Parses a Ret instruction
     *
     * @return an RetAST node
     * @throws IOException if an exception is found
     */
    public final ExprAST parseRet() throws IOException {
        next();
        final ExprAST rightSide = parseExpression();
        if (rightSide == null) return null;

        return new RetAST(rightSide);
    }

    /**
     * Parses a for loop: {@code for (type name -> init | cond | op) { body }}
     *
     * @return a ForAST node or an error
     * @throws IOException if an exception is found
     */
    public final @Nullable ForAST parseFor() throws IOException {
        next();  // skip 'for'

        if (curTok != Lexer.Tokens.OPEN_PAR.value) {
            return error("Expected '(' after 'for'");
        }
        next();

        // Parse type
        final ZType varType = parseType();
        if (varType == null) {
            return error("Expected type in for");
        }

        // Parse name
        if (curTok != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected variable name in for");
        }
        final String varName = lex.identifierStr;
        next();

        // Parse initial value
        if (curTok != Lexer.Tokens.ASSIGN.value && curTok != Lexer.Tokens.SAME.value) {
            return error("Expected '->' or '=' for initial value in for");
        }
        next();
        final ExprAST init = parseExpression();
        if (init == null) return null;

        // Expect '|'
        if (curTok != Lexer.Tokens.PARAMS.value) {
            return error("Expected '|' after initial value in for");
        }
        next();

        // Parse condition
        final ExprAST cond = parseExpression();
        if (cond == null) return null;

        // Expect '|'
        if (curTok != Lexer.Tokens.PARAMS.value) {
            return error("Expected '|' after condition in for");
        }
        next();

        // Parse update
        final ExprAST update = parseExpression();
        if (update == null) return null;

        // Expect ')'
        if (curTok != Lexer.Tokens.CLOSE_PAR.value) {
            return error("Expected ')' after for header");
        }
        next();

        // Parse body
        if (curTok != Lexer.Tokens.OPEN_FUNC.value) {
            return error("Expected '{' for for body");
        }
        next();

        final List<ExprAST> bodyStatements = new ArrayList<>();
        while (curTok != Lexer.Tokens.CLOSE_FUNC.value && curTok != Lexer.Tokens.EOF.value) {
            if (curTok == ';') {
                next();
                continue;
            }
            final ExprAST stmt = parseStatement();
            if (stmt == null) return null;
            bodyStatements.add(stmt);
        }

        if (curTok != Lexer.Tokens.CLOSE_FUNC.value) {
            return error("Expected '}' at the end of the for body");
        }
        next();

        final ExprAST body = bodyStatements.size() == 1 ? bodyStatements.getFirst() : new BlockAST(bodyStatements);

        return new ForAST(varType, varName, init, cond, update, body);
    }

    /**
     * Parses a conditional: {@code if (cond) { } eif (cond) { } else { }}.
     *
     * @return an IfAST node or an error
     * @throws IOException if an exception is found
     */
    public final IfAST parseIf() throws IOException {
        next();  // skip 'if'

        final List<IfAST.Branch> branches = new ArrayList<>();
        ExprAST elseBody = null;

        final IfAST.Branch first = parseIfBranch("if");
        if (first == null) return null;
        branches.add(first);

        while (Lexer.Tokens.fromValues(curTok) == Lexer.Tokens.EIF) {
            next();  // skip 'eif'
            final IfAST.Branch branch = parseIfBranch("eif");
            if (branch == null) return null;
            branches.add(branch);
        }

        if (Lexer.Tokens.fromValues(curTok) == Lexer.Tokens.ELSE) {
            next();  // skip 'else'
            if (curTok != Lexer.Tokens.OPEN_FUNC.value) {
                return error("Expected '{' after 'else'");
            }
            next();
            final List<ExprAST> stmts = parseBlockBody();
            if (stmts == null) return null;
            elseBody = stmts.size() == 1 ? stmts.getFirst() : new BlockAST(stmts);
        }

        return new IfAST(branches, elseBody);
    }

    /**
     * Parses one {@code (cond) { body }} pair, shared by {@code if} and {@code eif}.
     */
    private IfAST.Branch parseIfBranch(final String keyword) throws IOException {
        if (curTok != Lexer.Tokens.OPEN_PAR.value) {
            return error("Expected '(' after '" + keyword + "'");
        }
        next();
        final ExprAST cond = parseExpression();
        if (cond == null) return null;
        if (curTok != Lexer.Tokens.CLOSE_PAR.value) {
            return error("Expected ')' after condition in " + keyword);
        }
        next();

        if (curTok != Lexer.Tokens.OPEN_FUNC.value) {
            return error("Expected '{' after condition in " + keyword);
        }
        next();
        final List<ExprAST> stmts = parseBlockBody();
        if (stmts == null) return null;
        final ExprAST body = stmts.size() == 1 ? stmts.getFirst() : new BlockAST(stmts);
        return new IfAST.Branch(cond, body);
    }

    /**
     * Parses {@code stmt*} and consumes the closing {@code '}'}'.
     *
     * @return the statements, or null on error
     */
    private List<ExprAST> parseBlockBody() throws IOException {
        final List<ExprAST> stmts = new ArrayList<>();
        while (curTok != Lexer.Tokens.CLOSE_FUNC.value && curTok != Lexer.Tokens.EOF.value) {
            if (curTok == ';') {
                next();
                continue;
            }
            final ExprAST s = parseStatement();
            if (s == null) return Collections.emptyList();
            stmts.add(s);
        }
        if (curTok != Lexer.Tokens.CLOSE_FUNC.value) {
            return error("Expected '}' at the end of the block");
        }
        next();
        return stmts;
    }

    /**
     * Parses a statement
     * @return a node, in reality it just calls other functions
     * @throws IOException if an exception is found
     */
    public final ExprAST parseStatement() throws IOException {
        final Lexer.Tokens token = Lexer.Tokens.fromValues(curTok);
        if (token == Lexer.Tokens.RET) {
            return parseRet();
        } else if (token == Lexer.Tokens.IF) {
            return parseIf();
        } else if (token == Lexer.Tokens.FOR) {
            return parseFor();
        } else if (isTypeToken(token)) {
            return parseVariable(false);
        } else if (token == Lexer.Tokens.CN) {
            return parseConstant();
        } else if (token == Lexer.Tokens.ARRAY_OPEN) {
            return parseArray();
        } else {
            return parseExpression();
        }
    }

    /**
     * Parses an expression (general use)
     * @return null or calls other functions
     * @throws IOException if an exception is found
     */
    public final ExprAST parseExpression() throws IOException {
        final ExprAST leftOperandSide = parsePrimary();
        if (leftOperandSide == null) return null;

        return parseBinOpRHS(0, leftOperandSide);
    }

    /**
     * Parses a binary operation
     * @param ExprPrev precedence of the operator
     * @param leftOperandSide left side
     * @return null or a BinaryExprAST node
     * @throws IOException if an exception is found
     */
    public final ExprAST parseBinOpRHS(final int ExprPrev, ExprAST leftOperandSide) throws IOException {
        while (true) {
            final int TokPrev = getTokPrecedence();

            if (TokPrev < ExprPrev) {
                return leftOperandSide;
            }

            final int BinOp = curTok;
            next();

            if (BinOp == Lexer.Tokens.ASSIGN.value || BinOp == Lexer.Tokens.SAME.value) {
                // Right-associative: parse the whole RHS.
                final ExprAST rhs = parseExpression();
                if (rhs == null) return null;

                ExprAST target = leftOperandSide;
                while (target instanceof ParenExprAST p) target = p.inner();

                if (target instanceof DerefExprAST deref) {
                    return new AssignThroughPointerExprAST(deref, rhs);
                }
                if (target instanceof VarRefExprAST var) {
                    if (var.name().contains(".")) {
                        return error("Cannot assign to module member '" + var.name() + "'");
                    }
                    return new AssignExprAST(var.name(), rhs);
                }
                return error("Cannot assign to this expression");
            }

            ExprAST rightOperandSide = parsePrimary();
            if (rightOperandSide == null) return null;

            final int NextPrev = getTokPrecedence();
            if (TokPrev < NextPrev) {
                rightOperandSide = parseBinOpRHS(TokPrev + 1, rightOperandSide);
                if (rightOperandSide == null) return null;
            }

            leftOperandSide = new BinaryExprAST(Lexer.Tokens.fromValues(BinOp).description, leftOperandSide, rightOperandSide);
        }
    }

    /**
     * Parses a function prototype
     * @return an error or a PrototypeAST node
     * @throws IOException if an exception is found
     */
    public final @Nullable PrototypeAST parsePrototype(final boolean extern) throws IOException {
        if (curTok != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected function name in prototype, got " + describeToken(curTok) + " instead");
        }
        final String fnName = lex.identifierStr;
        next();

        // Expect '('
        if (curTok != Lexer.Tokens.OPEN_PAR.value ) {
            return error("Expected '(' in prototype, got " + describeToken(curTok) + " instead");
        }
        next();

        final List<PrototypeAST.Param> params = new ArrayList<>();
        if (curTok != Lexer.Tokens.CLOSE_PAR.value) {
            while (true) {
                final ZType paramType = parseType();
                if (paramType == null) {
                    return error("Expected parameter type");
                }

                if (extern) {
                    params.add(new PrototypeAST.Param(paramType.zName(), ""));
                } else {
                    if (curTok != Lexer.Tokens.IDENTIFIER.value) {
                        return error("Expected parameter name");
                    }
                    final String paramName = lex.identifierStr;
                    next();
                    params.add(new PrototypeAST.Param(paramType.zName(), paramName));
                }

                if (curTok == Lexer.Tokens.PARAMS.value) {
                    next();
                    continue;
                }
                if (curTok == Lexer.Tokens.CLOSE_PAR.value) {
                    break;
                }
                return error("Expected '|' or ')' in parameter list");
            }
        }

        next();

        ZType returnType = null;
        if (!extern && curTok == Lexer.Tokens.ASSIGN.value) {
            next();
            final Lexer.Tokens retTok = Lexer.Tokens.fromValues(curTok);
            if (!isTypeToken(retTok)) {
                return error("Expected return type after '->', got "
                        + describeToken(curTok) + " instead");
            }
            returnType = parseType();
            if (returnType == null) {
                return error("Expected return type after '->', got "
                        + describeToken(curTok) + " instead");
            }
            if (returnType.isVoid()) {
                return error("A function cannot return void");
            }
            next();
        }

        return new PrototypeAST(fnName, params, returnType);
    }

    /**
     * Parses a function definition
     *
     * @return null or a FunctionAST node
     * @throws IOException if an exception is found
     */
    public final @Nullable FunctionAST parseDefinition() throws IOException {
        if (curTok == Lexer.Tokens.EOF.value) {
            return null;
        }

        if (Lexer.Tokens.fromValues(curTok) == Lexer.Tokens.IMPORT) {
            return error("'import' must appear before any 'extern' or definition");
        }

        if (curTok != Lexer.Tokens.FUNC.value) {
            return error("Expected 'proc' at the start of a definition, got " + describeToken(curTok) + " instead");
        }

        // consume 'proc' so the prototype starts on the function name
        next();
        final PrototypeAST proto = parsePrototype(false);
        if (proto == null) return null;

        if (curTok == Lexer.Tokens.OPEN_FUNC.value) {
            next();
        }

        final List<ExprAST> bodyStatements = new ArrayList<>();
        while (curTok != Lexer.Tokens.CLOSE_FUNC.value && curTok != Lexer.Tokens.EOF.value) {
            final ExprAST stmt = parseStatement();
            if (stmt == null) return null;
            bodyStatements.add(stmt);
        }

        if (curTok != Lexer.Tokens.CLOSE_FUNC.value) {
            return error("Expected '}' at the end of the function body");
        }
        next();

        final ExprAST body = bodyStatements.size() == 1
                ? bodyStatements.getFirst()
                : new BlockAST(bodyStatements);
        return new FunctionAST(proto, body);
    }
}
