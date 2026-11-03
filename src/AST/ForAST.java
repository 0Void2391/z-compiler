package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Codegen.Loop;
import src.Codegen.SymbolTable;
import src.Parser.TypeRef;
import src.Parser.ZType;

/**
 * A for loop: {@code for (type name -> init | cond | op) { body }}.
 *
 * <p>The loop variable is declared in the current scope, initialized,
 * checked before each iteration, updated at the end of each iteration,
 * and stays visible after the loop.
 */
public final class ForAST extends ExprAST {
    private static final Logger logger = LoggerFactory.getLogger(ForAST.class);

    private final TypeRef varType;
    private final String varName;
    private final ExprAST init;
    private final ExprAST cond;
    private final ExprAST update;
    private final ExprAST body;

    public ForAST(final TypeRef varType, final String varName, final ExprAST init, final ExprAST cond, final ExprAST update, final ExprAST body) {
        this.varType = varType;
        this.varName = varName;
        this.init = init;
        this.cond = cond;
        this.update = update;
        this.body = body;
    }

    /** @return the declared type of the loop variable */
    public TypeRef varType() { return varType; }

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
    public TypeRef type(final IRBuilder builder) {
        return ZType.VOID;
    }

    @Override
    public Value codeGen(final IRBuilder builder) {
        builder.symbols().enterScope();
        try {
            if (builder.symbols().lookup(varName) != null) {
                logger.error("Error: Shadowing or local redeclaration of : {}", varName);
                builder.markFailed();
                return null;
            }
            final String slot = builder.emitAlloca(varName, varType.llvm());
            builder.symbols().define(varName, new SymbolTable.SymbolInfo(
                    varName, varType, slot, false));

            if (init != null) {
                if (verify(builder, slot, init)) return null;
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
            builder.enterLoop(incLabel, endLabel);
            final Loop loop;
            try {
                body.codeGen(builder);
            } finally {
                loop = builder.exitLoop();
            }
            if (builder.hasFailed()) return null;
            // the update is only needed when the body can reach it
            if (builder.isTerminated() && !loop.isContinued()) {
                builder.emitLabel(endLabel);
                return null;
            }
            if (!builder.isTerminated()) {
                builder.emitBr(incLabel);
            }

            builder.emitLabel(incLabel);
            if (verify(builder, slot, update)) return null;
            builder.emitBrWithLoopMetadata(condLabel, 4, 4);

            builder.emitLabel(endLabel);
            return null;
        } finally {
            builder.symbols().exitScope();
        }
    }

    private boolean verify(IRBuilder builder, String slot, ExprAST init) {
        final Value produced = init.codeGen(builder);
        if (produced == null) {
            builder.markFailed();
            return true;
        }
        final Value fitted = init.coerce(builder, produced, varType);
        if (fitted == null) {
            builder.markFailed();
            return true;
        }
        builder.emitStore(fitted.text(), varType.llvm(), slot);
        return false;
    }
}