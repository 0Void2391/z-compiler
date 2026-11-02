package Tests;

import org.junit.jupiter.api.Test;
import src.Lexer.Lexer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMoreLexer {

    private static Lexer lexer(final String source) {
        return new Lexer(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void testTwoDotsStayTwoDots() throws IOException {
        final Lexer lex = lexer("..x");

        assertEquals('.', lex.getTok());
        assertEquals('.', lex.getTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
    }

    @Test
    void testLoneDotIsADot() throws IOException {
        final Lexer lex = lexer(".");

        assertEquals('.', lex.getTok());
        assertEquals(Lexer.Tokens.EOF.value, lex.getTok());
    }

    @Test
    void testThreeDotsAfterAnIdentifierStayDots() throws IOException {
        final Lexer lex = lexer("x...");

        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals('.', lex.getTok());
        assertEquals('.', lex.getTok());
        assertEquals('.', lex.getTok());
        assertEquals(Lexer.Tokens.EOF.value, lex.getTok());
    }

    @Test
    void testLeadDotNumberAfterIdentifier() throws IOException {
        final Lexer lex = lexer("x.5");

        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals('.', lex.getTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
        assertEquals(5, lex.getNumVal());
    }

    @Test
    void testMalformedNumberBecomesZero() throws IOException {
        final Lexer lex = lexer("1e");

        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
        assertEquals("1e", lex.getNumberText());
        assertEquals(0.0, lex.getNumVal());
        assertTrue(lex.getNumIsFloat());
        assertFalse(lex.getNumIsInteger());
    }

    @Test
    void testAllStringEscapes() throws IOException {
        final Lexer lex = lexer("\"\\r\\0\\q\"");

        assertEquals(Lexer.Tokens.STRING_LITERAL.value, lex.getTok());
        assertEquals("\r\0q", lex.getStringVal());
    }

    @Test
    void testBareNotIsItsAsciiCode() throws IOException {
        final Lexer lex = lexer("!");

        assertEquals('!', lex.getTok());
    }

    @Test
    void testNotEqual() throws IOException {
        final Lexer lex = lexer("!=");

        assertEquals(Lexer.Tokens.NE.value, lex.getTok());
    }

    @Test
    void testEqualAndOrderingOperators() throws IOException {
        final Lexer lex = lexer("== <= >= < > =");

        assertEquals(Lexer.Tokens.EQ.value, lex.getTok());
        assertEquals(Lexer.Tokens.LE.value, lex.getTok());
        assertEquals(Lexer.Tokens.GE.value, lex.getTok());
        assertEquals(Lexer.Tokens.LESS.value, lex.getTok());
        assertEquals(Lexer.Tokens.MORE.value, lex.getTok());
        assertEquals(Lexer.Tokens.SAME.value, lex.getTok());
    }

    @Test
    void testMinusAtEndOfInputIsSubtraction() throws IOException {
        final Lexer lex = lexer("-");

        assertEquals(Lexer.Tokens.SUB.value, lex.getTok());
        assertEquals(Lexer.Tokens.EOF.value, lex.getTok());
    }

    @Test
    void testCarriageReturnEndsALineComment() throws IOException {
        final Lexer lex = lexer("// comment\rret");

        assertEquals(Lexer.Tokens.RET.value, lex.getTok());
    }

    @Test
    void testIdentifierLookalikesAreNotKeywords() throws IOException {
        final Lexer lex = lexer("procx int32x eofx numbers");

        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals("procx", lex.getIdentifierStr());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals("int32x", lex.getIdentifierStr());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals("eofx", lex.getIdentifierStr());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals("numbers", lex.getIdentifierStr());
    }
}
