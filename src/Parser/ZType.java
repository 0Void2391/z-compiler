package src.Parser;

/**
 * The types Z can name, and what each one is in LLVM.
 *
 * <p>{@code uint32} and {@code uint64} share their LLVM type with
 * {@code int32} and {@code int64}: LLVM does not distinguish signed from
 * unsigned integers in the type system, only in the instructions that use
 * them. The signedness is carried here so that
 * {@link src.AST.BinaryExprAST} picks {@code udiv}/{@code ult} over
 * {@code sdiv}/{@code slt}, and {@link src.AST.ExprAST#coerce} picks
 * {@code zext}/{@code uitofp} over {@code sext}/{@code sitofp}.
 *
 * <p>{@code str} is an {@code i8*}. The language has no string literals
 * yet, so the only thing a {@code str} can do for now is be declared and
 * passed around; it starts life as a null pointer.
 */
public enum ZType {
    PTR("ptr", "ptr", "0", 64, Kind.POINTER),
    INT32 ("int32",  "i32",    "0",     32, Kind.SIGNED),
    INT64 ("int64",  "i64",    "0",     64, Kind.SIGNED),
    UINT32("uint32", "i32",    "0",     32, Kind.UNSIGNED),
    UINT64("uint64", "i64",    "0",     64, Kind.UNSIGNED),
    FLT32 ("flt32",  "float",  "0.0",   32, Kind.FLOAT),
    FLT64 ("flt64",  "double", "0.0",   64, Kind.FLOAT),
    BOOL  ("bool",     "i1",     "false",  1, Kind.BOOL),
    STRING("str",    "i8*",    "null",   0, Kind.STRING),
    VOID  ("void",   "void",   "",       0, Kind.VOID);

    /** What family a type belongs to. */
    private enum Kind { POINTER, SIGNED, UNSIGNED, FLOAT, BOOL, STRING, VOID }

    private final String zName;
    private final String llvm;
    private final String initial;
    private final int bits;
    private final Kind kind;

    ZType(final String zName, final String llvm, final String initial, final int bits, final Kind kind) {
        this.zName = zName;
        this.llvm = llvm;
        this.initial = initial;
        this.bits = bits;
        this.kind = kind;
    }

    /** @return the name the language spells this type with */
    public String zName() { return zName; }

    /** @return the LLVM type: {@code i32}, {@code double}, … */
    public String llvm() { return llvm; }

    /** @return the LLVM literal text for a variable declared but never assigned */
    public String initial() { return initial; }

    /** @return the width in bits */
    public int bits() { return bits; }

    /** @return true for the integer types: signed, unsigned, and bool */
    public boolean isIntegral() {
        return kind == Kind.SIGNED || kind == Kind.UNSIGNED || kind == Kind.BOOL;
    }

    /** @return true for {@code flt32} and {@code flt64} */
    public boolean isFloat() { return kind == Kind.FLOAT; }

    /** @return true for the signed integer types */
    public boolean isSigned() { return kind == Kind.SIGNED; }

    /** @return true for {@code uint32} and {@code uint64} */
    public boolean isUnsigned() { return kind == Kind.UNSIGNED; }

    /** @return true for a value that is a truth value rather than a number */
    public boolean isBoolean() { return kind == Kind.BOOL; }

    /** @return true for {@code str}, an {@code i8*} */
    public boolean isString() { return kind == Kind.STRING; }

    /** @return true for {@link #VOID}: a statement that produces nothing */
    public boolean isVoid() { return kind == Kind.VOID; }

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
     *
     * <p>Same-width signed and unsigned integers unify to unsigned, the way
     * C does. Different widths pick the wider one. A string cannot be
     * combined with anything else.
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

        if (a.isString() || b.isString()) {
            throw new IllegalArgumentException("str cannot be combined with " + other(a, b));
        }

        if (a.isFloat() && b.isFloat()) return a.bits() >= b.bits() ? a : b;
        if (a.isFloat() || b.isFloat()) {
            final ZType floating = a.isFloat() ? a : b;
            final ZType integer = a.isFloat() ? b : a;
            return integer.bits() > floating.bits() ? FLT64 : floating;
        }

        // Both are integers. If either is unsigned the result is unsigned,
        // at the wider of the two widths. Otherwise both are signed.
        final int width = Math.max(a.bits(), b.bits());
        if (a.isUnsigned() || b.isUnsigned()) {
            return width == 64 ? UINT64 : UINT32;
        }
        return width == 64 ? INT64 : INT32;
    }

    private static String other(final ZType a, final ZType b) {
        return (a.isBoolean() || a.isString() ? b : a).zName();
    }

    /**
     * Prints a whole number in the right LLVM syntax for this type.
     *
     * @param value the value to print
     * @return literal text valid for this type
     */
    public String literal(final long value) {
        return switch (kind) {
            case BOOL -> Boolean.toString(value != 0);
            case SIGNED, UNSIGNED -> bits == 32 ? Integer.toString((int) value) : Long.toString(value);
            case FLOAT -> Double.toString(value);
            case POINTER, STRING, VOID -> throw new IllegalStateException(zName + " has no literal");
        };
    }

    /**
     * Prints a fractional number in the right LLVM syntax for this type.
     *
     * @param value the value to print
     * @return literal text valid for this type
     */
    public String literal(final double value) {
        return switch (kind) {
            case BOOL -> Boolean.toString(value != 0);
            case SIGNED, UNSIGNED -> bits == 32 ? Integer.toString((int) value) : Long.toString((long) value);
            case FLOAT -> Double.toString(value);
            case POINTER, STRING, VOID -> throw new IllegalStateException(zName + " has no literal");
        };
    }
}