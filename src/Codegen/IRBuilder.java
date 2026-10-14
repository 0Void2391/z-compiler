package src.Codegen;

import src.AST.PrototypeAST;
import src.AST.Value;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Class with helper methods to generate LLVM IR code using testual strings
 */
public class IRBuilder {
    /**
     * registerCount stores the next available register (es. %1)
     * irCode is the StringBuilder for the code
     * registerTypes stores the LLVM type of each register
     */
    private int registerCount = 1;
    private final StringBuilder irCode = new StringBuilder();
    private final Map<String, String> registerTypes = new HashMap<>();

    /**
     * The names visible to the function being generated.
     *
     */
    private final SymbolTable symbols = new SymbolTable();

    /**
     * Every function signature seen so far, so a call can be typed and checked.
     *
     */
    private final Map<String, PrototypeAST> signatures = new LinkedHashMap<>();

    private boolean terminated;
    private String returnType = "double";

    /**.
     */
    private boolean failed;

    /** Records that generation hit an error the caller cannot see. */
    public void markFailed() {
        this.failed = true;
    }

    /** @return whether anything reported an error since the last reset */
    public boolean hasFailed() {
        return failed;
    }

    /** Global definitions: string constants and library declarations. */
    private final StringBuilder globals = new StringBuilder();

    /** How many string constants have been emitted so far. */
    private int stringCount = 0;

    /** True once the libc declarations have been emitted. */
    private boolean declaresEmitted = false;

    /**
     *
     * @param prototype the signature to declare
     * @return false when the name is already taken, in which case nothing changes
     */
    public boolean declare(final PrototypeAST prototype) {
        if (signatures.containsKey(prototype.getName())) return false;
        signatures.put(prototype.getName(), prototype);
        return true;
    }

    public void emitReturn(final Value value) {
        final String target = returnType;
        final String from = value.type().llvm();
        if (from.equals(target)) {
            appendLine("ret " + target + " " + value.text());
            terminated = true;
            return;
        }
        final String converted = nextRegister();
        appendLine(converted + " = " + conversionOpcode(value.type().llvm(), target) + from + " " + value.text() + " to " + target);
        appendLine("ret " + target + " " + converted);
        terminated = true;
    }

    /** Emits a terminator that has no operand, such as a {@code main} that fell off the end. */
    public void emitBareReturn(final String literal) {
        appendLine("ret " + literal);
        terminated = true;
    }

    private static String conversionOpcode(final String from, final String to) {
        final boolean fromFloat = from.equals("float") || from.equals("double");
        final boolean toFloat = to.equals("float") || to.equals("double");
        if (fromFloat && !toFloat) return "fptosi ";
        if (!fromFloat && toFloat) return "sitofp ";
        if (fromFloat) return to.equals("float") ? "fptrunc " : "fpext ";
        return from.equals("i64") && to.equals("i32") ? "trunc " : "sext ";
    }

    /** @return whether the current block already has a terminator */
    public boolean isTerminated() {
        return terminated;
    }

    /** Declares what the function being generated returns. */
    public void setReturnType(final String llvmType) {
        this.returnType = llvmType;
    }

    /** @return the symbol table for the function being generated */
    public SymbolTable symbols() {
        return symbols;
    }

    /** @return the signatures of the functions generated so far */
    public Map<String, PrototypeAST> signatures() {
        return signatures;
    }

    /**
     * @return a string with the next register (es. %2)
     */
    public final String nextRegister() {
        return "%" + (registerCount++);
    }

    /**
     * appends a line to the string builder and adds \n
     * @param line the String to append
     */
    public void appendLine(String line) {
        irCode.append(" ").append(line).append("\n");
    }

    /**
     * @return the IR code in a string form
     */
    public final String getIR() {
        return irCode.toString();
    }

    @Override
    /**
     * return the String form of the IR
     */
    public final String toString() {
        return getIR();
    }

    /**
     * reset the count of the registers (es. when entering a new function)
     */
    public void reset() {
        registerCount = 1;
        labelCount = 0;
        irCode.setLength(0);
        registerTypes.clear();
        terminated = false;
        failed = false;
    }

    /**
     * emitAlloca emits an allocation instruction for a variable
     * @param varName name of the ptr to use
     * @param llvmType LLVM type of the variable (es. int32 becomes i32)
     * @return a String with the IR code
     */
    public final String emitAlloca(final String varName, final String llvmType) {
        final String ptr = "%" + varName;
        appendLine(ptr + " = alloca " + llvmType);
        return ptr;
    }

    /**
     * emitStore emits the IR code to store a value in a variable
     * @param val the value to store
     * @param llvmType the llvmtype
     * @param ptr the name of the ptr
     */
    public void emitStore(final String val, final String llvmType, final String ptr) {
        appendLine("store " + llvmType + " " + val + ", ptr " + ptr);
    }

    /**
     * emitLoad emits the Ir code to Load the value from a ptr
     * @param llvmType llvmtype
     * @param ptr the name of the ptr
     * @return the register in which the value was loaded
     */
    public final String emitLoad(final String llvmType, final String ptr) {
        final String reg = nextRegister();
        appendLine(reg + " = load " + llvmType + ", ptr " + ptr);
        registerTypes.put(reg, llvmType);
        return reg;
    }

    /**
     * Gets the LLVM type of a register
     * @param reg the register name (e.g., "%1")
     * @return the LLVM type or null if not tracked
     */
    public final String getRegisterType(final String reg) {
        return registerTypes.get(reg);
    }

    /**
     * nextLabel emits the next available label
     * @param prefix the prefix of the label
     * @return the label
     */
    private int labelCount = 0;
    public final String nextLabel(final String prefix) {
        return prefix + "." + (labelCount++);
    }


    /**
     * setter method for registerTypes
     * @param reg name of the register
     * @param type type to set
     */
    public void setRegisterType(final String reg, final String type) {
        registerTypes.put(reg, type);
    }

    /**
     * emitLabel starts a new basic block, e.g. "for.cond:"
     * @param label the label name (without '%')
     */
    public void emitLabel(final String label) {
        appendLine(label + ":");
        terminated = false;
    }

    /**
     * emitBr outputs an unconditional branch, e.g. "br label %for.cond"
     * @param label the target label (without '%')
     */
    public void emitBr(final String label) {
        appendLine("br label %" + label);
        terminated = true;
    }

    /**
     * emitCondBr outputs a conditional branch, e.g.
     * "br i1 %1, label %for.body, label %for.end"
     * @param condReg the i1 condition register
     * @param trueLabel label to jump to if true
     * @param falseLabel label to jump to if false
     */
    public void emitCondBr(final String condReg, final String trueLabel, final String falseLabel) {
        appendLine("br i1 " + condReg + ", label %" + trueLabel + ", label %" + falseLabel);
        terminated = true;
    }

    /**
     * @return the global definitions (string constants and libc declares),
     *         which must go in front of the functions that use them
     */
    public String getGlobals() {
        return globals.toString();
    }

    /**
     * Adds a string literal to the module's globals and returns the name of
     * the resulting global (without the leading {@code @}).
     *
     * <p>The bytes are emitted as a private unnamed_addr constant with a
     * trailing null byte, which is what C string functions expect.
     *
     * @param bytes the UTF-8 bytes of the literal
     * @return the global's name
     */
    public String emitStringConstant(final byte[] bytes) {
        ensureStringDeclares();
        final String name = ".str." + (stringCount++);
        final int length = bytes.length + 1;

        globals.append("@").append(name)
                .append(" = private unnamed_addr constant [")
                .append(length).append(" x i8] c\"");
        for (final byte b : bytes) {
            final int u = b & 0xFF;
            if (u == '"') globals.append("\\22");
            else if (u == '\\') globals.append("\\5C");
            else if (u >= 32 && u < 127) globals.append((char) u);
            else globals.append(String.format("\\%02X", u));
        }
        globals.append("\\00\"\n");

        return name;
    }

    /**
     * Emits the libc declares the first time a string is actually used.
     * A program without strings never sees them.
     */
    private void ensureStringDeclares() {
        if (declaresEmitted) return;
        declaresEmitted = true;
        globals.append("declare ptr @malloc(i64)\n");
        globals.append("declare i64 @strlen(ptr)\n");
        globals.append("declare ptr @strcpy(ptr, ptr)\n");
        globals.append("declare ptr @strcat(ptr, ptr)\n");
    }
}
