package src.AST;

import src.Codegen.IRBuilder;
import src.Parser.StructType;
import src.Parser.TypeRef;
import src.Parser.ZType;

/**
 * A struct declaration: {@code struct Point { int32 x | int32 y }}.
 *
 * <p>Declaration produces no value. Its only effect is to register the
 * type and emit the LLVM type definition.
 */
public final class StructDeclAST extends ExprAST {

    private final StructType type;

    public StructDeclAST(final StructType type) {
        this.type = type;
    }

    @Override
    public TypeRef type(final IRBuilder builder) {
        return ZType.VOID;
    }

    @Override
    public LLVMTextUnit codeGen(final IRBuilder builder) {
        builder.declareStruct(type);
        return null;
    }
}