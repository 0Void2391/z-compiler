package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Parser.StructRegistry;
import src.Parser.StructType;
import src.Parser.TypeRef;
import src.Parser.ZType;

import java.util.List;

/**
 *
 *
 */
public final class CallExprAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(CallExprAST.class);

    private final String callee;
    private final List<ExprAST> arguments;
    private final boolean inTailPosition;

    public CallExprAST(final String callee, final List<ExprAST> arguments) {
        this.callee = callee;
        this.arguments = List.copyOf(arguments);
        this.inTailPosition = false;
    }

    public CallExprAST(final String callee, final List<ExprAST> arguments, final boolean inTailPosition) {
        this.callee = callee;
        this.arguments = List.copyOf(arguments);
        this.inTailPosition = inTailPosition;
    }

    /** @return the name being called */
    public String callee() {
        return callee;
    }

    /** @return the arguments, in order */
    public List<ExprAST> arguments() {
        return arguments;
    }

    @Override
    public TypeRef type(final IRBuilder builder) {
        final IRBuilder.ResolvedSignature resolved = builder.resolve(callee);
        if (resolved == null) return ZType.FLT64;
        if (resolved.externReturnType() != null) return resolved.externReturnType();
        final PrototypeAST signature = resolved.prototype();
        if (signature.returnType() != null) return signature.returnType();
        if (signature.isMain()) return ZType.INT32;
        return ZType.FLT64;
    }

    @Override
    public Value codeGen(final IRBuilder builder) {
        final IRBuilder.ResolvedSignature resolved = builder.resolve(callee);
        if (resolved == null) {
            logger.error("Error: unknown function: {}", callee);
            builder.markFailed();
            return null;
        }
        final PrototypeAST signature = resolved.prototype();

        final List<PrototypeAST.Param> params = signature.params();

        final boolean variadic = !params.isEmpty() && (params.getLast().varArg() || params.getLast().type().equals(ZType.VARARG.zName()));
        final int fixedParams = variadic ? params.size() - 1 : params.size();

        if (variadic ? arguments.size() < fixedParams : arguments.size() != fixedParams) {
            logger.error("Error: ''{}'' takes {} argument(s) but {} were given", callee, fixedParams, arguments.size());
            builder.markFailed();
            return null;
        }

        for (int i = 0; i < fixedParams; i++) {
            if (params.get(i).mut()) {
                final String targetI = getPointerTarget(arguments.get(i), builder);
                if (targetI != null) {
                    for (int j = 0; j < fixedParams; j++) {
                        if (i != j) {
                            final String targetJ = getPointerTarget(arguments.get(j), builder);
                            if (targetI.equals(targetJ)) {
                                logger.error("Error: alias detected: '{}' cannot be passed multiple times when an argument is marked 'mut'", targetI);
                                builder.markFailed();
                                return null;
                            }
                        }
                    }
                }
            }
        }

        final List<String> printed = new java.util.ArrayList<>();
        final ZType externReturn = resolved.externReturnType();
        for (int i = 0; i < arguments.size(); i++) {
            final Value argument = arguments.get(i).codeGen(builder);
            if (argument == null) {
                builder.markFailed();
                return null;
            }
            final TypeRef expected;
            if (i < fixedParams) {
                expected = resolveParamType(params.get(i).type());
            } else if (params.getLast().varArg()) {
                expected = resolveParamType(params.getLast().type());
            } else {
                final TypeRef argType = argument.type();
                if (!(argType instanceof ZType zt)) {
                    logger.error("Error: struct argument to a variadic function");
                    builder.markFailed();
                    return null;
                }
                expected = switch (zt) {
                    case FLT32 -> ZType.FLT64;
                    case BOOL -> ZType.INT32;
                    default -> zt;
                };
            }
            final Value fitted = coerce(builder, argument, expected);
            if (fitted == null) return null;

            // Extern (C) functions take the raw bytes, not the fat struct.
            // `printf("%s" | s)` passes s.ptr, `printf`.
            if (externReturn != null && fitted.type().isString()) {
                final String ptr = builder.nextRegister();
                builder.appendLine(ptr + " = extractvalue { i8*, i64 } " + fitted.text() + ", 0");
                printed.add("i8* " + ptr);
            } else {
                printed.add(expected.llvm() + " " + fitted.text());
            }
        }

        final TypeRef result;
        if (externReturn != null) {
            result = externReturn;
        } else if (signature.returnType() != null) {
            result = signature.returnType();
        } else if (signature.isMain()) {
            result = ZType.INT32;
        } else {
            result = ZType.FLT64;
        }

        final StringBuilder call = new StringBuilder(inTailPosition ? "tail call " : "call ").append(result.llvm());
        if (variadic) {
            call.append(" (");
            for (int i = 0; i < fixedParams; i++) {
                if (i > 0) call.append(", ");
                // The type printed for a fixed argument is the one the
                // argument was actually coerced to (e.g. i8* for a fat
                // str that printf unwraps), not the prototype's type.
                call.append(printed.get(i).split(" ", 2)[0]);
            }
            call.append(fixedParams > 0 ? ", ...)" : "...)");
        }
        call.append(" @").append(resolved.irName()).append("(").append(String.join(", ", printed)).append(")");
        if (result.isVoid()) {
            builder.appendLine(call.toString());
            return null;
        }
        final String register = builder.nextRegister();
        builder.appendLine(register + " = " + call);
        builder.setRegisterType(register, result.llvm());
        return new Value(register, result);
    }

    /**
     * Resolves a parameter type from the prototype.
     *
     * @param name a type name as written in the prototype
     * @return the built-in type or the struct it names
     */
    private static TypeRef resolveParamType(final String name) {
        try {
            return ZType.fromZName(name);
        } catch (final IllegalArgumentException e) {
            final StructType st = StructRegistry.lookup(name);
            if (st == null) throw e;
            return st;
        }
    }

    private static String getPointerTarget(final ExprAST expr, final IRBuilder builder) {
        if (expr instanceof AddressOfExprAST addr && addr.operand() instanceof VarRefExprAST var) {
            return var.name();
        }
        if (expr instanceof VarRefExprAST var && var.type(builder).isPointer()) {
            return var.name();
        }
        return null;
    }
}
