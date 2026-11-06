package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Parser.TypeRef;
import src.Parser.ZType;

/**
 * {@code *p}: the value a pointer points at.
 */
public final class DerefExprAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(DerefExprAST.class);

    private final ExprAST pointer;

    public DerefExprAST(final ExprAST pointer) {
        this.pointer = pointer;
    }

    /** @return the pointer being read through */
    public ExprAST pointer() {
        return pointer;
    }

    /** @return the type pointed at, or null when {@code pointer} is not a typed pointer */
    public TypeRef elementType(final IRBuilder builder) {
        return pointer.type(builder).elementType();
    }

    @Override
    public TypeRef type(final IRBuilder builder) {
        final TypeRef elem = elementType(builder);
        return elem == null ? ZType.PTR : elem;
    }

    @Override
    public LLVMTextUnit codeGen(final IRBuilder builder) {
        final LLVMTextUnit p = pointer.codeGen(builder);
        if (p == null) return null;
        final TypeRef elem = p.type().elementType();
        if (elem == null) {
            logger.error("Error: '*' needs a typed pointer, got {}", p.type().zName());
            builder.markFailed();
            return null;
        }
        final String reg = builder.nextRegister();
        builder.appendLine(reg + " = load " + elem.llvm() + ", ptr " + p.text());
        builder.setRegisterType(reg, elem.llvm());
        return new LLVMTextUnit(reg, elem);
    }
}