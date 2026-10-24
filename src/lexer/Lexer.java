package src.lexer;

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
        EXTERN("extern", -50),
        FUNC("proc", -1),
        INT64("int64", -2),
        NUMBER("unused", -3), // number returns
        RET("ret", -4),
        INT32("int32", -5),
        FLOAT32("flt32", -6),
        FLOAT64("flt64", -7),
        STRING("str", -8),
        BOOL("bool", -9),
        UINT32("uint32", -10),
        UINT64("uint64", -11),
        MUL("*", -12),
        DIV("/", -13),
        ADD("+", -14),
        SUB("-", -15),
        ASSIGN("->", -30),
        IF("if", -16),
        EIF("eif", -17),
        ELSE("else", -18),
        FOR("for", -19),
        OPEN_PAR("(", -20),
        CLOSE_PAR(")", -21),
        OPEN_FUNC("{", -22),
        CLOSE_FUNC("}", -23),
        LESS("<", -24),
        MORE(">", -25),
        IMPORT("import", -26),
        ARRAY_OPEN("[", -27),
        ARRAY_CLOSE("]", -28),
        PARAMS("|", -29),
        EOF("eof", -31),
        SAME("=", -32),
        PTR("ptr", -40),
        IDENTIFIER("id", -34),
        CN("cn", -35),
        STRING_LITERAL("string-literal", -36),
        VOID("void", -37),
        AND("&", -38);

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
     * Lastchar is used to store the last char lol, IdentifierStr is used to store the String value of the identifier token (es. variable)
     * NumVal is used to store the value of the number token (es. 10)
     */
    private int lastChar = ' ';
    public String identifierStr;
    public double numVal;
    /** the number exactly as it was written, which NumVal cannot represent */
    public String numberText;
    /** true when the literal had a decimal point or an exponent */
    public boolean numIsFloat;
    /** true when the literal was written without one */
    public boolean numIsInteger;
    /** the contents of a string literal, after escape processing */
    public String stringVal;

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

            // if it's not a word it must be an identifier ig
            return Tokens.IDENTIFIER.value;
        }

        // Numbers.
        if (Character.isDigit(lastChar) || lastChar == '.') {
            final StringBuilder numStr = new StringBuilder();
            boolean floating = false;

            if (lastChar == '.') {
                read();
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

            return Tokens.DIV.value;
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