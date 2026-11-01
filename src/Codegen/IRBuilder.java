package src.Codegen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.AST.PrototypeAST;
import src.AST.Value;
import src.Parser.StructType;
import src.Parser.TypeRef;
import src.Parser.ZType;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Class with helper methods to generate LLVM IR code using testual strings
 */
public class IRBuilder {
    private static final Logger logger = LoggerFactory.getLogger(IRBuilder.class);

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

    private final Map<String, PrototypeAST> signatures = new LinkedHashMap<>();

    private String currentModule;

    public void setCurrentModule(final String moduleName) {
        this.currentModule = moduleName;
    }

    public String currentModule() {
        return currentModule;
    }

    public String qualify(final String name) {
        return currentModule == null || name.contains(".") ? name : currentModule + "." + name;
    }

    public String irName(final String key) {
        return key.replace('.', '_');
    }


    public String irNameOf(final String name) {
        return irName(qualify(name));
    }

    public record ResolvedSignature(PrototypeAST prototype, String irName, ZType externReturnType) {
        public ResolvedSignature(final PrototypeAST prototype, final String irName) {
            this(prototype, irName, null);
        }
    }


    public ResolvedSignature resolve(final String callee) {
        if (callee.contains(".")) {
            final PrototypeAST prototype = signatures.get(callee);
            if (prototype != null) return new ResolvedSignature(prototype, irName(callee));
            // an extern of the standard library keeps its plain C name
            if (!callee.startsWith("std.")) return null;
            return resolveExtern(callee.substring("std.".length()));
        }
        if (currentModule != null) {
            // a wrapper calls the C function it shares its name with
            if (moduleExterns.contains(currentModule + "." + callee)) {
                final ResolvedSignature external = resolveExtern(callee);
                if (external != null) return external;
            }
            final PrototypeAST own = signatures.get(currentModule + "." + callee);
            if (own != null) return new ResolvedSignature(own, irName(currentModule + "." + callee));
        }
        final PrototypeAST root = signatures.get(callee);
        if (root != null) return new ResolvedSignature(root, irName(callee), externReturnTypes.get(callee));
        // the standard library can be called without its prefix once imported
        final PrototypeAST std = signatures.get("std." + callee);
        if (std == null) return null;
        return new ResolvedSignature(std, irName("std." + callee), externReturnTypes.get("std." + callee));
    }

    private ResolvedSignature resolveExtern(final String name) {
        final PrototypeAST prototype = signatures.get(name);
        final ZType returnType = externReturnTypes.get(name);
        if (prototype == null || returnType == null) return null;
        return new ResolvedSignature(prototype, irName(name), returnType);
    }

    private static final String DOUBLE = "double";
    private static final String FLT = "float";
    private boolean terminated;
    private TypeRef returnType = ZType.FLT64;

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
        final String key = qualify(prototype.name());
        if (signatures.containsKey(key)) return false;
        signatures.put(key, prototype);
        return true;
    }


    /**
     * The return types of the extern (C) functions declared so far.
     */
    private final Map<String, ZType> externReturnTypes = new HashMap<>();

    /** Externs declared by each module, used to resolve its wrappers. */
    private final Set<String> moduleExterns = new HashSet<>();

    /** What happened when an extern was declared. */
    public enum ExternDeclaration {
        DECLARED,
        ALREADY_DECLARED,
        CONFLICT
    }

    /**
     * Declares an extern function together with the type it returns.
     *
     * @param prototype the signature to declare
     * @param returnType what the C function returns
     * @return whether the declaration is new, already present, or conflicts
     */
    public ExternDeclaration declareExtern(final PrototypeAST prototype, final ZType returnType) {
        moduleExterns.add(qualify(prototype.name()));
        final PrototypeAST existing = signatures.get(prototype.name());
        if (existing == null) {
            signatures.put(prototype.name(), prototype);
            externReturnTypes.put(prototype.name(), returnType);
            return ExternDeclaration.DECLARED;
        }

        final ZType existingReturn = externReturnTypes.get(prototype.name());
        if (existingReturn == null) {
            return ExternDeclaration.CONFLICT;
        }
        if (existing.equals(prototype) && existingReturn == returnType) {
            return ExternDeclaration.ALREADY_DECLARED;
        }
        return ExternDeclaration.CONFLICT;
    }

    public void emitReturn(final Value value) {
        final TypeRef targetType = returnType;
        if (returnType == ZType.VOID) {
            appendLine("ret void");
            terminated = true;
            return;
        }

        if (value == null) { // it must be main
            appendLine("ret i32 281");
            terminated = true;
            return;
        }


        final String target = targetType.llvm();
        final String from = value.type().llvm();

        if (from.equals(target)) {
            appendLine("ret " + target + " " + value.text());
            terminated = true;
            return;
        }

        if (from.equals("ptr") || target.equals("ptr")) {
            logger.error("Error: cannot convert between {} and {}", from, target);
            markFailed();
            return;
        }

        // str is an i8*. It is not a number, and the conversion opcodes
        // below would produce invalid IR for it.
        if (from.equals("i8*") || target.equals("i8*")) {
            logger.error("Error: cannot convert between str and {} ",(from.equals("i8*") ? target : from));
            markFailed();
            return;
        }
        final String opcode = conversionOpcode(value.type(), targetType);
        if (opcode == null) {
            logger.error("Error: cannot convert between {} and {}", from, target);
            markFailed();
            return;
        }
        final String converted = nextRegister();
        appendLine(converted + " = " + opcode + from + " " + value.text() + " to " + target);
        appendLine("ret " + target + " " + converted);
        terminated = true;
    }

    /** Emits a terminator that has no operand, such as a {@code main} that fell off the end. */
    public void emitBareReturn(final String literal) {
        appendLine("ret " + literal);
        terminated = true;
    }

    private static String conversionOpcode(final TypeRef fromType, final TypeRef targetType) {
        final String from = fromType.llvm();
        final String to = targetType.llvm();
        final boolean fromFloat = fromType.isFloat();
        final boolean toFloat = targetType.isFloat();
        if (fromFloat && !toFloat) {
            return targetType.isUnsigned() || targetType.isBoolean() ? "fptoui " : "fptosi ";
        }
        if (!fromFloat && toFloat) {
            return fromType.isUnsigned() || fromType.isBoolean() ? "uitofp " : "sitofp ";
        }
        if (fromFloat) return to.equals(FLT) ? "fptrunc " : "fpext ";
        if (!from.startsWith("i") || !to.startsWith("i")) return null;
        final int fromBits = fromType.bits();
        final int toBits = integerBits(to);
        if (fromBits > toBits) return "trunc ";
        if (fromBits < toBits) {
            return fromType.isUnsigned() || fromType.isBoolean() ? "zext " : "sext ";
        }
        return null;
    }

    private static int integerBits(final String type) {
        return type.equals("i1") ? 1 : Integer.parseInt(type.substring(1));
    }

    /** @return whether the current block already has a terminator */
    public boolean isTerminated() {
        return terminated;
    }

    /** Declares what the function being generated returns. */
    public void setReturnType(final TypeRef type) {
        this.returnType = type;
    }

    /** @return the symbol table for the function being generated */
    public SymbolTable symbols() {
        return symbols;
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

    /**
     * return the String form of the IR
     */
    @Override
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
        loops.clear();
        terminated = false;
        failed = false;
    }

    /** the loops being generated, the innermost first */
    private final Deque<Loop> loops = new ArrayDeque<>();

    /** Starts a loop whose body can {@code break} or {@code continue}. */
    public void enterLoop(final String continueLabel, final String breakLabel) {
        loops.push(new Loop(continueLabel, breakLabel));
    }

    /** Ends the innermost loop. */
    public Loop exitLoop() {
        return loops.pop();
    }

    /** @return the innermost loop, or null outside of any loop */
    public Loop currentLoop() {
        return loops.peek();
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

    public final String emitAllocaGlobal(final String varName, final String llvmType) {
        final String ptr = "%" + varName;
        appendLine(ptr + " = global " + llvmType);
        return ptr;
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
     * Gets the LLVM type of register
     * @param reg the register name (e.g., "%1")
     * @return the LLVM type or null if not tracked
     */
    public final String getRegisterType(final String reg) {
        return registerTypes.get(reg);
    }

    private int labelCount = 0;
    /**
     * nextLabel emits the next available label
     * @param prefix the prefix of the label
     * @return the label
     */
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

        globals.append("@").append(name).append(" = private unnamed_addr constant [").append(length).append(" x i8] c\"");
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

    public void appendToGlobals(final String append) {
        globals.append(append);
    }

    /**
     * Emits the libc declares the first time a string is actually used.
     * A program without strings never sees them.
     */
    private void ensureStringDeclares() {
        if (declaresEmitted) return;
        declaresEmitted = true;
        globals.append("declare ptr @malloc(i64)\n");
        if (!hasExtern("strlen", ZType.UINT64)) {
            globals.append("declare i64 @strlen(ptr)\n");
        }
        globals.append("declare ptr @strcpy(ptr, ptr)\n");
        globals.append("declare ptr @strcat(ptr, ptr)\n");
    }

    private boolean hasExtern(final String name, final ZType returnType) {
        final PrototypeAST prototype = signatures.get(name);
        if (prototype == null || externReturnTypes.get(name) != returnType) return false;
        return prototype.params().size() == 1 && prototype.params().getFirst().type().equals(ZType.STRING.zName());
    }

    /** The structs this module has already declared. */
    private final Set<String> declaredStructs = new HashSet<>();

    /** Emits {@code %Point = type { i32, i32 }} once. */
    public void declareStruct(final StructType type) {
        if (!declaredStructs.add(type.zName())) return;
        final StringBuilder sb = new StringBuilder("%").append(type.zName()).append(" = type { ");
        for (int i = 0; i < type.fields().size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(type.fields().get(i).type().llvm());
        }
        sb.append(" }\n");
        appendToGlobals(sb.toString());
    }

    /** Emits {@code %r = getelementptr inbounds %T, ptr %base, i32 0, i32 idx}. */
    public String emitGEP(final String basePtr, final String structType, final int idx) {
        final String reg = nextRegister();
        appendLine(reg + " = getelementptr inbounds " + structType
                + ", ptr " + basePtr + ", i32 0, i32 " + idx);
        return reg;
    }
}
