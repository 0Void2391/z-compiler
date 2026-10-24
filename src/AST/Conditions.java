package src.AST;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Codegen.IRBuilder;
import src.Parser.ZType;

/**
 * Shared helper for evaluating conditions to a single {@code i1} register.
 *
 * <p>{@code if}, {@code eif}, and {@code for} all need this. Doing it here
 * keeps the raw {@code icmp}/{@code fcmp} instead of the {@code i1 -> i32}
 * {@code zext} that {@link BinaryExprAST} emits for comparisons.
 */
final class Conditions {
    private static final Logger logger = LoggerFactory.getLogger(Conditions.class);

    private Conditions() {}

    /**
     * Emits the comparison for {@code condition} and returns the {@code i1}
     * register, or {@code null} if evaluation failed.
     */
    static String emitI1(final IRBuilder builder, final ExprAST condition) {
        if (condition instanceof BinaryExprAST bin && bin.isComparison()) {
            final Value a = bin.left().codeGen(builder);
            if (a == null) return null;
            final Value b = bin.right().codeGen(builder);
            if (b == null) return null;

            final ZType common;
            try {
                common = ZType.unify(a.type(), b.type());
            } catch (final IllegalArgumentException e) {
                logger.error("Error: {}", e.getMessage());
                return null;
            }
            final Value left = bin.left().coerce(builder, a, common);
            final Value right = bin.right().coerce(builder, b, common);
            if (left == null || right == null) return null;

            final boolean ordered = common.isFloat();
            final String cmp = builder.nextRegister();
            builder.appendLine(cmp + " = " + (ordered ? "fcmp " : "icmp ") + bin.predicate(ordered, common) + " " + common.llvm() + " " + left.text() + ", " + right.text());
            return cmp;
        }

        final Value v = condition.codeGen(builder);
        if (v == null) return null;
        final String cmp = builder.nextRegister();
        if (v.type().isFloat()) {
            builder.appendLine(cmp + " = fcmp one " + v.type().llvm() + " " + v.text() + ", 0.0");
        } else {
            builder.appendLine(cmp + " = icmp ne " + v.type().llvm() + " " + v.text() + ", 0");
        }
        return cmp;
    }
}