package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
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

    public CallExprAST(final String callee, final List<ExprAST> arguments) {
        this.callee = callee;
        this.arguments = List.copyOf(arguments);
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
    public ZType type(final IRBuilder builder) {
        final ZType externReturn = builder.externReturnType(callee);
        if (externReturn != null) return externReturn;
        final IRBuilder.ResolvedSignature resolved = builder.resolve(callee);
        final PrototypeAST signature = resolved == null ? null : resolved.prototype();
        if (signature != null && signature.isMain()) return ZType.INT32;
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
        if (params.size() != arguments.size()) {
            logger.error("Error: ''{}'' takes {} argument(s) but {} were given", callee, params.size(), arguments.size());
            builder.markFailed();
            return null;
        }

        final List<String> printed = new java.util.ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            final Value argument = arguments.get(i).codeGen(builder);
            if (argument == null) {
                builder.markFailed();
                return null;
            }
            final ZType expected = ZType.fromZName(params.get(i).type());
            final Value fitted = coerce(builder, argument, expected);
            if (fitted == null) return null;
            printed.add(expected.llvm() + " " + fitted.text());
        }

        final ZType externReturn = builder.externReturnType(callee);
        final ZType result;
        if (externReturn != null) {
            result = externReturn;
        } else {
            if (signature.isMain()) result = ZType.INT32;
            else result = ZType.FLT64;
        }

        final String call = "call " + result.llvm() + " @" + resolved.irName() + "(" + String.join(", ", printed) + ")";
        if (result.isVoid()) {
            builder.appendLine(call);
            return null;
        }
        final String register = builder.nextRegister();
        builder.appendLine(register + " = " + call);
        builder.setRegisterType(register, result.llvm());
        return new Value(register, result);
    }
}
