package src.AST;

import src.Parser.ZType;

import java.util.List;

/**
 * A function signature: name, parameters, and an optional declared
 * return type.
 *
 * <p>{@code returnType} is null when the source did not write one.
 * {@link FunctionAST} then falls back to the historical rule
 * ({@code main} returns {@code int32}, everything else {@code flt64}).
 * The C {@code extern}s declared through {@code ExternExprAST} keep
 * their return type in the builder instead, because externs have no
 * source-level return type in the prototype syntax.
 */
public record PrototypeAST(String name, List<Param> params, ZType returnType) {

    public PrototypeAST {
        params = List.copyOf(params);
    }

    /**
     * One declared parameter.
     */
    public record Param(String type, String name) {
    }

    /** A prototype with no declared return type. */
    public PrototypeAST(final String name, final List<Param> params) {
        this(name, params, null);
    }

    public boolean isMain() {
        return name.equals("main");
    }
}