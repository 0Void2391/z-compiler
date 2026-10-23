package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Parser.ZType;

import java.util.List;

public final class ExternExprAST extends ExprAST{
    private static final Logger logger = LoggerFactory.getLogger(ExternExprAST.class);

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
    public Value codeGen(IRBuilder builder) {
        final ZType llvmType = ZType.fromZName(type);
        final IRBuilder.ExternDeclaration declaration = builder.declareExtern(proto, llvmType);
        if (declaration == IRBuilder.ExternDeclaration.CONFLICT) {
            logger.error("Error: conflicting declaration of extern '{}'", proto.name());
            builder.markFailed();
            return null;
        }
        if (declaration == IRBuilder.ExternDeclaration.ALREADY_DECLARED) {
            return new Value("", ZType.VOID);
        }

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
