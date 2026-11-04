package src.Lexer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;


/**
 * Parses the source code and returns various Tokens, if it's unknown returns the ASCII value
 */
public class Lexer {
    private static final Logger logger = LoggerFactory.getLogger(Lexer.class);

    /**
     * Enum of Tokens
     */
    public enum Tokens {
        FREE("free", -52),
        ALLOC("alloc", -51),
        STRUCT("struct", -50),
        VARARG("...", -49),
        EXTERN("extern", -48),
        CONTINUE("continue", -47),
        BREAK("break", -46),
        FALSE("false", -45),
        TRUE("true", -44),
        MOD("%", -43),
        NE("!=", -42),
        EQ("==", -41),
        PTR("ptr", -40),
        GE(">=", -39),
        LE("<=", -38),
        VOID("void", -37),
        STRING_LITERAL("string-literal", -36),
        CN("cn", -35),
        IDENTIFIER("id", -34),
        AND("&", -33),
        SAME("=", -32),
        EOF("eof", -31),
        ASSIGN("->", -30),
        PARAMS("|", -29),
        ARRAY_CLOSE("]", -28),
        ARRAY_OPEN("[", -27),
        IMPORT("import", -26),
        MORE(">", -25),
        LESS("<", -24),
        CLOSE_FUNC("}", -23),
        OPEN_FUNC("{", -22),
        CLOSE_PAR(")", -21),
        OPEN_PAR("(", -20),
        FOR("for", -19),
        ELSE("else", -18),
        EIF("eif", -17),
        IF("if", -16),
        SUB("-", -15),
        ADD("+", -14),
        DIV("/", -13),
        MUL("*", -12),
        UINT64("uint64", -11),
        UINT32("uint32", -10),
        BOOL("bool", -9),
        STRING("str", -8),
        FLOAT64("flt64", -7),
        FLOAT32("flt32", -6),
        INT32("int32", -5),
        RET("ret", -4),
        NUMBER("unused", -3), // number returns
        INT64("int64", -2),
        FUNC("proc", -1),
        MUT("mut",-53);

        /**
         * Attributes: description is to identify the token; the value is the return value
         */
        public final String description;
        public final int value;

        // constructor for the Tokens
        Tokens(final String description, final int value) {
            this.description = description;
            this.value = value;
        }

        // helper method to get the token from the value
        public static Tokens fromValues(final int value) {
            return java.util.Arrays.stream(values()).filter(token -> token.value == value).findFirst().orElse(null);
        }
    }

    /**
     * lastChar is used to store the last char lol, IdentifierStr is used to store the String value of the identifier token (es. variable)
     * NumVal is used to store the value of the number token (es. 10)
     */
    private int lastChar = ' ';
    /** a '.' already read while looking for a vararg, still to be returned */
    private boolean pendingDot;
    private String identifierStr;
    private double numVal;
    /** the number exactly as it was written, which NumVal cannot represent */
    private String numberText;
    /** true when the literal had a decimal point or an exponent */
    private boolean numIsFloat;
    /** true when the literal was written without one */
    private boolean numIsInteger;
    /** the contents of a string literal, after escape processing */
    private String stringVal;

    public final String getStringVal() { return stringVal; }
    public final boolean getNumIsFloat() { return numIsFloat; }
    public final boolean getNumIsInteger() { return numIsInteger; }
    public final double getNumVal() { return numVal; }
    public final String getIdentifierStr() { return identifierStr; }
    public final String getNumberText() { return numberText; }

    // stream for the input
    private final InputStream input;

    /**
     * HashMap to store the keywords and their corresponding Tokens
     */
    private static final Map<String, Tokens> keywordMap = new HashMap<>();

    // using static like this is like a constructor that executes itself on the declaration
    static {
        for (Tokens t : Tokens.values()) {
            keywordMap.put(t.description, t);
        }
    }


    /**
     * Constructor for the Lexer
     * @param input stream for the input
     */
    public Lexer(final InputStream input) {
        this.input = input;
    }

    private void read() throws IOException {
        lastChar = input.read();
    }

    /**
     * Returns the next token
     * @return the next token
     * @throws IOException if an I/O error occurs
     */
    public int getTok() throws IOException {

        // a second dot left over from something that was not a vararg
        if (pendingDot) {
            pendingDot = false;
            return '.';
        }

        // skips whitespaces
        while (Character.isWhitespace(lastChar)) {
            read();
        }

        // EOF for some fucking reason in java is -1
        if (lastChar == -1) {
            return Tokens.EOF.value;
        }

        // assignment operator ->
        if (lastChar == '-') {
            read();

            if (lastChar == '>') {
                read();
                return Tokens.ASSIGN.value;
            }

            return Tokens.SUB.value;
        }

        // chars

        if (Character.isAlphabetic(lastChar)) {
            final StringBuilder sb = new StringBuilder();

            do {
                sb.append((char) lastChar);
                read();
            } while (Character.isLetterOrDigit(lastChar));

            identifierStr = sb.toString();

            // hashmap should be faster
            final Tokens token = keywordMap.get(identifierStr);
            if (token != null && token != Tokens.IDENTIFIER && token != Tokens.NUMBER && token != Tokens.EOF) {
                return token.value;
            }

            if (lastChar == '.') {
                read();
                pendingDot = true;
            }

            // if it's not a word it must be an identifier ig
            return Tokens.IDENTIFIER.value;
        }

        // Numbers.
        if (Character.isDigit(lastChar) || lastChar == '.') {
            final StringBuilder numStr = new StringBuilder();
            boolean floating = false;

            if (lastChar == '.') {
                read();
                if (lastChar == '.') {
                    read();
                    if (lastChar == '.') {
                        read();
                        return Tokens.VARARG.value;
                    }

                    pendingDot = true;
                    return '.';
                }
                if (!Character.isDigit(lastChar)) {
                    return '.';
                }

                numStr.append('.');
                floating = true;
            }

            do {
                if (lastChar == '.' || lastChar == 'e' || lastChar == 'E') {
                    floating = true;
                }
                numStr.append((char) lastChar);
                read();
            } while (Character.isDigit(lastChar) || lastChar == '.' || lastChar == 'e' || lastChar == 'E');

            numberText = numStr.toString();
            try {
                numVal = Double.parseDouble(numberText);
            } catch (final NumberFormatException e) {
                logger.error("Error: malformed number: {}", numberText);
                numVal = 0;
            }
            numIsFloat = floating;
            numIsInteger = !floating;
            return Tokens.NUMBER.value;
        }

        // String literals: "hello\n"
        if (lastChar == '"') {
            final StringBuilder sb = new StringBuilder();
            lastChar = input.read();
            while (lastChar != '"' && lastChar != -1) {
                if (lastChar == '\\') {
                    lastChar = input.read();
                    switch (lastChar) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case '\\' -> sb.append('\\');
                        case '"' -> sb.append('"');
                        case '0' -> sb.append('\0');
                        default -> sb.append((char) lastChar);
                    }
                } else {
                    sb.append((char) lastChar);
                }
                lastChar = input.read();
            }
            if (lastChar == -1) {
                logger.error("Error: unterminated string literal");
                return Tokens.EOF.value;
            }
            lastChar = input.read();  // consume closing quote
            stringVal = sb.toString();
            return Tokens.STRING_LITERAL.value;
        }


        // Comments
        if (lastChar == '/') {
            read();

            if (lastChar == '/') {
                do {
                    read();
                } while (lastChar != -1 && lastChar != '\n' && lastChar != '\r');

                return getTok();
            }

            // Multi-line comments
            if (lastChar == '*') {
                read();
                int previous = 0;
                while (lastChar != -1 && !(previous == '*' && lastChar == '/')) {
                    previous = lastChar;
                    read();
                }

                if (lastChar == -1) {
                    logger.error("Error: unterminated comment");
                    return Tokens.EOF.value;
                }

                read();  // consume closing slash
                return getTok();
            }

            return Tokens.DIV.value;
        }

        // two-character operators
        if (lastChar == '<' || lastChar == '>' || lastChar == '=' || lastChar == '!') {
            final int first = lastChar;
            read();

            if (lastChar == '=') {
                read();
                return switch (first) {
                    case '<' -> Tokens.LE.value;
                    case '>' -> Tokens.GE.value;
                    case '=' -> Tokens.EQ.value;
                    default -> Tokens.NE.value;
                };
            }

            return switch (first) {
                case '<' -> Tokens.LESS.value;
                case '>' -> Tokens.MORE.value;
                case '=' -> Tokens.SAME.value;
                default -> first;
            };
        }

        // symbols
        final Tokens symbol = keywordMap.get(Character.toString((char) lastChar));

        if (symbol != null) {
            read();
            return symbol.value;
        }

        // returns the ASCII if unrecognized
        final int ThisChar = lastChar;

        // next one
        read();

        return ThisChar;
        }
    }