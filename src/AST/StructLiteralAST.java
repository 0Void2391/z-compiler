package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Parser.StructRegistry;
import src.Parser.StructType;
import src.Parser.TypeRef;
import src.Parser.ZType;

import java.util.List;

/**
 * A struct literal: {@code Point{1 | 2}}.
 *
 * <p>Builds the aggregate with a chain of {@code insertvalue}: start from
 * {@code undef}, insert each value at its field index, and hand the final
 * register back.
 */
public final class StructLiteralAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(StructLiteralAST.class);

    private final String typeName;
    private final List<ExprAST> values;

    public StructLiteralAST(final String typeName, final List<ExprAST> values) {
        this.typeName = typeName;
        this.values = List.copyOf(values);
    }

    @Override
    public TypeRef type(final IRBuilder builder) {
        final StructType st = StructRegistry.lookup(typeName);
        return st == null ? ZType.PTR : st;
    }

    @Override
    public Value codeGen(final IRBuilder builder) {
        final StructType st = StructRegistry.lookup(typeName);
        if (st == null) {
            logger.error("Error: unknown struct: {}", typeName);
            builder.markFailed();
            return null;
        }
        if (values.size() != st.fields().size()) {
            logger.error("Error: '{}' expects {} values but {} were given",
                    typeName, st.fields().size(), values.size());
            builder.markFailed();
            return null;
        }

        String aggregate = "undef";
        for (int i = 0; i < values.size(); i++) {
            final Value v = values.get(i).codeGen(builder);
            if (v == null) {
                builder.markFailed();
                return null;
            }
            final TypeRef fieldType = st.fields().get(i).type();
            final Value fitted = values.get(i).coerce(builder, v, fieldType);
            if (fitted == null) return null;
            final String reg = builder.nextRegister();
            builder.appendLine(reg + " = insertvalue " + st.llvm() + " " + aggregate
                    + ", " + fieldType.llvm() + " " + fitted.text() + ", " + i);
            aggregate = reg;
        }
        return new Value(aggregate, st);
    }

    /**
     * Builds the aggregate at compile time, as an LLVM constant.
     *
     * <p>Every field has to be a constant too: {@code undef} would be
     * wrong here, because a global initializer must not need a register.
     *
     * @param builder the module being built
     * @return the constant aggregate, or null when a field is not constant
     */
    @Override
    public Value constantValue(final IRBuilder builder) {
        final StructType st = StructRegistry.lookup(typeName);
        if (st == null || values.size() != st.fields().size()) return null;

        final StringBuilder sb = new StringBuilder("{ ");
        for (int i = 0; i < values.size(); i++) {
            final Value v = values.get(i).constantValue(builder);
            if (v == null) return null;
            if (i > 0) sb.append(", ");
            sb.append(v.type().llvm()).append(" ").append(v.text());
        }
        sb.append(" }");
        return new Value(sb.toString(), st);
    }
}