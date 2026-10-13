package src.AST;

import src.Codegen.IRBuilder;
import src.Parser.ZType;

import java.util.List;

public final class ExternExprAST extends ExprAST{
    private final PrototypeAST proto;
    private final String type;

    public ExternExprAST(final PrototypeAST proto, final String type) {
        this.proto = proto;
        this.type = type;
    }

    public PrototypeAST getProto() {
        return proto;
    }

    public String getType() {
        return type;
    }

    @Override
    public ZType type(IRBuilder builder) {
        return ZType.VOID; // mmh
    }

    @Override
    public Value Codegen(IRBuilder builder) {
        builder.declare(proto);
        final ZType llvmType = ZType.fromZName(type);
        final List<PrototypeAST.Param> params = proto.params();

        final StringBuilder parameters = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) parameters.append(", ");
            final PrototypeAST.Param param = params.get(i);
            parameters.append(ZType.fromZName(param.type()).llvm());
        }

        final String code = "declare " + llvmType.llvm() + " @" + proto.name() + "(" + parameters + ")\n";

        return new Value(code, ZType.VOID);
    }
}
