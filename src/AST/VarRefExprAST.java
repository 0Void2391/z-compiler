package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Codegen.SymbolTable;
import src.Parser.TypeRef;
import src.Parser.ZType;

public final class VarRefExprAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(VarRefExprAST.class);

    private final String name;

    public VarRefExprAST(final String name) {
        this.name = name;
    }

    /** @return the name being read */
    public String name() {
        return name;
    }

    @Override
    public TypeRef type(final IRBuilder builder) {
        SymbolTable.SymbolInfo info = builder.symbols().lookup(name);
        if (info == null && !name.contains(".")) info = builder.symbols().lookup(builder.qualify(name));
        if (info != null) return info.getType();

        final int dot = name.lastIndexOf('.');
        if (dot > 0) {
            final SymbolTable.SymbolInfo varArg = builder.symbols().lookup(name.substring(0, dot));
            if (varArg != null && varArg.isVarArg()) return varArg.getType();
        }
        return ZType.INT32;
    }

    @Override
    public LLVMTextUnit codeGen(final IRBuilder builder) {
        SymbolTable.SymbolInfo info = builder.symbols().lookup(name);
        if (info == null && !name.contains(".")) {
            info = builder.symbols().lookup(builder.qualify(name));
        }
        if (info != null) {
            if (info.isVarArg()) {
                logger.error("Error: variadic parameter '{}' must be read with an index", name);
                builder.markFailed();
                return null;
            }
            final TypeRef type = info.getType();
            return new LLVMTextUnit(builder.emitLoad(type.llvm(), info.getPointerReg()), type);
        }

        final int dot = name.lastIndexOf('.');
        if (dot > 0) {
            final String base = name.substring(0, dot);
            final String indexText = name.substring(dot + 1);
            final SymbolTable.SymbolInfo varArg = builder.symbols().lookup(base);
            if (varArg != null && varArg.isVarArg()) {
                final int index;
                try {
                    index = Integer.parseInt(indexText);
                } catch (final NumberFormatException e) {
                    logger.error("Error: invalid variadic index in '{}'", name);
                    builder.markFailed();
                    return null;
                }
                final String register = builder.loadVarArg(base, index);
                if (register == null) {
                    logger.error("Error: cannot read variadic argument '{}'", name);
                    builder.markFailed();
                    return null;
                }
                return new LLVMTextUnit(register, varArg.getType());
            }
        }

        logger.error("Error: undeclared variable: {}", name);
        builder.markFailed();
        return null;
    }
}