package src.AST;

import java.util.List;

// maybe we should convert this to a record
public record PrototypeAST(String name, List<Param> params) {

    /**
     * One declared parameter.
     */
    public record Param(String type, String name) {
    }

    public PrototypeAST(final String name, final List<Param> params) {
        this.name = name;
        this.params = List.copyOf(params);
    }

    /**
     * @return the function name
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * @return the declared parameters
     */
    @Override
    public List<Param> params() {
        return params;
    }

    public boolean isMain() {
        return name.equals("main");
    }
}
