package src.Codegen;

import src.Parser.ZType;

import java.util.*;

/**
 * SymbolTable is a class that represents the symbol table of the program
 */
public class SymbolTable {

    /**
     * SymbolInfo is a class that represents the information of a symbol
     *
     * <p>The type is kept as a {@link ZType}, not as an LLVM type string.
     * {@code int32} and {@code uint32} share the LLVM type {@code i32};
     * storing only the LLVM name would make it impossible to tell them
     * apart when reading the symbol back, and signed vs unsigned division
     * and comparison would silently pick the wrong instruction.
     */
    public static class SymbolInfo {
        /**
         * name is the name of the var
         * type is the Z type of the var
         * pointerReg is the register of the var
         * isConstant is true if the var is a constant
         * isUsed is true if the var is used
         * callCount is the number of times the var is called
         */
        private final String name;
        private final ZType type;
        private final String pointerReg;
        private final boolean isConstant;
        private boolean isUsed;
        private int callCount;

        /**
         * constructor for the SymbolInfo for a variable
         * @param name name of the var
         * @param type Z type of the var
         * @param pointerReg the register in which the pointer is stored
         * @param isConstant is it a constant?
         */
        public SymbolInfo(final String name, final ZType type, final String pointerReg, final boolean isConstant) {
            this.name = name;
            this.type = type;
            this.pointerReg = pointerReg;
            this.isConstant = isConstant;
            this.isUsed = false;
            this.callCount = 0;
        }

        /**
         * getter methods for a symbol info
         * @return name, type, ptrReg, isConstant, isUsed, callCount
         */
        public final String getName() { return name; }
        public final ZType getZType() { return type; }
        public final String getLlvmType() { return type.llvm(); }
        public final String getPointerReg() { return pointerReg; }
        public final boolean isConstant() { return isConstant; }
        public final boolean isUsed() { return isUsed; }
        public void setUsed(final boolean used) { this.isUsed = used; }
        public final int getCallCount() { return callCount; }
        public void incrementCallCount() { this.callCount++; }
    }

    /**
     * map of globals
     */
    private final Map<String, SymbolInfo> globals = new HashMap<>();

    /**
     * Deque of maps of symbol info
     */
    private final Deque<Map<String, SymbolInfo>> scopes = new ArrayDeque<>();

    /**
     * constructor for the symbol table
     * creates the initial global scope
     */
    public SymbolTable() {
        enterScope(); // initial global scope
    }

    /**
     * enters a new scope inside the parent
     */
    public void enterScope() {
        scopes.push(new HashMap<>());
    }

    /**
     * exit the current scope to go back at the parent
     */
    public void exitScope() {
        if (scopes.size() > 1) {
            scopes.pop();
        }
    }

    /**
     * defines a new global
     * @param name name
     * @param info info
     */
    public void defineGlobal(final String name, final SymbolInfo info) {
        globals.put(name, info);
    }

    /**
     * defines a symbol in the current scope
     * returns false if the symbol is already present
     */
    public final boolean define(final String name, final SymbolInfo info) {
        final Map<String, SymbolInfo> current = scopes.peek();
        if (current == null || current.containsKey(name)) {
            return false;
        }
        current.put(name, info);
        return true;
    }

    /**
     * searches a symbol starting from the current scope and going up
     */
    public final SymbolInfo lookup(final String name) {
        for (final Map<String, SymbolInfo> scope : scopes) {
            final SymbolInfo info = scope.get(name);
            if (info != null) {
                info.setUsed(true); // for optimization uses /warning
                return info;
            }
        }
        final SymbolInfo info = globals.get(name);
        if (name != null) return info;
        return null; // symbol not found
    }

    /**
     * searches for a symbol only in the current scope
     */
    public final SymbolInfo lookupCurrentScope(final String name) {
        final Map<String, SymbolInfo> current = scopes.peek();
        return current != null ? current.get(name) : null;
    }

    /**
     * getter method for a SymbolTable
     * @return the size (number of stacks, functions)
     */
    public final int getDepth() {
        return scopes.size();
    }
}