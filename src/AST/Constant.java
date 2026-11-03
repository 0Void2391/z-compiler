package src.AST;
import src.Parser.ZType;
import src.Parser.TypeRef;

public final class Constant {
    private final ZType type;
    private final long integer;
    private final double real;
    private final boolean isFloating;

    private Constant (ZType type, long integer, double real, boolean isFloating) {
        this.type = type;
        this.integer = integer;
        this.real = real;
        this.isFloating = isFloating;
    }

    public static Constant ofInteger(final ZType type, final long value) {
        if (!type.isSigned() && !type.isUnsigned()) {
            throw new IllegalArgumentException("Integer isn't signed or unsigned");
        }

        final long normalized;
        if (type.bits() == 32) {
            normalized = type.isSigned() ? (int) value : value & 0xFFFFFFFFL;
        } else {
            normalized = value;
        }

        final double asReal = type.isUnsigned() && type.bits() == 64
                ? unsignedToDouble(normalized)
                : (double) normalized;

        return new Constant(type, normalized, asReal, false);
    }

    public static Constant ofFloat(ZType type, double flt) throws IllegalArgumentException {
        double tmp;
        if (!type.isFloat()) {
            throw new IllegalArgumentException("Value isn't a float");
        }
        tmp = flt;
        if (type.bits() == 32) {
            tmp = (float) tmp;
        }
        return new Constant(type, (long) tmp, tmp, true);
    }

    public static Constant zeroOf(TypeRef type) throws NullPointerException{
        if (type == null) {
            throw new NullPointerException();
        }
        if (type.isSigned() || type.isUnsigned()) {
            return new Constant((ZType) type, 0,0, false);
        } else if (type.isFloat()) {
            return new Constant((ZType) type, 0,0,true);
        }
        return null;
    }

    public ZType type() {
        return this.type;
    }

    public boolean isFloat() {
        return this.type.isFloat();
    }

    public boolean isIntegral() {
        return this.type.isSigned() || this.type.isUnsigned();
    }

    public long asLong() throws IllegalArgumentException {
        if (this.isFloat()) { //no implicit conversions
            throw new IllegalArgumentException("Constant is a float");
        }
        return this.integer;
    }

    private static double unsignedToDouble(final long value) {
        if (value >= 0) {
            return (double) value;
        }
        long high = value >>> 11;            // the 53 bits that fit in a double
        final long low = value & 0x7FFL;     // the 11 discarded bits
        if (low > 0x400L || (low == 0x400L && (high & 1L) != 0)) {
            high++;                          // round up, ties-to-even
        }
        return Math.scalb((double) high, 11);
    }

    private static float unsignedToFloat(final long value) {
        if (value >= 0) {
            return (float) value;
        }
        long high = value >>> 40;            // the 24 bits that fit in a float
        final long low = value & ((1L << 40) - 1); // the 40 discarded bits
        final long half = 1L << 39;
        if (low > half || (low == half && (high & 1L) != 0)) {
            high++;                          // round up, ties-to-even
        }
        return (float) Math.scalb((double) high, 40);
    }

    public double asDouble() {
        if (this.type.isUnsigned() && this.type.bits() == 64) {
            return unsignedToDouble(integer);
        }
        return real;
    }

    public Value toValue() {
        if (this.isFloating) {
            return new Value(this.type.literal(real), type);
        }
        return new Value(this.type.literal(integer), type);
    }

    public Constant convertTo(final ZType target) {
        if (target == null) {
            return null;
        }
        if (!target.isSigned() && !target.isUnsigned() && !target.isFloat()) {
            return null; // str, ptr, bool, void, vararg
        }
        if (this.type == target) {
            return this;
        }

        if (this.isFloat()) {
            if (target.isFloat()) {
                return ofFloat(target, this.real); // fptrunc / fpext
            }
            return target.isUnsigned()
                    ? floatToUnsigned(target)   // fptoui
                    : floatToSigned(target);    // fptosi
        }

        // Only signed and unsigned integers are in the numeric domain.
        if (!this.type.isSigned() && !this.type.isUnsigned()) {
            return null;
        }

        if (target.isFloat()) {
            return integerToFloat(target);      // sitofp / uitofp
        }
        return integerToInteger(target);        // trunc / zext / sext
    }

    /** @return a mask with the low {@code bits} bits set. */
    private static long maskOf(final int bits) {
        return bits == 64 ? -1L : (1L << bits) - 1L;
    }

    /** Sign-extends the low {@code bits} of {@code value} to 64 bits. */
    private static long signExtend(final long value, final int bits) {
        if (bits == 64) {
            return value;
        }
        final int shift = 64 - bits;
        return (value << shift) >> shift;
    }

    /**
     * Integral to integral: trunc, zext or sext, exactly like
     * {@link ExprAST#coerce}.
     */
    private Constant integerToInteger(final ZType target) {
        final int sourceBits = this.type.bits();
        final int targetBits = target.bits();
        final long source = this.integer & maskOf(sourceBits);

        long value;
        if (targetBits < sourceBits) {
            value = source & maskOf(targetBits);        // trunc
        } else {
            value = this.type.isSigned()
                    ? signExtend(source, sourceBits)    // sext
                    : source;                           // zext
            value &= maskOf(targetBits);
        }

        // Canonical form: signed 32 is sign-extended inside the long, unsigned
        // 32 is non-negative, the 64-bit types keep their raw bit pattern.
        if (target.isSigned() && targetBits == 32) {
            value = (int) value;
        }

        final double asReal = target.isUnsigned() && targetBits == 64
                ? unsignedToDouble(value)
                : (double) value;
        return new Constant(target, value, asReal, false);
    }

    /** Integral to float: {@code sitofp} or {@code uitofp}. */
    private Constant integerToFloat(final ZType target) {
        final int bits = this.type.bits();
        final long source = this.integer & maskOf(bits);

        if (target.bits() == 32) {
            // Convert straight to float: going through a double could round twice.
            final float value = this.type.isUnsigned()
                    ? unsignedToFloat(source)
                    : (float) signExtend(source, bits);
            return ofFloat(target, value);
        }
        final double value = this.type.isUnsigned()
                ? unsignedToDouble(source)
                : (double) signExtend(source, bits);
        return ofFloat(target, value);
    }

    /** Float to signed integer: {@code fptosi}, truncating towards zero. */
    private Constant floatToSigned(final ZType target) {
        final double value = this.real;
        if (!Double.isFinite(value)) {
            return null; // NaN and infinities are poison for fptosi
        }

        final double truncated = value < 0 ? Math.ceil(value) : Math.floor(value);
        final double limit = Math.scalb(1.0, target.bits() - 1);
        if (truncated < -limit || truncated >= limit) {
            return null; // out of range is poison
        }

        final long bits = target.bits() == 32 ? (int) truncated : (long) truncated;
        return new Constant(target, bits, (double) bits, false);
    }

    /** Float to unsigned integer: {@code fptoui}, truncating towards zero. */
    private Constant floatToUnsigned(final ZType target) {
        final double value = this.real;
        if (!Double.isFinite(value)) {
            return null; // NaN and infinities are poison for fptoui
        }

        final double truncated = value < 0 ? Math.ceil(value) : Math.floor(value);
        final double limit = Math.scalb(1.0, target.bits());
        if (truncated < 0 || truncated >= limit) {
            return null; // out of range is poison
        }

        final long bits;
        if (target.bits() == 64) {
            final double twoPow63 = Math.scalb(1.0, 63);
            bits = truncated < twoPow63
                    ? (long) truncated
                    : ((long) (truncated - twoPow63)) | Long.MIN_VALUE;
        } else {
            bits = (long) truncated;
        }

        return new Constant(target, bits,
                target.bits() == 64 ? unsignedToDouble(bits) : (double) bits, false);
    }

    /**
     * @return {@code this + other}, or {@code null} when the operands are not
     *         numeric, have different types, or the result cannot be represented.
     */
    public Constant add(final Constant other) {
        return arithmetic(other, '+');
    }

    /** @return {@code this - other}, or {@code null} on the same conditions as {@link #add}. */
    public Constant sub(final Constant other) {
        return arithmetic(other, '-');
    }

    /** @return {@code this * other}, or {@code null} on the same conditions as {@link #add}. */
    public Constant mul(final Constant other) {
        return arithmetic(other, '*');
    }

    /** @return {@code this / other}, or {@code null} for a zero divisor or signed overflow. */
    public Constant div(final Constant other) {
        return arithmetic(other, '/');
    }

    /** @return {@code this % other}, or {@code null} for a zero divisor or signed overflow. */
    public Constant rem(final Constant other) {
        return arithmetic(other, '%');
    }

    private Constant arithmetic(final Constant other, final char op) {
        if (other == null || this.type != other.type) {
            return null;
        }
        if (this.type.isFloat()) {
            return floatArithmetic(other, op);
        }
        if (!this.type.isSigned() && !this.type.isUnsigned()) {
            return null; // bool, str, ptr, void, vararg
        }
        return integerArithmetic(other, op);
    }

    private Constant floatArithmetic(final Constant other, final char op) {
        if (this.type.bits() == 32) {
            // binary32 arithmetic: overflow gives +/-Infinity, 0/0 gives NaN.
            final float a = (float) this.real;
            final float b = (float) other.real;
            final float result = switch (op) {
                case '+' -> a + b;
                case '-' -> a - b;
                case '*' -> a * b;
                case '/' -> a / b;
                case '%' -> a % b;
                default -> Float.NaN;
            };
            if (!Float.isFinite(result)) {
                return null; // leave it to fadd/fsub/fmul/fdiv/frem
            }
            return ofFloat(this.type, result);
        }

        final double a = this.real;
        final double b = other.real;
        final double result = switch (op) {
            case '+' -> a + b;
            case '-' -> a - b;
            case '*' -> a * b;
            case '/' -> a / b;
            case '%' -> a % b;
            default -> Double.NaN;
        };
        if (!Double.isFinite(result)) {
            return null;
        }
        return ofFloat(this.type, result);
    }

    private Constant integerArithmetic(final Constant other, final char op) {
        final long a = normalize(this.type, this.integer);
        final long b = normalize(this.type, other.integer);

        return switch (op) {
            case '+' -> ofNormalizedInteger(this.type, a + b);
            case '-' -> ofNormalizedInteger(this.type, a - b);
            case '*' -> ofNormalizedInteger(this.type, a * b);
            case '/' -> divide(a, b);
            case '%' -> remainder(a, b);
            default -> null;
        };
    }

    private Constant divide(final long a, final long b) {
        if (b == 0) {
            return null; // division by zero: sdiv/udiv are poison
        }
        if (this.type.isUnsigned()) {
            return ofNormalizedInteger(this.type, Long.divideUnsigned(a, b));
        }
        if (signedOverflow(a, b)) {
            return null; // INT_MIN / -1: sdiv is poison
        }
        return ofNormalizedInteger(this.type, a / b);
    }

    private Constant remainder(final long a, final long b) {
        if (b == 0) {
            return null; // division by zero: srem/urem are poison
        }
        if (this.type.isUnsigned()) {
            return ofNormalizedInteger(this.type, Long.remainderUnsigned(a, b));
        }
        if (signedOverflow(a, b)) {
            return null; // INT_MIN % -1: srem is poison
        }
        return ofNormalizedInteger(this.type, a % b);
    }

    private boolean signedOverflow(final long a, final long b) {
        if (b != -1) {
            return false;
        }
        return this.type.bits() == 32
                ? a == Integer.MIN_VALUE
                : a == Long.MIN_VALUE;
    }

    /**
     * @return {@code -this}, or {@code null} for a non-numeric or non-finite
     *         constant.
     */
    public Constant negate() {
        if (this.type.isFloat()) {
            if (this.type.bits() == 32) {
                final float result = -(float) this.real;
                return Float.isFinite(result) ? ofFloat(this.type, result) : null;
            }
            final double result = -this.real;
            return Double.isFinite(result) ? ofFloat(this.type, result) : null;
        }
        if (!this.type.isSigned() && !this.type.isUnsigned()) {
            return null;
        }
        return ofNormalizedInteger(this.type, -this.integer);
    }

    /**
     * Compares this constant with {@code other} using a source operator.
     *
     * @param op one of {@code <}, {@code <=}, {@code >}, {@code >=},
     *           {@code ==}, {@code !=}
     * @return {@link Boolean#TRUE} or {@link Boolean#FALSE}, or {@code null}
     *         when the operands are not comparable or the operator is unknown.
     */
    public Boolean compare(final String op, final Constant other) {
        if (other == null || this.type != other.type) {
            return null;
        }

        if (this.type.isFloat()) {
            final double a = this.real;
            final double b = other.real;
            return switch (op) {
                case "<" -> a < b;
                case "<=" -> a <= b;
                case ">" -> a > b;
                case ">=" -> a >= b;
                case "==" -> a == b;
                case "!=" -> a != b;
                default -> null;
            };
        }

        if (!this.type.isSigned() && !this.type.isUnsigned()) {
            return null;
        }

        final int cmp = this.type.isUnsigned()
                ? Long.compareUnsigned(this.integer, other.integer)
                : Long.compare(this.integer, other.integer);
        return switch (op) {
            case "<" -> cmp < 0;
            case "<=" -> cmp <= 0;
            case ">" -> cmp > 0;
            case ">=" -> cmp >= 0;
            case "==" -> cmp == 0;
            case "!=" -> cmp != 0;
            default -> null;
        };
    }

    /** @return true when the constant is numerically zero (including -0.0). */
    public boolean isZero() {
        return this.type.isFloat() ? this.real == 0.0 : this.integer == 0;
    }

    /** Normalizes an integer value to the canonical representation of {@code type}. */
    private static long normalize(final ZType type, final long value) {
        if (type.bits() == 32) {
            return type.isSigned() ? (int) value : value & 0xFFFFFFFFL;
        }
        return value;
    }

    /** Builds an integral constant from a raw value, normalizing it to {@code type}. */
    private static Constant ofNormalizedInteger(final ZType type, final long value) {
        final long normalized = normalize(type, value);
        final double asReal = type.isUnsigned() && type.bits() == 64
                ? unsignedToDouble(normalized)
                : (double) normalized;
        return new Constant(type, normalized, asReal, false);
    }

}
