package src.AST;

import org.jspecify.annotations.Nullable;
import src.Codegen.IRBuilder;
import src.Parser.ZType;

import java.util.ArrayList;
import java.util.List;

/**
 * A conditional: {@code if (cond) { } eif (cond) { } else { }}.
 *
 * <p>{@code eif} is the language's {@code else if}. There can be any
 * number of them; the final {@code else} is optional.
 */
public final class IfAST extends ExprAST {

    /**
     * One condition and its body. The first is the {@code if}, the rest are {@code eif}s.
     */
    public record Branch(ExprAST cond, ExprAST body) {
    }

    private final List<Branch> branches;
    private final ExprAST elseBody;   // may be null

    public IfAST(final List<Branch> branches, final ExprAST elseBody) {
        this.branches = List.copyOf(branches);
        this.elseBody = elseBody;
    }

    /** @return the {@code if} and {@code eif} branches, in order */
    public List<Branch> branches() { return branches; }

    /** @return the {@code else} body, or null if there is none */
    public ExprAST elseBody() { return elseBody; }

    @Override
    public ZType type(final IRBuilder builder) {
        return ZType.VOID;
    }

    @Override
    public @Nullable Value codeGen(final IRBuilder builder) {
        final List<String> condLabels = new ArrayList<>();
        final List<String> thenLabels = new ArrayList<>();
        for (int i = 0; i < branches.size(); i++) {
            condLabels.add(builder.nextLabel("if.cond"));
            thenLabels.add(builder.nextLabel("if.then"));
        }
        final String elseLabel = elseBody != null ? builder.nextLabel("if.else") : null;
        final String endLabel = builder.nextLabel("if.end");

        builder.emitBr(condLabels.getFirst());

        for (int i = 0; i < branches.size(); i++) {
            builder.emitLabel(condLabels.get(i));
            final String condReg = Conditions.emitI1(builder, branches.get(i).cond());
            if (condReg == null) {
                builder.markFailed();
                return null;
            }

            final String falseTarget;
            if (i + 1 < branches.size()) {
                falseTarget = condLabels.get(i + 1);
            } else if (elseBody != null) {
                falseTarget = elseLabel;
            } else {
                falseTarget = endLabel;
            }
            builder.emitCondBr(condReg, thenLabels.get(i), falseTarget);

            builder.emitLabel(thenLabels.get(i));
            branches.get(i).body().codeGen(builder);
            if (builder.hasFailed()) return null;
            if (!builder.isTerminated()) {
                builder.emitBr(endLabel);
            }
        }

        if (elseBody != null) {
            builder.emitLabel(elseLabel);
            elseBody.codeGen(builder);
            if (builder.hasFailed()) return null;
            if (!builder.isTerminated()) {
                builder.emitBr(endLabel);
            }
        }

        builder.emitLabel(endLabel);
        return null;
    }
}