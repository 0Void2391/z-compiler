package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Codegen.SymbolTable;
import src.Parser.ZType;

/**
 * {@code &x}: the address of a variable.
 *
 * <p>Only variables are addressable. Go allows the same, and it keeps
 * this node small: there is no temporary, no stack slot, no copy.
 */
public final class AddressOfExprAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(AddressOfExprAST.class);

    private final ExprAST operand;

    public AddressOfExprAST(final ExprAST operand) {
        this.operand = operand;
    }

    /** @return the expression whose address is taken */
    public ExprAST operand() {
        return operand;
    }

    @Override
    public ZType type(final IRBuilder builder) {
        return operand.type(builder).pointerTo();
    }

    @Override
    public Value codeGen(final IRBuilder builder) {
        if (!(operand instanceof VarRefExprAST var)) {
            logger.error("Error: cannot take the address of this expression");
            builder.markFailed();
            return null;
        }
        SymbolTable.SymbolInfo info = builder.symbols().lookup(var.name());
        if (info == null && !var.name().contains(".")) {
            info = builder.symbols().lookup(builder.qualify(var.name()));
        }
        if (info == null) {
            logger.error("Error: undeclared variable: {}", var.name());
            builder.markFailed();
            return null;
        }
        return new Value(info.getPointerReg(), info.getZType().pointerTo());
    }
}