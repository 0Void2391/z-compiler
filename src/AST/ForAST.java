package src.AST;

import src.Codegen.IRBuilder;
import src.Codegen.SymbolTable;
import src.Parser.ZType;

/**
 * A for loop: {@code for (type name -> init | cond | op) { body }}.
 *
 * <p>The loop variable is declared in the current scope, initialised,
 * checked before each iteration, updated at the end of each iteration,
 * and stays visible after the loop.
 */
public final class ForAST extends ExprAST {

    private final ZType varType;
    private final String varName;
    private final ExprAST init;
    private final ExprAST cond;
    private final ExprAST update;
    private final ExprAST body;

    public ForAST(final ZType varType, final String varName,
                  final ExprAST init, final ExprAST cond,
                  final ExprAST update, final ExprAST body) {
        this.varType = varType;
        this.varName = varName;
        this.init = init;
        this.cond = cond;
        this.update = update;
        this.body = body;
    }

    /** @return the declared type of the loop variable */
    public ZType varType() { return varType; }

    /** @return the loop variable name */
    public String varName() { return varName; }

    /** @return the initialiser expression */
    public ExprAST init() { return init; }

    /** @return the loop condition */
    public ExprAST cond() { return cond; }

    /** @return the update expression */
    public ExprAST update() { return update; }

    /** @return the loop body */
    public ExprAST body() { return body; }

    @Override
    public ZType type(final IRBuilder builder) {
        return ZType.VOID;
    }

    @Override
    public Value Codegen(final IRBuilder builder) {
        if (builder.symbols().lookupCurrentScope(varName) != null) {
            System.err.println("Error: Shadowing o ridichiarazione locale: " + varName);
            builder.markFailed();
            return null;
        }
        final String slot = builder.emitAlloca(varName, varType.llvm());
        builder.symbols().define(varName, new SymbolTable.SymbolInfo(
                varName, varType.llvm(), slot, false));

        if (init != null) {
            final Value produced = init.Codegen(builder);
            if (produced == null) {
                builder.markFailed();
                return null;
            }
            final Value fitted = init.coerce(builder, produced, varType);
            if (fitted == null) {
                builder.markFailed();
                return null;
            }
            builder.emitStore(fitted.text(), varType.llvm(), slot);
        } else {
            builder.emitStore(varType.initial(), varType.llvm(), slot);
        }

        final String condLabel = builder.nextLabel("for.cond");
        final String bodyLabel = builder.nextLabel("for.body");
        final String incLabel  = builder.nextLabel("for.inc");
        final String endLabel  = builder.nextLabel("for.end");


        builder.emitBr(condLabel);
        builder.emitLabel(condLabel);


        final String condReg = Conditions.emitI1(builder, cond);
        if (condReg == null) {
            builder.markFailed();
            return null;
        }
        builder.emitCondBr(condReg, bodyLabel, endLabel);


        builder.emitLabel(bodyLabel);
        body.Codegen(builder);
        if (builder.hasFailed()) return null;
        if (builder.isTerminated()) {
            builder.emitLabel(endLabel);
            return null;
        }
        builder.emitBr(incLabel);

        builder.emitLabel(incLabel);
        final Value updateProduced = update.Codegen(builder);
        if (updateProduced == null) {
            builder.markFailed();
            return null;
        }
        final Value updateFitted = update.coerce(builder, updateProduced, varType);
        if (updateFitted == null) {
            builder.markFailed();
            return null;
        }
        builder.emitStore(updateFitted.text(), varType.llvm(), slot);
        builder.emitBr(condLabel);

        builder.emitLabel(endLabel);
        return null;
    }
}