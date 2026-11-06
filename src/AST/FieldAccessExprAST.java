package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Codegen.SymbolTable;
import src.Parser.StructType;
import src.Parser.TypeRef;
import src.Parser.ZType;

/**
 * {@code p.x}: read a field of a struct variable.
 *
 * <p>At parse time this may also be a module variable {@code math.sqrt},
 * because the parser cannot tell them apart. If the base name turns out
 * to be a module, codegen falls back to reading {@code base.field} from
 * the symbol table.
 */
public final class FieldAccessExprAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(FieldAccessExprAST.class);

    private final ExprAST base;
    private final String field;

    public FieldAccessExprAST(final ExprAST base, final String field) {
        this.base = base;
        this.field = field;
    }

    /**
     * Emits the field's address, without loading it.
     *
     * <p>This is what nested field access uses: {@code o.inner.v} needs
     * the address of {@code inner} to GEP into it. A module variable
     * {@code math.sqrt} is not a struct field, so it is not handled here;
     * {@link #codeGen} deals with it before calling this.
     *
     * @return the field pointer, or null on error
     */
    String emitFieldPointer(final IRBuilder builder) {
        final String basePtr;
        if (base instanceof VarRefExprAST var) {
            final SymbolTable.SymbolInfo info = builder.symbols().lookup(var.name());
            if (info == null) {
                logger.error("Error: undeclared variable: {}", var.name());
                builder.markFailed();
                return null;
            }
            if (!(info.getType() instanceof StructType)) {
                logger.error("Error: '{}' is not a struct, cannot access '.{}'",
                        var.name(), field);
                builder.markFailed();
                return null;
            }
            basePtr = info.getPointerReg();
        } else if (base instanceof FieldAccessExprAST nested) {
            basePtr = nested.emitFieldPointer(builder);
            if (basePtr == null) return null;
        } else {
            logger.error("Error: field access needs a variable or field base");
            builder.markFailed();
            return null;
        }

        final TypeRef baseType = base.type(builder);
        if (!(baseType instanceof StructType st)) {
            logger.error("Error: base of '.{}' is not a struct", field);
            builder.markFailed();
            return null;
        }
        final int idx = st.indexOf(field);
        if (idx < 0) {
            logger.error("Error: struct '{}' has no field '{}'", st.zName(), field);
            builder.markFailed();
            return null;
        }
        return builder.emitGEP(basePtr, st.llvm(), idx);
    }

    @Override
    public TypeRef type(final IRBuilder builder) {
        if (base instanceof VarRefExprAST var) {
            final SymbolTable.SymbolInfo moduleVar =
                    builder.symbols().lookup(var.name() + "." + field);
            if (moduleVar != null) return moduleVar.getType();
            final SymbolTable.SymbolInfo info = builder.symbols().lookup(var.name());
            if (info != null && info.getType() instanceof StructType st) {
                final TypeRef ft = st.fieldType(field);
                return ft == null ? ZType.INT32 : ft;
            }
        } else if (base instanceof FieldAccessExprAST) {
            final TypeRef baseType = base.type(builder);
            if (baseType instanceof StructType st) {
                final TypeRef ft = st.fieldType(field);
                return ft == null ? ZType.INT32 : ft;
            }
        }
        return ZType.INT32;
    }

    @Override
    public LLVMTextUnit codeGen(final IRBuilder builder) {
        // Module variable, e.g. math.sqrt: the base is a module, the
        // field is a global. The symbol table holds the global directly.
        if (base instanceof VarRefExprAST var) {
            final SymbolTable.SymbolInfo moduleVar =
                    builder.symbols().lookup(var.name() + "." + field);
            if (moduleVar != null) {
                return new LLVMTextUnit(
                        builder.emitLoad(moduleVar.getLlvmType(), moduleVar.getPointerReg()),
                        moduleVar.getType());
            }
        }
        final String fieldPtr = emitFieldPointer(builder);
        if (fieldPtr == null) return null;
        final TypeRef ft = type(builder);
        return new LLVMTextUnit(builder.emitLoad(ft.llvm(), fieldPtr), ft);
    }
}