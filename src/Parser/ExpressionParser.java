package src.Parser;

import org.jetbrains.annotations.Nullable;
import src.AST.*;
import src.Lexer.Lexer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static src.Parser.Parser.error;
import static src.Parser.TypeParser.describeToken;

public class ExpressionParser {
    private final ParserContext context;

    private static final Map<Integer, Integer> BinOpPrecedence;
    static {
        BinOpPrecedence = Map.ofEntries(
            Map.entry(Lexer.Tokens.LESS.value, 10),
            Map.entry(Lexer.Tokens.MORE.value, 10),
            Map.entry(Lexer.Tokens.LE.value, 10),
            Map.entry(Lexer.Tokens.GE.value, 10),
            Map.entry(Lexer.Tokens.EQ.value, 10),
            Map.entry(Lexer.Tokens.NE.value, 10),
            Map.entry(Lexer.Tokens.SAME.value, 10),
            Map.entry(Lexer.Tokens.ASSIGN.value, 5),
            Map.entry(Lexer.Tokens.ADD.value, 20),
            Map.entry(Lexer.Tokens.SUB.value, 20),
            Map.entry(Lexer.Tokens.MUL.value, 40),
            Map.entry(Lexer.Tokens.DIV.value, 40),
            Map.entry(Lexer.Tokens.MOD.value, 40)
        );
    }

    protected static final double LONG_EXACT_LIMIT = 9007199254740992L;

    public ExpressionParser(final Parser parser) {
        this.context = parser.getContext();
    }

    public int getTokPrecedence() {
        final Integer TokPrev = BinOpPrecedence.get(context.getCurTok());
        if (TokPrev == null || TokPrev <= 0) return -1;
        return TokPrev;
    }

    /**
     * Parses an expression (general use)
     * @return null or calls other functions
     * @throws IOException if an exception is found
     */
    public @Nullable ExprAST parseExpression(final boolean inTailPosition) throws IOException {
        final ExprAST leftOperandSide = parsePrimary(inTailPosition);
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
    public @org.jspecify.annotations.Nullable ExprAST parseBinOpRHS(final int ExprPrev, ExprAST leftOperandSide) throws IOException {
        while (true) {
            final int TokPrev = getTokPrecedence();

            if (TokPrev < ExprPrev) {
                return leftOperandSide;
            }

            final int BinOp = context.getCurTok();
            context.next();

            if (BinOp == Lexer.Tokens.ASSIGN.value || BinOp == Lexer.Tokens.SAME.value) {
                // Right-associative: parse the whole RHS.
                final ExprAST rhs = parseExpression(false);
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
            if (BinOp == Lexer.Tokens.ARRAY_OPEN.value) {
                final List<NumberExprAST> indexes = new ArrayList<>(); //list of indexes for matrix
                while (context.getCurTok() == Lexer.Tokens.ARRAY_OPEN.value) {
                    context.next();
                    if (context.getCurTok() != Lexer.Tokens.NUMBER.value) {
                        error("Expected index value");
                    }
                    double size = context.getLex().getNumVal();
                    NumberExprAST idx = (NumberExprAST) parseNumberExpr(size); //no support for expressions when indexing
                    indexes.add(idx);
                    context.next();
                    if (context.getCurTok() != Lexer.Tokens.ARRAY_CLOSE.value) {
                        return error("Expected ']' to close indexing");
                    }
                }
                return new ArrayIdxExprAST(leftOperandSide, indexes);
            }
            ExprAST rightOperandSide = parsePrimary(false);
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
     * Parses a primary expression (general one)
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    public @org.jspecify.annotations.Nullable ExprAST parsePrimary(final boolean inTailPosition) throws IOException {
        if (context.getCurTok() == Lexer.Tokens.OPEN_FUNC.value) {
            context.next();
        }

        if (context.getCurTok() == Lexer.Tokens.IDENTIFIER.value) {
            return parseIdentifierExpr(inTailPosition);
        } else if (context.getCurTok() == Lexer.Tokens.NUMBER.value) {
            return parseNumberExpr(context.getLex().getNumVal());
        } else if (context.getCurTok() == Lexer.Tokens.STRING_LITERAL.value) {
            final String value = context.getLex().getStringVal();
            context.next();
            return new StringExprAST(value);
        } else if (context.getCurTok() == Lexer.Tokens.ALLOC.value) {
            context.next();
            if (context.getCurTok() != Lexer.Tokens.OPEN_PAR.value) {
                return error("Expected '(' after 'alloc'");
            }
            context.next();
            final ExprAST sizeExpr = parseExpression(inTailPosition);
            if (sizeExpr == null) return null;
            if (context.getCurTok() != Lexer.Tokens.CLOSE_PAR.value) {
                return error("Expected ')' after alloc size");
            }
            context.next();
            return new AllocExprAST(sizeExpr);
        } else if (context.getCurTok() == Lexer.Tokens.FREE.value) {
            context.next();
            if (context.getCurTok() != Lexer.Tokens.OPEN_PAR.value) {
                return error("Expected '(' after 'free'");
            }
            context.next();
            final ExprAST ptrExpr = parseExpression(inTailPosition);
            if (ptrExpr == null) return null;
            if (context.getCurTok() != Lexer.Tokens.CLOSE_PAR.value) {
                return error("Expected ')' after free argument");
            }
            context.next();
            return new FreeExprAST(ptrExpr);
        } else if (context.getCurTok() == Lexer.Tokens.TRUE.value || context.getCurTok() == Lexer.Tokens.FALSE.value) {
            final boolean value = context.getCurTok() == Lexer.Tokens.TRUE.value;
            context.next();
            return new BoolExprAST(value);
        } else if (context.getCurTok() == Lexer.Tokens.OPEN_PAR.value) {
            return parseParenExpr(inTailPosition);
        } else if (context.getCurTok() == Lexer.Tokens.AND.value) {
            context.next();
            final ExprAST operand = parsePrimary(inTailPosition);
            if (operand == null) return null;
            return new AddressOfExprAST(operand);
        } else if (context.getCurTok() == Lexer.Tokens.MUL.value) {
            context.next();
            final ExprAST operand = parsePrimary(inTailPosition);
            if (operand == null) return null;
            return new DerefExprAST(operand);
        } else if (context.getCurTok() == Lexer.Tokens.SUB.value) {
            context.next();
            final ExprAST operand = parsePrimary(inTailPosition);
            if (operand == null) return null;
            return new UnaryExprAST(operand);
        } else {
            return error("unknown token when expecting an expression: " + describeToken(context.getCurTok()));
        }
    }

    /**
     * Parses an identifier expression
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    public @Nullable ExprAST parseIdentifierExpr(final boolean inTailPosition) throws IOException {
        final List<String> parts = new ArrayList<>();
        final StringBuilder fullName = new StringBuilder(context.getLex().getIdentifierStr());
        parts.add(context.getLex().getIdentifierStr());
        context.next();

        while (context.getCurTok() == '.') {
            context.next();
            if (context.getCurTok() == Lexer.Tokens.NUMBER.value) {
                fullName.append(".").append(context.getLex().getNumberText());
                context.next();
                return new VarRefExprAST(fullName.toString());
            } else if (context.getCurTok() != Lexer.Tokens.IDENTIFIER.value) {
                return error("Expected a name or number (varargs) after '.' in '" + fullName + "'");
            }
            fullName.append(".").append(context.getLex().getIdentifierStr());
            parts.add(context.getLex().getIdentifierStr());
            context.next();
        }

        // struct literal: Point{1 | 2}
        if (context.getCurTok() == Lexer.Tokens.OPEN_FUNC.value) {
            if (parts.size() == 1 && StructRegistry.isStruct(parts.getFirst())) {
                context.next();
                final List<ExprAST> values = new ArrayList<>();
                if (context.getCurTok() != Lexer.Tokens.CLOSE_FUNC.value) {
                    while (true) {
                        final ExprAST val = parseExpression(false);
                        if (val == null) return null;
                        values.add(val);
                        if (context.getCurTok() == Lexer.Tokens.CLOSE_FUNC.value) break;
                        if (context.getCurTok() != Lexer.Tokens.PARAMS.value) {
                            return error("Expected '|' or '}' in struct literal");
                        }
                        context.next();
                    }
                }
                context.next();
                return new StructLiteralAST(parts.getFirst(), values);
            }
            // Not a struct: this `X{...}` is not valid
            return error("Unknown struct: " + parts.getFirst());
        }

        // function call: foo(...) or math.sqrt(...)
        if (context.getCurTok() == Lexer.Tokens.OPEN_PAR.value) {
            context.next();
            final List<ExprAST> args = new ArrayList<>();
            if (context.getCurTok() != Lexer.Tokens.CLOSE_PAR.value) {
                while (true) {
                    final ExprAST arg = parseExpression(false);
                    if (arg == null) return null;
                    args.add(arg);
                    if (context.getCurTok() == Lexer.Tokens.CLOSE_PAR.value) break;
                    if (context.getCurTok() != Lexer.Tokens.PARAMS.value) {
                        return error("Expected ')' or '|' in argument list in: " + fullName);
                    }
                    context.next();
                }
            }
            context.next();
            return new CallExprAST(fullName.toString(), args, inTailPosition);
        }

        // no dot: plain variable
        if (parts.size() == 1) {
            return new VarRefExprAST(parts.getFirst());
        }

        // dotted: field access chain, e.g. p.x or p.inner.x
        ExprAST current = new VarRefExprAST(parts.getFirst());
        for (int i = 1; i < parts.size(); i++) {
            current = new FieldAccessExprAST(current, parts.get(i));
        }
        return current;
    }

    /**
     * Parses a number expression
     *
     * @param numVal double value
     * @return a number Expression
     * @throws IOException if an exception is found
     */
    public ExprAST parseNumberExpr(final double numVal) throws IOException {
        final ExprAST result = literal(numVal, context.getLex().getNumberText(), context.getLex().getNumIsFloat());
        context.next();
        return result;
    }

    /**
     * Parses a parenthesis expression
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    public @org.jspecify.annotations.Nullable ExprAST parseParenExpr(final boolean inTailPosition) throws IOException {
        context.next();
        final ExprAST v = parseExpression(false);
        if (v == null) return null;

        if (context.getCurTok() != Lexer.Tokens.CLOSE_PAR.value) {
            return error("Expected ')'");
        }
        context.next();
        return new ParenExprAST(v);
    }

    /**
     *
     * @param numVal the value the context.getLex()er produced
     * @param text the literal as written, or null if there is none
     * @param floating whether the source had a decimal point or an exponent
     * @return the number node
     */
    protected static NumberExprAST literal(final double numVal, final String text, final boolean floating) {
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
}