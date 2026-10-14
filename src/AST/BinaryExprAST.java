package src.AST;

import src.Codegen.IRBuilder;
import src.Parser.ZType;


public final class BinaryExprAST extends ExprAST {

    private final char op;
    private final ExprAST left;
    private final ExprAST right;

    public BinaryExprAST(final char op, final ExprAST left, final ExprAST right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }

    /** @return the operator character as written in the source */
    public char operator() {
        return op;
    }

    /** @return the left operand */
    public ExprAST left() {
        return left;
    }

    /** @return the right operand */
    public ExprAST right() {
        return right;
    }

    private boolean isComparison() {
        return op == '<' || op == '>';
    }

    private ZType operandType(final IRBuilder builder) {
        final ZType a = left.type(builder);
        final ZType b = right.type(builder);
        try {
            return ZType.unify(a, b);
        } catch (final IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            return a;
        }
    }

    @Override
    public ZType type(final IRBuilder builder) {
        return operandType(builder);
    }

    @Override
    public Value Codegen(final IRBuilder builder) {
        final Value lhs = left.Codegen(builder);
        if (lhs == null) {
            builder.markFailed();
            return null;
        }
        final Value rhs = right.Codegen(builder);
        if (rhs == null) {
            builder.markFailed();
            return null;
        }

        final ZType common = operandType(builder);
        final Value a = coerce(builder, lhs, common);
        final Value b = coerce(builder, rhs, common);
        if (a == null || b == null) return null;

        if (op == '+' && a.type().isString() && b.type().isString()) {
            return emitStringConcat(builder, a, b);
        }

        if (isComparison()) {
            final boolean ordered = a.type().isFloat();
            final String compared = builder.nextRegister();
            builder.appendLine(compared + " = " + (ordered ? "fcmp " : "icmp ")
                    + predicate(ordered, a.type()) + " "
                    + a.type().llvm() + " " + a.text() + ", " + b.text());
            final String result = builder.nextRegister();
            builder.appendLine(result + " = zext i1 " + compared + " to i32");
            builder.setRegisterType(result, ZType.INT32.llvm());
            return new Value(result, ZType.INT32);
        }

        final String opcode = arithmeticOpcode(a.type());
        if (opcode == null) {
            System.err.println("Operazione non supportata: " + op);
            builder.markFailed();
            return null;
        }
        final String result = builder.nextRegister();
        builder.appendLine(result + " = " + opcode + " " + a.type().llvm() + " " + a.text() + ", " + b.text());
        builder.setRegisterType(result, a.type().llvm());
        return new Value(result, a.type());
    }

    private String predicate(final boolean ordered, final ZType type) {
        if (ordered) {
            return switch (op) {
                case '<' -> "olt";
                case '>' -> "ogt";
                default -> throw new IllegalStateException("not a comparison: " + op);
            };
        }
        final boolean unsigned = type.isUnsigned();
        return switch (op) {
            case '<' -> unsigned ? "ult" : "slt";
            case '>' -> unsigned ? "ugt" : "sgt";
            default -> throw new IllegalStateException("not a comparison: " + op);
        };
    }

    private String arithmeticOpcode(final ZType type) {
        if (type.isFloat()) {
            return switch (op) {
                case '+' -> "fadd";
                case '-' -> "fsub";
                case '*' -> "fmul";
                case '/' -> "fdiv";
                default -> null;
            };
        }
        final boolean unsigned = type.isUnsigned();
        return switch (op) {
            case '+' -> "add";
            case '-' -> "sub";
            case '*' -> "mul";
            case '/' -> unsigned ? "udiv" : "sdiv";
            default -> null;
        };
    }

    /**
     * Concatenates two strings by calling into libc.
     *
     * <p>{@code malloc(strlen(a) + strlen(b) + 1)} then {@code strcpy} then
     * {@code strcat}. Nothing is freed: without a garbage collector the
     * result lives for the rest of the process, which is the same thing
     * that happens to any other string built at run time.
     */
    private Value emitStringConcat(final IRBuilder builder,
                                   final Value a, final Value b) {
        final String la = builder.nextRegister();
        builder.appendLine(la + " = call i64 @strlen(ptr " + a.text() + ")");

        final String lb = builder.nextRegister();
        builder.appendLine(lb + " = call i64 @strlen(ptr " + b.text() + ")");

        final String sum = builder.nextRegister();
        builder.appendLine(sum + " = add i64 " + la + ", " + lb);

        final String total = builder.nextRegister();
        builder.appendLine(total + " = add i64 " + sum + ", 1");

        final String buf = builder.nextRegister();
        builder.appendLine(buf + " = call ptr @malloc(i64 " + total + ")");

        builder.appendLine("call ptr @strcpy(ptr " + buf + ", ptr " + a.text() + ")");
        builder.appendLine("call ptr @strcat(ptr " + buf + ", ptr " + b.text() + ")");

        builder.setRegisterType(buf, ZType.STRING.llvm());
        return new Value(buf, ZType.STRING);
    }
}
