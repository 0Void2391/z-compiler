package src.AST;

import src.Codegen.IRBuilder;
import src.Parser.TypeRef;
import src.Parser.ZType;

import java.util.List;

public class ArrayIdxExprAST extends ExprAST {
    private final ExprAST arrExpr;
    private final List<NumberExprAST> indexes;

    public ArrayIdxExprAST(final ExprAST arr, final List<NumberExprAST> indexes) {
        this.arrExpr = arr;
        this.indexes = (indexes != null) ? List.copyOf(indexes) : List.of();
    }
    public TypeRef type(final IRBuilder builder) {
        return arrExpr.type(builder);
    } //will return a pointer as a type
    public Value codeGen(final IRBuilder builder) {
        return new Value("placeholder", ZType.VOID); //placeholder
    }

    public List<NumberExprAST> getIndexes() {
        return indexes;
    }
}
