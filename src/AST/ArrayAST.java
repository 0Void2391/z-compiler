package src.AST;

import src.Codegen.IRBuilder;
import src.Parser.ZType;

import java.util.List;

public final class ArrayAST extends ExprAST { //we could also consider doing extends DeclaratorExprAST

    private final ZType type;
    private final NumberExprAST size;
    private final NumberExprAST depth;
    private final String name;
    private final List<NumberExprAST> values;

    public ArrayAST(ZType zType, NumberExprAST arraySize, NumberExprAST arrayDepth, String name, List<NumberExprAST> values) {
        this.type = zType;
        this.size = arraySize;
        this.depth = arrayDepth;
        this.name = name;
        this.values = (values != null) ? List.copyOf(values) : List.of();
        return;
    }
    public ZType type(final IRBuilder builder) {
        return type;
    } //placeholder
    public Value codeGen(final IRBuilder builder) {
        return new Value("placeholder", ZType.VOID); //placeholder
    }

    public NumberExprAST getSize() {
        return size;
    }

    public NumberExprAST getDepth() {
        return depth;
    }

    public String getName() {
        return name;
    }

    public List<NumberExprAST> getValues() {
        return values;
    }
}
