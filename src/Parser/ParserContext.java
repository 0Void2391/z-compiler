package src.Parser;

import src.Lexer.Lexer;
import java.io.IOException;

public final class ParserContext {
    /**
     * curTok stores the current Token from the Lexer
     * lex stores the Lexer
     */
    private int curTok;
    private final Lexer lex;

    public ParserContext(final Lexer lex) throws IOException {
        this.lex = lex;
        this.curTok = lex.getTok();
    }

    /**
     * Helper method to get the next token from the context.getLex()er
     *
     * @throws IOException if an exception is found
     */
    public void next() throws IOException {
        this.curTok = lex.getTok();
    }

    public int getCurTok() { return curTok; }
    public Lexer getLex() { return lex; }
}