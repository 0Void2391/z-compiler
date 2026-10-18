package src.AST;

import src.Codegen.IRBuilder;
import src.Parser.ZType;

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
    public ZType type(final IRBuilder b) {
        return operand.type(b);
    }

    @Override
    public Value codeGen(final IRBuilder b) {
        final Value value = operand.codeGen(b);
        if (value == null) {
            b.markFailed();
            return null;
        }
        final String register = b.nextRegister();
        final String instruction = value.type().isFloat() ? "fneg " + value.type().llvm() + " " + value.text() : "sub " + value.type().llvm() + " 0, " + value.text();
        b.appendLine(register + " = " + instruction);
        b.setRegisterType(register, value.type().llvm());
        return new Value(register, value.type());
    }
}
