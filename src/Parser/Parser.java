package src.Parser;

import src.AST.*;
import src.lexer.Lexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 */
public class Parser {
    /**
     * curTok stores the current Token from the Lexer
     * lex stores the Lexer
     */
    private int curTok;
    private Lexer lex = null;
    private String fileName;

    /**
     * BinOpPrecedence stores the precedence of binary operators
     */
    protected static final HashMap<Integer, Integer> BinOpPrecedence = new HashMap<>();
    static {
        BinOpPrecedence.put(Lexer.Tokens.LESS.value, 10);
        BinOpPrecedence.put(Lexer.Tokens.MORE.value, 10);
        BinOpPrecedence.put(Lexer.Tokens.SAME.value, 10);
        BinOpPrecedence.put(Lexer.Tokens.ASSIGN.value, 5);
        BinOpPrecedence.put(Lexer.Tokens.ADD.value, 20);
        BinOpPrecedence.put(Lexer.Tokens.SUB.value, 20);
        BinOpPrecedence.put(Lexer.Tokens.MUL.value, 40);
        BinOpPrecedence.put(Lexer.Tokens.DIV.value, 40);
    }


    private static final double LONG_EXACT_LIMIT = 1L << 53; // nice, right?

    /**
     * Constructor for Parser
     * @param lex the lexer object
     * @throws IOException
     */
    public Parser(final Lexer lex, final String fileName) throws IOException {
        this.lex = lex;
        this.curTok = lex.GetTok();
        this.fileName = fileName;
    }

    /**
     * @param message what was expected
     * @return null
     */
    private static <T> T error(final String message) {
        System.err.printf("Error: %s%n", message);
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
            System.err.printf("Error: %s%n", e.getMessage());
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
     * MainLoop method to parse the input
     * @throws IOException
     * @deprecated
     */
    public void MainLoop() throws IOException {
        while (true) {
            final Lexer.Tokens token = Lexer.Tokens.fromValues(curTok);

            if (token == Lexer.Tokens.EOF) {
                return;
            }
            if (curTok == ';') {
                Next();
                continue;
            }

            ExprAST parsed = null;
            if (token == Lexer.Tokens.FUNC) {
                parsed = ParseDefinition(false);
            } else if (token == Lexer.Tokens.CN) {
                parsed = ParseConstant();
            } else if (isTypeToken(token)) {
                parsed = ParseVariable();
            } else if (token == Lexer.Tokens.EXTERN){
                ParseExtern();
            } else {
                error("Unknown token in main loop: " + curTok);
                Next();
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
     * @throws IOException
     */
    public final DeclaratorExprAST ParseVariable() throws IOException {
        final Lexer.Tokens typeTok = Lexer.Tokens.fromValues(curTok);
        Next();

        final ZType zType = resolveType(typeTok.description);
        if (zType == null) return null;

        if (curTok != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected name after type");
        }

        final String name = lex.IdentifierStr;
        Next();

        if (curTok != Lexer.Tokens.ASSIGN.value && curTok != Lexer.Tokens.SAME.value) {
            return new DeclaratorExprAST(zType, name, null, false);
        }

        Next();
        final ExprAST val = ParseExpression();
        if (val == null) return null;

        return new DeclaratorExprAST(zType, name, val, false);
    }

    /**
     * Parses a definition and assignment of a constant
     *
     * @return an AST node or an error
     * @throws IOException
     */
    public final DeclaratorExprAST ParseConstant() throws IOException {
        Next();

        final Lexer.Tokens typeTok = Lexer.Tokens.fromValues(curTok);
        if (!isTypeToken(typeTok)) {
            return error("Expected type after 'cn'");
        }
        final ZType zType = resolveType(typeTok.description);
        if (zType == null) return null;
        Next();

        if (curTok != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected constant name");
        }
        final String name = lex.IdentifierStr;
        Next();

        if (curTok != Lexer.Tokens.ASSIGN.value && curTok != Lexer.Tokens.SAME.value) {
            return error("Expected '->' or '=' in constant declaration");
        }
        Next();

        final ExprAST value = ParseExpression();
        if (value == null) return null;

        return new DeclaratorExprAST(zType, name, value, true);
    }

    /**
     * Helper method to get the next token from the lexer
     * @return the next token
     * @throws IOException
     */
    private final int Next() throws IOException {
        curTok = lex.GetTok();
        return curTok;
    }

    /**
     * Parses a number expression
     *
     * @param numVal double value
     * @return a number Expression
     * @throws IOException
     */
    public final ExprAST ParseNumberExpr(final double numVal) throws IOException {
        final ExprAST result = literal(numVal, lex.NumberText, lex.NumIsFloat);
        Next();
        return result;
    }

    public final ExternExprAST ParseExtern() throws IOException {
        if (curTok != Lexer.Tokens.EXTERN.value) {
            return null;
        }
        Next();  // consume 'extern'

        final PrototypeAST proto = ParsePrototype(true);
        if (proto == null) return null;

        if (curTok != Lexer.Tokens.ASSIGN.value) {
            return error("Missing return value for extern function: " + proto.name());
        }

        Next();
        final Lexer.Tokens token = Lexer.Tokens.fromValues(curTok);
        if (!isTypeToken(token)) {
            return error("Invalid return type for extern function: " + proto.name());
        }
        final ZType returnType = resolveType(token.description);
        if (returnType == null) return null;
        Next();

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
     * @throws IOException
     */
    public final ExprAST ParseParenExpr() throws IOException {
        Next();
        final ExprAST V = ParseExpression();
        if (V == null) return null;

        if (curTok != Lexer.Tokens.CLOSE_PAR.value) {
            return error("expected ')'");
        }
        Next();
        return new ParenExprAST(V);
    }

    /**
     * Parses an identifier expression
     *
     * @return an Expression node or an error
     * @throws IOException
     */
    public final ExprAST ParseIdentifierExpr() throws IOException {
        final String idName = lex.IdentifierStr;
        Next();

        if (curTok == Lexer.Tokens.ASSIGN.value || curTok == Lexer.Tokens.SAME.value) {
            Next();
            final ExprAST expr = ParseExpression();
            if (expr == null) return null;
            return new AssignExprAST(idName, expr);
        }

        if (curTok != Lexer.Tokens.OPEN_PAR.value) {
            return new VarRefExprAST(idName);
        }

        Next();
        final List<ExprAST> args = new ArrayList<>();
        if (curTok != Lexer.Tokens.CLOSE_PAR.value) {
            while (true) {
                final ExprAST arg = ParseExpression();
                if (arg == null) return null;
                args.add(arg);

                if (curTok == Lexer.Tokens.CLOSE_PAR.value) break;

                if (curTok != Lexer.Tokens.PARAMS.value) {
                    return error("Expected ')' or '|' in argument list");
                }
                Next();
            }
        }
        Next();
        return new CallExprAST(idName, args);
    }

    /**
     * Parses a primary expression (general one)
     *
     * @return an Expression node or an error
     * @throws IOException
     */
    public final ExprAST ParsePrimary() throws IOException {
        if (curTok == Lexer.Tokens.OPEN_FUNC.value) {
            Next();
        }

        if (curTok == Lexer.Tokens.IDENTIFIER.value) {
            return ParseIdentifierExpr();
        } else if (curTok == Lexer.Tokens.NUMBER.value) {
            return ParseNumberExpr(lex.NumVal);
        } else if (curTok == Lexer.Tokens.STRING_LITERAL.value) {
            final String value = lex.StringVal;
            Next();
            return new StringExprAST(value);
        } else if (curTok == Lexer.Tokens.OPEN_PAR.value) {
            return ParseParenExpr();
        } else if (curTok == Lexer.Tokens.SUB.value) {
            Next();
            final ExprAST operand = ParsePrimary();
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
    public final int GetTokPrecedence() {
        final Integer TokPrec = BinOpPrecedence.get(curTok);
        if (TokPrec == null || TokPrec <= 0) return -1;
        return TokPrec;
    }

    /**
     * Parses a Ret instruction
     *
     * @return an RetAST node
     * @throws IOException
     */
    public final ExprAST ParseRet() throws IOException {
        Next();
        final ExprAST rightSide = ParseExpression();
        if (rightSide == null) return null;

        return new RetAST(rightSide);
    }

    /**
     * Parses a for loop: {@code for (type name -> init | cond | op) { body }}
     *
     * @return a ForAST node or an error
     * @throws IOException
     */
    public final ForAST ParseFor() throws IOException {
        Next();  // skip 'for'

        if (curTok != Lexer.Tokens.OPEN_PAR.value) {
            return error("Expected '(' after 'for'");
        }
        Next();

        // Parse type
        final Lexer.Tokens typeTok = Lexer.Tokens.fromValues(curTok);
        if (!isTypeToken(typeTok)) {
            return error("Expected type in for");
        }
        final ZType varType = resolveType(typeTok.description);
        if (varType == null) return null;
        Next();

        // Parse name
        if (curTok != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected variable name in for");
        }
        final String varName = lex.IdentifierStr;
        Next();

        // Parse initial value
        if (curTok != Lexer.Tokens.ASSIGN.value && curTok != Lexer.Tokens.SAME.value) {
            return error("Expected '->' or '=' for initial value in for");
        }
        Next();
        final ExprAST init = ParseExpression();
        if (init == null) return null;

        // Expect '|'
        if (curTok != Lexer.Tokens.PARAMS.value) {
            return error("Expected '|' after initial value in for");
        }
        Next();

        // Parse condition
        final ExprAST cond = ParseExpression();
        if (cond == null) return null;

        // Expect '|'
        if (curTok != Lexer.Tokens.PARAMS.value) {
            return error("Expected '|' after condition in for");
        }
        Next();

        // Parse update
        final ExprAST update = ParseExpression();
        if (update == null) return null;

        // Expect ')'
        if (curTok != Lexer.Tokens.CLOSE_PAR.value) {
            return error("Expected ')' after for header");
        }
        Next();

        // Parse body
        if (curTok != Lexer.Tokens.OPEN_FUNC.value) {
            return error("Expected '{' for for body");
        }
        Next();

        final List<ExprAST> bodyStatements = new ArrayList<>();
        while (curTok != Lexer.Tokens.CLOSE_FUNC.value && curTok != Lexer.Tokens.EOF.value) {
            if (curTok == ';') {
                Next();
                continue;
            }
            final ExprAST stmt = ParseStatement();
            if (stmt == null) return null;
            bodyStatements.add(stmt);
        }

        if (curTok != Lexer.Tokens.CLOSE_FUNC.value) {
            return error("Expected '}' at the end of the for body");
        }
        Next();

        final ExprAST body = bodyStatements.size() == 1 ? bodyStatements.get(0) : new BlockAST(bodyStatements);

        return new ForAST(varType, varName, init, cond, update, body);
    }

    /**
     * Parses a conditional: {@code if (cond) { } eif (cond) { } else { }}.
     *
     * @return an IfAST node or an error
     * @throws IOException
     */
    public final IfAST ParseIf() throws IOException {
        Next();  // skip 'if'

        final List<IfAST.Branch> branches = new ArrayList<>();
        ExprAST elseBody = null;

        final IfAST.Branch first = parseIfBranch("if");
        if (first == null) return null;
        branches.add(first);

        while (Lexer.Tokens.fromValues(curTok) == Lexer.Tokens.EIF) {
            Next();  // skip 'eif'
            final IfAST.Branch branch = parseIfBranch("eif");
            if (branch == null) return null;
            branches.add(branch);
        }

        if (Lexer.Tokens.fromValues(curTok) == Lexer.Tokens.ELSE) {
            Next();  // skip 'else'
            if (curTok != Lexer.Tokens.OPEN_FUNC.value) {
                return error("Expected '{' after 'else'");
            }
            Next();
            final List<ExprAST> stmts = parseBlockBody();
            if (stmts == null) return null;
            elseBody = stmts.size() == 1 ? stmts.get(0) : new BlockAST(stmts);
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
        Next();
        final ExprAST cond = ParseExpression();
        if (cond == null) return null;
        if (curTok != Lexer.Tokens.CLOSE_PAR.value) {
            return error("Expected ')' after condition in " + keyword);
        }
        Next();

        if (curTok != Lexer.Tokens.OPEN_FUNC.value) {
            return error("Expected '{' after condition in " + keyword);
        }
        Next();
        final List<ExprAST> stmts = parseBlockBody();
        if (stmts == null) return null;
        final ExprAST body = stmts.size() == 1 ? stmts.get(0) : new BlockAST(stmts);
        return new IfAST.Branch(cond, body);
    }

    /**
     * Parses {@code stmt*} and consumes the closing {@code '}'}.
     *
     * @return the statements, or null on error
     */
    private List<ExprAST> parseBlockBody() throws IOException {
        final List<ExprAST> stmts = new ArrayList<>();
        while (curTok != Lexer.Tokens.CLOSE_FUNC.value && curTok != Lexer.Tokens.EOF.value) {
            if (curTok == ';') {
                Next();
                continue;
            }
            final ExprAST s = ParseStatement();
            if (s == null) return null;
            stmts.add(s);
        }
        if (curTok != Lexer.Tokens.CLOSE_FUNC.value) {
            return error("Expected '}' at the end of the block");
        }
        Next();
        return stmts;
    }

    /**
     * Parses a statement
     * @return a node, in reality it just calls other functions
     * @throws IOException
     */
    public final ExprAST ParseStatement() throws IOException {
        final Lexer.Tokens token = Lexer.Tokens.fromValues(curTok);
        if (token == Lexer.Tokens.RET) {
            return ParseRet();
        } else if (token == Lexer.Tokens.IF) {
            return ParseIf();
        } else if (token == Lexer.Tokens.FOR) {
            return ParseFor();
        } else if (isTypeToken(token)) {
            return ParseVariable();
        } else if (token == Lexer.Tokens.CN) {
            return ParseConstant();
        } else {
            return ParseExpression();
        }
    }

    /**
     * Parses an expression (general use)
     * @return null or calls other functions
     * @throws IOException
     */
    public final ExprAST ParseExpression() throws IOException {
        final ExprAST LHS = ParsePrimary();
        if (LHS == null) return null;

        return ParseBinOpRHS(0, LHS);
    }

    /**
     * Parses a binary operation
     * @param ExprPrec precedence of the operator
     * @param LHS left side
     * @return null or a BinaryExprAST node
     * @throws IOException
     */
    public final ExprAST ParseBinOpRHS(final int ExprPrec, ExprAST LHS) throws IOException {
        while (true) {
            final int TokPrec = GetTokPrecedence();

            if (TokPrec < ExprPrec) {
                return LHS;
            }

            final int BinOp = curTok;
            Next();

            ExprAST RHS = ParsePrimary();
            if (RHS == null) return null;

            final int NextPrec = GetTokPrecedence();
            if (TokPrec < NextPrec) {
                RHS = ParseBinOpRHS(TokPrec + 1, RHS);
                if (RHS == null) return null;
            }

            LHS = new BinaryExprAST(Lexer.Tokens.fromValues(BinOp).description.charAt(0), LHS, RHS);
        }
    }

    /**
     * Parses a function prototype
     * @return an error or a PrototypeAST node
     * @throws IOException
     */
    public final PrototypeAST ParsePrototype(final boolean extern) throws IOException {
        if (curTok != Lexer.Tokens.IDENTIFIER.value) {
            return error("Expected function name in prototype, got " + describeToken(curTok) + " instead");
        }
        final String fnName = lex.IdentifierStr;
        Next();

        // Expect '('
        if (curTok != Lexer.Tokens.OPEN_PAR.value ) {
            return error("Expected '(' in prototype, got " + describeToken(curTok) + " instead");
        }
        Next();

        final List<PrototypeAST.Param> params = new ArrayList<>();
        if (curTok != Lexer.Tokens.CLOSE_PAR.value ) {
            while (true) {
                final Lexer.Tokens typeTok = Lexer.Tokens.fromValues(curTok);
                if (!isTypeToken(typeTok)) {
                    return error("Expected parameter type");
                }
                final ZType paramType = resolveType(typeTok.description);
                if (paramType == null) return null;
                Next();

                if (extern) {
                    params.add(new PrototypeAST.Param(paramType.zName(), ""));
                } else {
                    if (curTok != Lexer.Tokens.IDENTIFIER.value) {
                        return error("Expected parameter name");
                    }
                    final String paramName = lex.IdentifierStr;
                    Next();

                    params.add(new PrototypeAST.Param(paramType.zName(), paramName));
                }

                if (curTok == Lexer.Tokens.PARAMS.value) {
                    Next();
                    continue;
                }
                if (curTok == Lexer.Tokens.CLOSE_PAR.value) {
                    break;
                }
                return error("Expected '|' or ')' in parameter list");
            }
        }

        if (curTok != Lexer.Tokens.CLOSE_PAR.value) {
            return error("Expected ')' in prototype");
        }
        Next();

        return new PrototypeAST(fnName, params);
    }

    /**
     * Parses a function definition
     *
     * @return null or a FunctionAST node
     * @throws IOException
     */
    public final FunctionAST ParseDefinition(final boolean foundExtern) throws IOException {
        if (curTok == Lexer.Tokens.EOF.value) {
            return null;
        }

        // consume 'proc' so the prototype starts on the function name
        Next();
        final PrototypeAST proto = ParsePrototype(false);
        if (proto == null) return null;

        if (curTok == Lexer.Tokens.OPEN_FUNC.value) {
            Next();
        }

        final List<ExprAST> bodyStatements = new ArrayList<>();
        while (curTok != Lexer.Tokens.CLOSE_FUNC.value && curTok != Lexer.Tokens.EOF.value) {
            if (curTok == ';') {
                Next();
                continue;
            }
            final ExprAST stmt = ParseStatement();
            if (stmt == null) return null;
            bodyStatements.add(stmt);
        }

        if (curTok != Lexer.Tokens.CLOSE_FUNC.value) {
            return error("Expected '}' at the end of the function body");
        }
        Next();

        final ExprAST body = bodyStatements.size() == 1 ? bodyStatements.getFirst() : new BlockAST(bodyStatements);
        return new FunctionAST(proto, body);
    }
}
