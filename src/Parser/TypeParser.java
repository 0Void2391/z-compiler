package src.Parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import src.Lexer.Lexer;

import java.io.IOException;

public class TypeParser {
    private static final Logger logger = LoggerFactory.getLogger(TypeParser.class);
    private final ParserContext context;

    public TypeParser(final Parser parser) {
        this.context = parser.getContext();
    }

    /**
     * The type a name written in the source refers to.
     *
     * @param name a type name as it appears in the program
     * @return the type, or null if there is no such type
     */
    protected static ZType resolveType(final String name) {
        try {
            return ZType.fromZName(name);
        } catch (final IllegalArgumentException e) {
            if (logger.isErrorEnabled()) {
                logger.error("Error: {}", e.getMessage());
            }
            return null;
        }
    }

    /**
     * Checks if a token is a type token
     *
     * @param token the token to check, which may be null
     * @return true if the token is a type token, false otherwise
     */
    protected static boolean isTypeToken(final Lexer.Tokens token) {
        if (token == null) return false;
        if (ZType.isType(token.description)) return true;
        return switch (token) {
            case STRING, UINT32, UINT64, BOOL, VOID -> true;
            default -> false;
        };
    }

    /**
     * Parses a type: a type keyword, optionally followed by {@code *} for a pointer.
     *
     * @return the type, or null when the current token is not a type
     * @throws IOException if an exception is found
     */
    public TypeRef parseType(final boolean varargs) throws IOException {
        final Lexer.Tokens typeTok = Lexer.Tokens.fromValues(context.getCurTok());
        if (typeTok == Lexer.Tokens.VARARG) {
            if (!varargs) return null;
            context.next();
            return ZType.VARARG;
        }

        // Built-in type or user-declared struct?
        TypeRef type = null;
        if (isTypeToken(typeTok)) {
            type = resolveType(typeTok.description);
        } else if (typeTok == Lexer.Tokens.IDENTIFIER
                && StructRegistry.isStruct(context.getLex().getIdentifierStr())) {
            type = StructRegistry.lookup(context.getLex().getIdentifierStr());
        }
        if (type == null) return null;
        context.next();

        while (context.getCurTok() == Lexer.Tokens.MUL.value) {
            type = type.pointerTo();
            context.next();
        }
        return type;
    }

    /**
     * Names a token for an error message.
     *
     * @param token the token value
     * @return its description, its character for punctuation, or its raw value
     */
    protected static String describeToken(final int token) {
        final Lexer.Tokens known = Lexer.Tokens.fromValues(token);
        if (known != null) return known.description;
        if (token > 0 && token <= Character.MAX_VALUE) return "'" + (char) token + "'";
        return String.valueOf(token);
    }


    /**
     * @return whether the parser is positioned on a type keyword
     */
    public boolean atTypeDeclaration() {
        return isTypeToken(Lexer.Tokens.fromValues(context.getCurTok()))
                || (context.getCurTok() == Lexer.Tokens.IDENTIFIER.value
                && StructRegistry.isStruct(context.getLex().getIdentifierStr()));
    }
}
