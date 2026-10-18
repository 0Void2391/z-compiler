package Tests;

import src.lexer.Lexer;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;


public class TestLexer {

    @Test
    void testProcDeclarationTokens() throws IOException {
        // for now (int64 fails because it gets treated as one string
        String code = "proc name ( int64 name | int64 name) { \n ret 281 \n }";

        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes()));

        assertEquals(Lexer.Tokens.FUNC.value, lex.getTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals(Lexer.Tokens.OPEN_PAR.value, lex.getTok());
        assertEquals(Lexer.Tokens.INT64.value, lex.getTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals(Lexer.Tokens.PARAMS.value, lex.getTok());
        assertEquals(Lexer.Tokens.INT64.value, lex.getTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals(Lexer.Tokens.CLOSE_PAR.value, lex.getTok());
        assertEquals(Lexer.Tokens.OPEN_FUNC.value, lex.getTok());
        assertEquals(Lexer.Tokens.RET.value, lex.getTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
        assertEquals(Lexer.Tokens.CLOSE_FUNC.value, lex.getTok());
    }

    @Test
    void testConditionalTokens() throws IOException {
        // for now (int64 fails because it gets treated as one string
        final String code = "if name > 64 { \n\n } eif name < 32 { \n \n } else { \n \n }";

        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes()));

        assertEquals(Lexer.Tokens.IF.value, lex.getTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals(Lexer.Tokens.MORE.value, lex.getTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
        assertEquals(Lexer.Tokens.OPEN_FUNC.value, lex.getTok());
        assertEquals(Lexer.Tokens.CLOSE_FUNC.value, lex.getTok());
        assertEquals(Lexer.Tokens.EIF.value, lex.getTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals(Lexer.Tokens.LESS.value, lex.getTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
        assertEquals(Lexer.Tokens.OPEN_FUNC.value, lex.getTok());
        assertEquals(Lexer.Tokens.CLOSE_FUNC.value, lex.getTok());
        assertEquals(Lexer.Tokens.ELSE.value, lex.getTok());
        assertEquals(Lexer.Tokens.OPEN_FUNC.value, lex.getTok());
        assertEquals(Lexer.Tokens.CLOSE_FUNC.value, lex.getTok());
    }

    @Test
    void testAssignmentOperator() throws IOException {
        final String code = "->";

        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes()));

        assertEquals(Lexer.Tokens.ASSIGN.value, lex.getTok());
    }

    @Test
    void testSlashComments() throws IOException {
        final String code = "// this is a comment\nret";

        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes()));

        assertEquals(Lexer.Tokens.RET.value, lex.getTok());
    }

    @Test
    void testConstantToken() throws IOException {
        final String code = "cn int64 PI -> 314";
        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes()));

        assertEquals(Lexer.Tokens.CN.value, lex.getTok());
        assertEquals(Lexer.Tokens.INT64.value, lex.getTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals("PI", lex.identifierStr);
        assertEquals(Lexer.Tokens.ASSIGN.value, lex.getTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
        assertEquals(314.0, lex.numVal);
    }

    @Test
    void testImport() throws IOException {
        final String code = "import libc";
        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.IMPORT.value, lex.getTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals("libc", lex.identifierStr);
    }

    @Test
    void testVariableAssignment() throws IOException {
        final String code = "int32 var -> 30";
        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.INT32.value, lex.getTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals(Lexer.Tokens.ASSIGN.value, lex.getTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
    }

    @Test
    void testExtern() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream("extern test(int32)".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.EXTERN.value, lex.getTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals(Lexer.Tokens.OPEN_PAR.value, lex.getTok());
        assertEquals(Lexer.Tokens.INT32.value, lex.getTok());
        assertEquals(Lexer.Tokens.CLOSE_PAR.value, lex.getTok());
    }

    @Test
    void testIntegerLiteralKeepsItsText() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "9007199254740993".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
        assertEquals("9007199254740993", lex.numberText);
        assertTrue(lex.numIsInteger);
        assertFalse(lex.numIsFloat);
    }

    @Test
    void testFloatLiteralIsMarkedAsFloating() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "3.14".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
        assertEquals("3.14", lex.numberText);
        assertTrue(lex.numIsFloat);
    }

    @Test
    void testExponentIsAFloatLiteral() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "1e3".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
        assertEquals("1e3", lex.numberText);
        assertTrue(lex.numIsFloat);
        assertEquals(1000.0, lex.numVal);
    }

    @Test
    void testSubtractionIsNotAnAssignment() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "- 5".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.SUB.value, lex.getTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
    }

    @Test
    void testEndOfInput() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "ret".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.RET.value, lex.getTok());
        assertEquals(Lexer.Tokens.EOF.value, lex.getTok());
        assertEquals(Lexer.Tokens.EOF.value, lex.getTok());
    }

    @Test
    void testTypeKeywords() throws IOException {
        final String code = "int32 int64 flt32 flt64";

        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.INT32.value, lex.getTok());
        assertEquals(Lexer.Tokens.INT64.value, lex.getTok());
        assertEquals(Lexer.Tokens.FLOAT32.value, lex.getTok());
        assertEquals(Lexer.Tokens.FLOAT64.value, lex.getTok());
        assertEquals(Lexer.Tokens.EOF.value, lex.getTok());
    }

    @Test
    void testForKeywordAndItsSymbols() throws IOException {
        final String code = "for (int32 i -> 0 | i < 10 | i + 1) {";

        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.FOR.value, lex.getTok());
        assertEquals(Lexer.Tokens.OPEN_PAR.value, lex.getTok());
        assertEquals(Lexer.Tokens.INT32.value, lex.getTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals("i", lex.identifierStr);
        assertEquals(Lexer.Tokens.ASSIGN.value, lex.getTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
        assertEquals(Lexer.Tokens.PARAMS.value, lex.getTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals(Lexer.Tokens.LESS.value, lex.getTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
        assertEquals(Lexer.Tokens.PARAMS.value, lex.getTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals(Lexer.Tokens.ADD.value, lex.getTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
        assertEquals(Lexer.Tokens.CLOSE_PAR.value, lex.getTok());
        assertEquals(Lexer.Tokens.OPEN_FUNC.value, lex.getTok());
    }

    @Test
    void testDivSymbol() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "7 / 2".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
        assertEquals(Lexer.Tokens.DIV.value, lex.getTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.getTok());
    }

    @Test
    void testIdentifierKeepsItsName() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "myVar42".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.getTok());
        assertEquals("myVar42", lex.identifierStr);
    }

    @Test
    void testCommentAtEndOfInput() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "ret // trailing".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.RET.value, lex.getTok());
        assertEquals(Lexer.Tokens.EOF.value, lex.getTok());
    }

    @Test
    void testUnknownCharacterIsItsAsciiCode() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "@".getBytes(StandardCharsets.UTF_8)));

        assertEquals('@', lex.getTok());
    }

    @Test
    void testStringLiteral() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "\"hello\"".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.STRING_LITERAL.value, lex.getTok());
        assertEquals("hello", lex.stringVal);
    }

    @Test
    void testStringLiteralEscapes() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "\"a\\nb\\t\\\\\\\"c\"".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.STRING_LITERAL.value, lex.getTok());
        assertEquals("a\nb\t\\\"c", lex.stringVal);
    }

    @Test
    void testUnterminatedStringLiteral() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "\"hello".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.EOF.value, lex.getTok());
    }
}