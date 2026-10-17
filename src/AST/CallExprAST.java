package src.AST;

import src.Codegen.IRBuilder;
import src.Parser.ZType;

import java.util.List;

/**
 *
 *
 */
public final class CallExprAST extends ExprAST {

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
        return signature == null ? ZType.FLT64 : signature.isMain() ? ZType.INT32 : ZType.FLT64;
    }

    @Override
    public Value Codegen(final IRBuilder builder) {
        final IRBuilder.ResolvedSignature resolved = builder.resolve(callee);
        if (resolved == null) {
            System.err.println("Error: unknown function: " + callee);
            builder.markFailed();
            return null;
        }
        final PrototypeAST signature = resolved.prototype();

        final List<PrototypeAST.Param> params = signature.params();
        if (params.size() != arguments.size()) {
            System.err.println("Error: '" + callee + "' takes " + params.size() + " argument(s) but " + arguments.size() + " were given");
            builder.markFailed();
            return null;
        }

        final List<String> printed = new java.util.ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            final Value argument = arguments.get(i).Codegen(builder);
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
