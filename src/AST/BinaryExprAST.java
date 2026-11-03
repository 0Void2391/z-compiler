package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Parser.TypeRef;
import src.Parser.ZType;


public final class BinaryExprAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(BinaryExprAST.class);

    private final String op;
    private final ExprAST left;
    private final ExprAST right;

    public BinaryExprAST(final String op, final ExprAST left, final ExprAST right) {
        this.op = op;
        this.left = left;
        this.right = right;
    }

    /** @return the first character of the operator, as written in the source */
    public char operator() {
        return op.charAt(0);
    }

    /** @return the left operand */
    public ExprAST left() {
        return left;
    }

    /** @return the right operand */
    public ExprAST right() {
        return right;
    }

    boolean isComparison() {
        return switch (op) {
            case "<", "<=", ">", ">=", "==", "!=" -> true;
            default -> false;
        };
    }

    private TypeRef operandType(final IRBuilder builder) {
        final TypeRef a = left.type(builder);
        final TypeRef b = right.type(builder);
        if (a.isStruct() || b.isStruct()) {
            logger.error("Error: struct cannot be used in a binary expression");
            builder.markFailed();
            return ZType.INT32;
        }
        try {
            return ZType.unify((ZType) a, (ZType) b);
        } catch (final IllegalArgumentException e) {
            logger.error("Error: {}", e.getMessage());
            return a;
        }
    }

    @Override
    public TypeRef type(final IRBuilder builder) {
        return operandType(builder);
    }

    @Override
    public Value codeGen(final IRBuilder builder) {
        final Value lhs = left.codeGen(builder);
        if (lhs == null) {
            builder.markFailed();
            return null;
        }
        final Value rhs = right.codeGen(builder);
        if (rhs == null) {
            builder.markFailed();
            return null;
        }

        final TypeRef common = operandType(builder);
        final Value a = left.coerce(builder, lhs, common);
        final Value b = right.coerce(builder, rhs, common);
        if (a == null || b == null) return null;

        if (op.equals("+") && a.type().isString() && b.type().isString()) {
            return emitStringConcat(builder, a, b);
        }

        if (isComparison()) {
            final boolean ordered = a.type().isFloat();
            final String cmpFlags = ordered && "fast".equals(builder.fastMathFlags()) ? "fast " : "";
            final String compared = builder.nextRegister();
            builder.appendLine(compared + " = " + (ordered ? "fcmp " + cmpFlags : "icmp ")
                    + predicate(ordered, a.type()) + " "
                    + a.type().llvm() + " " + a.text() + ", " + b.text());
            final String result = builder.nextRegister();
            builder.appendLine(result + " = zext i1 " + compared + " to i32");
            builder.setRegisterType(result, ZType.INT32.llvm());
            return new Value(result, ZType.INT32);
        }

        final String opcode = arithmeticOpcode(a.type());
        if (opcode == null) {
            logger.error("Unsupported operation: {}", op);
            builder.markFailed();
            return null;
        }
        final String flags = a.type().isFloat() && !builder.fastMathFlags().isEmpty() ? " " + builder.fastMathFlags() : "";
        final String result = builder.nextRegister();
        builder.appendLine(result + " = " + opcode + flags + " " + a.type().llvm() + " " + a.text() + ", " + b.text());

        builder.setRegisterType(result, a.type().llvm());
        return new Value(result, a.type());
    }

    String predicate(final boolean ordered, final TypeRef type) {
        if (ordered) {
            return switch (op) {
                case "<" -> "olt";
                case "<=" -> "ole";
                case ">" -> "ogt";
                case ">=" -> "oge";
                case "==" -> "oeq";
                case "!=" -> "une";
                default -> throw new IllegalStateException("not a comparison: " + op);
            };
        }
        final boolean unsigned = type.isUnsigned();
        return switch (op) {
            case "<" -> unsigned ? "ult" : "slt";
            case "<=" -> unsigned ? "ule" : "sle";
            case ">" -> unsigned ? "ugt" : "sgt";
            case ">=" -> unsigned ? "uge" : "sge";
            case "==" -> "eq";
            case "!=" -> "ne";
            default -> throw new IllegalStateException("not a comparison: " + op);
        };
    }

    private String arithmeticOpcode(final TypeRef type) {
        if (!(type instanceof ZType zt)) {
            return null;
        }
        if (zt.isFloat()) {
            return switch (op) {
                case "+" -> "fadd";
                case "-" -> "fsub";
                case "*" -> "fmul";
                case "/" -> "fdiv";
                case "%" -> "frem";
                default -> null;
            };
        }
        final boolean unsigned = zt.isUnsigned() || zt.isBoolean();
        return switch (op) {
            case "+" -> "add";
            case "-" -> "sub";
            case "*" -> "mul";
            case "/" -> unsigned ? "udiv" : "sdiv";
            case "%" -> unsigned ? "urem" : "srem";
            default -> null;
        };
    }

    /**
     * Concatenates two fat strings.
     *
     * <p>{@code a} and {@code b} are {@code { i8*, i64 }} values. The
     * bytes are copied into a fresh block from the bump allocator, then
     * the result is built as a new {@code { ptr, len_a + len_b }}. No
     * {@code strlen}: the lengths are already in the struct.
     */
    private Value emitStringConcat(final IRBuilder builder, final Value a, final Value b) {
        builder.ensureStringRuntime();

        final String aPtr = builder.nextRegister();
        builder.appendLine(aPtr + " = extractvalue { i8*, i64 } " + a.text() + ", 0");
        final String aLen = builder.nextRegister();
        builder.appendLine(aLen + " = extractvalue { i8*, i64 } " + a.text() + ", 1");

        final String bPtr = builder.nextRegister();
        builder.appendLine(bPtr + " = extractvalue { i8*, i64 } " + b.text() + ", 0");
        final String bLen = builder.nextRegister();
        builder.appendLine(bLen + " = extractvalue { i8*, i64 } " + b.text() + ", 1");

        final String total = builder.nextRegister();
        builder.appendLine(total + " = add i64 " + aLen + ", " + bLen);

        final String allocSize = builder.nextRegister();
        builder.appendLine(allocSize + " = add i64 " + total + ", 1");

        final String buf = builder.emitBumpAlloc(allocSize);

        final String copied = builder.nextRegister();
        builder.appendLine(copied + " = call ptr @strcpy(ptr " + buf + ", ptr " + aPtr + ")");
        final String concatenated = builder.nextRegister();
        builder.appendLine(concatenated + " = call ptr @strcat(ptr " + buf + ", ptr " + bPtr + ")");

        final String r1 = builder.nextRegister();
        builder.appendLine(r1 + " = insertvalue { i8*, i64 } undef, ptr " + buf + ", 0");
        final String r2 = builder.nextRegister();
        builder.appendLine(r2 + " = insertvalue { i8*, i64 } " + r1 + ", i64 " + total + ", 1");

        builder.setRegisterType(r2, ZType.STRING.llvm());
        return new Value(r2, ZType.STRING);
    }
}
