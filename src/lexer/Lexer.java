package src.lexer;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;


/**
 * Parses the source code and returns various Tokens, if it's unknown returns the ASCII value
 */
public class Lexer {

    /**
     * Enum of Tokens
     */
    public enum Tokens {
        FUNC("proc", -1),
        INT64("int64", -2),
        NUMBER("unused", -3), // number returns
        RET("ret", -4),
        INT32("int32", -5),
        FLOAT32("flt32", -6),
        FLOAT64("flt64", -7),
        STRING("str", -8),
        BOOL("01", -9),
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
        IDENTIFIER("id", -34),
        CN("cn", -35),
        STRING_LITERAL("string-literal", -36);

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
    private int LastChar = ' ';
    public String IdentifierStr;
    public double NumVal;
    /** the number exactly as it was written, which NumVal cannot represent */
    public String NumberText;
    /** true when the literal had a decimal point or an exponent */
    public boolean NumIsFloat;
    /** true when the literal was written without one */
    public boolean NumIsInteger;
    /** the contents of a string literal, after escape processing */
    public String StringVal;

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

    /**
     * Returns the next token
     * @return the next token
     * @throws IOException if an I/O error occurs
     */
    public int GetTok() throws IOException {

        // skips whitespaces
        while (Character.isWhitespace(LastChar)) {
            LastChar = input.read();
        }

        // EOF for some fucking reason in java is -1
        if (LastChar == -1) {
            return Tokens.EOF.value;
        }

        // assignment operator ->
        if (LastChar == '-') {
            LastChar = input.read();

            if (LastChar == '>') {
                LastChar = input.read();
                return Tokens.ASSIGN.value;
            }

            return Tokens.SUB.value;
        }

        // chars

        if (Character.isAlphabetic(LastChar)) {
                final StringBuilder sb = new StringBuilder();

                do {
                    sb.append((char) LastChar);
                    LastChar = input.read();
                } while (Character.isLetterOrDigit(LastChar));

                IdentifierStr = sb.toString();

                // hashmap should be faster
                final Tokens token = keywordMap.get(IdentifierStr);
                if (token != null && token != Tokens.IDENTIFIER && token != Tokens.NUMBER && token != Tokens.EOF) {
                    return token.value;
                }

                // if it's not a word it must be an identifier ig
                return Tokens.IDENTIFIER.value;
        }

            // Numbers.
            //
            // The spelling is kept as well as the value. NumVal is a double, and
            // a double cannot hold 9007199254740993 — it comes back as
            // 9007199254740992, one out, with nothing to say so. Carrying the text
            // lets an integer literal survive to the IR as the number that was
            // written, which is the only way an int64 literal above 2^53 can be
            // correct.
            if (Character.isDigit(LastChar) || LastChar == '.') {
                final StringBuilder NumStr = new StringBuilder();
                boolean floating = false;

                do {
                    if (LastChar == '.' || LastChar == 'e' || LastChar == 'E') {
                        floating = true;
                    }
                    NumStr.append((char) LastChar);
                    LastChar = input.read();
                } while (Character.isDigit(LastChar) || LastChar == '.'
                        || LastChar == 'e' || LastChar == 'E');

                NumberText = NumStr.toString();
                try {
                    NumVal = Double.parseDouble(NumberText);
                } catch (final NumberFormatException e) {
                    System.err.println("Error: malformed number: " + NumberText);
                    NumVal = 0;
                }
                NumIsFloat = floating;
                NumIsInteger = !floating;
                return Tokens.NUMBER.value;
            }

        // String literals: "hello\n"
        if (LastChar == '"') {
            final StringBuilder sb = new StringBuilder();
            LastChar = input.read();
            while (LastChar != '"' && LastChar != -1) {
                if (LastChar == '\\') {
                    LastChar = input.read();
                    switch (LastChar) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case '\\' -> sb.append('\\');
                        case '"' -> sb.append('"');
                        case '0' -> sb.append('\0');
                        default -> sb.append((char) LastChar);
                    }
                } else {
                    sb.append((char) LastChar);
                }
                LastChar = input.read();
            }
            if (LastChar == -1) {
                System.err.println("Error: unterminated string literal");
                return Tokens.EOF.value;
            }
            LastChar = input.read();  // consume closing quote
            StringVal = sb.toString();
            return Tokens.STRING_LITERAL.value;
        }


        // Comments
        if (LastChar == '/') {
            LastChar = input.read();

            if (LastChar == '/') {
                do {
                    LastChar = input.read();
                } while (LastChar != -1 && LastChar != '\n' && LastChar != '\r');

                return GetTok();
            }

            return Tokens.DIV.value;
        }

        // symbols
        final Tokens symbol = keywordMap.get(Character.toString((char) LastChar));

        if (symbol != null) {
            LastChar = input.read();
            return symbol.value;
        }

            // returns the ASCII if unrecognized
            final int ThisChar = LastChar;

            // next one
            LastChar = input.read();

            return ThisChar;
        }

    }