package src.AST;

import src.Codegen.IRBuilder;
import src.Parser.TypeRef;

public final class UnaryExprAST extends ExprAST {

    private final ExprAST operand;

    public UnaryExprAST(final ExprAST operand) {
        this.operand = operand;
    }

    /** @return the expression being negated */
    public ExprAST operand() {
        return operand;
    }

    @Override
    public TypeRef type(final IRBuilder b) {
        return operand.type(b);
    }

    @Override
    public LLVMTextUnit codeGen(final IRBuilder b) {
        final LLVMTextUnit llvmTextUnit = operand.codeGen(b);
        if (llvmTextUnit == null) {
            b.markFailed();
            return null;
        }
        final String flags = llvmTextUnit.type().isFloat() && !b.fastMathFlags().isEmpty() ? " " + b.fastMathFlags() : "";
        final String register = b.nextRegister();
        final String instruction = llvmTextUnit.type().isFloat()
                ? "fneg" + flags + " " + llvmTextUnit.type().llvm() + " " + llvmTextUnit.text()
                : "sub " + llvmTextUnit.type().llvm() + " 0, " + llvmTextUnit.text();
        b.appendLine(register + " = " + instruction);
        b.setRegisterType(register, llvmTextUnit.type().llvm());
        return new LLVMTextUnit(register, llvmTextUnit.type());
    }

    @Override
    public LLVMTextUnit constantValue(final IRBuilder b) {
        final LLVMTextUnit llvmTextUnit = operand.constantValue(b);
        if (llvmTextUnit == null) return null;
        final TypeRef type = llvmTextUnit.type();
        if (type.isFloat()) {
            return new LLVMTextUnit(type.literal(-Double.parseDouble(llvmTextUnit.text())), type);
        }
        if (type.isIntegral() && !type.isBoolean()) {
            return new LLVMTextUnit(type.literal(-Long.parseLong(llvmTextUnit.text())), type);
        }
        return null;
    }
}
