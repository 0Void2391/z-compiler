package src.Parser;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import src.AST.ArrayAST;
import src.AST.ArrayIdxExprAST;
import src.AST.AssignExprAST;
import src.AST.ExprAST;
import src.AST.IfAST;
import src.AST.NumberExprAST;
import src.AST.VarRefExprAST;
import src.Lexer.Lexer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParserInternalsTest {

    private static Parser parser(final String source) throws IOException {
        return new Parser(new Lexer(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8))));
    }

    private static ArrayAST array(final String source) throws IOException {
        final Parser parser = parser(source);
        parser.getContext().next();
        return parser.getArrayParser().parseArray();
    }

    @AfterEach
    void clean() {
        StructRegistry.clear();
    }

    @Test
    void testSubParsersAreExposed() throws IOException {
        final Parser parser = parser("proc main() { ret 0 }");

        assertNotNull(parser.getDefinitionParser());
        assertNotNull(parser.getArrayParser());
        assertNotNull(parser.getParserType());
        assertNotNull(parser.getHeaderParser());
        assertNotNull(parser.getExpressionParser());
        assertNotNull(parser.getBranchesParser());
        assertNotNull(parser.getLoopParser());
        assertNotNull(parser.getRetParser());
        assertNotNull(parser.getStructParser());
        assertNotNull(parser.getVariableParser());
        assertSame(parser.getContext().getLex(), parser.getContext().getLex());
    }

    @Test
    void testDirectExpressionEntryPoints() throws IOException {
        assertInstanceOf(NumberExprAST.class, parser("42").parseNumberExpr(42));
        assertInstanceOf(NumberExprAST.class, parser("42").parsePrimary());
        assertInstanceOf(VarRefExprAST.class, parser("x").parseIdentifierExpr());
        assertInstanceOf(VarRefExprAST.class, parser("x").parsePrimary());
        assertNotNull(parser("(1 + 2)").parseParenExpr());
        assertNotNull(parser("(1 + 2)").parseExpression());
        assertNotNull(parser("+ 1").parseBinOpRHS(0, new NumberExprAST("1", 1)));
    }

    @Test
    void testIfBranchEntryPoint() throws IOException {
        final IfAST.Branch branch = parser("(1 < 2) { ret 1 }").parseIfBranch("if");

        assertNotNull(branch);
        assertNotNull(branch.cond());
        assertNotNull(branch.body());
    }

    @Test
    void testTypeDeclarationPredicate() throws IOException {
        StructRegistry.register(new StructType("Point", java.util.List.of()));
        assertTrue(parser("Point").atTypeDeclaration());
        assertTrue(parser("int32").atTypeDeclaration());
        assertFalse(parser("42").atTypeDeclaration());
    }

    @Test
    void testVarargType() throws IOException {
        assertSame(ZType.VARARG, parser("...").parseType(false));
    }

    @Test
    void testUnknownTypeIsNull() throws IOException {
        assertNull(parser("nope").parseType(false));
    }

    @Test
    void testMainLoopSkipsSemicolonsAndUnknownTokens() throws IOException {
        parser("; ; @ ; int32 x ;").mainLoop();
    }

    @Test
    void testMainLoopHandlesExternAndArrays() throws IOException {
        parser("extern sqrt(flt64) -> flt64").mainLoop();
        parser("[int32|3|1] values").mainLoop();
    }

    @Test
    void testParserErrorReturnsNull() {
        assertNull(Parser.error("boom"));
    }

    @Test
    void testTypeHelpers() {
        assertFalse(TypeParser.isTypeToken(null));
        assertTrue(TypeParser.isTypeToken(Lexer.Tokens.STRING));
        assertTrue(TypeParser.isTypeToken(Lexer.Tokens.INT32));
        assertFalse(TypeParser.isTypeToken(Lexer.Tokens.IDENTIFIER));
        assertSame(ZType.INT32, TypeParser.resolveType("int32"));
        assertNull(TypeParser.resolveType("int7"));
    }

    @Test
    void testTokenDescriptions() {
        assertEquals("int32", TypeParser.describeToken(Lexer.Tokens.INT32.value));
        assertEquals("'$'", TypeParser.describeToken('$'));
        assertEquals("70000", TypeParser.describeToken(70000));
        assertEquals("-999", TypeParser.describeToken(-999));
    }

    @Test
    void testLiteralKeepsIntegerText() {
        final NumberExprAST number = ExpressionParser.literal(1.0, "1", false);

        assertEquals("1", number.text());
        assertFalse(number.isFloating());
    }

    @Test
    void testLiteralFallsBackWhenTextIsNotALong() {
        final NumberExprAST number = ExpressionParser.literal(1.0, "1e", false);

        assertEquals("1", number.text());
        assertFalse(number.isFloating());
    }

    @Test
    void testLiteralKeepsFractionsAndHugeValues() {
        assertEquals("1.5", ExpressionParser.literal(1.5, "1.5", false).text());
        assertEquals("1.0E20", ExpressionParser.literal(1e20, null, false).text());
        assertEquals("1.0", ExpressionParser.literal(1, "1", true).text());
    }

    @Test
    void testArrayWithoutValues() throws IOException {
        final ArrayAST array = array("[int32|5|2] values");

        assertNotNull(array);
        assertEquals("values", array.getName());
        assertEquals(0, array.getValues().size());
        assertEquals(ZType.INT32_PTR, array.type(null));
        assertEquals("placeholder", array.codeGen(null).text());
    }

    @Test
    void testArrayWithValues() throws IOException {
        final ArrayAST array = array("[int32|3|1] values { -> 1|2|3 }");

        assertNotNull(array);
        assertEquals(3, array.getValues().size());
        assertEquals("1", array.getValues().getFirst().text());
    }

    @Test
    void testArrayAcceptsEqualsAfterTheBrace() throws IOException {
        final ArrayAST array = array("[int32|2|1] values { = 7|8 }");

        assertNotNull(array);
        assertEquals(2, array.getValues().size());
    }

    @Test
    void testArrayStopsAtTheClosingBrace() throws IOException {
        final ArrayAST array = array("[int32|3|1] values { -> 1 }");

        assertNotNull(array);
        assertEquals(1, array.getValues().size());
    }

    @Test
    void testArrayIgnoresExtraValues() throws IOException {
        final ArrayAST array = array("[int32|2|1] values { -> 1|2|3 }");

        assertNotNull(array);
        assertEquals(2, array.getValues().size());
    }

    @Test
    void testArrayRequiresAType() throws IOException {
        assertNull(array("[nope|3|1] values"));
    }

    @Test
    void testArrayRejectsATypeWithoutAPointerForm() throws IOException {
        assertNull(array("[ptr|3|1] values"));
    }

    @Test
    void testArrayRequiresThePipeAfterTheType() throws IOException {
        assertNull(array("[int32 3|1] values"));
    }

    @Test
    void testArrayRequiresASize() throws IOException {
        assertNull(array("[int32|] values"));
    }

    @Test
    void testArrayRequiresADepth() throws IOException {
        assertNull(array("[int32|3|] values"));
    }

    @Test
    void testArrayAcceptsAZeroSize() throws IOException {
        final ArrayAST array = array("[int32|0|1] values");

        assertNotNull(array);
        assertEquals("0", array.getSize().text());
    }

    @Test
    void testArrayRequiresTheClosingBracket() throws IOException {
        assertNull(array("[int32|3|1 values"));
    }

    @Test
    void testArrayRequiresAName() throws IOException {
        assertNull(array("[int32|3|1]"));
    }

    @Test
    void testArrayRequiresAnArrowBeforeItsValues() throws IOException {
        assertNull(array("[int32|3|1] values { 1 }"));
    }

    @Test
    void testArrayRequiresASeparatorBetweenValues() throws IOException {
        assertNull(array("[int32|3|1] values { -> 1 2 }"));
    }

    @Test
    void testParenthesisedAssignmentTarget() throws IOException {
        final ExprAST expression = parser("(x) -> 1").parseExpression();

        assertInstanceOf(AssignExprAST.class, expression);
        assertEquals("x", ((AssignExprAST) expression).varName());
    }

    @Test
    void testVarargIndexReference() throws IOException {
        final ExprAST expression = parser("args.1").parseExpression();

        assertEquals("args.1", assertInstanceOf(VarRefExprAST.class, expression).name());
    }

    @Test
    void testBracesCanWrapAPrimaryExpression() throws IOException {
        final Parser parser = parser("{ 1 }");

        assertInstanceOf(NumberExprAST.class, parser.parsePrimary());
    }


    @Test
    void testEveryBuiltInTokenIsAType() {
        for (final Lexer.Tokens token : new Lexer.Tokens[]{
                Lexer.Tokens.STRING, Lexer.Tokens.UINT32, Lexer.Tokens.UINT64,
                Lexer.Tokens.BOOL, Lexer.Tokens.VOID, Lexer.Tokens.INT64,
                Lexer.Tokens.FLOAT32, Lexer.Tokens.FLOAT64, Lexer.Tokens.PTR}) {
            assertTrue(TypeParser.isTypeToken(token));
        }
    }

    @Test
    void testArrayIndexingThroughTheBinaryEntryPoint() throws IOException {
        final Parser parser = parser("x[[1]]");
        final ExprAST result = parser.parseBinOpRHS(-1, parser.parsePrimary());

        assertInstanceOf(ArrayIdxExprAST.class, result);
        assertEquals(1, ((ArrayIdxExprAST) result).getIndexes().size());
    }

    @Test
    void testArrayIndexingNeedsANumberAndAClosingBracket() throws IOException {
        final Parser missingNumber = parser("x[[value]]");
        assertNotNull(missingNumber.parseBinOpRHS(-1, missingNumber.parsePrimary()));

        final Parser missingBracket = parser("x[[1] 2");
        assertNull(missingBracket.parseBinOpRHS(-1, missingBracket.parsePrimary()));
    }

    @Test
    void testAssignmentToADottedVarRefFails() throws IOException {
        assertNull(parser("args.1 -> 2").parseExpression());
    }

    @Test
    void testNestedPrecedenceFailureReturnsNull() throws IOException {
        assertNull(parser("1 + 2 *").parseExpression());
    }

    @Test
    void testParenWithAMissingExpressionFails() throws IOException {
        assertNull(parser("()").parseExpression());
    }

    @Test
    void testArrayStatementEntryPoint() throws IOException {
        assertNull(parser("[int32|3|1] values").parseStatement());
    }

    @Test
    void testArrayDepthBranch() throws IOException {
        final ArrayAST array = array("[int32|5|2|3] 9 values");

        assertNotNull(array);
        assertEquals("values", array.getName());
        assertEquals("3", array.getDepth().text());
    }

    @Test
    void testArrayDepthBranchErrors() throws IOException {
        assertNotNull(array("[int32|5|2|x] 9 values"));
        assertNull(array("[int32|0|1|2] 9 values"));
    }

    @Test
    void testPrototypeNeedsAnOpeningParenthesis() throws IOException {
        assertNull(parser("f").parsePrototype(false));
    }
}
