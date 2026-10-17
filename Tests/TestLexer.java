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

        assertEquals(Lexer.Tokens.FUNC.value, lex.GetTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.OPEN_PAR.value, lex.GetTok());
        assertEquals(Lexer.Tokens.INT64.value, lex.GetTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.PARAMS.value, lex.GetTok());
        assertEquals(Lexer.Tokens.INT64.value, lex.GetTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.CLOSE_PAR.value, lex.GetTok());
        assertEquals(Lexer.Tokens.OPEN_FUNC.value, lex.GetTok());
        assertEquals(Lexer.Tokens.RET.value, lex.GetTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.CLOSE_FUNC.value, lex.GetTok());
    }

    @Test
    void testConditionalTokens() throws IOException {
        // for now (int64 fails because it gets treated as one string
        final String code = "if name > 64 { \n\n } eif name < 32 { \n \n } else { \n \n }";

        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes()));

        assertEquals(Lexer.Tokens.IF.value, lex.GetTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.MORE.value, lex.GetTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.OPEN_FUNC.value, lex.GetTok());
        assertEquals(Lexer.Tokens.CLOSE_FUNC.value, lex.GetTok());
        assertEquals(Lexer.Tokens.EIF.value, lex.GetTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.LESS.value, lex.GetTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.OPEN_FUNC.value, lex.GetTok());
        assertEquals(Lexer.Tokens.CLOSE_FUNC.value, lex.GetTok());
        assertEquals(Lexer.Tokens.ELSE.value, lex.GetTok());
        assertEquals(Lexer.Tokens.OPEN_FUNC.value, lex.GetTok());
        assertEquals(Lexer.Tokens.CLOSE_FUNC.value, lex.GetTok());
    }

    @Test
    void testAssignmentOperator() throws IOException {
        final String code = "->";

        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes()));

        assertEquals(Lexer.Tokens.ASSIGN.value, lex.GetTok());
    }

    @Test
    void testSlashComments() throws IOException {
        final String code = "// this is a comment\nret";

        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes()));

        assertEquals(Lexer.Tokens.RET.value, lex.GetTok());
    }

    @Test
    void testConstantToken() throws IOException {
        final String code = "cn int64 PI -> 314";
        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes()));

        assertEquals(Lexer.Tokens.CN.value, lex.GetTok());
        assertEquals(Lexer.Tokens.INT64.value, lex.GetTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.GetTok());
        assertEquals("PI", lex.IdentifierStr);
        assertEquals(Lexer.Tokens.ASSIGN.value, lex.GetTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.GetTok());
        assertEquals(314.0, lex.NumVal);
    }

    @Test
    void testImport() throws IOException {
        final String code = "import libc";
        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.IMPORT.value, lex.GetTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.GetTok());
        assertEquals("libc", lex.IdentifierStr);
    }

    @Test
    void testVariableAssignment() throws IOException {
        final String code = "int32 var -> 30";
        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.INT32.value, lex.GetTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.ASSIGN.value, lex.GetTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.GetTok());
    }

    @Test
    void testExtern() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream("extern test(int32)".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.EXTERN.value, lex.GetTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.OPEN_PAR.value, lex.GetTok());
        assertEquals(Lexer.Tokens.INT32.value, lex.GetTok());
        assertEquals(Lexer.Tokens.CLOSE_PAR.value, lex.GetTok());
    }

    @Test
    void testIntegerLiteralKeepsItsText() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "9007199254740993".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.NUMBER.value, lex.GetTok());
        assertEquals("9007199254740993", lex.NumberText);
        assertTrue(lex.NumIsInteger);
        assertFalse(lex.NumIsFloat);
    }

    @Test
    void testFloatLiteralIsMarkedAsFloating() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "3.14".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.NUMBER.value, lex.GetTok());
        assertEquals("3.14", lex.NumberText);
        assertTrue(lex.NumIsFloat);
    }

    @Test
    void testExponentIsAFloatLiteral() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "1e3".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.NUMBER.value, lex.GetTok());
        assertEquals("1e3", lex.NumberText);
        assertTrue(lex.NumIsFloat);
        assertEquals(1000.0, lex.NumVal);
    }

    @Test
    void testSubtractionIsNotAnAssignment() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "- 5".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.SUB.value, lex.GetTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.GetTok());
    }

    @Test
    void testEndOfInput() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "ret".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.RET.value, lex.GetTok());
        assertEquals(Lexer.Tokens.EOF.value, lex.GetTok());
        assertEquals(Lexer.Tokens.EOF.value, lex.GetTok());
    }

    @Test
    void testTypeKeywords() throws IOException {
        final String code = "int32 int64 flt32 flt64";

        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.INT32.value, lex.GetTok());
        assertEquals(Lexer.Tokens.INT64.value, lex.GetTok());
        assertEquals(Lexer.Tokens.FLOAT32.value, lex.GetTok());
        assertEquals(Lexer.Tokens.FLOAT64.value, lex.GetTok());
        assertEquals(Lexer.Tokens.EOF.value, lex.GetTok());
    }

    @Test
    void testForKeywordAndItsSymbols() throws IOException {
        final String code = "for (int32 i -> 0 | i < 10 | i + 1) {";

        final Lexer lex = new Lexer(new ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.FOR.value, lex.GetTok());
        assertEquals(Lexer.Tokens.OPEN_PAR.value, lex.GetTok());
        assertEquals(Lexer.Tokens.INT32.value, lex.GetTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.GetTok());
        assertEquals("i", lex.IdentifierStr);
        assertEquals(Lexer.Tokens.ASSIGN.value, lex.GetTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.PARAMS.value, lex.GetTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.LESS.value, lex.GetTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.PARAMS.value, lex.GetTok());
        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.ADD.value, lex.GetTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.CLOSE_PAR.value, lex.GetTok());
        assertEquals(Lexer.Tokens.OPEN_FUNC.value, lex.GetTok());
    }

    @Test
    void testDivSymbol() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "7 / 2".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.NUMBER.value, lex.GetTok());
        assertEquals(Lexer.Tokens.DIV.value, lex.GetTok());
        assertEquals(Lexer.Tokens.NUMBER.value, lex.GetTok());
    }

    @Test
    void testIdentifierKeepsItsName() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "myVar42".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.IDENTIFIER.value, lex.GetTok());
        assertEquals("myVar42", lex.IdentifierStr);
    }

    @Test
    void testCommentAtEndOfInput() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "ret // trailing".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.RET.value, lex.GetTok());
        assertEquals(Lexer.Tokens.EOF.value, lex.GetTok());
    }

    @Test
    void testUnknownCharacterIsItsAsciiCode() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "@".getBytes(StandardCharsets.UTF_8)));

        assertEquals('@', lex.GetTok());
    }

    @Test
    void testStringLiteral() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "\"hello\"".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.STRING_LITERAL.value, lex.GetTok());
        assertEquals("hello", lex.StringVal);
    }

    @Test
    void testStringLiteralEscapes() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "\"a\\nb\\t\\\\\\\"c\"".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.STRING_LITERAL.value, lex.GetTok());
        assertEquals("a\nb\t\\\"c", lex.StringVal);
    }

    @Test
    void testUnterminatedStringLiteral() throws IOException {
        final Lexer lex = new Lexer(new ByteArrayInputStream(
                "\"hello".getBytes(StandardCharsets.UTF_8)));

        assertEquals(Lexer.Tokens.EOF.value, lex.GetTok());
    }
}