package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Codegen.SymbolTable;
import src.Parser.StructRegistry;
import src.Parser.StructType;
import src.Parser.TypeRef;
import src.Parser.ZType;

import java.util.List;

public final class FunctionAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(FunctionAST.class);
    private static final short LINE_COUNT_TO_INLINE = 40;

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

    /** @return the declared return type, or the historical default */
    public TypeRef returnType() {
        if (proto.returnType() != null) {
            return proto.returnType();
        }
        return proto.isMain() ? ZType.INT32 : ZType.FLT64;
    }

    @Override
    public ZType type(final IRBuilder builder) {
        return ZType.VOID;
    }

    @Override
    public LLVMTextUnit codeGen(final IRBuilder builder) {
        builder.reset();
        builder.symbols().enterScope();
        builder.declare(proto);
        builder.setReturnType(returnType());

        final boolean isMain = proto.isMain();
        final List<PrototypeAST.Param> params = proto.params();

        final StringBuilder parameters = new StringBuilder();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) parameters.append(", ");
            final PrototypeAST.Param param = params.get(i);
            if (param.varArg()) parameters.append("...");
            else {
                final String type = resolveParamType(param.type()).llvm();
                String sb = type;
                if (type.contains("ptr") && param.mut()) {
                    sb += " noalias";
                }
                sb += " %" + param.name();
                parameters.append(sb);
            }
        }

        boolean parametersAreDistinct = true;
        boolean hasVarArg = false;
        for (final PrototypeAST.Param param : params) {
            final TypeRef type = resolveParamType(param.type());
            String slot = "";
            if (!param.varArg()) {
                slot = builder.emitAlloca(param.name() + ".addr", type.llvm());
                builder.emitStore("%" + param.name(), type.llvm(), slot);
            }
            parametersAreDistinct &= builder.symbols().define(param.name(), new SymbolTable.SymbolInfo(param.name(), type, slot, false, param.varArg()));
            hasVarArg |= param.varArg();
        }
        if (!parametersAreDistinct) {
            logger.error("Error: '{}' declares the same parameter twice", proto.name());
            builder.markFailed();
            builder.symbols().exitScope();
            return null;
        }

        if (hasVarArg) builder.beginVarArgFunction();

        if (body != null) body.codeGen(builder);

        if (builder.hasFailed()) {
            builder.symbols().exitScope();
            return null;
        }

        if (!builder.isTerminated()) {
            if (!isMain && returnType() != ZType.VOID) {
                logger.error("Error: '{}' can reach the end without returning" , proto.name());
                builder.markFailed();
                builder.symbols().exitScope();
                return null;
            }
            final TypeRef ret = returnType();
            final String zero;
            if (ret.isBoolean()) {
                zero = "i1 false";
            } else if (ret.isString()) {
                zero = "{ i8*, i64 } zeroinitializer";
            } else if (ret.isPointer()) {
                zero = "ptr null";
            } else if (ret.isFloat()) {
                zero = ret.llvm() + " 0.0";
            } else if (ret.isIntegral()) {
                zero = ret.llvm() + " 0";
            } else if (ret.isVoid() ){
                zero = "void";
            } else {
                logger.error("Error: cannot produce a default return for '{}'", ret.zName());
                builder.markFailed();
                builder.symbols().exitScope();
                return null;
            }
            builder.emitBareReturn(zero);
        }

        final StringBuilder attrs = new StringBuilder();

        final boolean small = builder.getIR().lines().count() < LINE_COUNT_TO_INLINE;
        if (small) attrs.append(" alwaysinline");
        else attrs.append(" inlinehint");


        attrs.append(" nounwind nosync");
        if (isMain) attrs.append(" hot willreturn");
        final String text = "define " + (isMain ? "dso_local" : "internal dso_local") + " " + returnType().llvm() + " @" + builder.irNameOf(proto.name()) + "(" + parameters + ")" + attrs + " {\nentry:\n" + builder.getEntryAllocas() + builder.getIR() + "}\n";

        builder.symbols().exitScope();
        return new LLVMTextUnit(text, ZType.VOID);
    }

    private static TypeRef resolveParamType(final String name) {
        try {
            return ZType.fromZName(name);
        } catch (final IllegalArgumentException e) {
            final StructType st = StructRegistry.lookup(name);
            if (st == null) throw e;
            return st;
        }
    }
}
