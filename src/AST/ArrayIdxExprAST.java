package src.AST;

import src.Parser.ZType;
import src.Codegen.IRBuilder;
import src.Parser.ZType;

import java.util.List;

public class ArrayIdxExprAST extends ExprAST {
    private ExprAST arrExpr;
    private final List<NumberExprAST> indexes;

    public ArrayIdxExprAST(ExprAST arr, final List<NumberExprAST> indexes) {
        this.arrExpr = arr;
        this.indexes = indexes;
        return;
    }
    public ZType type(final IRBuilder builder) {
        return arrExpr.type(builder);
    } //will return a pointer as a type
    public Value codeGen(final IRBuilder builder) {
        return new Value("placeholder", ZType.VOID); //placeholder
    }
}
