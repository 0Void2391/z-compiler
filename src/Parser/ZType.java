package src.Parser;

/**
 * The types Z can name, and what each one is in LLVM.
 */
public enum ZType {
    PTR("ptr", "ptr", "0", 64, false),
    INT32("int32", "i32", "0", 32, true),
    INT64("int64", "i64", "0", 64, true),
    FLT32("flt32", "float", "0.0", 32, false),
    FLT64("flt64", "double", "0.0", 64, false),
    BOOL("01", "i1", "false", 1, true),
    VOID("void", "void", "", 0, false);

    private final String zName;
    private final String llvm;
    private final String initial;
    private final int bits;
    private final boolean integral;

    ZType(final String zName, final String llvm, final String initial, final int bits, final boolean integral) {
        this.zName = zName;
        this.llvm = llvm;
        this.initial = initial;
        this.bits = bits;
        this.integral = integral;
    }

    /** @return the name the language spells this type with */
    public String zName() {
        return zName;
    }

    /** @return the LLVM type: {@code i32}, {@code double}, … */
    public String llvm() {
        return llvm;
    }

    /** @return the LLVM literal text for a variable declared but never assigned */
    public String initial() {
        return initial;
    }

    /** @return the width in bits */
    public int bits() {
        return bits;
    }

    /** @return true for the integer and boolean types */
    public boolean isIntegral() {
        return integral;
    }

    /** @return true for {@code flt32} and {@code flt64} */
    public boolean isFloat() {
        return !integral;
    }

    /** @return true for a value that is a truth value rather than a number */
    public boolean isBoolean() {
        return this == BOOL;
    }

    /** @return true for {@link #VOID}: a statement that produces nothing */
    public boolean isVoid() {
        return this == VOID;
    }

    /**
     * @param name a type name as written in a program
     * @return the type it names
     * @throws IllegalArgumentException if no such type exists
     */
    public static ZType fromZName(final String name) {
        for (final ZType type : values()) {
            if (type.zName.equals(name)) return type;
        }
        throw new IllegalArgumentException("no such type: " + name);
    }

    /** @return whether {@code name} is a type this language has */
    public static boolean isType(final String name) {
        for (final ZType type : values()) {
            if (type.zName.equals(name)) return true;
        }
        return false;
    }

    /**
     * The type two operands are computed in.
     */
    public static ZType unify(final ZType a, final ZType b) {
        if (a == null) return b;
        if (b == null) return a;
        if (a == b) return a;
        if (a.isVoid()) return b;
        if (b.isVoid()) return a;
        if (a.isBoolean() || b.isBoolean()) {
            if (a.isBoolean() && b.isBoolean()) return BOOL;
            throw new IllegalArgumentException("a truth value cannot be combined with " + other(a, b));
        }
        if (a.isFloat() && b.isFloat()) return a.bits() >= b.bits() ? a : b;
        if (a.isFloat() || b.isFloat()) {
            final ZType floating = a.isFloat() ? a : b;
            final ZType integer = a.isFloat() ? b : a;
            return integer.bits() > floating.bits() ? FLT64 : floating;
        }
        return a.bits() >= b.bits() ? a : b;
    }

    private static String other(final ZType a, final ZType b) {
        return (a.isBoolean() ? b : a).zName();
    }

    /**
     * Prints a whole number in the right LLVM syntax for this type.
     *
     * @param value the value to print
     * @return literal text valid for this type
     */
    public String literal(final long value) {
        if (this == BOOL) return Boolean.toString(value != 0);
        if (isVoid()) throw new IllegalStateException("void has no literal");
        if (isFloat()) return Double.toString(value);
        if (bits() == 32) return Integer.toString((int) value);
        return Long.toString(value);
    }

    /**
     * Prints a fractional number in the right LLVM syntax for this type.
     *
     * @param value the value to print
     * @return literal text valid for this type
     */
    public String literal(final double value) {
        if (this == BOOL) return Boolean.toString(value != 0);
        if (isVoid()) throw new IllegalStateException("void has no literal");
        if (isFloat()) return Double.toString(value);
        if (bits() == 32) return Integer.toString((int) value);
        return Long.toString((long) value);
    }
}
