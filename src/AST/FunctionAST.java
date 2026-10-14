package src.AST;

import src.Codegen.IRBuilder;
import src.Codegen.SymbolTable;
import src.Parser.ZType;

import java.util.List;

public final class FunctionAST extends ExprAST {

    private final PrototypeAST proto;
    private final ExprAST body;

    public FunctionAST(final PrototypeAST proto, final ExprAST body) {
        this.proto = proto;
        this.body = body;
    }

    /** @return the signature */
    public PrototypeAST prototype() {
        return proto;
    }

    /** @return what this function returns: {@code int32} for main, else {@code flt64} */
    public ZType returnType() {
        return proto.isMain() ? ZType.INT32 : ZType.FLT64;
    }

    @Override
    public ZType type(final IRBuilder builder) {
        return ZType.VOID;
    }

    @Override
    public Value Codegen(final IRBuilder builder) {
        builder.reset();
        builder.symbols().enterScope();
        builder.declare(proto);
        builder.setReturnType(returnType().llvm());

        final boolean isMain = proto.isMain();
        final List<PrototypeAST.Param> params = proto.params();

        final StringBuilder parameters = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) parameters.append(", ");
            final PrototypeAST.Param param = params.get(i);
            parameters.append(ZType.fromZName(param.type()).llvm()).append(" %").append(param.name());
        }

        boolean parametersAreDistinct = true;
        for (final PrototypeAST.Param param : params) {
            final ZType type = ZType.fromZName(param.type());
            final String slot = builder.emitAlloca(param.name() + ".addr", type.llvm());
            builder.emitStore("%" + param.name(), type.llvm(), slot);
            parametersAreDistinct &= builder.symbols().define(param.name(), new SymbolTable.SymbolInfo(param.name(), type, slot, false));
        }
        if (!parametersAreDistinct) {
            System.err.println("Error: '" + proto.name() + "' declares the same parameter twice");
            builder.markFailed();
            builder.symbols().exitScope();
            return null;
        }

        if (body != null) body.Codegen(builder);

        if (builder.hasFailed()) {
            builder.symbols().exitScope();
            return null;
        }

        if (!builder.isTerminated()) {
            if (!isMain) {
                System.err.println("Error: '" + proto.name() + "' can reach the end without returning");
                builder.markFailed();
                builder.symbols().exitScope();
                return null;
            }
            builder.emitBareReturn("i32 0");
        }

        final String text = "define " + (isMain ? "dso_local" : "private dso_local") + " " + returnType().llvm() + " @" + proto.name() + "(" + parameters + ") {\nentry:\n" + builder.getIR() + "}\n";

        builder.symbols().exitScope();
        return new Value(text, ZType.VOID);
    }
}
